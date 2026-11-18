package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta monocroma estilo Nothing OS: negro, grises y blanco. Solo la alarma lleva color.
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF0D0D0D)
val DarkSurfaceVariant = Color(0xFF1A1A1A)
val DarkBorder = Color(0xFF333333)
val DarkBorderSubtle = Color(0xFF1F1F1F)

// Colores de texto
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0B0B0)
val TextMuted = Color(0xFF8C8C8C)

// Estados en escala de grises: ARMADA = blanco sólido, DESARMADA = gris. El texto siempre acompaña al color.
val AccentDisarmed = Color(0xFFB0B0B0)
val AccentArmed = Color(0xFFFFFFFF)
// Único color de la app, solo para alarma e intrusión (rojo Nothing, aclarado hasta contraste >=4.5:1 sobre negro)
val AccentAlarm = Color(0xFFEC3A41)

// Modos de estado del sistema
enum class SystemAlarmState(val label: String, val accentColor: Color) {
    DISARMED("DESARMADA", AccentDisarmed),
    ARMED("ARMADA", AccentArmed),
    ALARM("ALARMA", AccentAlarm)
}
