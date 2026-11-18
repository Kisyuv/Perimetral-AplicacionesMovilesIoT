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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SecurityEvent
import com.example.ui.theme.DotoFamily
import com.example.ui.theme.AccentAlarm
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.LocalAlarmAccentColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EventsScreen(
    events: List<SecurityEvent> = emptyList(),
    modifier: Modifier = Modifier
) {
    val accent = LocalAlarmAccentColor.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 24.dp)
            .testTag("events_screen")
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Encabezado de Pantalla
        Column {
            Text(
                text = "REGISTRO DE EVENTOS",
                color = TextPrimary,
                fontSize = 24.sp,
                fontFamily = DotoFamily,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Historial de intrusiones y activaciones en tiempo real",
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (events.isEmpty()) {
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
                        text = "SIN EVENTOS REGISTRADOS",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "El registro de alarmas y aperturas de bodega se sincroniza con Firestore en tiempo real.",
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
                items(events) { event ->
                    EventCard(event = event, accent = accent)
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: SecurityEvent,
    accent: androidx.compose.ui.graphics.Color
) {
    val dateFormat = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
    val formattedTime = dateFormat.format(Date(event.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurface)
            .border(1.dp, if (event.tipo == "alarma") AccentAlarm else DarkBorder, RoundedCornerShape(6.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (event.tipo) {
                    "alarma" -> "🚨 DISPARO DE ALARMA"
                    "pir" -> "Detección de Movimiento (PIR)"
                    "reed" -> "Contacto Magnético (Puerta)"
                    "armado" -> "Sistema Armado"
                    "desarmado" -> "Sistema Desarmado"
                    else -> "Evento de Sistema"
                },
                color = if (event.tipo == "alarma") AccentAlarm else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formattedTime,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = event.detalle,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "ORIGEN: ${event.origen.uppercase()}",
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )
    }
}
