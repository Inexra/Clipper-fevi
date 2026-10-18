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
        val totalDurationMs = 20 * 60 * 1000L
        val clips = AutoClippingEngine.findBestMoments(
            projectId = 1L,
            totalDurationMs = totalDurationMs,
            segments = emptyList(),
            config = AutoClippingEngine.ClippingConfig(
                clipCount = 3,
                aspectRatio = AspectRatio.VERTICAL_9_16
            )
        )

        assertEquals(3, clips.size)
        val ordered = clips.sortedBy { it.startMs }
        // The fallback samples the centre of each of three equal timeline slots.
        assertTrue(ordered[0].startMs in (2 * 60_000L)..(4 * 60_000L))
        assertTrue(ordered[1].startMs in (8 * 60_000L)..(11 * 60_000L))
        assertTrue(ordered[2].startMs in (15 * 60_000L)..(18 * 60_000L))
        assertTrue(ordered[2].endMs <= totalDurationMs)
        assertTrue(ordered[1].startMs - ordered[0].startMs > 5 * 60_000L)
        assertTrue(ordered[2].startMs - ordered[1].startMs > 5 * 60_000L)
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
