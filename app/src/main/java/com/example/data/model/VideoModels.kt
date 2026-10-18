package com.example.data.model

import java.util.Locale

data class VideoMetadata(
    val uri: String,
    val fileName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Float = 30f,
    val hasAudio: Boolean = true,
    val sizeBytes: Long = 0L,
    val mimeType: String = "video/mp4"
) {
    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "Unknown size"
            val mb = sizeBytes / (1024f * 1024f)
            return if (mb >= 1024) {
                String.format(Locale.US, "%.2f GB", mb / 1024f)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }

    val resolutionLabel: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else "Unknown res"
}

enum class AspectRatio(val label: String, val ratio: Float, val iconLabel: String) {
    VERTICAL_9_16("9:16 Shorts/Reels", 9f / 16f, "9:16"),
    SQUARE_1_1("1:1 Square Feed", 1f, "1:1"),
    PORTRAIT_4_5("4:5 Instagram", 4f / 5f, "4:5"),
    LANDSCAPE_16_9("16:9 Landscape", 16f / 9f, "16:9")
}

enum class ContentStyle(val title: String, val description: String) {
    FACTS("Mind-Blowing Facts", "Prioritizes surprising curiosities & fact revelations"),
    EDUCATIONAL("Educational / How-to", "Finds structured explanations & teaching moments"),
    PODCAST("Podcast / Conversation", "Pins banter, bold opinions & debate exchanges"),
    STORYTELLING("Storytelling", "Detects narrative setup, climax & resolutions"),
    MOTIVATIONAL("Motivational", "Highlights high-energy inspiring statements"),
    COMEDY("Comedy / Reactions", "Identifies jokes, funny remarks & laugh peaks"),
    GAMING("Gaming Highlights", "Highlights clutch gameplay commentary & hype"),
    GENERAL("General Best Moments", "Balanced clipping of hooks and complete thoughts")
}

data class WordTiming(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float = 0.95f
)

data class TranscriptSegment(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<WordTiming> = emptyList(),
    val hookScore: Float = 50f,
    val speechEnergy: Float = 0.5f
) {
    val formattedTimestamp: String
        get() {
            val startSec = startMs / 1000
            val endSec = endMs / 1000
            return String.format(Locale.US, "%02d:%02d - %02d:%02d", startSec / 60, startSec % 60, endSec / 60, endSec % 60)
        }
}

data class ClipMetadata(
    val youtubeTitle: String = "",
    val youtubeDescription: String = "",
    val socialCaption: String = "",
    val hashtags: List<String> = emptyList(),
    val searchKeywords: List<String> = emptyList(),
    val thumbnailText: String = "",
    val pinnedComment: String = "",
    val summary: String = ""
)

enum class SubtitlePreset(
    val displayName: String,
    val textColor: String,
    val outlineColor: String,
    val backgroundColor: String,
    val activeWordColor: String
) {
    BOLD_YELLOW("Bold Yellow", "#FFE600", "#000000", "#00000000", "#00F0FF"),
    CLEAN_WHITE("Clean White", "#FFFFFF", "#1E1E1E", "#00000000", "#FFE600"),
    NEON_BOX("Neon Cyan Box", "#00E5FF", "#000000", "#800A0A14", "#FFFFFF"),
    MINIMAL("Minimal", "#E2E8F0", "#00000000", "#50000000", "#93C5FD"),
    LARGE_READABLE("Large Contrast", "#FFFFFF", "#000000", "#CC110E1C", "#FBBF24")
}

enum class SubtitlePosition {
    BOTTOM, CENTER, TOP
}

data class SubtitleStyleConfig(
    val preset: SubtitlePreset = SubtitlePreset.BOLD_YELLOW,
    val fontSizeSp: Float = 24f,
    val textColorHex: String = preset.textColor,
    val outlineColorHex: String = preset.outlineColor,
    val backgroundColorHex: String = preset.backgroundColor,
    val position: SubtitlePosition = SubtitlePosition.BOTTOM,
    val wordsPerCaption: Int = 4,
    val wordHighlightEnabled: Boolean = true,
    val activeWordColorHex: String = preset.activeWordColor,
    val marginBottomDp: Float = 48f
)

enum class ClipStatus {
    DISCOVERED,
    RENDERING,
    COMPLETED,
    FAILED
}

data class ClipCandidate(
    val id: String,
    val projectId: Long,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long = endMs - startMs,
    val reasonWhySelected: String,
    val score: Float,
    val aspectRatio: AspectRatio = AspectRatio.VERTICAL_9_16,
    val status: ClipStatus = ClipStatus.DISCOVERED,
    val outputPath: String? = null,
    val subtitles: List<TranscriptSegment> = emptyList(),
    val metadata: ClipMetadata? = null,
    val srtContent: String? = null
) {
    val formattedDuration: String
        get() {
            val totalSec = durationMs / 1000
            return "${totalSec}s"
        }

    val formattedRange: String
        get() {
            val sSec = startMs / 1000
            val eSec = endMs / 1000
            return String.format(Locale.US, "%02d:%02d - %02d:%02d", sSec / 60, sSec % 60, eSec / 60, eSec % 60)
        }
}

enum class ExportResolution(val label: String, val width: Int, val height: Int) {
    RES_720P("720p HD", 720, 1280),
    RES_1080P("1080p Full HD", 1080, 1920),
    RES_ORIGINAL("Original Resolution", 0, 0)
}

enum class CustomLengthMode {
    SMART_AI,
    SEQUENTIAL
}

enum class MetadataTone(val label: String) {
    VIRAL_HOOK("High Engagement / Viral"),
    EDUCATIONAL("Informative / Educational"),
    ENTERTAINING("Humorous / Entertaining"),
    PROFESSIONAL("Professional / Clean"),
    DRAMATIC("Dramatic / Story Hook"),
    CASUAL("Casual / Conversational")
}
