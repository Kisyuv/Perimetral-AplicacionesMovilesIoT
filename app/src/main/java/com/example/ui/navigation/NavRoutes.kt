package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    DASHBOARD(
        route = "dashboard",
        title = "Estado del Sistema",
        subtitle = "Central de control perimetral para bodega",
        icon = Icons.Outlined.Shield
    ),
    ZONES(
        route = "zones",
        title = "Zonas y Sensores",
        subtitle = "Supervisión de cortinas y haces infrarrojos",
        icon = Icons.Outlined.Sensors
    ),
    EVENTS(
        route = "events",
        title = "Registro de Eventos",
        subtitle = "Historial de intrusiones fuera de horario",
        icon = Icons.Outlined.History
    ),
    SCHEDULE(
        route = "schedule",
        title = "Horarios y Reglas",
        subtitle = "Ventana operativa y toque de queda nocturno",
        icon = Icons.Outlined.AccessTime
    ),
    NETWORK(
        route = "network",
        title = "Red Local WiFi",
        subtitle = "Enlace LAN de bodega y nodos sensores",
        icon = Icons.Outlined.Wifi
    ),
    SECURITY(
        route = "security",
        title = "Seguridad y Códigos",
        subtitle = "Gestión de PIN de bodega y autorización",
        icon = Icons.Outlined.Lock
    ),
    TESTS(
        route = "tests",
        title = "Pruebas",
        subtitle = "Diagnóstico y pruebas ISO/IEC 27400",
        icon = Icons.Outlined.Speed
    )
}
