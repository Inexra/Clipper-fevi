package com.example.engine

import com.example.data.model.AspectRatio
import com.example.data.model.MetadataTone
import com.example.data.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoClippingEngineFallbackTest {
    @Test
    fun fallbackClipsAreDistributedAcrossTheWholeVideo() {
        val clips = AutoClippingEngine.findBestMoments(
            projectId = 1L,
            totalDurationMs = 20 * 60 * 1000L,
            segments = emptyList(),
            config = AutoClippingEngine.ClippingConfig(
                clipCount = 3,
                aspectRatio = AspectRatio.VERTICAL_9_16
            )
        )

        assertEquals(3, clips.size)
        assertTrue(clips[0].startMs < 60_000L)
        assertTrue(clips[1].startMs in (5 * 60_000L)..(7 * 60_000L))
        assertTrue(clips[2].startMs in (11 * 60_000L)..(13 * 60_000L))
        assertTrue(clips[2].endMs <= 20 * 60 * 1000L)
    }

    @Test
    fun offlineMetadataUsesTranscriptContent() {
        val metadata = LocalMetadataGenerator.generate(
            listOf(
                TranscriptSegment(1, 0L, 3000L, "Scientists discovered a surprising ocean planet."),
                TranscriptSegment(2, 3000L, 6000L, "The ocean planet may contain water beneath its surface.")
            ),
            MetadataTone.EDUCATIONAL
        )

        assertTrue(metadata.youtubeTitle.isNotBlank())
        assertTrue(metadata.youtubeDescription.contains("ocean", ignoreCase = true))
        assertTrue(metadata.hashtags.isNotEmpty())
        assertTrue(metadata.summary.contains("planet", ignoreCase = true))
    }
}
