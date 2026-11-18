package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Acento activo: blanco en estado normal, rojo solo mientras suena la alarma
val LocalAlarmAccentColor = staticCompositionLocalOf { TextPrimary }

// Esquinas sobrias, sin sombras: la jerarquía la dan los bordes de 1dp
val PerimetralShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp)
)

@Composable
fun PerimetralTheme(
    systemState: SystemAlarmState = SystemAlarmState.DISARMED,
    content: @Composable () -> Unit
) {
    val activeAccent = if (systemState == SystemAlarmState.ALARM) AccentAlarm else TextPrimary

    val darkColorScheme = darkColorScheme(
        primary = activeAccent,
        onPrimary = DarkBackground,
        primaryContainer = DarkSurfaceVariant,
        onPrimaryContainer = activeAccent,
        secondary = TextSecondary,
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
            shapes = PerimetralShapes,
            content = content
        )
    }
}
