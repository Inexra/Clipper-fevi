package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.GlassCard
import com.example.ui.components.MetricPill
import com.example.ui.components.StudioTopBar
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCardBorder
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ClipperViewModel

@Composable
fun ImportVideoScreen(
    workflow: String,
    viewModel: ClipperViewModel,
    onBackClick: () -> Unit,
    onVideoReady: (projectId: Long) -> Unit
) {
    val currentProject by viewModel.currentProject.collectAsStateWithLifecycle()
    val videoMeta by viewModel.videoMetadata.collectAsStateWithLifecycle()

    var isReading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isReading = true
            errorMessage = null
            viewModel.loadVideo(uri) { success, err ->
                isReading = false
                if (!success) {
                    errorMessage = err ?: "Could not open video."
                }
            }
        }
    }

    val workflowTitle = when (workflow) {
        "custom_length" -> "Custom Length Maker"
        "subtitles" -> "Auto Subtitles"
        "metadata" -> "AI Metadata Generator"
        else -> "AI Automatic Clipper"
    }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = "Import Source Video",
                subtitle = workflowTitle,
                showBackButton = true,
                onBackClick = onBackClick
            )
        },
        containerColor = StudioBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Import / Pick File Button Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = NeonViolet.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(NeonViolet.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = if (videoMeta == null) "Select Video from Device" else "Replace Selected Video",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        Text(
                            text = "Supports MP4, MKV, MOV, WebM and camera recordings. All processing stays local on your device.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Button(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("select_video_button")
                        ) {
                            Icon(imageVector = Icons.Default.FileOpen, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Browse Video Files", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (isReading) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(24.dp))
                            Text("Analyzing video tracks, audio presence & codecs...", color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = NeonPink,
                        backgroundColor = NeonPink.copy(alpha = 0.1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = NeonPink)
                            Column {
                                Text("Invalid Video File", color = NeonPink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(errorMessage!!, color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Specs Card & Preview when video is loaded
            if (videoMeta != null) {
                val meta = videoMeta!!

                item {
                    Text(
                        text = "Video Specifications",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 16.sp
                        )
                    )
                }

                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = meta.fileName,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (meta.hasAudio) NeonGreen.copy(alpha = 0.15f) else NeonPink.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (meta.hasAudio) "AUDIO DETECTED" else "NO AUDIO",
                                        color = if (meta.hasAudio) NeonGreen else NeonPink,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricPill(icon = Icons.Default.Schedule, label = meta.formattedDuration)
                                MetricPill(icon = Icons.Default.VideoFile, label = meta.resolutionLabel)
                                MetricPill(icon = Icons.Default.Speed, label = "${meta.frameRate.toInt()} fps")
                            }

                            Text(
                                text = "File Size: ${meta.formattedSize} • Codec: ${meta.mimeType}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )

                            if (!meta.hasAudio) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NeonAmber.copy(alpha = 0.15f))
                                        .padding(10.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Warning: This video has no audio track. Automated transcription and speech-based clipping require spoken dialogue.",
                                            color = TextPrimary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Video Preview
                item {
                    Text(
                        text = "Source Preview",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 16.sp
                        )
                    )
                }

                item {
                    VideoPlayerView(
                        videoUri = meta.uri,
                        clipStartMs = 0L,
                        clipEndMs = meta.durationMs,
                        autoPlay = false
                    )
                }

                // Proceed Button
                item {
                    Button(
                        onClick = {
                            if (currentProject != null) {
                                onVideoReady(currentProject!!.id)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("proceed_to_config_button")
                    ) {
                        Text(
                            text = "Configure Clipping & Options",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
