package com.openchat.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryTeal,
    secondary = PrimaryTealDark,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkCardBackground,
    onPrimary = DarkBackground,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTeal,
    secondary = PrimaryTealDark,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightCardBackground,
    onPrimary = LightBackground,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary,
    error = ErrorRed
)

@Composable
fun OpenChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontSize: Float = 16f,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = getTypography(fontSize),
        shapes = Shapes,
        content = content
    )
}
