package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.ProjectEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.MetricPill
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.NeonVioletDark
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCardBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ClipperViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ClipperViewModel,
    onNavigateToWorkflow: (workflow: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenProject: (projectId: Long) -> Unit,
    onOpenClip: (clipId: String) -> Unit
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val exportedClips by viewModel.exportedClips.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            StudioTopBar(
                title = "Fevi AI Clipper",
                subtitle = "Viral Short-Form Studio",
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextPrimary
                        )
                    }
                }
            )
        },
        containerColor = StudioBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Processing Banner
            if (processingState.isProcessing) {
                item {
                    GlassCard(
                        borderColor = NeonCyan,
                        backgroundColor = NeonVioletDark.copy(alpha = 0.25f)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = processingState.stage.ifBlank { "Processing video..." },
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            LinearProgressIndicator(
                                progress = { processingState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = NeonCyan,
                                trackColor = StudioSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Hero studio tagline
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(NeonVioletDark.copy(alpha = 0.4f), StudioSurface)
                            )
                        )
                        .border(1.dp, StudioCardBorder, RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "AUTOMATED VIRAL REPURPOSING",
                                color = NeonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "Turn long videos into shorts in seconds",
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            lineHeight = 26.sp
                        )
                        Text(
                            text = "AI analyzes audio energy, transcribes dialogue, detects viral hooks, and generates synchronized captions.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 4 Main Action Cards
            item {
                Text(
                    text = "Core Workflows",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 16.sp
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 1. AI Clipper
            item {
                WorkflowCard(
                    title = "AI Automatic Clipper",
                    subtitle = "Automatically find the best moments & viral hooks with sentence boundary snapping",
                    icon = Icons.Default.AutoAwesome,
                    badgeText = "SMART AI",
                    accentColor = NeonViolet,
                    testTag = "workflow_ai_clipper",
                    onClick = { onNavigateToWorkflow("clipper") }
                )
            }

            // 2. Custom Length Maker
            item {
                WorkflowCard(
                    title = "Custom Length Maker",
                    subtitle = "Generate clips with a target duration (15s, 30s, 60s) in AI or Sequential mode",
                    icon = Icons.Default.Schedule,
                    badgeText = "DURATION",
                    accentColor = NeonCyan,
                    testTag = "workflow_custom_length",
                    onClick = { onNavigateToWorkflow("custom_length") }
                )
            }

            // 3. Auto Subtitles
            item {
                WorkflowCard(
                    title = "Auto Subtitles & Captions",
                    subtitle = "Transcribe speech, edit timings, and burn synchronized dynamic word captions",
                    icon = Icons.Default.Subtitles,
                    badgeText = "CAPTIONS",
                    accentColor = NeonAmber,
                    testTag = "workflow_auto_subtitles",
                    onClick = { onNavigateToWorkflow("subtitles") }
                )
            }

            // 4. AI Metadata Generator
            item {
                WorkflowCard(
                    title = "AI Metadata Generator",
                    subtitle = "Generate YouTube Shorts titles, descriptions, hashtags, keywords & thumbnail text",
                    icon = Icons.Default.Description,
                    badgeText = "METADATA",
                    accentColor = NeonPink,
                    testTag = "workflow_ai_metadata",
                    onClick = { onNavigateToWorkflow("metadata") }
                )
            }

            // Recent Projects
            item {
                SectionHeader(
                    title = "Recent Projects",
                    subtitle = "${projects.size} video projects saved"
                )
            }

            if (projects.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No projects yet",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Import any video to begin auto clipping and generating captions.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(projects) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onOpenProject(project.id) },
                        onDelete = { viewModel.deleteProject(project.id) }
                    )
                }
            }

            // Exported Clips
            if (exportedClips.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Ready Clips (Gallery)",
                        subtitle = "${exportedClips.size} clips exported to Movies/FeviClipper"
                    )
                }
                items(exportedClips) { clip ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenClip(clip.id) },
                        borderColor = NeonGreen.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = clip.title,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Duration: ${clip.durationMs / 1000}s • Saved to Gallery",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = NeonGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun WorkflowCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        onClick = onClick,
        borderColor = accentColor.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = accentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun ProjectCard(
    project: ProjectEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(project.createdAt))
    val durationSec = project.durationMs / 1000
    val durationFormatted = "${durationSec / 60}m ${durationSec % 60}s"

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricPill(icon = Icons.Default.Schedule, label = durationFormatted)
                    MetricPill(icon = Icons.Default.VideoLibrary, label = "${project.width}x${project.height}")
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Imported $dateStr",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
