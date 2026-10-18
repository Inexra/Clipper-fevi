package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.model.ClipCandidate
import com.example.data.model.ClipStatus
import com.example.engine.VideoClipperEngine
import com.example.ui.components.GlassCard
import com.example.ui.components.MetricPill
import com.example.ui.components.ScoreBadge
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ClipperViewModel
import kotlinx.coroutines.launch

@Composable
fun ClipsResultScreen(
    projectId: Long,
    viewModel: ClipperViewModel,
    onBackClick: () -> Unit,
    onOpenClipDetail: (clipId: String) -> Unit
) {
    val clips by viewModel.candidateClips.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isBulkExporting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = "Generated AI Clips",
                subtitle = "${clips.size} candidate shorts ready",
                showBackButton = true,
                onBackClick = onBackClick
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = StudioBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Header & Bulk Export
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = NeonViolet.copy(alpha = 0.5f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Ready for YouTube Shorts / Reels",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Ranked by engagement hook, completeness & dialogue clarity",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                isBulkExporting = true
                                scope.launch {
                                    var count = 0
                                    clips.forEach { clip ->
                                        viewModel.exportClip(
                                            clip = clip,
                                            options = VideoClipperEngine.ExportOptions(),
                                            onProgress = {},
                                            onComplete = { success, _ -> if (success) count++ }
                                        )
                                    }
                                    isBulkExporting = false
                                    snackbarHostState.showSnackbar("Exported $count clips to Gallery (Movies/FeviClipper)!")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("bulk_export_button"),
                            enabled = !isBulkExporting && clips.isNotEmpty()
                        ) {
                            if (isBulkExporting) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Exporting Clips to Gallery...", color = Color.Black, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Bulk Export All to Gallery (MP4)", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Clips List
            items(clips) { clip ->
                ClipCardItem(
                    clip = clip,
                    onOpen = { onOpenClipDetail(clip.id) },
                    onExportMp4 = {
                        viewModel.exportClip(
                            clip = clip,
                            options = VideoClipperEngine.ExportOptions(),
                            onProgress = {},
                            onComplete = { success, msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg ?: "Export finished") }
                            }
                        )
                    },
                    onExportSrt = {
                        viewModel.exportSrt(clip) { success, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg ?: "SRT exported") }
                        }
                    },
                    onDelete = { viewModel.deleteClip(clip.id) }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun ClipCardItem(
    clip: ClipCandidate,
    onOpen: () -> Unit,
    onExportMp4: () -> Unit,
    onExportSrt: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = clip.title,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Why selected: ${clip.reasonWhySelected}",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                ScoreBadge(score = clip.score)
            }

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(icon = Icons.Default.Schedule, label = "${clip.durationMs / 1000}s (${clip.formattedRange})")
                MetricPill(icon = Icons.Default.Subtitles, label = "${clip.subtitles.size} Subtitle phrases")
                if (clip.status == ClipStatus.COMPLETED) {
                    MetricPill(icon = Icons.Default.CheckCircle, label = "Exported", accentColor = NeonGreen)
                }
            }

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExportSrt) {
                    Icon(Icons.Default.Subtitles, contentDescription = "Export SRT", tint = NeonAmber, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onExportMp4) {
                    Icon(Icons.Default.Download, contentDescription = "Export MP4", tint = NeonGreen, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Preview & Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
