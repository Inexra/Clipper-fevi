package com.example.engine

import android.util.Log
import com.example.data.model.ClipMetadata
import com.example.data.model.MetadataTone
import com.example.data.network.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object MetadataGeneratorEngine {
    private const val TAG = "MetadataGenerator"

    suspend fun generateClipMetadata(
        clipTitle: String,
        transcriptText: String,
        tone: MetadataTone = MetadataTone.VIRAL_HOOK,
        customApiKey: String? = null
    ): ClipMetadata = withContext(Dispatchers.IO) {
        val effectiveKey = GeminiService.getEffectiveApiKey(customApiKey)

        if (effectiveKey.isNotBlank()) {
            val aiResult = generateWithGemini(clipTitle, transcriptText, tone, effectiveKey)
            if (aiResult != null) {
                return@withContext aiResult
            }
        }

        // Local template-based fallback generator reflecting actual transcript content
        generateLocalMetadata(clipTitle, transcriptText, tone)
    }

    private suspend fun generateWithGemini(
        clipTitle: String,
        transcriptText: String,
        tone: MetadataTone,
        apiKey: String
    ): ClipMetadata? {
        val cleanTranscript = transcriptText.take(1500).trim()
        val prompt = """
            You are a top-tier YouTube Shorts & TikTok growth strategist.
            Generate viral, high-CTR metadata strictly based on the following transcript.
            DO NOT invent facts, names, or false claims not present in the transcript.
            
            Clip working title: "$clipTitle"
            Tone: ${tone.label}
            Transcript excerpt:
            "$cleanTranscript"
            
            Return ONLY a raw JSON object with these exact keys:
            {
              "youtubeTitle": "Punchy title under 60 chars with emotional hook or curiosity gap",
              "youtubeDescription": "SEO-optimized 2-3 paragraph description with timestamps, keywords, and call to action",
              "socialCaption": "Engaging 1-2 sentence caption for TikTok / Instagram Reels",
              "hashtags": ["shorts", "trending", "relevantTag1", "relevantTag2", "relevantTag3"],
              "searchKeywords": ["keyword1", "keyword2", "keyword3", "keyword4"],
              "thumbnailText": "2-4 BOLD WORDS for video thumbnail hook",
              "pinnedComment": "Engaging question or prompt to drive comments in the comment section",
              "summary": "1-2 sentence concise summary of this clip"
            }
            Do not enclose in markdown code fences, only output raw valid JSON.
        """.trimIndent()

        return try {
            val res = GeminiService.generateContent(prompt, apiKey)
            if (res.isSuccess) {
                val raw = res.getOrNull()?.trim() ?: ""
                val clean = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val json = JSONObject(clean)

                val hashtagsList = mutableListOf<String>()
                val tagsArr = json.optJSONArray("hashtags")
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        hashtagsList.add(tagsArr.getString(i).removePrefix("#"))
                    }
                }

                val keywordsList = mutableListOf<String>()
                val kwArr = json.optJSONArray("searchKeywords")
                if (kwArr != null) {
                    for (i in 0 until kwArr.length()) {
                        keywordsList.add(kwArr.getString(i))
                    }
                }

                ClipMetadata(
                    youtubeTitle = json.optString("youtubeTitle", clipTitle),
                    youtubeDescription = json.optString("youtubeDescription", ""),
                    socialCaption = json.optString("socialCaption", ""),
                    hashtags = hashtagsList,
                    searchKeywords = keywordsList,
                    thumbnailText = json.optString("thumbnailText", "WATCH THIS"),
                    pinnedComment = json.optString("pinnedComment", "What do you think? Let me know below! 👇"),
                    summary = json.optString("summary", "")
                )
            } else {
                Log.w(TAG, "Gemini call failed: ${res.exceptionOrNull()?.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing Gemini metadata response", e)
            null
        }
    }

    /**
     * Smart local metadata generator that mines transcript tokens,
     * questions, and hooks without hallucinating facts.
     */
    fun generateLocalMetadata(
        clipTitle: String,
        transcriptText: String,
        tone: MetadataTone
    ): ClipMetadata {
        val words = transcriptText.split(" ").filter { it.isNotBlank() }
        val firstSentence = transcriptText.split(".", "!", "?").firstOrNull { it.isNotBlank() }?.trim() ?: clipTitle

        // Extract key words (> 4 letters)
        val stopWords = setOf("about", "after", "again", "because", "could", "every", "first", "their", "there", "these", "would", "which", "where", "while", "today", "welcome")
        val keywords = words
            .map { it.lowercase().replace("[^a-z]".toRegex(), "") }
            .filter { it.length > 4 && it !in stopWords }
            .distinct()
            .take(6)

        val ytTitle = when (tone) {
            MetadataTone.VIRAL_HOOK -> "Wait Until You Hear This... 🤯 #shorts"
            MetadataTone.EDUCATIONAL -> "The Secret Behind ${keywords.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "This"} Explained"
            MetadataTone.ENTERTAINING -> "Nobody Saw This Coming! 😂 #shorts"
            MetadataTone.DRAMATIC -> "This Changed Everything Forever."
            MetadataTone.PROFESSIONAL -> "Key Insight: ${firstSentence.take(45)}"
            MetadataTone.CASUAL -> "You definitely need to know this 👇"
        }

        val description = buildString {
            append("In this clip: $firstSentence\n\n")
            append("Key Topics Discussed:\n")
            keywords.forEach { append("• ${it.replaceFirstChar { c -> c.uppercase() }}\n") }
            append("\nSubscribe for more daily short-form insights!\n")
            append("#shorts #feviclipper #viral")
        }

        val caption = "Look closely at this breakdown. Did you know this? 👇"

        val defaultTags = mutableListOf("shorts", "trending", "viral")
        defaultTags.addAll(keywords.take(4))

        val thumbText = keywords.take(2).joinToString(" ") { it.uppercase() }.ifBlank { "WATCH THIS" }

        return ClipMetadata(
            youtubeTitle = ytTitle,
            youtubeDescription = description,
            socialCaption = caption,
            hashtags = defaultTags,
            searchKeywords = keywords,
            thumbnailText = thumbText,
            pinnedComment = "What's your take on this? Comment below! 👇",
            summary = firstSentence.take(120)
        )
    }
}
