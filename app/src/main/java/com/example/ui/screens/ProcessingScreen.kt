package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.GlassCard
import com.example.ui.components.StudioTopBar
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
fun ProcessingScreen(
    projectId: Long,
    workflow: String,
    viewModel: ClipperViewModel,
    onCancel: () -> Unit,
    onCompleted: (projectId: Long) -> Unit
) {
    val state by viewModel.processingState.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.cancelProcessing()
        onCancel()
    }

    LaunchedEffect(state.completedProjectId) {
        if (state.completedProjectId != null && !state.isProcessing) {
            onCompleted(state.completedProjectId!!)
        }
    }

    val pipelineStages = listOf(
        Pair("1. Audio Extraction", "Sampling speech frequencies & volume energy"),
        Pair("2. Speech Transcription", "Generating timestamped words & sentence boundaries"),
        Pair("3. Hook Discovery & Scoring", "Finding viral hooks, complete ideas & natural pauses"),
        Pair("4. Boundary Optimization", "Snapping clip margins to prevent mid-sentence cuts"),
        Pair("5. Subtitle & Metadata Synthesis", "Formatting SRT subtitles and YouTube Shorts titles")
    )

    Scaffold(
        topBar = {
            StudioTopBar(
                title = "Processing Video",
                subtitle = "Fevi AI Clipping Pipeline",
                showBackButton = false
            )
        },
        containerColor = StudioBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.error != null) {
                // Error Display
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = NeonPink,
                    backgroundColor = NeonPink.copy(alpha = 0.1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = NeonPink, modifier = Modifier.size(44.dp))
                        Text("Processing Error", color = NeonPink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(state.error!!, color = TextPrimary, fontSize = 13.sp)
                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant)
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            } else {
                // Active Processing Indicator
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(NeonViolet.copy(alpha = 0.15f))
                        .border(2.dp, NeonCyan.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { state.progress.coerceIn(0.05f, 1f) },
                        modifier = Modifier.size(100.dp),
                        color = NeonCyan,
                        strokeWidth = 6.dp,
                        trackColor = StudioSurfaceVariant
                    )
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        color = TextPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    )
                }

                Text(
                    text = state.stage.ifBlank { "Analyzing video content..." },
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                // Pipeline Stages Progress List
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        pipelineStages.forEachIndexed { idx, (stageTitle, stageDesc) ->
                            val stageProgressFraction = (idx + 1) / 5f
                            val isDone = state.progress >= stageProgressFraction
                            val isCurrent = state.progress < stageProgressFraction && state.progress >= (idx / 5f)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isDone -> NeonGreen
                                                isCurrent -> NeonCyan
                                                else -> StudioSurfaceVariant
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDone) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    } else if (isCurrent) {
                                        CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                    } else {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TextSecondary))
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stageTitle,
                                        color = if (isDone || isCurrent) TextPrimary else TextSecondary,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = stageDesc,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Cancel Button
                OutlinedButton(
                    onClick = {
                        viewModel.cancelProcessing()
                        onCancel()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPink)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Cancel Processing")
                }
            }
        }
    }
}
