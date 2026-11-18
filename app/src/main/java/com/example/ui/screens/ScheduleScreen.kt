package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SiteAlarmState
import com.example.ui.theme.DotoFamily
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.LocalAlarmAccentColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ScheduleScreen(
    siteState: SiteAlarmState = SiteAlarmState(),
    onUpdateSchedule: (inicio: String, fin: String, autoArm: Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val accent = LocalAlarmAccentColor.current
    val scrollState = rememberScrollState()

    var showScheduleEditor by remember { mutableStateOf(false) }
    var editInicio by remember { mutableStateOf(siteState.horario["inicio"] as? String ?: "08:00") }
    var editFin by remember { mutableStateOf(siteState.horario["fin"] as? String ?: "19:00") }

    // Diálogo Editor de Horarios
    if (showScheduleEditor) {
        AlertDialog(
            onDismissRequest = { showScheduleEditor = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(text = "Horario Laboral de Bodega", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Durante la jornada laboral el perímetro permanece desarmado; fuera de este horario se arma automáticamente si el switch está activo.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = editInicio,
                        onValueChange = { editInicio = it },
                        label = { Text("Hora Apertura (HH:mm)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editFin,
                        onValueChange = { editFin = it },
                        label = { Text("Hora Cierre (HH:mm)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent)
                        .clickable {
                            onUpdateSchedule(editInicio, editFin, siteState.armadoAutomatico)
                            showScheduleEditor = false
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("Guardar Horario", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { showScheduleEditor = false }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text("Cancelar", color = TextSecondary, fontSize = 12.sp)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
            .testTag("schedule_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Encabezado de Pantalla
        Column {
            Text(
                text = "HORARIOS Y REGLAS",
                color = TextPrimary,
                fontSize = 24.sp,
                fontFamily = DotoFamily,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Configuración operativa y armado automático nocturno",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }

        // Sección Movida: HORARIO OPERATIVO BODEGA Y ARMADO AUTOMÁTICO
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "HORARIO OPERATIVO BODEGA", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${siteState.horario["inicio"] ?: "08:00"} a ${siteState.horario["fin"] ?: "19:00"} (Lunes a Viernes)",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                editInicio = siteState.horario["inicio"] as? String ?: "08:00"
                                editFin = siteState.horario["fin"] as? String ?: "19:00"
                                showScheduleEditor = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Editar", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Armado automático fuera de horario",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Arma el sistema automáticamente fuera de la jornada laboral",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = siteState.armadoAutomatico,
                        onCheckedChange = { checked ->
                            val ini = siteState.horario["inicio"] as? String ?: "08:00"
                            val fi = siteState.horario["fin"] as? String ?: "19:00"
                            onUpdateSchedule(ini, fi, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accent,
                            checkedTrackColor = accent.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkBorder
                        ),
                        modifier = Modifier.testTag("auto_arm_toggle_switch")
                    )
                }
            }
        }

        // Tarjeta Ventana de Operación Diaria (Resumen visual)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "VENTANA DE OPERACIÓN DIARIA",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Apertura Bodega", color = TextSecondary, fontSize = 11.sp)
                        Text(text = "${siteState.horario["inicio"] ?: "08:00"} AM", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }

                    Text(text = "→", color = TextMuted, fontSize = 16.sp)

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Cierre y Armado", color = TextSecondary, fontSize = 11.sp)
                        Text(text = "${siteState.horario["fin"] ?: "19:00"} PM", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Tarjeta Regla de Detección Fuera de Horario
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "REGLA DE DETECCIÓN FUERA DE HORARIO",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Cualquier corte de haz láser, apertura de cortina o detección de presencia fuera del horario de ${siteState.horario["inicio"] ?: "08:00"} a ${siteState.horario["fin"] ?: "19:00"} disparará alarma perimetral inmediata y notificará en tiempo real al panel y la nube.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
