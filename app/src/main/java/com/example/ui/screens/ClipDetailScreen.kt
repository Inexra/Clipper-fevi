package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AspectRatio
import com.example.data.model.ExportResolution
import com.example.data.model.MetadataTone
import com.example.data.model.SubtitlePosition
import com.example.data.model.SubtitlePreset
import com.example.engine.VideoClipperEngine
import com.example.ui.components.GlassCard
import com.example.ui.components.ScoreBadge
import com.example.ui.components.StudioTopBar
import com.example.ui.components.VideoPlayerView
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipDetailScreen(
    clipId: String,
    viewModel: ClipperViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val clip by viewModel.selectedClip.collectAsStateWithLifecycle()
    val project by viewModel.currentProject.collectAsStateWithLifecycle()
    val subtitleStyle by viewModel.subtitleStyle.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var trimStartSec by remember { mutableFloatStateOf(0f) }
    var trimEndSec by remember { mutableFloatStateOf(0f) }

    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var burnSubtitles by remember { mutableStateOf(true) }
    var selectedAspect by remember { mutableStateOf(AspectRatio.VERTICAL_9_16) }
    var selectedRes by remember { mutableStateOf(ExportResolution.RES_1080P) }

    LaunchedEffect(clipId) {
        viewModel.selectClip(clipId)
    }

    LaunchedEffect(clip) {
        if (clip != null) {
            trimStartSec = clip!!.startMs / 1000f
            trimEndSec = clip!!.endMs / 1000f
            selectedAspect = clip!!.aspectRatio
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clipData)
        scope.launch { snackbarHostState.showSnackbar("Copied $label to clipboard!") }
    }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = clip?.title ?: "Clip Detail",
                subtitle = "Preview, edit & export short",
                showBackButton = true,
                onBackClick = onBackClick
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = StudioBackground
    ) { padding ->
        if (clip == null || project == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonCyan)
            }
            return@Scaffold
        }

        val currentClip = clip!!
        val currentProject = project!!

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Video Player Preview with live subtitles
            item {
                VideoPlayerView(
                    videoUri = currentProject.sourceVideoUri,
                    clipStartMs = (trimStartSec * 1000).toLong(),
                    clipEndMs = (trimEndSec * 1000).toLong(),
                    aspectRatio = selectedAspect,
                    subtitles = currentClip.subtitles,
                    subtitleStyle = subtitleStyle,
                    autoPlay = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Boundary Trimming Slider
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Boundary Trimming", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "${trimStartSec.toInt()}s - ${trimEndSec.toInt()}s (${(trimEndSec - trimStartSec).toInt()}s duration)",
                                color = NeonCyan,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }

                        // Start boundary slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Start", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                            Slider(
                                value = trimStartSec,
                                onValueChange = {
                                    trimStartSec = it.coerceAtMost(trimEndSec - 3f)
                                    viewModel.updateClipBoundaries(
                                        currentClip.id,
                                        (trimStartSec * 1000).toLong(),
                                        (trimEndSec * 1000).toLong()
                                    )
                                },
                                valueRange = 0f..(currentProject.durationMs / 1000f),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                            )
                        }

                        // End boundary slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("End", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                            Slider(
                                value = trimEndSec,
                                onValueChange = {
                                    trimEndSec = it.coerceAtLeast(trimStartSec + 3f)
                                    viewModel.updateClipBoundaries(
                                        currentClip.id,
                                        (trimStartSec * 1000).toLong(),
                                        (trimEndSec * 1000).toLong()
                                    )
                                },
                                valueRange = 0f..(currentProject.durationMs / 1000f),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = NeonViolet, activeTrackColor = NeonViolet)
                            )
                        }
                    }
                }
            }

            // Tabs: Subtitles, Metadata, Export
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = StudioSurface,
                    contentColor = NeonCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = NeonCyan
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Subtitles & Style", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("AI Metadata", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Export MP4", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }
            }

            // TAB 0: SUBTITLES & STYLE
            if (selectedTab == 0) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Subtitle Presets", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SubtitlePreset.entries.forEach { preset ->
                                    val isSelected = subtitleStyle.preset == preset
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) NeonViolet else StudioSurfaceVariant)
                                            .border(1.dp, if (isSelected) NeonCyan else StudioCardBorder, RoundedCornerShape(8.dp))
                                            .clickable { viewModel.setDefaultPreset(preset) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = preset.displayName,
                                            color = if (isSelected) Color.White else TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            // Word highlight toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Dynamic Word Highlighting", color = TextPrimary, fontSize = 13.sp)
                                Switch(
                                    checked = subtitleStyle.wordHighlightEnabled,
                                    onCheckedChange = {
                                        viewModel.updateSubtitleStyle(subtitleStyle.copy(wordHighlightEnabled = it))
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = NeonVioletDark)
                                )
                            }

                            // Position
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Position:", color = TextSecondary, fontSize = 12.sp)
                                SubtitlePosition.entries.forEach { pos ->
                                    val isSel = subtitleStyle.position == pos
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSel) NeonCyan else StudioSurfaceVariant)
                                            .clickable { viewModel.updateSubtitleStyle(subtitleStyle.copy(position = pos)) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(pos.name, color = if (isSel) Color.Black else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Download SRT button
                            OutlinedButton(
                                onClick = {
                                    viewModel.exportSrt(currentClip) { success, msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg ?: "SRT exported") }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = NeonAmber)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export Subtitles as SRT File", color = TextPrimary)
                            }
                        }
                    }
                }
            }

            // TAB 1: AI METADATA
            if (selectedTab == 1) {
                val meta = currentClip.metadata
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("YouTube Shorts Metadata", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Button(
                                    onClick = { viewModel.regenerateMetadata(currentClip.id, MetadataTone.VIRAL_HOOK) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Regenerate", fontSize = 11.sp)
                                }
                            }

                            if (meta != null) {
                                // Title Field
                                MetadataFieldBox(
                                    title = "YouTube Shorts Title",
                                    content = meta.youtubeTitle,
                                    onCopy = { copyToClipboard("Title", meta.youtubeTitle) }
                                )

                                // Description Field
                                MetadataFieldBox(
                                    title = "Video Description",
                                    content = meta.youtubeDescription,
                                    onCopy = { copyToClipboard("Description", meta.youtubeDescription) }
                                )

                                // Social Caption
                                MetadataFieldBox(
                                    title = "Reels / TikTok Caption",
                                    content = meta.socialCaption,
                                    onCopy = { copyToClipboard("Caption", meta.socialCaption) }
                                )

                                // Hashtags
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Hashtags", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = {
                                            copyToClipboard("Hashtags", meta.hashtags.joinToString(" ") { "#$it" })
                                        }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Tags", tint = NeonCyan, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        meta.hashtags.forEach { tag ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(NeonCyan.copy(alpha = 0.15f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("#$tag", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }

                                // Thumbnail Text
                                MetadataFieldBox(
                                    title = "Suggested Thumbnail Text",
                                    content = meta.thumbnailText,
                                    onCopy = { copyToClipboard("Thumbnail Text", meta.thumbnailText) }
                                )

                                // Pinned Comment
                                MetadataFieldBox(
                                    title = "Suggested Pinned Comment",
                                    content = meta.pinnedComment,
                                    onCopy = { copyToClipboard("Pinned Comment", meta.pinnedComment) }
                                )
                            }
                        }
                    }
                }
            }

            // TAB 2: EXPORT OPTIONS
            if (selectedTab == 2) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Export Render Options", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                            // Aspect Ratio
                            Text("Aspect Ratio", color = TextSecondary, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AspectRatio.entries.forEach { aspect ->
                                    val isSel = selectedAspect == aspect
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) NeonViolet else StudioSurfaceVariant)
                                            .border(1.dp, if (isSel) NeonCyan else StudioCardBorder, RoundedCornerShape(8.dp))
                                            .clickable { selectedAspect = aspect }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(aspect.label, color = if (isSel) Color.White else TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }

                            // Burn Subtitles toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Include Subtitles with Video", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("Companion synchronized .srt file is also saved", color = TextSecondary, fontSize = 11.sp)
                                }
                                Switch(
                                    checked = burnSubtitles,
                                    onCheckedChange = { burnSubtitles = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = NeonVioletDark)
                                )
                            }

                            // Resolution
                            Text("Resolution", color = TextSecondary, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExportResolution.entries.forEach { res ->
                                    val isSel = selectedRes == res
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) NeonCyan else StudioSurfaceVariant)
                                            .clickable { selectedRes = res }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(res.label, color = if (isSel) Color.Black else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (isExporting) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Clipping and saving MP4 to Gallery...", color = TextPrimary, fontSize = 13.sp)
                                    LinearProgressIndicator(
                                        progress = { exportProgress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                        color = NeonCyan,
                                        trackColor = StudioSurfaceVariant
                                    )
                                }
                            }

                            // Export Button
                            Button(
                                onClick = {
                                    isExporting = true
                                    exportProgress = 0.1f
                                    viewModel.exportClip(
                                        clip = currentClip,
                                        options = VideoClipperEngine.ExportOptions(
                                            aspectRatio = selectedAspect,
                                            resolution = selectedRes,
                                            burnSubtitles = burnSubtitles,
                                            srtText = currentClip.srtContent
                                        ),
                                        onProgress = { exportProgress = it },
                                        onComplete = { success, msg ->
                                            isExporting = false
                                            scope.launch { snackbarHostState.showSnackbar(msg ?: "Export finished") }
                                        }
                                    )
                                },
                                enabled = !isExporting,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("export_clip_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Export MP4 to Device Gallery",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun MetadataFieldBox(
    title: String,
    content: String,
    onCopy: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = NeonCyan, modifier = Modifier.size(16.dp))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(StudioSurfaceVariant)
                .border(1.dp, StudioCardBorder, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text(
                text = content.ifBlank { "None generated" },
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}
