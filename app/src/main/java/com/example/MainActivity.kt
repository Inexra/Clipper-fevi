package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.ui.navigation.Screen
import com.example.ui.screens.ClipDetailScreen
import com.example.ui.screens.ClippingConfigScreen
import com.example.ui.screens.ClipsResultScreen
import com.example.ui.screens.CustomLengthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImportVideoScreen
import com.example.ui.screens.ProcessingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StudioBackground
import com.example.ui.viewmodel.ClipperViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ClipperViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = StudioBackground
                ) {
                    FeviApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun FeviApp(viewModel: ClipperViewModel) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val currentScreen = backStack.lastOrNull() ?: Screen.Home

    fun navigateTo(screen: Screen) {
        backStack.add(screen)
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        }
    }

    fun navigateToRoot() {
        backStack.clear()
        backStack.add(Screen.Home)
    }

    if (backStack.size > 1) {
        BackHandler {
            navigateBack()
        }
    }

    when (currentScreen) {
        is Screen.Home -> {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToWorkflow = { workflow ->
                    navigateTo(Screen.ImportVideo(workflow))
                },
                onNavigateToSettings = {
                    navigateTo(Screen.Settings)
                },
                onOpenProject = { projectId ->
                    viewModel.selectProject(projectId)
                    navigateTo(Screen.ClipsResult(projectId))
                },
                onOpenClip = { clipId ->
                    viewModel.selectClip(clipId)
                    navigateTo(Screen.ClipDetail(clipId))
                }
            )
        }

        is Screen.Settings -> {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { navigateBack() }
            )
        }

        is Screen.ImportVideo -> {
            val workflow = currentScreen.workflow
            ImportVideoScreen(
                workflow = workflow,
                viewModel = viewModel,
                onBackClick = { navigateBack() },
                onVideoReady = { projectId ->
                    if (workflow == "custom_length") {
                        navigateTo(Screen.CustomLengthConfig(projectId))
                    } else {
                        navigateTo(Screen.ClippingConfig(projectId))
                    }
                }
            )
        }

        is Screen.ClippingConfig -> {
            ClippingConfigScreen(
                projectId = currentScreen.projectId,
                viewModel = viewModel,
                onBackClick = { navigateBack() },
                onStartProcessing = { pId ->
                    navigateTo(Screen.Processing(pId, "clipper"))
                }
            )
        }

        is Screen.CustomLengthConfig -> {
            CustomLengthScreen(
                projectId = currentScreen.projectId,
                viewModel = viewModel,
                onBackClick = { navigateBack() },
                onStartProcessing = { pId ->
                    navigateTo(Screen.Processing(pId, "custom_length"))
                }
            )
        }

        is Screen.Processing -> {
            ProcessingScreen(
                projectId = currentScreen.projectId,
                workflow = currentScreen.workflow,
                viewModel = viewModel,
                onCancel = { navigateBack() },
                onCompleted = { pId ->
                    // Replace processing screen with result screen
                    if (backStack.isNotEmpty()) {
                        backStack.removeAt(backStack.size - 1)
                    }
                    navigateTo(Screen.ClipsResult(pId))
                }
            )
        }

        is Screen.ClipsResult -> {
            ClipsResultScreen(
                projectId = currentScreen.projectId,
                viewModel = viewModel,
                onBackClick = { navigateToRoot() },
                onOpenClipDetail = { clipId ->
                    navigateTo(Screen.ClipDetail(clipId))
                }
            )
        }

        is Screen.ClipDetail -> {
            ClipDetailScreen(
                clipId = currentScreen.clipId,
                viewModel = viewModel,
                onBackClick = { navigateBack() }
            )
        }
    }
}
