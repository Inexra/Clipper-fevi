package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.AspectRatio
import com.example.data.model.SubtitleStyleConfig
import com.example.data.model.TranscriptSegment
import com.example.engine.SubtitleGenerator
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    videoUri: String,
    modifier: Modifier = Modifier,
    clipStartMs: Long = 0L,
    clipEndMs: Long = Long.MAX_VALUE,
    aspectRatio: AspectRatio = AspectRatio.VERTICAL_9_16,
    subtitles: List<TranscriptSegment> = emptyList(),
    subtitleStyle: SubtitleStyleConfig? = null,
    autoPlay: Boolean = true
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(autoPlay) }
    var currentPositionMs by remember { mutableLongStateOf(clipStartMs) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = autoPlay
        }
    }

    LaunchedEffect(videoUri) {
        if (videoUri.isNotBlank()) {
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUri))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            if (clipStartMs > 0) {
                exoPlayer.seekTo(clipStartMs)
            }
        }
    }

    // Monitor playback boundary and progress
    LaunchedEffect(exoPlayer, clipStartMs, clipEndMs) {
        while (true) {
            val pos = exoPlayer.currentPosition
            currentPositionMs = pos

            // Loop clip within bounds
            if (pos >= clipEndMs) {
                exoPlayer.seekTo(clipStartMs)
            }

            isPlaying = exoPlayer.isPlaying
            delay(50)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    val containerRatio = when (aspectRatio) {
        AspectRatio.VERTICAL_9_16 -> 9f / 16f
        AspectRatio.SQUARE_1_1 -> 1f
        AspectRatio.PORTRAIT_4_5 -> 4f / 5f
        AspectRatio.LANDSCAPE_16_9 -> 16f / 9f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(containerRatio)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = if (aspectRatio == AspectRatio.VERTICAL_9_16) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtitle Overlay
        if (subtitleStyle != null && subtitles.isNotEmpty()) {
            val (activeSeg, _) = SubtitleGenerator.getActiveSubtitleAt(subtitles, currentPositionMs)
            SubtitleOverlay(
                segment = activeSeg,
                currentMs = currentPositionMs,
                style = subtitleStyle,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Tap to Play / Pause Controller
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        if (exoPlayer.currentPosition !in clipStartMs..clipEndMs) {
                            exoPlayer.seekTo(clipStartMs)
                        }
                        exoPlayer.play()
                    }
                    isPlaying = exoPlayer.isPlaying
                },
            contentAlignment = Alignment.Center
        ) {
            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}
