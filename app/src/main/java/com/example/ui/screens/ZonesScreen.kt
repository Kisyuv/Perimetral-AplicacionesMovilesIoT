package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.SensorsOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.PerimeterZone
import com.example.ui.theme.DotoFamily
import com.example.ui.theme.AccentAlarm
import com.example.ui.theme.AccentArmed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.LocalAlarmAccentColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ZonesScreen(
    zones: List<PerimeterZone> = emptyList(),
    modifier: Modifier = Modifier
) {
    val accent = LocalAlarmAccentColor.current
    val activeCount = zones.count { it.isActive }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp)
            .testTag("zones_screen")
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Encabezado de Pantalla
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ZONAS Y SENSORES",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontFamily = DotoFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Supervisión perimetral de cortinas y haces infrarrojos",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (activeCount > 0) AccentArmed.copy(alpha = 0.15f) else DarkSurfaceVariant)
                    .border(1.dp, if (activeCount > 0) AccentArmed else DarkBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (activeCount > 0) "$activeCount ACTIVAS" else "TODAS DESACTIVADAS",
                    color = if (activeCount > 0) AccentArmed else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Banner informativo sobre el estado predeterminado
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (activeCount > 0) Icons.Outlined.Shield else Icons.Outlined.SensorsOff,
                    contentDescription = null,
                    tint = if (activeCount > 0) AccentArmed else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (activeCount > 0)
                        "Hay $activeCount zona(s) activa(s) reportando telemetría al panel."
                    else
                        "Por defecto todas las zonas están desactivadas hasta que sean activadas desde el menú del panel.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (zones.isEmpty()) {
            // Estado vacío con tarjeta
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SIN SENSORES CONECTADOS",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Los sensores perimetrales sincronizarán su telemetría a través de Firebase Firestore.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(zones) { zone ->
                    ZoneItemCard(zone = zone, accent = accent)
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ZoneItemCard(
    zone: PerimeterZone,
    accent: androidx.compose.ui.graphics.Color
) {
    val isZoneActive = zone.isActive

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(
                1.dp,
                if (isZoneActive) AccentArmed.copy(alpha = 0.5f) else DarkBorder,
                RoundedCornerShape(8.dp)
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = zone.id,
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = zone.sector,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = zone.name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = zone.sensorType,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Badge de estado de zona (ACTIVADA vs DESACTIVADA)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isZoneActive) AccentArmed.copy(alpha = 0.15f)
                        else DarkSurfaceVariant
                    )
                    .border(
                        1.dp,
                        if (isZoneActive) AccentArmed else DarkBorder,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isZoneActive) AccentArmed else TextMuted)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isZoneActive) "ACTIVADA" else "DESACTIVADA",
                        color = if (isZoneActive) AccentArmed else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = if (isZoneActive) "En vigilancia activa" else "Inactivo (sin armar)",
                color = if (isZoneActive) AccentArmed else TextMuted,
                fontSize = 10.sp
            )
        }
    }
}
