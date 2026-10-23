package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CameraRear
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoorSliding
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SecurityEvent
import com.example.data.model.SiteAlarmState
import com.example.data.model.SiteCameraConfig
import com.example.data.model.SiteSensorState
import com.example.domain.model.PerimeterZone
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
    onUpdateCameraConfig: (modoPir: String, sensibilidad: Long, tiempoConfirmacion: Long) -> Unit,
    onResetSensors: () -> Unit = {},
    zones: List<PerimeterZone> = emptyList(),
    onActivateZone: (zoneId: String) -> Unit = {},
    onDeactivateZone: (zoneId: String) -> Unit = {},
    onActivateAllZones: () -> Unit = {},
    onDeactivateAllZones: () -> Unit = {},
    onUpdateSchedule: ((inicio: String, fin: String, autoArm: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = LocalAlarmAccentColor.current
    val scrollState = rememberScrollState()

    var showPinDialogForAction by remember { mutableStateOf<String?>(null) } // "disarm", "silence_siren", "turn_off_strobe"
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

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
                        text = "Ingresa el PIN de seguridad de 4 dígitos (PIN predeterminado: 2580):",
                        fontSize = 13.sp,
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
                        Text(text = it, color = AccentAlarm, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
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
                                pinError = "Ingresa exactamente los 4 dígitos del PIN."
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(text = "Confirmar", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .clickable {
                            showPinDialogForAction = null
                            pinInput = ""
                            pinError = null
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(text = "Cancelar", color = TextSecondary, fontSize = 12.sp)
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Tarjeta Grande de Estado Actual y Botón Armar / Desarmar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Indicador circular con halo de luz
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f))
                        .border(2.dp, accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (siteState.estado) {
                            "armada" -> Icons.Outlined.Shield
                            "alarma" -> Icons.Outlined.WarningAmber
                            else -> Icons.Outlined.LockOpen
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = siteState.estado.uppercase(),
                    color = accent,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when (siteState.estado) {
                        "desarmada" -> "Perímetro libre. Sensores en monitoreo informativo de rutina."
                        "armada" -> "Vigilancia perimetral activa. Alarma armada para detección de intrusiones."
                        "alarma" -> "¡INTRUSIÓN CONFIRMADA EN BODEGA! Sirena y estroboscopio activos."
                        else -> "Estado sincronizado con la nube."
                    },
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Botón Principal de Armar / Desarmar
                if (siteState.estado == "desarmada") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentArmed)
                            .clickable(onClick = onArm)
                            .padding(vertical = 14.dp)
                            .testTag("arm_system_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "ARMAR SISTEMA", color = DarkBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.5.dp, accent, RoundedCornerShape(10.dp))
                            .clickable { showPinDialogForAction = "disarm" }
                            .padding(vertical = 14.dp)
                            .testTag("disarm_system_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LockOpen, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "DESARMAR CON PIN", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Control Manual de Actuadores (Silenciar sirena / Apagar estroboscopio)
        if (siteState.sirenaActiva || siteState.estroboActivo || siteState.estado == "alarma") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurface)
                    .border(1.dp, AccentAlarm, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = AccentAlarm, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ACTUADORES DISPARADOS",
                            color = AccentAlarm,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "La sirena y/o el estroboscopio están operando. Puedes silenciarlos individualmente con tu PIN.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (siteState.sirenaActiva) AccentAlarm.copy(alpha = 0.15f) else DarkSurfaceVariant)
                                .border(1.dp, if (siteState.sirenaActiva) AccentAlarm else DarkBorder, RoundedCornerShape(8.dp))
                                .clickable { showPinDialogForAction = "silence_siren" }
                                .padding(vertical = 12.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Campaign, contentDescription = null, tint = if (siteState.sirenaActiva) AccentAlarm else TextMuted, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Silenciar Sirena", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (siteState.estroboActivo) AccentAlarm.copy(alpha = 0.15f) else DarkSurfaceVariant)
                                .border(1.dp, if (siteState.estroboActivo) AccentAlarm else DarkBorder, RoundedCornerShape(8.dp))
                                .clickable { showPinDialogForAction = "turn_off_strobe" }
                                .padding(vertical = 12.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.FlashlightOff, contentDescription = null, tint = if (siteState.estroboActivo) AccentAlarm else TextMuted, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Apagar Estrobo", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        // 3. Telemetría y Activación de Sensores en Bodega por Ubicación
        var showZoneMenu by remember { mutableStateOf(false) }
        val activeZonesCount = zones.count { it.isActive }
        val allZonesActive = zones.isNotEmpty() && zones.all { it.isActive }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SENSORES EN BODEGA",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Lectura en tiempo real y activación por zona",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    // Botón para restablecer sensores
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .clickable { onResetSensors() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Restablecer", color = TextSecondary, fontSize = 11.sp)
                    }
                }

                // Menú emergente de selección de zona / toda la bodega
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, if (activeZonesCount > 0) accent else DarkBorder, RoundedCornerShape(8.dp))
                        .clickable { showZoneMenu = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("zone_activation_dropdown_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = if (activeZonesCount > 0) accent else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ZONA A ACTIVAR",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = when {
                                        allZonesActive -> "Toda la bodega activa (${zones.size} zonas)"
                                        activeZonesCount > 0 -> "$activeZonesCount de ${zones.size} zonas activas"
                                        else -> "Presiona aquí para elegir zona o activar toda la bodega"
                                    },
                                    color = if (activeZonesCount > 0) TextPrimary else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (activeZonesCount > 0) accent.copy(alpha = 0.15f) else DarkSurface)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (activeZonesCount > 0) "ACTIVA" else "DESACTIVADA",
                                    color = if (activeZonesCount > 0) accent else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Outlined.ArrowDropDown,
                                contentDescription = "Menú de zonas",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showZoneMenu,
                        onDismissRequest = { showZoneMenu = false },
                        modifier = Modifier
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    ) {
                        // Opción requerida: Activar en toda la bodega
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = AccentArmed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Activar en toda la bodega",
                                            color = AccentArmed,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Habilita sensores y alarmas en todas las zonas",
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onActivateAllZones()
                                showZoneMenu = false
                            }
                        )

                        if (activeZonesCount > 0) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.PowerSettingsNew,
                                            contentDescription = null,
                                            tint = AccentAlarm,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Desactivar toda la bodega",
                                            color = AccentAlarm,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                },
                                onClick = {
                                    onDeactivateAllZones()
                                    showZoneMenu = false
                                }
                            )
                        }

                        HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                        // Zonas de la pantalla Zonas
                        zones.forEach { zone ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = zone.id,
                                                    color = accent,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = zone.name,
                                                    color = TextPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Text(
                                                text = "${zone.sector} (${zone.sensorType})",
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (zone.isActive) AccentArmed.copy(alpha = 0.15f) else DarkSurfaceVariant)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (zone.isActive) "ACTIVA" else "DESACTIVADA",
                                                color = if (zone.isActive) AccentArmed else TextMuted,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (zone.isActive) {
                                        onDeactivateZone(zone.id)
                                    } else {
                                        onActivateZone(zone.id)
                                    }
                                    showZoneMenu = false
                                }
                            )
                        }
                    }
                }

                // Indicadores en Vivo en el Panel
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val pirActive = sensorState.pirMovimiento
                    val pirColor = if (pirActive) AccentAlarm else AccentDisarmed

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, if (pirActive) AccentAlarm else DarkBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Sensors, contentDescription = null, tint = pirColor, modifier = Modifier.size(18.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(pirColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (pirActive) "ACTIVO" else "OK",
                                        color = pirColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(text = "Sensor PIR", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (pirActive) "¡Movimiento detectado!" else "Sin movimiento",
                                color = pirColor,
                                fontSize = 11.sp
                            )
                        }
                    }

                    val reedActive = sensorState.reedPuertaAbierta
                    val reedColor = if (reedActive) AccentAlarm else AccentDisarmed

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, if (reedActive) AccentAlarm else DarkBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.DoorSliding, contentDescription = null, tint = reedColor, modifier = Modifier.size(18.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(reedColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (reedActive) "ABIERTA" else "OK",
                                        color = reedColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(text = "Reed Switch", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (reedActive) "¡Puerta abierta!" else "Puerta cerrada",
                                color = reedColor,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Text(
                    text = "Pruebas de Simulación (envía evento a la bodega):",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                // Botones para simular
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            .clickable { onSimulateSensor("pir", "Movimiento simulado por operador") }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Sensors, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Simular Movimiento", color = TextPrimary, fontSize = 12.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            .clickable { onSimulateSensor("reed", "Apertura forzada simulada por operador") }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DoorSliding, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Simular Puerta", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 4. Configuración del Sensor de Movimiento (Cámara trasera vs Simulado + Slider)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "MODO DE SENSOR DE MOVIMIENTO",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                // Selector Cámara Trasera vs Simulado
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (cameraConfig.modoPir == "simulado") DarkSurface else DarkSurfaceVariant)
                            .clickable {
                                onUpdateCameraConfig("simulado", cameraConfig.sensibilidad, cameraConfig.tiempoConfirmacion)
                            }
                            .padding(vertical = 10.dp),
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
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (cameraConfig.modoPir == "camara") DarkSurface else DarkSurfaceVariant)
                            .clickable {
                                onUpdateCameraConfig("camara", cameraConfig.sensibilidad, cameraConfig.tiempoConfirmacion)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.CameraRear,
                                contentDescription = null,
                                tint = if (cameraConfig.modoPir == "camara") accent else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cámara trasera",
                                color = if (cameraConfig.modoPir == "camara") TextPrimary else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Slider Sensibilidad (1 a 10)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

        // 5. Historial de Eventos en Tiempo Real
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "EVENTOS EN TIEMPO REAL", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                }
                Text(text = "Últimos registros", color = TextMuted, fontSize = 11.sp)
            }

            if (events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Sin eventos recientes en el historial", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                events.take(6).forEach { ev ->
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ev.timestamp))
                    val isAlarmEvent = ev.tipo == "alarma"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface)
                            .border(1.dp, if (isAlarmEvent) AccentAlarm.copy(alpha = 0.6f) else DarkBorder, RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ev.detalle,
                                color = if (isAlarmEvent) AccentAlarm else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isAlarmEvent) FontWeight.Bold else FontWeight.Normal
                            )
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
