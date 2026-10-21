package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta base minimalista oscura para Perimetral
val DarkBackground = Color(0xFF090C10)
val DarkSurface = Color(0xFF10141B)
val DarkSurfaceVariant = Color(0xFF161C26)
val DarkBorder = Color(0xFF222B38)
val DarkBorderSubtle = Color(0xFF18202A)

// Colores de texto
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Color de acento único según estado de alarma
val AccentDisarmed = Color(0xFF10B981) // Verde esmeralda minimalista
val AccentArmed = Color(0xFFF59E0B)    // Ámbar sobrio
val AccentAlarm = Color(0xFFEF4444)    // Rojo alerta

// Modos de estado del sistema
enum class SystemAlarmState(val label: String, val accentColor: Color) {
    DISARMED("DESARMADA", AccentDisarmed),
    ARMED("ARMADA", AccentArmed),
    ALARM("ALARMA", AccentAlarm)
}
