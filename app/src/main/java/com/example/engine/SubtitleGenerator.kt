package com.example.engine

import android.content.Context
import android.os.Environment
import com.example.data.model.TranscriptSegment
import com.example.data.model.WordTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object SubtitleGenerator {

    /**
     * Converts transcript segments into standard SubRip (.srt) caption text.
     */
    fun toSrt(segments: List<TranscriptSegment>, offsetMs: Long = 0L): String {
        val sb = StringBuilder()
        var srtIndex = 1

        segments.forEach { seg ->
            val startMs = (seg.startMs - offsetMs).coerceAtLeast(0L)
            val endMs = (seg.endMs - offsetMs).coerceAtLeast(startMs + 100L)

            sb.append(srtIndex).append("\n")
            sb.append(formatSrtTime(startMs)).append(" --> ").append(formatSrtTime(endMs)).append("\n")
            sb.append(seg.text.trim()).append("\n\n")
            srtIndex++
        }

        return sb.toString()
    }

    /**
     * Converts transcript segments into WebVTT (.vtt) caption text.
     */
    fun toVtt(segments: List<TranscriptSegment>, offsetMs: Long = 0L): String {
        val sb = StringBuilder("WEBVTT\n\n")

        segments.forEachIndexed { index, seg ->
            val startMs = (seg.startMs - offsetMs).coerceAtLeast(0L)
            val endMs = (seg.endMs - offsetMs).coerceAtLeast(startMs + 100L)

            sb.append(index + 1).append("\n")
            sb.append(formatVttTime(startMs)).append(" --> ").append(formatVttTime(endMs)).append("\n")
            sb.append(seg.text.trim()).append("\n\n")
        }

        return sb.toString()
    }

    /**
     * Splits longer transcript sentences into short, punchy 3-5 word subtitle chunks
     * ideal for TikTok / Shorts / Reels pacing.
     */
    fun splitForShortForm(
        segments: List<TranscriptSegment>,
        wordsPerCaption: Int = 4
    ): List<TranscriptSegment> {
        val result = mutableListOf<TranscriptSegment>()
        var globalId = 1

        segments.forEach { seg ->
            val words = if (seg.words.isNotEmpty()) {
                seg.words
            } else {
                val split = seg.text.split(" ").filter { it.isNotBlank() }
                val dur = (seg.endMs - seg.startMs) / split.size.coerceAtLeast(1)
                split.mapIndexed { i, w ->
                    WordTiming(w, seg.startMs + (i * dur), seg.startMs + ((i + 1) * dur))
                }
            }

            if (words.size <= wordsPerCaption) {
                result.add(seg.copy(id = globalId++))
            } else {
                words.chunked(wordsPerCaption).forEach { chunk ->
                    val chunkStart = chunk.first().startMs
                    val chunkEnd = chunk.last().endMs
                    val chunkText = chunk.joinToString(" ") { it.word }
                    result.add(
                        TranscriptSegment(
                            id = globalId++,
                            startMs = chunkStart,
                            endMs = chunkEnd,
                            text = chunkText,
                            words = chunk,
                            hookScore = seg.hookScore,
                            speechEnergy = seg.speechEnergy
                        )
                    )
                }
            }
        }

        return result
    }

    /**
     * Finds active segment and active word for a specific player timestamp.
     */
    fun getActiveSubtitleAt(
        segments: List<TranscriptSegment>,
        currentPositionMs: Long
    ): Pair<TranscriptSegment?, WordTiming?> {
        val activeSeg = segments.firstOrNull { currentPositionMs in it.startMs..it.endMs }
            ?: return Pair(null, null)

        val activeWord = activeSeg.words.firstOrNull { currentPositionMs in it.startMs..it.endMs }
        return Pair(activeSeg, activeWord)
    }

    suspend fun exportSrtFile(
        context: Context,
        clipTitle: String,
        srtContent: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val sanitized = clipTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_").take(30)
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val file = File(dir, "${sanitized}_subtitles.srt")
            FileOutputStream(file).use { it.write(srtContent.toByteArray()) }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatSrtTime(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1000
        val millis = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun formatVttTime(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1000
        val millis = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }
}
