package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Provider para el color de acento único activo del sistema perimetral
val LocalAlarmAccentColor = staticCompositionLocalOf { AccentDisarmed }

@Composable
fun PerimetralTheme(
    systemState: SystemAlarmState = SystemAlarmState.DISARMED,
    content: @Composable () -> Unit
) {
    val activeAccent = systemState.accentColor

    val darkColorScheme = darkColorScheme(
        primary = activeAccent,
        onPrimary = DarkBackground,
        primaryContainer = DarkSurfaceVariant,
        onPrimaryContainer = activeAccent,
        secondary = activeAccent,
        onSecondary = DarkBackground,
        background = DarkBackground,
        onBackground = TextPrimary,
        surface = DarkSurface,
        onSurface = TextPrimary,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = TextSecondary,
        outline = DarkBorder,
        outlineVariant = DarkBorderSubtle,
        error = AccentAlarm,
        onError = Color.White
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(
        LocalAlarmAccentColor provides activeAccent
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme,
            typography = Typography,
            content = content
        )
    }
}
