package com.example

import com.example.data.model.AspectRatio
import com.example.data.model.ContentStyle
import com.example.data.model.CustomLengthMode
import com.example.data.model.MetadataTone
import com.example.data.model.TranscriptSegment
import com.example.data.model.WordTiming
import com.example.engine.AutoClippingEngine
import com.example.engine.MetadataGeneratorEngine
import com.example.engine.SubtitleGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipperUnitTests {

    private fun createSampleTranscript(): List<TranscriptSegment> {
        return listOf(
            TranscriptSegment(
                id = 1,
                startMs = 500L,
                endMs = 4500L,
                text = "Did you know that top creators use one specific secret?",
                words = listOf(
                    WordTiming("Did", 500, 1000),
                    WordTiming("you", 1000, 1500),
                    WordTiming("know", 1500, 2000),
                    WordTiming("that", 2000, 2500),
                    WordTiming("top", 2500, 3000),
                    WordTiming("creators", 3000, 3500),
                    WordTiming("use", 3500, 4000),
                    WordTiming("secret?", 4000, 4500)
                ),
                hookScore = 95f,
                speechEnergy = 0.85f
            ),
            TranscriptSegment(
                id = 2,
                startMs = 5000L,
                endMs = 9000L,
                text = "Notice how retention spikes whenever they cut directly to the core lesson.",
                words = emptyList(),
                hookScore = 75f,
                speechEnergy = 0.70f
            ),
            TranscriptSegment(
                id = 3,
                startMs = 9500L,
                endMs = 15000L,
                text = "Once you implement this framework, everything else becomes significantly easier.",
                words = emptyList(),
                hookScore = 80f,
                speechEnergy = 0.65f
            ),
            TranscriptSegment(
                id = 4,
                startMs = 15500L,
                endMs = 21000L,
                text = "And here is the most important part that changes everything.",
                words = emptyList(),
                hookScore = 90f,
                speechEnergy = 0.75f
            ),
            TranscriptSegment(
                id = 5,
                startMs = 21500L,
                endMs = 28000L,
                text = "Make sure you test this strategy on your very next video upload.",
                words = emptyList(),
                hookScore = 65f,
                speechEnergy = 0.60f
            )
        )
    }

    @Test
    fun testAutoClippingFindsTopMoments() {
        val transcript = createSampleTranscript()
        val config = AutoClippingEngine.ClippingConfig(
            clipCount = 2,
            minDurationSec = 10,
            maxDurationSec = 30,
            style = ContentStyle.FACTS,
            aspectRatio = AspectRatio.VERTICAL_9_16
        )

        val clips = AutoClippingEngine.findBestMoments(
            projectId = 1L,
            totalDurationMs = 30000L,
            segments = transcript,
            config = config
        )

        assertFalse("Clips should not be empty", clips.isEmpty())
        assertTrue("Should return up to requested count", clips.size <= 2)

        val firstClip = clips.first()
        assertTrue("Clip duration must be at least min duration", firstClip.durationMs >= 9000L)
        assertTrue("Clip score must be calculated", firstClip.score >= 50f)
        assertNotNull("Reason why selected should be provided", firstClip.reasonWhySelected)
    }

    @Test
    fun testCustomLengthSequentialSplitting() {
        val transcript = createSampleTranscript()
        val config = AutoClippingEngine.CustomDurationConfig(
            targetDurationSec = 10,
            clipCount = 3,
            mode = CustomLengthMode.SEQUENTIAL,
            aspectRatio = AspectRatio.VERTICAL_9_16
        )

        val clips = AutoClippingEngine.createCustomDurationClips(
            projectId = 1L,
            totalDurationMs = 30000L,
            segments = transcript,
            config = config
        )

        assertEquals("Should create 3 sequential clips", 3, clips.size)
        assertEquals("First clip starts at 0", 0L, clips[0].startMs)
        assertEquals("First clip ends at 10s", 10000L, clips[0].endMs)
        assertEquals("Second clip starts at 10s", 10000L, clips[1].startMs)
        assertEquals("Second clip ends at 20s", 20000L, clips[1].endMs)
    }

    @Test
    fun testSubtitleGeneratorSrtFormatting() {
        val segments = listOf(
            TranscriptSegment(
                id = 1,
                startMs = 1200L,
                endMs = 3500L,
                text = "Hello viral world"
            )
        )

        val srt = SubtitleGenerator.toSrt(segments)
        assertTrue("SRT should contain index", srt.contains("1\n"))
        assertTrue("SRT should contain start timestamp", srt.contains("00:00:01,200"))
        assertTrue("SRT should contain end timestamp", srt.contains("00:00:03,500"))
        assertTrue("SRT should contain phrase text", srt.contains("Hello viral world"))
    }

    @Test
    fun testSubtitleGeneratorVttFormatting() {
        val segments = listOf(
            TranscriptSegment(
                id = 1,
                startMs = 1000L,
                endMs = 4000L,
                text = "WebVTT caption test"
            )
        )

        val vtt = SubtitleGenerator.toVtt(segments)
        assertTrue("VTT must begin with WEBVTT header", vtt.startsWith("WEBVTT"))
        assertTrue("VTT should contain period separator", vtt.contains("00:00:01.000 --> 00:00:04.000"))
        assertTrue("VTT should contain text", vtt.contains("WebVTT caption test"))
    }

    @Test
    fun testShortFormPhraseChunking() {
        val longSegment = TranscriptSegment(
            id = 1,
            startMs = 0L,
            endMs = 10000L,
            text = "One two three four five six seven eight nine ten",
            words = (1..10).map { WordTiming("word$it", (it - 1) * 1000L, it * 1000L) }
        )

        val chunked = SubtitleGenerator.splitForShortForm(listOf(longSegment), wordsPerCaption = 4)
        assertTrue("Long sentence should be split into multiple readable chunks", chunked.size >= 3)
    }

    @Test
    fun testLocalMetadataGenerator() {
        val transcript = "Did you know that retention is the single most important factor for algorithms? This changes everything."
        val metadata = MetadataGeneratorEngine.generateLocalMetadata(
            clipTitle = "Algorithm Secret",
            transcriptText = transcript,
            tone = MetadataTone.VIRAL_HOOK
        )

        assertNotNull(metadata.youtubeTitle)
        assertTrue("YouTube title should not be blank", metadata.youtubeTitle.isNotBlank())
        assertTrue("Description should mention key content", metadata.youtubeDescription.isNotBlank())
        assertFalse("Hashtags list should be populated", metadata.hashtags.isEmpty())
        assertTrue("Summary should be populated", metadata.summary.isNotBlank())
    }
}
