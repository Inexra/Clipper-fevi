package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.AspectRatio
import com.example.data.model.ContentStyle
import com.example.data.model.SubtitlePreset
import com.example.engine.AutoClippingEngine
import com.example.ui.components.GlassCard
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.NeonVioletDark
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCardBorder
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ClipperViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClippingConfigScreen(
    projectId: Long,
    viewModel: ClipperViewModel,
    onBackClick: () -> Unit,
    onStartProcessing: (projectId: Long) -> Unit
) {
    val defaultAspect by viewModel.defaultAspectRatio.collectAsStateWithLifecycle()
    val defaultPres by viewModel.defaultPreset.collectAsStateWithLifecycle()

    var clipCount by remember { mutableIntStateOf(3) }
    var minDurationSec by remember { mutableFloatStateOf(15f) }
    var maxDurationSec by remember { mutableFloatStateOf(60f) }
    var selectedAspect by remember { mutableStateOf(defaultAspect) }
    var selectedStyle by remember { mutableStateOf(ContentStyle.GENERAL) }
    var selectedPreset by remember { mutableStateOf(defaultPres) }
    var prioritizeSpeech by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = "AI Clipper Configuration",
                subtitle = "Tune clip discovery preferences",
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
            // Number of clips
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Clips to Generate", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("$clipCount clips", color = NeonCyan, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                        Slider(
                            value = clipCount.toFloat(),
                            onValueChange = { clipCount = it.toInt() },
                            valueRange = 1f..8f,
                            steps = 6,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = StudioSurfaceVariant
                            )
                        )
                    }
                }
            }

            // Duration Range
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Duration Range", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${minDurationSec.toInt()}s - ${maxDurationSec.toInt()}s", color = NeonCyan, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                        Slider(
                            value = maxDurationSec,
                            onValueChange = { maxDurationSec = it },
                            valueRange = 15f..120f,
                            steps = 6,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = StudioSurfaceVariant
                            )
                        )
                        Text(
                            text = "Engine will snap boundaries to natural sentence pauses within this range.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Aspect Ratio Selector
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Output Video Format", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AspectRatio.entries.forEach { aspect ->
                                val isSelected = aspect == selectedAspect
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) NeonViolet else StudioSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) NeonCyan else StudioCardBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedAspect = aspect }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = aspect.label,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Content Style
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Content Style & Hook Focus", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ContentStyle.entries.forEach { style ->
                                val isSelected = style == selectedStyle
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) NeonViolet else StudioSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) NeonCyan else StudioCardBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedStyle = style }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = style.title,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        Text(
                            text = selectedStyle.description,
                            color = NeonCyan,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Caption Preset
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Auto Subtitle Style", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SubtitlePreset.entries.forEach { preset ->
                                val isSelected = preset == selectedPreset
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) NeonViolet else StudioSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) NeonCyan else StudioCardBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            selectedPreset = preset
                                            viewModel.setDefaultPreset(preset)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
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
                    }
                }
            }

            // Prioritize speech over scene
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Prioritize Dialogue Boundaries", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Keeps complete sentences instead of cutting off mid-word", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = prioritizeSpeech,
                            onCheckedChange = { prioritizeSpeech = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonCyan,
                                checkedTrackColor = NeonVioletDark
                            )
                        )
                    }
                }
            }

            // Start Clipping Button
            item {
                Button(
                    onClick = {
                        val config = AutoClippingEngine.ClippingConfig(
                            clipCount = clipCount,
                            minDurationSec = minDurationSec.toInt(),
                            maxDurationSec = maxDurationSec.toInt(),
                            style = selectedStyle,
                            aspectRatio = selectedAspect,
                            prioritizeSpeechOverScene = prioritizeSpeech
                        )
                        viewModel.startAutoClipping(projectId, config)
                        onStartProcessing(projectId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_clipping_button")
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Generate $clipCount AI Clips",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
