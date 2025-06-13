package com.nova.music.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),  // Purple primary
    background = Color(0xFF121212),  // Pure black background
    surface = Color(0xFF121212),  // Pure black surface
    secondary = Color(0xFF03DAC6),  // Teal secondary
    onBackground = Color(0xFFFFFFFF),  // White text
    onSurface = Color(0xFFFFFFFF),  // White text
    onSurfaceVariant = Color(0xFFE6E1E5),  // Light gray text
    surfaceVariant = Color(0xFF1E1E1E),  // Slightly lighter black for cards
    primaryContainer = Color(0xFF3700B3),  // Darker purple for containers
    onPrimaryContainer = Color(0xFFE6E1E5),  // Light gray on containers
    secondaryContainer = Color(0xFF1E1E1E),  // Dark surface for containers
    onSecondaryContainer = Color(0xFFE6E1E5)  // Light gray on containers
)

@Composable
fun NovaTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color(0xFF121212).toArgb()  // Pure black status bar
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}