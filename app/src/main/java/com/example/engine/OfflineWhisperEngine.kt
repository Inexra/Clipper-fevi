package com.example.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.data.model.TranscriptSegment
import com.example.data.model.WordTiming
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

/**
 * Offline English Whisper Tiny INT8 transcription using sherpa-onnx.
 * Model files download once to app-private storage; source audio is decoded on device.
 */
object OfflineWhisperEngine {
    private const val BASE = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/"
    private const val RATE = 16000
    private const val CHUNK_SECONDS = 25

    private fun modelDir(context: Context) = File(context.filesDir, "models/whisper-tiny-en")

    fun isModelDownloaded(context: Context): Boolean {
        val dir = modelDir(context)
        return File(dir, "tiny.en-encoder.int8.onnx").length() > 10_000_000L &&
            File(dir, "tiny.en-decoder.int8.onnx").length() > 80_000_000L &&
            File(dir, "tiny.en-tokens.txt").length() > 100_000L
    }

    suspend fun downloadModel(context: Context, onProgress: (Float) -> Unit = {}) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = modelDir(context)
            dir.mkdirs()
            val files = listOf(
                "tiny.en-encoder.int8.onnx" to 0.12f,
                "tiny.en-decoder.int8.onnx" to 0.84f,
                "tiny.en-tokens.txt" to 0.04f
            )
            files.forEachIndexed { index, item ->
                val target = File(dir, item.first)
                if (!target.exists() || target.length() < if (index == 0) 10_000_000L else if (index == 1) 80_000_000L else 100_000L) {
                    val part = File(dir, target.name + ".part")
                    val connection = (URL(BASE + target.name + "?download=true").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20000
                        readTimeout = 60000
                        instanceFollowRedirects = true
                    }
                    try {
                        connection.connect()
                        if (connection.responseCode !in 200..299) throw IllegalStateException("Whisper model download failed (HTTP ${connection.responseCode})")
                        connection.inputStream.buffered().use { input ->
                            part.outputStream().buffered().use { output ->
                                val buffer = ByteArray(256 * 1024)
                                var n: Int
                                while (input.read(buffer).also { n = it } >= 0) {
                                    if (n > 0) output.write(buffer, 0, n)
                                }
                            }
                        }
                        if (part.length() < if (index == 0) 10_000_000L else if (index == 1) 80_000_000L else 100_000L) {
                            part.delete()
                            throw IllegalStateException("Whisper model file was incomplete: ${target.name}")
                        }
                        if (!part.renameTo(target)) {
                            part.copyTo(target, overwrite = true)
                            part.delete()
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
                onProgress((files.take(index + 1).sumOf { it.second.toDouble() }).toFloat().coerceIn(0f, 1f))
            }
        }
    }

    suspend fun transcribeVideo(
        context: Context,
        uri: Uri,
        language: String = "en",
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): List<TranscriptSegment> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!isModelDownloaded(context)) {
            onProgress("Downloading offline Whisper speech model (~105 MB)", 0.08f)
            downloadModel(context) { p -> onProgress("Downloading offline Whisper speech model", 0.08f + p * 0.22f) }
        }
        onProgress("Decoding the video's audio locally", 0.32f)
        val pcm = decodeToMono16k(context, uri)
        if (pcm.isEmpty()) throw IllegalStateException("Could not decode audio from this video.")
        val dir = modelDir(context)
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = File(dir, "tiny.en-encoder.int8.onnx").absolutePath,
                    decoder = File(dir, "tiny.en-decoder.int8.onnx").absolutePath,
                    language = language.ifBlank { "en" },
                    task = "transcribe",
                    enableSegmentTimestamps = true,
                    enableTokenTimestamps = true
                ),
                tokens = File(dir, "tiny.en-tokens.txt").absolutePath,
                numThreads = 2,
                provider = "cpu",
                debug = false
            )
        )
        val recognizer = OfflineRecognizer(config = config)
        val result = mutableListOf<TranscriptSegment>()
        try {
            val bytesPerSecond = RATE * 2
            val chunkBytes = CHUNK_SECONDS * bytesPerSecond
            val chunkCount = (pcm.size + chunkBytes - 1) / chunkBytes
            for (chunkIndex in 0 until chunkCount) {
                val startByte = chunkIndex * chunkBytes
                val endByte = min(pcm.size, startByte + chunkBytes)
                val sampleCount = (endByte - startByte) / 2
                if (sampleCount < RATE / 2) continue
                val samples = FloatArray(sampleCount)
                var p = startByte
                for (i in samples.indices) {
                    val lo = pcm[p++].toInt() and 0xff
                    val hi = pcm[p++].toInt()
                    samples[i] = ((hi shl 8) or lo).toShort() / 32768.0f
                }
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, RATE)
                    recognizer.decode(stream)
                    val text = recognizer.getResult(stream).text.trim()
                    if (text.isNotBlank()) {
                        val startMs = chunkIndex * CHUNK_SECONDS * 1000L
                        val endMs = min((pcm.size.toLong() * 1000L / bytesPerSecond), startMs + sampleCount * 1000L / RATE)
                        result += splitChunkIntoSegments(text, startMs, max(startMs + 500L, endMs), result.size + 1)
                    }
                } finally {
                    stream.release()
                }
                onProgress("Transcribing audio locally (${chunkIndex + 1}/$chunkCount)", 0.55f + 0.43f * (chunkIndex + 1) / chunkCount)
            }
        } finally {
            recognizer.release()
        }
        if (result.isEmpty()) throw IllegalStateException("Whisper did not detect intelligible speech in this audio.")
        result
    }

    private fun splitChunkIntoSegments(text: String, startMs: Long, endMs: Long, firstId: Int): List<TranscriptSegment> {
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        if (sentences.isEmpty()) return emptyList()
        val totalWords = sentences.sumOf { it.split(Regex("\\s+")).size }.coerceAtLeast(1)
        var cursor = startMs
        return sentences.mapIndexed { index, sentence ->
            val words = sentence.split(Regex("\\s+")).filter { it.isNotBlank() }
            val duration = if (index == sentences.lastIndex) endMs - cursor
                else ((endMs - startMs) * words.size / totalWords).coerceAtLeast(250L)
            val finish = min(endMs, cursor + duration).coerceAtLeast(cursor + 1L)
            val perWord = max(1L, (finish - cursor) / words.size.coerceAtLeast(1))
            val timings = words.mapIndexed { wordIndex, word ->
                val wordStart = cursor + wordIndex * perWord
                WordTiming(word, wordStart, min(finish, wordStart + perWord))
            }
            TranscriptSegment(
                id = firstId + index,
                startMs = cursor,
                endMs = finish,
                text = sentence,
                words = timings,
                hookScore = if (sentence.contains(Regex("\\b(why|how|secret|never|surprise|important)\\b", RegexOption.IGNORE_CASE))) 82f else 55f,
                speechEnergy = 0.5f
            ).also { cursor = finish }
        }
    }

    private fun decodeToMono16k(context: Context, uri: Uri): ByteArray {
        val extractor = MediaExtractor()
        val output = ByteArrayOutputStream()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if ((candidate.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = i
                    format = candidate
                    break
                }
            }
            if (track < 0 || format == null) throw IllegalStateException("This video has no readable audio track.")
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var inputRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var inputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var sourceFrame = 0L
            var nextOutputFrame = 0.0
            var outputRate = inputRate
            var outputChannels = inputChannels
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        outputRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outputChannels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val buffer = codec.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val pcm = buffer.slice().order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            val frameBytes = 2 * outputChannels
                            val frames = info.size / frameBytes
                            for (frame in 0 until frames) {
                                var sum = 0
                                for (ch in 0 until outputChannels) sum += pcm.short.toInt()
                                val mono = (sum / outputChannels).coerceIn(-32768, 32767)
                                if (sourceFrame >= nextOutputFrame) {
                                    output.write(mono and 0xff)
                                    output.write((mono shr 8) and 0xff)
                                    nextOutputFrame += outputRate.toDouble() / RATE
                                }
                                sourceFrame++
                            }
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
        return output.toByteArray()
    }
}
