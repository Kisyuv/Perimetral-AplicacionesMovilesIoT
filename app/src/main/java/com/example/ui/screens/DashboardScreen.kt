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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DoorSliding
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SecurityEvent
import com.example.data.model.SiteAlarmState
import com.example.data.model.SiteCameraConfig
import com.example.data.model.SiteSensorState
import com.example.ui.theme.AccentAlarm
import com.example.ui.theme.AccentArmed
import com.example.ui.theme.AccentDisarmed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.LocalAlarmAccentColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    siteState: SiteAlarmState,
    sensorState: SiteSensorState,
    cameraConfig: SiteCameraConfig,
    events: List<SecurityEvent>,
    onArm: () -> Unit,
    onDisarm: (pin: String) -> Unit,
    onSilenceActuators: (pin: String, silenceSiren: Boolean, turnOffStrobe: Boolean) -> Unit,
    onSimulateSensor: (type: String, detail: String) -> Unit,
    onUpdateSchedule: (inicio: String, fin: String, autoArm: Boolean) -> Unit,
    onUpdateCameraConfig: (modoPir: String, sensibilidad: Long, tiempoConfirmacion: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAlarmAccentColor.current
    val scrollState = rememberScrollState()

    var showPinDialogForAction by remember { mutableStateOf<String?>(null) } // "disarm", "silence_siren", "turn_off_strobe"
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    var showScheduleEditor by remember { mutableStateOf(false) }
    var editInicio by remember { mutableStateOf(siteState.horario["inicio"] as? String ?: "08:00") }
    var editFin by remember { mutableStateOf(siteState.horario["fin"] as? String ?: "19:00") }

    // Diálogo de verificación de PIN de 4 dígitos
    if (showPinDialogForAction != null) {
        AlertDialog(
            onDismissRequest = {
                showPinDialogForAction = null
                pinInput = ""
                pinError = null
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = when (showPinDialogForAction) {
                        "disarm" -> "Desarmar Perímetro"
                        "silence_siren" -> "Silenciar Sirena"
                        "turn_off_strobe" -> "Apagar Estroboscopio"
                        else -> "Autorización de Seguridad"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ingresa el PIN de seguridad de 4 dígitos (predeterminado: 2580):",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                pinInput = it
                                pinError = null
                            }
                        },
                        label = { Text("PIN de 4 dígitos", fontSize = 12.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_entry_input")
                    )

                    pinError?.let {
                        Text(text = it, color = AccentAlarm, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent)
                        .clickable {
                            if (pinInput.length == 4) {
                                when (showPinDialogForAction) {
                                    "disarm" -> onDisarm(pinInput)
                                    "silence_siren" -> onSilenceActuators(pinInput, true, false)
                                    "turn_off_strobe" -> onSilenceActuators(pinInput, false, true)
                                }
                                showPinDialogForAction = null
                                pinInput = ""
                                pinError = null
                            } else {
                                pinError = "Ingresa los 4 dígitos del PIN."
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = "Confirmar", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .clickable {
                            showPinDialogForAction = null
                            pinInput = ""
                            pinError = null
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(text = "Cancelar", color = TextSecondary, fontSize = 12.sp)
                }
            }
        )
    }

    // Diálogo Editor de Horarios
    if (showScheduleEditor) {
        AlertDialog(
            onDismissRequest = { showScheduleEditor = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Editar Horario Operativo", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Configura el horario laboral de la bodega (formato 24h HH:mm):", fontSize = 12.sp, color = TextSecondary)

                    OutlinedTextField(
                        value = editInicio,
                        onValueChange = { editInicio = it },
                        label = { Text("Hora Inicio (Apertura)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )

                    OutlinedTextField(
                        value = editFin,
                        onValueChange = { editFin = it },
                        label = { Text("Hora Fin (Cierre y Armado)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent)
                        .clickable {
                            onUpdateSchedule(editInicio, editFin, siteState.armadoAutomatico)
                            showScheduleEditor = false
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Guardar", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { showScheduleEditor = false }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
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
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // 1. Tarjeta Grande de Estado Actual y Botón Armar / Desarmar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = siteState.estado.uppercase(),
                    color = accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = when (siteState.estado) {
                        "desarmada" -> "Perímetro libre. Sensores en monitoreo informativo."
                        "armada" -> "Vigilancia perimetral activa. Detección fuera de horario habilitada."
                        "alarma" -> "¡INTRUSIÓN EN CURSO! Actuadores y sirena activados."
                        else -> "Estado sincronizado con Firestore."
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Botón Principal de Conmutación de Estado (Armar / Desarmar)
                if (siteState.estado == "desarmada") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentArmed)
                            .clickable(onClick = onArm)
                            .padding(vertical = 12.dp)
                            .testTag("arm_system_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "ARMAR SISTEMA", color = DarkBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, accent, RoundedCornerShape(6.dp))
                            .clickable { showPinDialogForAction = "disarm" }
                            .padding(vertical = 12.dp)
                            .testTag("disarm_system_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LockOpen, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "DESARMAR CON PIN", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Control Manual de Actuadores (Silenciar sirena / Apagar estroboscopio)
        if (siteState.sirenaActiva || siteState.estroboActivo || siteState.estado == "alarma") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "CONTROL MANUAL DE ACTUADORES", color = AccentAlarm, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurface)
                            .border(1.dp, if (siteState.sirenaActiva) AccentAlarm else DarkBorder, RoundedCornerShape(6.dp))
                            .clickable { showPinDialogForAction = "silence_siren" }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Campaign, contentDescription = null, tint = AccentAlarm, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Silenciar Sirena", color = TextPrimary, fontSize = 12.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurface)
                            .border(1.dp, if (siteState.estroboActivo) AccentAlarm else DarkBorder, RoundedCornerShape(6.dp))
                            .clickable { showPinDialogForAction = "turn_off_strobe" }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FlashlightOff, contentDescription = null, tint = AccentAlarm, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Apagar Estrobo", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 3. Simulación de Sensores Perimetrales
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "SIMULACIÓN DE SENSORES EN BODEGA", color = TextMuted, fontSize = 11.sp, letterSpacing = 1.sp)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Sensor PIR
                if (cameraConfig.modoPir == "simulado") {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                            .clickable { onSimulateSensor("pir", "Movimiento detectado por PIR pasivo") }
                            .padding(vertical = 14.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Sensors, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Movimiento (PIR)", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .padding(vertical = 14.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Videocam, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sensor por cámara activo (${cameraConfig.estadoCamara})",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Sensor Reed Switch (Puerta / Portón)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                        .clickable { onSimulateSensor("reed", "Apertura forzada de puerta / cortina") }
                        .padding(vertical = 14.dp, horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DoorSliding, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Puerta (Reed)", color = TextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }

        // 4. Configuración del Sensor de Movimiento (Cámara frontal vs Simulado + Slider)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "MODO DE SENSOR DE MOVIMIENTO", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)

                // Selector Cámara vs Simulado
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceVariant)
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (cameraConfig.modoPir == "simulado") DarkSurface else DarkSurfaceVariant)
                            .clickable {
                                onUpdateCameraConfig("simulado", cameraConfig.sensibilidad, cameraConfig.tiempoConfirmacion)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Simulado",
                            color = if (cameraConfig.modoPir == "simulado") TextPrimary else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (cameraConfig.modoPir == "camara") DarkSurface else DarkSurfaceVariant)
                            .clickable {
                                onUpdateCameraConfig("camara", cameraConfig.sensibilidad, cameraConfig.tiempoConfirmacion)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cámara frontal",
                            color = if (cameraConfig.modoPir == "camara") TextPrimary else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Slider Sensibilidad (1 a 10)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Sensibilidad de detección", color = TextSecondary, fontSize = 12.sp)
                        Text(text = "${cameraConfig.sensibilidad} / 10", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Slider(
                        value = cameraConfig.sensibilidad.toFloat(),
                        onValueChange = {
                            onUpdateCameraConfig(cameraConfig.modoPir, it.toLong(), cameraConfig.tiempoConfirmacion)
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = DarkBorder
                        )
                    )
                }
            }
        }

        // 5. Editor de Horario y Armado Automático
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "HORARIO OPERATIVO BODEGA", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        Text(
                            text = "${siteState.horario["inicio"] ?: "08:00"} a ${siteState.horario["fin"] ?: "19:00"}",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurfaceVariant)
                            .clickable { showScheduleEditor = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = "Editar", color = TextPrimary, fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Armado automático fuera de horario", color = TextPrimary, fontSize = 12.sp)
                        Text(text = "Arma el sistema automáticamente fuera de la jornada", color = TextMuted, fontSize = 11.sp)
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
                            checkedTrackColor = DarkSurfaceVariant,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkBorder
                        )
                    )
                }
            }
        }

        // 6. Historial de Eventos en Tiempo Real
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "EVENTOS EN TIEMPO REAL", color = TextMuted, fontSize = 11.sp, letterSpacing = 1.sp)

            if (events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Sin eventos recientes", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                events.take(6).forEach { ev ->
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ev.timestamp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurface)
                            .border(1.dp, if (ev.tipo == "alarma") AccentAlarm else DarkBorder, RoundedCornerShape(6.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = ev.detalle, color = TextPrimary, fontSize = 12.sp)
                            Text(text = "Origen: ${ev.origen.uppercase()} · Tipo: ${ev.tipo.uppercase()}", color = TextMuted, fontSize = 10.sp)
                        }
                        Text(text = timeStr, color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
