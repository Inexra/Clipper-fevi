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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AspectRatio
import com.example.data.model.CustomLengthMode
import com.example.engine.AutoClippingEngine
import com.example.ui.components.GlassCard
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCardBorder
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ClipperViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomLengthScreen(
    projectId: Long,
    viewModel: ClipperViewModel,
    onBackClick: () -> Unit,
    onStartProcessing: (projectId: Long) -> Unit
) {
    val defaultAspect by viewModel.defaultAspectRatio.collectAsStateWithLifecycle()

    var targetDurationSec by remember { mutableIntStateOf(30) }
    var customInputText by remember { mutableStateOf("") }
    var isCustomSelected by remember { mutableStateOf(false) }
    var clipCount by remember { mutableIntStateOf(3) }
    var selectedMode by remember { mutableStateOf(CustomLengthMode.SMART_AI) }
    var selectedAspect by remember { mutableStateOf(defaultAspect) }

    val presetDurations = listOf(
        Pair("15s", 15),
        Pair("30s", 30),
        Pair("45s", 45),
        Pair("60s", 60),
        Pair("90s", 90),
        Pair("2 min", 120),
        Pair("3 min", 180)
    )

    Scaffold(
        topBar = {
            StudioTopBar(
                title = "Custom Length Maker",
                subtitle = "Duration-based clipping & splitting",
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
            // Mode Selector: Smart AI vs Sequential
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Clipping Mode", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Smart AI
                            val isSmart = selectedMode == CustomLengthMode.SMART_AI
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSmart) NeonViolet else StudioSurfaceVariant)
                                    .border(1.dp, if (isSmart) NeonCyan else StudioCardBorder, RoundedCornerShape(12.dp))
                                    .clickable { selectedMode = CustomLengthMode.SMART_AI }
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (isSmart) NeonCyan else TextSecondary, modifier = Modifier.size(16.dp))
                                        Text("Smart AI Mode", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Text("Finds complete sentences & best moments near target duration", color = TextSecondary, fontSize = 10.sp, lineHeight = 14.sp)
                                }
                            }

                            // Sequential
                            val isSeq = selectedMode == CustomLengthMode.SEQUENTIAL
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSeq) NeonViolet else StudioSurfaceVariant)
                                    .border(1.dp, if (isSeq) NeonCyan else StudioCardBorder, RoundedCornerShape(12.dp))
                                    .clickable { selectedMode = CustomLengthMode.SEQUENTIAL }
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = if (isSeq) NeonCyan else TextSecondary, modifier = Modifier.size(16.dp))
                                        Text("Sequential Mode", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Text("Splits source into consecutive chronological parts", color = TextSecondary, fontSize = 10.sp, lineHeight = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Target Duration Presets
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Target Duration", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetDurations.forEach { (label, sec) ->
                                val isSelected = !isCustomSelected && targetDurationSec == sec
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) NeonViolet else StudioSurfaceVariant)
                                        .border(1.dp, if (isSelected) NeonCyan else StudioCardBorder, RoundedCornerShape(10.dp))
                                        .clickable {
                                            targetDurationSec = sec
                                            isCustomSelected = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            // Custom Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isCustomSelected) NeonViolet else StudioSurfaceVariant)
                                    .border(1.dp, if (isCustomSelected) NeonCyan else StudioCardBorder, RoundedCornerShape(10.dp))
                                    .clickable { isCustomSelected = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Custom",
                                    color = if (isCustomSelected) Color.White else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        if (isCustomSelected) {
                            OutlinedTextField(
                                value = customInputText,
                                onValueChange = {
                                    customInputText = it
                                    val parsed = it.toIntOrNull()
                                    if (parsed != null && parsed > 0) {
                                        targetDurationSec = parsed
                                    }
                                },
                                label = { Text("Target Duration (seconds)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = StudioCardBorder,
                                    focusedLabelColor = NeonCyan,
                                    unfocusedLabelColor = TextSecondary,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Number of clips
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Number of Clips", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("$clipCount clips", color = NeonCyan, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                        Slider(
                            value = clipCount.toFloat(),
                            onValueChange = { clipCount = it.toInt() },
                            valueRange = 1f..6f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = StudioSurfaceVariant
                            )
                        )
                    }
                }
            }

            // Aspect Ratio
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
                                        .border(1.dp, if (isSelected) NeonCyan else StudioCardBorder, RoundedCornerShape(10.dp))
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

            // Generate Button
            item {
                Button(
                    onClick = {
                        val config = AutoClippingEngine.CustomDurationConfig(
                            targetDurationSec = targetDurationSec,
                            clipCount = clipCount,
                            mode = selectedMode,
                            aspectRatio = selectedAspect
                        )
                        viewModel.startCustomLengthClipping(projectId, config)
                        onStartProcessing(projectId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("generate_custom_clips_button")
                ) {
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Generate ${targetDurationSec}s Clips",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
