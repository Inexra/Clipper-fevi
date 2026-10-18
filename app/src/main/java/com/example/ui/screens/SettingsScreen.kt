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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.model.AspectRatio
import com.example.data.model.ExportResolution
import com.example.data.model.SubtitlePreset
import com.example.data.network.GeminiService
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
import com.example.engine.LocalQwenEngine
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: ClipperViewModel,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var qwenReady by remember { mutableStateOf(false) }
    var qwenDownloading by remember { mutableStateOf(false) }
    var qwenProgress by remember { mutableStateOf(0f) }
    var qwenError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { qwenReady = LocalQwenEngine.isModelAvailable(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val savedApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val defaultAspect by viewModel.defaultAspectRatio.collectAsStateWithLifecycle()
    val defaultPreset by viewModel.defaultPreset.collectAsStateWithLifecycle()
    val defaultResolution by viewModel.exportResolution.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()

    var apiKeyInput by remember { mutableStateOf("") }
    var isKeyVisible by remember { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }

    LaunchedEffect(savedApiKey) {
        apiKeyInput = savedApiKey
    }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = "Settings & Privacy",
                subtitle = "API configuration, models & storage",
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

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = NeonCyan.copy(alpha = 0.55f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonCyan)
                            Text("Offline AI models", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Text(
                            text = if (qwenReady)
                                "Qwen2.5 0.5B is bundled in this APK. First use copies it into app storage; local text generation then works without a Gemini API key."
                            else
                                "Qwen2.5 0.5B is normally included in the APK (~491 MB). If this build has the model assets, they are prepared locally without internet. Keep at least 1 GB free storage.",
                            color = TextSecondary, fontSize = 12.sp
                        )
                        if (qwenDownloading) {
                            CircularProgressIndicator(progress = { qwenProgress.coerceIn(0f, 1f) }, color = NeonCyan)
                            Text("Downloading model: ${(qwenProgress * 100).toInt()}%", color = TextSecondary, fontSize = 12.sp)
                        }
                        if (qwenError != null) Text(qwenError!!, color = NeonPink, fontSize = 12.sp)
                        Button(
                            onClick = {
                                scope.launch {
                                    qwenDownloading = true
                                    qwenError = null
                                    try {
                                        LocalQwenEngine.downloadModel(context) { qwenProgress = it }
                                        qwenReady = true
                                        snackbarHostState.showSnackbar("Qwen model downloaded")
                                    } catch (e: Exception) {
                                        qwenError = e.localizedMessage ?: "Model download failed"
                                    } finally {
                                        qwenDownloading = false
                                    }
                                }
                            },
                            enabled = !qwenDownloading && !qwenReady,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                        ) {
                            Text(if (qwenReady) "Qwen model bundled / ready" else if (qwenDownloading) "Preparing model…" else "Prepare offline Qwen model")
                        }
                    }
                }
            }

            // Gemini API Key Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = NeonViolet.copy(alpha = 0.5f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = NeonCyan)
                            Text(
                                text = "Gemini AI API Key",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Text(
                            text = "Used for advanced YouTube Shorts metadata generation & smart audio transcription via Gemini 3.5 Flash. If left blank, Fevi Clipper uses the built-in local template engine.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("Gemini API Key (AI Studio)") },
                            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility",
                                        tint = TextSecondary
                                    )
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = StudioCardBorder,
                                focusedLabelColor = NeonCyan,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_api_key_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.setGeminiApiKey(apiKeyInput)
                                    scope.launch { snackbarHostState.showSnackbar("API key saved!") }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save Key", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    isValidating = true
                                    scope.launch {
                                        val res = GeminiService.testApiKey(apiKeyInput.trim())
                                        isValidating = false
                                        if (res.isSuccess) {
                                            snackbarHostState.showSnackbar("Key validated successfully! (Gemini 3.5 Flash active)")
                                        } else {
                                            snackbarHostState.showSnackbar("Validation failed: ${res.exceptionOrNull()?.message}")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isValidating && apiKeyInput.isNotBlank()
                            ) {
                                if (isValidating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonCyan)
                                } else {
                                    Text("Validate", color = NeonCyan)
                                }
                            }

                            if (savedApiKey.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.setGeminiApiKey("")
                                        apiKeyInput = ""
                                        scope.launch { snackbarHostState.showSnackbar("API key removed") }
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = NeonPink)
                                }
                            }
                        }
                    }
                }
            }

            // Defaults Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Default Video Preferences",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        // Aspect Ratio
                        Text("Default Aspect Ratio", color = TextSecondary, fontSize = 12.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AspectRatio.entries.forEach { aspect ->
                                val isSel = defaultAspect == aspect
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) NeonViolet else StudioSurfaceVariant)
                                        .border(1.dp, if (isSel) NeonCyan else StudioCardBorder, RoundedCornerShape(8.dp))
                                        .clickable { viewModel.setDefaultAspectRatio(aspect) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(aspect.label, color = if (isSel) Color.White else TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }

                        // Subtitle Preset
                        Text("Default Subtitle Style", color = TextSecondary, fontSize = 12.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubtitlePreset.entries.forEach { preset ->
                                val isSel = defaultPreset == preset
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) NeonViolet else StudioSurfaceVariant)
                                        .border(1.dp, if (isSel) NeonCyan else StudioCardBorder, RoundedCornerShape(8.dp))
                                        .clickable { viewModel.setDefaultPreset(preset) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(preset.displayName, color = if (isSel) Color.White else TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }

                        // Export Resolution
                        Text("Default Export Resolution", color = TextSecondary, fontSize = 12.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExportResolution.entries.forEach { res ->
                                val isSel = defaultResolution == res
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) NeonCyan else StudioSurfaceVariant)
                                        .clickable { viewModel.setExportResolution(res) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(res.label, color = if (isSel) Color.Black else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Storage & Privacy Card
            item {
                val formattedStorage = if (storageBytes <= 0) "0 MB" else String.format(Locale.US, "%.1f MB", storageBytes / (1024f * 1024f))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = NeonCyan)
                            Text("Local Storage & Privacy", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Text(
                            text = "Temporary Cache Used: $formattedStorage\n" +
                                    "Saved destination: Movies/FeviClipper in device gallery.\n" +
                                    "Source videos remain 100% on your device and are never uploaded.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Button(
                            onClick = {
                                viewModel.cleanTempFiles()
                                scope.launch { snackbarHostState.showSnackbar("Temporary cache files cleaned!") }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = NeonCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clean Temporary Cache Files", color = TextPrimary)
                        }
                    }
                }
            }

            // Engine & Architecture Notice
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = StudioCardBorder
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                            Text("Motorola Moto G57 Power & Android 17 Tuned", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text(
                            text = "Native Android hardware-accelerated MediaExtractor & MediaMuxer pipeline. Speech pause detection via native audio frame analysis. Zero external cloud tracking.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
