package com.example.engine

import com.example.data.model.ClipMetadata
import com.example.data.model.MetadataTone
import com.example.data.model.TranscriptSegment
import java.util.Locale

/**
 * Offline metadata helper. It does not call a cloud API or claim to run an LLM:
 * it derives a title, summary, description and hashtags from the actual transcript.
 */
object LocalMetadataGenerator {
    private val stopWords = setOf(
        "the", "and", "for", "that", "this", "with", "you", "your", "are", "was", "were",
        "have", "has", "had", "from", "they", "them", "then", "than", "but", "not", "just",
        "into", "about", "what", "when", "where", "who", "why", "how", "its", "it's", "our",
        "out", "all", "can", "will", "would", "could", "should", "there", "their", "here",
        "because", "really", "very", "like", "know", "think", "yeah", "okay", "um", "uh"
    )

    fun generate(
        segments: List<TranscriptSegment>,
        tone: MetadataTone = MetadataTone.VIRAL_HOOK,
        channelName: String = ""
    ): ClipMetadata {
        val transcript = segments.sortedBy { it.startMs }
            .joinToString(" ") { it.text.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()

        if (transcript.isBlank()) {
            return ClipMetadata(
                youtubeTitle = "Video Highlight #Shorts",
                youtubeDescription = "A short highlight from the video. Add a transcript to generate more relevant metadata.",
                socialCaption = "Watch this highlight 👀",
                hashtags = listOf("#Shorts", "#Highlight"),
                searchKeywords = listOf("video highlight", "shorts"),
                thumbnailText = "WATCH THIS",
                pinnedComment = "What did you think of this moment?",
                summary = "Transcript unavailable; metadata uses a generic fallback."
            )
        }

        val words = Regex("[\\p{L}\\p{N}']+").findAll(transcript.lowercase(Locale.ROOT))
            .map { it.value.trim('\'') }
            .filter { it.length >= 3 && it !in stopWords && it.any(Char::isLetter) }
            .toList()
        val frequencies = words.groupingBy { it }.eachCount()
        val keywords = frequencies.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(8)
            .map { it.key }

        val firstSentence = transcript.split(Regex("(?<=[.!?])\\s+"))
            .firstOrNull { it.isNotBlank() } ?: transcript
        val cleanHook = firstSentence
            .replace(Regex("^[\\s\\p{Punct}]+"), "")
            .trim()
            .let { if (it.length > 72) it.take(69).trimEnd() + "…" else it }
        val primary = keywords.take(3).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val titlePrefix = when (tone) {
            MetadataTone.EDUCATIONAL -> "Here's what to know:"
            MetadataTone.ENTERTAINING -> "You need to see this:"
            MetadataTone.PROFESSIONAL -> "Key takeaway:"
            MetadataTone.DRAMATIC -> "The moment everything changes:"
            MetadataTone.CASUAL -> "This part:"
            MetadataTone.VIRAL_HOOK -> "Wait for it:"
        }
        val titleCore = if (cleanHook.isNotBlank()) cleanHook else primary.ifBlank { "Video highlight" }
        val title = (if (titleCore.length < 48) "$titlePrefix $titleCore" else titleCore)
            .replace(Regex("\\s+"), " ")
            .take(100)
            .trim()
        val hashtags = (keywords.take(5).map { "#" + it.filter(Char::isLetterOrDigit) } +
            listOf("#Shorts", "#FeviClipper")).distinct().take(8)

        val channelLine = channelName.trim().takeIf { it.isNotEmpty() }?.let { "Channel: $it\\n" } ?: ""
        val description = buildString {
            append(cleanHook)
            append("\\n\\n")
            append("In this clip: ")
            append(if (keywords.isEmpty()) "a highlight worth watching" else keywords.take(5).joinToString(", "))
            append(".\\n")
            append(channelLine)
            append("\\n")
            append(hashtags.joinToString(" "))
        }.take(5000)

        val summary = transcript.take(240).let { if (transcript.length > 240) "$it…" else it }
        return ClipMetadata(
            youtubeTitle = title,
            youtubeDescription = description,
            socialCaption = "$cleanHook\\n\\n${hashtags.take(5).joinToString(" ")}",
            hashtags = hashtags,
            searchKeywords = keywords,
            thumbnailText = (keywords.take(2).joinToString(" ").ifBlank { "VIDEO HIGHLIGHT" })
                .uppercase(Locale.ROOT).take(24),
            pinnedComment = "Which part stood out to you most?",
            summary = summary
        )
    }
}
