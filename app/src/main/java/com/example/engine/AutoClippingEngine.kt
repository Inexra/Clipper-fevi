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

    fun findBestMoments(
        projectId: Long,
        totalDurationMs: Long,
        segments: List<TranscriptSegment>,
        config: ClippingConfig
    ): List<ClipCandidate> {
        if (totalDurationMs <= 0L || config.clipCount <= 0) return emptyList()
        if (segments.isEmpty() || totalDurationMs <= 5000) {
            return generateFallbackClips(projectId, totalDurationMs, config.clipCount, config.aspectRatio)
        }

        val minMs = config.minDurationSec.coerceAtLeast(1) * 1000L
        val maxMs = max(minMs, config.maxDurationSec.coerceAtLeast(config.minDurationSec) * 1000L)
        val orderedSegments = segments
            .filter { it.startMs >= 0L && it.endMs > it.startMs && it.startMs < totalDurationMs }
            .map { it.copy(endMs = min(it.endMs, totalDurationMs)) }
            .sortedBy { it.startMs }
        if (orderedSegments.isEmpty()) {
            return generateFallbackClips(projectId, totalDurationMs, config.clipCount, config.aspectRatio)
        }

        val candidates = mutableListOf<ScoredCandidate>()
        for (i in orderedSegments.indices) {
            val startSegment = orderedSegments[i]
            val includedSegments = mutableListOf<TranscriptSegment>()
            for (j in i until orderedSegments.size) {
                val seg = orderedSegments[j]
                if (j > i) includedSegments.add(seg) else includedSegments.add(startSegment)
                val duration = seg.endMs - startSegment.startMs
                if (duration in minMs..maxMs) {
                    val result = scoreCandidate(includedSegments, config.style, config.prioritizeSpeechOverScene)
                    candidates.add(ScoredCandidate(startSegment.startMs, seg.endMs, result.score, result.reason, result.title, includedSegments.toList()))
                } else if (duration > maxMs) break
            }
        }

        val selected = mutableListOf<ScoredCandidate>()
        for (cand in candidates.sortedByDescending { it.score }) {
            val overlaps = selected.any { existing ->
                val overlap = max(0L, min(cand.endMs, existing.endMs) - max(cand.startMs, existing.startMs))
                val shorter = min(cand.endMs - cand.startMs, existing.endMs - existing.startMs)
                shorter > 0L && overlap > shorter * 0.40f
            }
            if (!overlaps) selected.add(cand)
            if (selected.size >= config.clipCount) break
        }

        if (selected.isEmpty()) {
            return generateFallbackClips(projectId, totalDurationMs, config.clipCount, config.aspectRatio)
        }

        return selected.mapIndexed { index, cand ->
            val start = max(0L, cand.startMs - 200L)
            val end = min(totalDurationMs, cand.endMs + 300L)
            ClipCandidate(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                title = cand.title.ifBlank { "Highlight Clip #${index + 1}" },
                startMs = start,
                endMs = end,
                durationMs = end - start,
                reasonWhySelected = cand.reason,
                score = cand.score,
                aspectRatio = config.aspectRatio,
                status = ClipStatus.DISCOVERED,
                subtitles = cand.segments,
                srtContent = SubtitleGenerator.toSrt(cand.segments, start),
                metadata = LocalMetadataGenerator.generate(cand.segments)
            )
        }
    }

    fun createCustomDurationClips(
        projectId: Long,
        totalDurationMs: Long,
        segments: List<TranscriptSegment>,
        config: CustomDurationConfig
    ): List<ClipCandidate> {
        if (totalDurationMs <= 0L || config.clipCount <= 0) return emptyList()
        val targetMs = config.targetDurationSec.coerceAtLeast(1) * 1000L

        if (config.mode == CustomLengthMode.SEQUENTIAL) {
            val result = mutableListOf<ClipCandidate>()
            var cursor = 0L
            var part = 1
            while (cursor < totalDurationMs && result.size < config.clipCount) {
                val end = min(totalDurationMs, cursor + targetMs)
                val subSegments = segments.filter { it.startMs < end && it.endMs > cursor }
                result.add(ClipCandidate(
                    id = UUID.randomUUID().toString(), projectId = projectId,
                    title = "Part $part (${config.targetDurationSec}s Segment)",
                    startMs = cursor, endMs = end, durationMs = end - cursor,
                    reasonWhySelected = "Sequential ${config.targetDurationSec}s continuous section",
                    score = 80f - part * 2f, aspectRatio = config.aspectRatio,
                    status = ClipStatus.DISCOVERED, subtitles = subSegments,
                    srtContent = SubtitleGenerator.toSrt(subSegments, cursor)
                ))
                cursor = end
                part++
            }
            return result
        }

        if (segments.isEmpty()) {
            return generateFallbackClips(projectId, totalDurationMs, config.clipCount, config.aspectRatio, targetMs)
        }

        val toleranceMs = (targetMs * 0.20f).toLong().coerceIn(2000L, 8000L)
        val minTargetMs = max(5000L, targetMs - toleranceMs)
        val maxTargetMs = targetMs + toleranceMs
        val ordered = segments.filter { it.endMs > it.startMs && it.startMs < totalDurationMs }
            .sortedBy { it.startMs }
        val candidates = mutableListOf<ScoredCandidate>()

        for (i in ordered.indices) {
            val startSeg = ordered[i]
            val included = mutableListOf<TranscriptSegment>()
            for (j in i until ordered.size) {
                included.add(ordered[j])
                val dur = included.last().endMs - startSeg.startMs
                if (dur in minTargetMs..maxTargetMs) {
                    val delta = abs(dur - targetMs).toFloat() / targetMs
                    val hook = startSeg.text.lowercase().let { text ->
                        listOf("why", "how", "secret", "important", "never", "watch", "wait").any(text::contains)
                    }
                    candidates.add(ScoredCandidate(
                        startMs = startSeg.startMs,
                        endMs = included.last().endMs,
                        score = (90f - delta * 30f + if (hook) 8f else 0f).coerceIn(50f, 99f),
                        reason = "Transcript window near ${config.targetDurationSec}s target",
                        title = startSeg.text.take(40).ifBlank { "Highlight" },
                        segments = included.toList()
                    ))
                } else if (dur > maxTargetMs) break
            }
        }

        val selected = mutableListOf<ScoredCandidate>()
        for (cand in candidates.sortedByDescending { it.score }) {
            val overlaps = selected.any { existing ->
                val overlap = max(0L, min(cand.endMs, existing.endMs) - max(cand.startMs, existing.startMs))
                val shorter = min(cand.endMs - cand.startMs, existing.endMs - existing.startMs)
                shorter > 0L && overlap > shorter * 0.40f
            }
            if (!overlaps) selected.add(cand)
            if (selected.size >= config.clipCount) break
        }
        if (selected.isEmpty()) {
            return generateFallbackClips(projectId, totalDurationMs, config.clipCount, config.aspectRatio, targetMs)
        }

        return selected.mapIndexed { idx, cand ->
            ClipCandidate(
                id = UUID.randomUUID().toString(), projectId = projectId,
                title = "Clip ${idx + 1} (${config.targetDurationSec}s Target)",
                startMs = cand.startMs, endMs = cand.endMs,
                durationMs = cand.endMs - cand.startMs,
                reasonWhySelected = cand.reason, score = cand.score,
                aspectRatio = config.aspectRatio, status = ClipStatus.DISCOVERED,
                subtitles = cand.segments,
                srtContent = SubtitleGenerator.toSrt(cand.segments, cand.startMs),
                metadata = LocalMetadataGenerator.generate(cand.segments)
            )
        }
    }

    private data class ScoreEvaluation(val score: Float, val reason: String, val title: String)

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
        var reason = "Self-contained transcript segment"
        var title = first.text.take(35).replace("\"", "") + if (first.text.length > 35) "…" else ""

        val hooks = listOf("why", "how", "secret", "never", "always", "stop", "biggest", "look", "watch", "wait", "remember", "did you know")
        if (first.hookScore > 80f || hooks.any(firstText::contains)) {
            score += 15f
            reason = "Strong opening statement & hook"
        }
        if (lastText.endsWith(".") || lastText.endsWith("!") || lastText.endsWith("?")) score += 8f
        val energy = segments.map { it.speechEnergy }.average().toFloat()
        if (prioritizeSpeech && energy > 0.65f) score += 6f
        when (style) {
            ContentStyle.FACTS -> if (listOf("fact", "know", "did you", "discovered").any(firstText::contains)) { score += 10f; reason = "Potential fact reveal" }
            ContentStyle.EDUCATIONAL -> if (listOf("step", "how to", "learn", "explain").any(firstText::contains)) { score += 10f; reason = "Instructional takeaway" }
            ContentStyle.PODCAST -> if (segments.size >= 4) score += 8f
            ContentStyle.MOTIVATIONAL -> if (energy > 0.70f) score += 10f
            ContentStyle.COMEDY -> if (lastText.contains("!") || energy > 0.75f) score += 8f
            else -> Unit
        }
        return ScoreEvaluation(score.coerceIn(50f, 98.5f), reason, title)
    }

    /**
     * Safe fallback when no transcript windows are available. Evenly samples the timeline
     * instead of packing all fallback clips into the first minute of the source.
     */
    private fun generateFallbackClips(
        projectId: Long,
        totalDurationMs: Long,
        count: Int,
        aspectRatio: AspectRatio,
        requestedDurationMs: Long = 30000L
    ): List<ClipCandidate> {
        if (totalDurationMs <= 0L || count <= 0) return emptyList()
        val slotMs = (totalDurationMs / count).coerceAtLeast(1L)
        val clipLength = min(requestedDurationMs.coerceAtLeast(1000L), slotMs)
        val list = mutableListOf<ClipCandidate>()

        for (i in 0 until count) {
            val slotStart = i * slotMs
            if (slotStart >= totalDurationMs) break
            val slotEnd = min(totalDurationMs, slotStart + slotMs)
            val available = slotEnd - slotStart
            val length = min(clipLength, available)
            val start = slotStart + ((available - length) / 2L)
            val end = min(totalDurationMs, start + length)
            if (end <= start) continue

            list.add(ClipCandidate(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                title = "Timeline Sample #${i + 1}",
                startMs = start,
                endMs = end,
                durationMs = end - start,
                reasonWhySelected = "Distributed fallback sample; speech analysis was unavailable",
                score = 50f,
                aspectRatio = aspectRatio,
                status = ClipStatus.DISCOVERED
            ))
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
