package com.example.engine

import android.content.Context
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-device Qwen2.5-0.5B-Instruct (Q4_K_M). The ~400 MB model is downloaded only
 * when the user asks for it; prompts and video transcripts remain on the device.
 */
object LocalQwenEngine {
    private const val MODEL_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    private const val MODEL_URL =
        "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true"

    fun modelFile(context: Context): File =
        File(File(context.getExternalFilesDir("models") ?: context.filesDir, "models"), MODEL_NAME)

    fun isModelDownloaded(context: Context): Boolean {
        val file = modelFile(context)
        return file.exists() && file.length() > 100_000_000L
    }

    suspend fun downloadModel(
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val destination = modelFile(context)
        if (isModelDownloaded(context)) return@withContext destination
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, "$MODEL_NAME.part")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FeviAIClipper/1.0")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Model download failed: HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            connection.inputStream.buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var count: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { count = it } >= 0) {
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0L) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (partial.length() < 100_000_000L) {
                partial.delete()
                throw IllegalStateException("Downloaded Qwen model is incomplete. Please retry on a stable Wi-Fi connection.")
            }
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            }
            onProgress(1f)
            destination
        } catch (e: Exception) {
            partial.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    suspend fun generateText(context: Context, prompt: String, maxTokens: Int = 256): String =
        withContext(Dispatchers.IO) {
            val file = modelFile(context)
            require(isModelDownloaded(context)) { "Download the Qwen model in Settings before using local AI." }
            val model = Llama.loadModel(
                modelPath = file.absolutePath,
                config = LlamaConfig(contextSize = 2048, threads = 2)
            )
            try {
                Llama.complete(
                    model = model,
                    prompt = prompt,
                    systemPrompt = "You are Fevi, an accurate video editing assistant. Use only the supplied transcript. Never invent dialogue or events. Follow the requested output format exactly.",
                    maxTokens = maxTokens
                ).text.trim()
            } finally {
                Llama.releaseModel(model)
            }
        }

    suspend fun generateMetadata(
        context: Context,
        transcript: String,
        clipTitle: String
    ): com.example.data.model.ClipMetadata {
        val prompt = """
            Create YouTube Shorts metadata from this real transcript. Return ONLY valid JSON with keys:
            title (max 95 characters), description (2-4 useful sentences and hashtags),
            socialCaption, hashtags (array of 4-8 strings with #), keywords (array of 4-8 strings),
            thumbnailText (max 24 characters), pinnedComment, summary (one sentence).
            Do not add facts not present in the transcript.
            Clip title: $clipTitle
            Transcript: ${transcript.take(3500)}
        """.trimIndent()
        val raw = generateText(context, prompt, 300)
        val jsonText = raw.substringAfter("{", "").let { if (it.isNotEmpty()) "{$it" else raw }
            .substringBeforeLast("}", "") + "}"
        val json = JSONObject(jsonText)
        fun string(key: String, fallback: String = "") = json.optString(key, fallback).trim()
        fun array(key: String): List<String> {
            val a = json.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
        }
        return com.example.data.model.ClipMetadata(
            youtubeTitle = string("title", clipTitle).take(100),
            youtubeDescription = string("description"),
            socialCaption = string("socialCaption"),
            hashtags = array("hashtags"),
            searchKeywords = array("keywords"),
            thumbnailText = string("thumbnailText").take(24),
            pinnedComment = string("pinnedComment"),
            summary = string("summary")
        )
    }
}
