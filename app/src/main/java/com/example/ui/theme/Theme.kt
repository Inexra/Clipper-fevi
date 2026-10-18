package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val FeviDarkColorScheme = darkColorScheme(
    primary = NeonViolet,
    onPrimary = TextPrimary,
    primaryContainer = NeonVioletDark,
    onPrimaryContainer = TextPrimary,
    secondary = NeonCyan,
    onSecondary = StudioBackground,
    secondaryContainer = StudioSurfaceVariant,
    onSecondaryContainer = NeonCyanGlow,
    tertiary = NeonAmber,
    onTertiary = StudioBackground,
    background = StudioBackground,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = StudioCardBorder,
    error = NeonPink,
    onError = TextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Fevi Clipper is tailored with a dark studio interface
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = FeviDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = StudioBackground.toArgb()
                window.navigationBarColor = StudioBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
