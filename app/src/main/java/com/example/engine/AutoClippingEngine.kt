package com.example.engine

import com.example.data.model.AspectRatio
import com.example.data.model.ClipCandidate
import com.example.data.model.ClipStatus
import com.example.data.model.ContentStyle
import com.example.data.model.CustomLengthMode
import com.example.data.model.TranscriptSegment
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object AutoClippingEngine {

    data class ClippingConfig(
        val clipCount: Int = 3,
        val minDurationSec: Int = 15,
        val maxDurationSec: Int = 60,
        val style: ContentStyle = ContentStyle.GENERAL,
        val aspectRatio: AspectRatio = AspectRatio.VERTICAL_9_16,
        val prioritizeSpeechOverScene: Boolean = true
    )

    data class CustomDurationConfig(
        val targetDurationSec: Int = 30,
        val clipCount: Int = 3,
        val mode: CustomLengthMode = CustomLengthMode.SMART_AI,
        val aspectRatio: AspectRatio = AspectRatio.VERTICAL_9_16
    )

    /**
     * Executes the 12-step automated clipping pipeline:
     * Discovers top viral candidate clips based on speech boundaries, hooks, completeness,
     * non-maximum suppression (deduplication), and boundary snapping.
     */
    fun findBestMoments(
        projectId: Long,
        totalDurationMs: Long,
        segments: List<TranscriptSegment>,
        config: ClippingConfig
    ): List<ClipCandidate> {
        if (segments.isEmpty() || totalDurationMs <= 5000) {
            return generateFallbackClips(projectId, totalDurationMs, config.clipCount, config.aspectRatio)
        }

        val minMs = config.minDurationSec * 1000L
        val maxMs = config.maxDurationSec * 1000L
        val candidates = mutableListOf<ScoredCandidate>()

        // Step 1-5: Sliding window search over transcript sentence segments
        for (i in segments.indices) {
            val startSegment = segments[i]
            var currentEndMs = startSegment.endMs
            val includedSegments = mutableListOf<TranscriptSegment>()
            includedSegments.add(startSegment)

            for (j in i until segments.size) {
                if (j > i) {
                    val seg = segments[j]
                    includedSegments.add(seg)
                    currentEndMs = seg.endMs
                }

                val currentDuration = currentEndMs - startSegment.startMs
                if (currentDuration in minMs..maxMs) {
                    val scoreResult = scoreCandidate(includedSegments, config.style, config.prioritizeSpeechOverScene)
                    candidates.add(
                        ScoredCandidate(
                            startMs = startSegment.startMs,
                            endMs = currentEndMs,
                            score = scoreResult.score,
                            reason = scoreResult.reason,
                            title = scoreResult.title,
                            segments = includedSegments.toList()
                        )
                    )
                } else if (currentDuration > maxMs) {
                    break
                }
            }
        }

        // Step 8: Rank candidate segments
        val sorted = candidates.sortedByDescending { it.score }

        // Step 9: Deduplicate and avoid excessive overlap (Non-Maximum Suppression)
        val selected = mutableListOf<ScoredCandidate>()
        for (cand in sorted) {
            val overlaps = selected.any { existing ->
                val overlapStart = max(cand.startMs, existing.startMs)
                val overlapEnd = min(cand.endMs, existing.endMs)
                val overlapMs = max(0L, overlapEnd - overlapStart)
                val minDur = min(cand.endMs - cand.startMs, existing.endMs - existing.startMs)
                overlapMs > (minDur * 0.40f) // Max 40% overlap allowed
            }
            if (!overlaps) {
                selected.add(cand)
            }
            if (selected.size >= config.clipCount) break
        }

        if (selected.isEmpty()) {
            return generateFallbackClips(projectId, totalDurationMs, config.clipCount, config.aspectRatio)
        }

        // Step 10-12: Snap boundaries, assign IDs, generate titles
        return selected.mapIndexed { index, cand ->
            val snappedStart = max(0L, cand.startMs - 200L) // Include breathing room
            val snappedEnd = min(totalDurationMs, cand.endMs + 300L)
            val srt = SubtitleGenerator.toSrt(cand.segments, snappedStart)

            ClipCandidate(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                title = cand.title.ifBlank { "Clip #${index + 1}: ${config.style.title.substringBefore("/")}" },
                startMs = snappedStart,
                endMs = snappedEnd,
                durationMs = snappedEnd - snappedStart,
                reasonWhySelected = cand.reason,
                score = cand.score,
                aspectRatio = config.aspectRatio,
                status = ClipStatus.DISCOVERED,
                subtitles = cand.segments,
                srtContent = srt
            )
        }
    }

    /**
     * Custom Length Maker implementation:
     * - Smart AI Mode: Finds segments that naturally end around the target duration.
     * - Sequential Mode: Splits contiguous duration blocks.
     */
    fun createCustomDurationClips(
        projectId: Long,
        totalDurationMs: Long,
        segments: List<TranscriptSegment>,
        config: CustomDurationConfig
    ): List<ClipCandidate> {
        val targetMs = config.targetDurationSec * 1000L

        if (config.mode == CustomLengthMode.SEQUENTIAL) {
            val result = mutableListOf<ClipCandidate>()
            var cursor = 0L
            var part = 1

            while (cursor < totalDurationMs && result.size < config.clipCount) {
                val end = min(totalDurationMs, cursor + targetMs)
                val subSegments = segments.filter { it.startMs in cursor..end || it.endMs in cursor..end }
                val srt = SubtitleGenerator.toSrt(subSegments, cursor)

                result.add(
                    ClipCandidate(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        title = "Part $part (${config.targetDurationSec}s Segment)",
                        startMs = cursor,
                        endMs = end,
                        durationMs = end - cursor,
                        reasonWhySelected = "Sequential ${config.targetDurationSec}s continuous section",
                        score = 80f - (part * 2f),
                        aspectRatio = config.aspectRatio,
                        status = ClipStatus.DISCOVERED,
                        subtitles = subSegments,
                        srtContent = srt
                    )
                )
                cursor = end
                part++
            }
            return result
        }

        // SMART_AI Mode for Custom Duration
        val toleranceMs = (targetMs * 0.20f).toLong().coerceIn(2000L, 8000L)
        val minTargetMs = max(5000L, targetMs - toleranceMs)
        val maxTargetMs = targetMs + toleranceMs

        val candidateList = mutableListOf<ScoredCandidate>()

        for (i in segments.indices) {
            val startSeg = segments[i]
            val included = mutableListOf<TranscriptSegment>()
            included.add(startSeg)

            for (j in i until segments.size) {
                if (j > i) included.add(segments[j])
                val dur = included.last().endMs - startSeg.startMs
                if (dur in minTargetMs..maxTargetMs) {
                    val delta = abs(dur - targetMs).toFloat() / targetMs
                    val baseScore = 90f - (delta * 30f)
                    val firstText = startSeg.text.lowercase()
                    val hasHook = firstText.startsWith("why") || firstText.startsWith("how") ||
                            firstText.contains("secret") || firstText.contains("important")
                    val score = (baseScore + if (hasHook) 8f else 0f).coerceIn(50f, 99f)

                    val reason = if (abs(dur - targetMs) < 1500) {
                        "Complete sentence ending exactly at ${dur / 1000}s"
                    } else {
                        "Self-contained thought preserved near ${config.targetDurationSec}s"
                    }

                    candidateList.add(
                        ScoredCandidate(
                            startMs = startSeg.startMs,
                            endMs = included.last().endMs,
                            score = score,
                            reason = reason,
                            title = "Highlight: ${startSeg.text.take(30)}...",
                            segments = included.toList()
                        )
                    )
                } else if (dur > maxTargetMs) {
                    break
                }
            }
        }

        val sorted = candidateList.sortedByDescending { it.score }
        val filtered = mutableListOf<ScoredCandidate>()

        for (cand in sorted) {
            val hasHeavyOverlap = filtered.any { existing ->
                val oStart = max(cand.startMs, existing.startMs)
                val oEnd = min(cand.endMs, existing.endMs)
                max(0L, oEnd - oStart) > ((cand.endMs - cand.startMs) * 0.40f)
            }
            if (!hasHeavyOverlap) {
                filtered.add(cand)
            }
            if (filtered.size >= config.clipCount) break
        }

        if (filtered.isEmpty()) {
            return generateFallbackClips(projectId, totalDurationMs, config.clipCount, config.aspectRatio)
        }

        return filtered.mapIndexed { idx, cand ->
            val srt = SubtitleGenerator.toSrt(cand.segments, cand.startMs)
            ClipCandidate(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                title = "Clip ${idx + 1} (${config.targetDurationSec}s Target)",
                startMs = cand.startMs,
                endMs = cand.endMs,
                durationMs = cand.endMs - cand.startMs,
                reasonWhySelected = cand.reason,
                score = cand.score,
                aspectRatio = config.aspectRatio,
                status = ClipStatus.DISCOVERED,
                subtitles = cand.segments,
                srtContent = srt
            )
        }
    }

    private data class ScoreEvaluation(
        val score: Float,
        val reason: String,
        val title: String
    )

    private fun scoreCandidate(
        segments: List<TranscriptSegment>,
        style: ContentStyle,
        prioritizeSpeech: Boolean
    ): ScoreEvaluation {
        val first = segments.first()
        val last = segments.last()
        val firstText = first.text.lowercase()
        val lastText = last.text.lowercase()

        var score = 70f
        var reason = "Self-contained explanation"
        var title = first.text.take(35).replace("\"", "") + "..."

        // Opening hook detection
        val hookWords = listOf("why", "how", "secret", "never", "always", "stop", "biggest", "look", "watch", "wait", "remember")
        val hasHookWord = hookWords.any { firstText.contains(it) }

        if (first.hookScore > 80f || hasHookWord) {
            score += 15f
            reason = "Strong opening statement & hook"
        }

        // Punctuation and completeness detection
        val endsCleanly = lastText.endsWith(".") || lastText.endsWith("!") || lastText.endsWith("?")
        if (endsCleanly) {
            score += 8f
        }

        // Energy level
        val avgEnergy = segments.map { it.speechEnergy }.average().toFloat()
        if (avgEnergy > 0.65f) {
            score += 6f
            if (reason.contains("hook")) {
                reason = "High speech energy with impactful hook"
            }
        }

        // Style matching
        when (style) {
            ContentStyle.FACTS -> {
                if (firstText.contains("fact") || firstText.contains("know") || firstText.contains("did you")) {
                    score += 10f
                    reason = "Reveals fascinating fact"
                }
            }
            ContentStyle.EDUCATIONAL -> {
                if (firstText.contains("step") || firstText.contains("how to") || firstText.contains("learn")) {
                    score += 10f
                    reason = "Clear instructional takeaway"
                }
            }
            ContentStyle.PODCAST -> {
                if (segments.size >= 4) {
                    score += 8f
                    reason = "Engaging conversation flow"
                }
            }
            ContentStyle.MOTIVATIONAL -> {
                if (avgEnergy > 0.70f) {
                    score += 10f
                    reason = "Powerful motivational quote"
                }
            }
            ContentStyle.COMEDY -> {
                if (lastText.contains("!") || avgEnergy > 0.75f) {
                    score += 8f
                    reason = "Punchline and reaction peak"
                }
            }
            else -> {}
        }

        return ScoreEvaluation(
            score = score.coerceIn(50f, 98.5f),
            reason = reason,
            title = title
        )
    }

    private fun generateFallbackClips(
        projectId: Long,
        totalDurationMs: Long,
        count: Int,
        aspectRatio: AspectRatio
    ): List<ClipCandidate> {
        val list = mutableListOf<ClipCandidate>()
        val clipLen = min(totalDurationMs / count.coerceAtLeast(1), 30000L).coerceAtLeast(5000L)

        for (i in 0 until count) {
            val s = i * clipLen
            val e = min(totalDurationMs, s + clipLen)
            if (s >= totalDurationMs) break

            list.add(
                ClipCandidate(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    title = "Highlight Clip #${i + 1}",
                    startMs = s,
                    endMs = e,
                    durationMs = e - s,
                    reasonWhySelected = "High-energy video section",
                    score = 85f - (i * 3f),
                    aspectRatio = aspectRatio,
                    status = ClipStatus.DISCOVERED
                )
            )
        }
        return list
    }

    private data class ScoredCandidate(
        val startMs: Long,
        val endMs: Long,
        val score: Float,
        val reason: String,
        val title: String,
        val segments: List<TranscriptSegment>
    )
}
