package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Size
import kotlin.math.sqrt
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CameraFront
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoorSliding
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.actuators.AlarmActuatorManager
import com.example.camera.MotionDetectorAnalyzer
import com.example.data.model.SiteAlarmState
import com.example.data.model.SiteCameraConfig
import com.example.data.model.SiteSensorState
import com.example.ui.components.rememberPermissionRequest
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
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@Composable
fun NodeHubScreen(
    siteState: SiteAlarmState,
    sensorState: SiteSensorState,
    cameraConfig: SiteCameraConfig,
    actuatorManager: AlarmActuatorManager,
    deviceModel: String,
    onMotionDetected: () -> Unit,
    onCameraStatusChange: (status: String) -> Unit,
    onFallbackToSimulated: () -> Unit,
    onResetSensors: () -> Unit = {},
    onSelectLens: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val useBackCamera = cameraConfig.lente == "trasera"
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accent = LocalAlarmAccentColor.current
    val scrollState = rememberScrollState()

    // Mantener la pantalla encendida en el dispositivo que actúa como nodo central
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            actuatorManager.setCameraControl(null)
            actuatorManager.stopTorchStrobe()
        }
    }

    var showCameraPreview by remember { mutableStateOf(false) }
    var showCameraRationaleDialog by remember { mutableStateOf(false) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
    var useTorchStrobe by remember { mutableStateOf(true) }
    var motionProgress by remember { mutableFloatStateOf(0f) }

    // Control preciso de estroboscopio: sólo se activa si estroboActivo está encendido
    var strobeFlashState by remember { mutableStateOf(false) }
    val isStrobeActive = siteState.estroboActivo

    LaunchedEffect(isStrobeActive, useTorchStrobe) {
        if (isStrobeActive) {
            actuatorManager.startTorchStrobe(useTorchStrobe)
            while (true) {
                strobeFlashState = !strobeFlashState
                delay(166L) // Máx 3 Hz por fotosensibilidad
            }
        } else {
            actuatorManager.stopTorchStrobe()
            strobeFlashState = false
        }
    }

    // Comprobación de optimización de batería
    LaunchedEffect(Unit) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            showBatteryOptimizationDialog = true
        }
    }

    // Instancia del analizador de movimiento CameraX (utilizando cámara frontal)
    val analyzer = remember {
        MotionDetectorAnalyzer(
            onMotionDetected = onMotionDetected,
            onMotionProgress = { motionProgress = it },
            onError = { _ ->
                onCameraStatusChange("error")
                onFallbackToSimulated()
            }
        )
    }

    // Listener de acelerómetro: suprime la detección de movimiento si el propio teléfono está siendo manipulado o movido
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            var lastMovementTime = 0L

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val gMagnitude = sqrt(x * x + y * y + z * z)
                val deltaG = kotlin.math.abs(gMagnitude - SensorManager.GRAVITY_EARTH)

                val now = System.currentTimeMillis()
                if (deltaG > 1.25f) {
                    lastMovementTime = now
                    analyzer.isDeviceMoving = true
                } else if (now - lastMovementTime > 1200L) {
                    analyzer.isDeviceMoving = false
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    // Sincronizar parámetros del analizador con Firestore
    LaunchedEffect(cameraConfig.sensibilidad, cameraConfig.tiempoConfirmacion, siteState.estado) {
        analyzer.sensitivity = cameraConfig.sensibilidad
        analyzer.confirmationSeconds = cameraConfig.tiempoConfirmacion
        analyzer.setAlarmState(siteState.estado == "alarma")
        if (siteState.estado == "armada") {
            analyzer.startCalibration(3)
        }
    }

    // Permiso de cámara: lo solicita el nodo solo cuando se va a usar la cámara frontal
    val cameraPermission = rememberPermissionRequest(
        permissions = arrayOf(Manifest.permission.CAMERA),
        deniedMessage = "Se negó el permiso de cámara: el detector por cámara frontal no se activó."
    )
    LaunchedEffect(cameraPermission.message) {
        if (cameraPermission.message != null) onCameraStatusChange("sin_permiso")
    }

    // Si el modo configurado en Firestore es cámara, validar permisos
    LaunchedEffect(cameraConfig.modoPir) {
        if (cameraConfig.modoPir == "camara") {
            if (!cameraPermission.isGranted) {
                showCameraRationaleDialog = true
            } else {
                onCameraStatusChange("activa")
            }
        } else {
            onCameraStatusChange("inactiva")
        }
    }

    // Diálogo Rationale de Privacidad para Cámara Frontal
    if (showCameraRationaleDialog) {
        AlertDialog(
            onDismissRequest = {
                showCameraRationaleDialog = false
                onFallbackToSimulated()
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            icon = { Icon(Icons.Outlined.CameraFront, contentDescription = null, tint = accent, modifier = Modifier.size(32.dp)) },
            title = { Text("Sensor de Movimiento por Cámara Frontal", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Perimetral utiliza la cámara frontal apuntando al pasillo de la bodega para analizar variaciones de luminosidad en tiempo real y detectar intrusos.\n\nPrivacidad absoluta: los fotogramas se procesan únicamente en la memoria volátil del procesador, nunca se graban, no se almacenan y nunca se transmiten a internet.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent)
                        .clickable {
                            showCameraRationaleDialog = false
                            cameraPermission.request { onCameraStatusChange("activa") }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("Activar Cámara Frontal", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .clickable {
                            showCameraRationaleDialog = false
                            onFallbackToSimulated()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text("Usar Simulado", color = TextSecondary, fontSize = 12.sp)
                }
            }
        )
    }

    // Diálogo de Optimización de Batería
    if (showBatteryOptimizationDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryOptimizationDialog = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            icon = { Icon(Icons.Outlined.BatteryAlert, contentDescription = null, tint = AccentArmed, modifier = Modifier.size(32.dp)) },
            title = { Text("Exclusión de Ahorro de Batería", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Para que la central de alarma de la bodega continúe vigilando ininterrumpidamente con la pantalla apagada o en reposo, es necesario autorizar la exclusión de optimización de batería.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent)
                        .clickable {
                            showBatteryOptimizationDialog = false
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("Autorizar", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { showBatteryOptimizationDialog = false }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text("Más tarde", color = TextSecondary, fontSize = 12.sp)
                }
            }
        )
    }

    // Fondo reactivo con efecto estroboscópico suave durante alerta
    val backgroundFlashColor = if (isStrobeActive && strobeFlashState) TextPrimary else DarkBackground

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundFlashColor)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
            .testTag("node_hub_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Tarjeta de Encabezado e Identidad del Nodo (Amigable y Clara)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.dp, if (isStrobeActive) AccentAlarm else DarkBorder, RoundedCornerShape(14.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CENTRAL DE BODEGA",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.15f))
                            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = siteState.estado.uppercase(),
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Dispositivo: $deviceModel · Pantalla en modo continuo · Sincronizado en tiempo real",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                if (siteState.estado == "alarma") {
                    Text(
                        text = "¡Alarma activada en ${siteState.zonaAlarma.ifBlank { "zona no identificada" }}!",
                        color = AccentAlarm,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Indicadores Amigables de Sensores en Vivo (PIR y Reed)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ESTADO DE SENSORES EN TIEMPO REAL",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )

                // Botón amigable para restablecer sensores
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SensorLiveStatusCard(
                    name = "Sensor PIR (Movimiento)",
                    subtext = if (cameraConfig.modoPir == "camara") "Cámara ${cameraConfig.lente} · ZN-05" else "Modo simulado · ZN-05",
                    isActive = sensorState.pirMovimiento,
                    activeText = "¡MOVIMIENTO DETECTADO!",
                    idleText = "Normal · Sin movimiento",
                    icon = Icons.Outlined.Sensors,
                    modifier = Modifier.weight(1f)
                )

                SensorLiveStatusCard(
                    name = "Reed Switch (Portón)",
                    subtext = "Contacto magnético cortina · ZN-01",
                    isActive = sensorState.reedPuertaAbierta,
                    activeText = "¡PUERTA ABIERTA!",
                    idleText = "Normal · Puerta cerrada",
                    icon = Icons.Outlined.DoorSliding,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Progreso de confirmación de movimiento si está activa la cámara
        if (cameraConfig.modoPir == "camara" && motionProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, AccentAlarm.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Verificando persistencia de movimiento...", color = AccentAlarm, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "${(motionProgress * 100).toInt()}%", color = AccentAlarm, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { motionProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = AccentAlarm,
                        trackColor = DarkSurfaceVariant
                    )
                }
            }
        }

        // Permiso de cámara negado: aviso y reintento
        if (cameraConfig.modoPir == "camara" && !cameraPermission.isGranted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurface)
                    .border(1.dp, AccentAlarm.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = cameraPermission.message
                            ?: "El detector por cámara frontal necesita permiso de cámara en este nodo.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent)
                            .clickable { cameraPermission.request { onCameraStatusChange("activa") } }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Activar cámara frontal", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Módulo CameraX con Cámara Frontal
        if (cameraConfig.modoPir == "camara" && cameraPermission.isGranted) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CameraFront, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = "CÁMARA ${cameraConfig.lente.uppercase()} (DETECTOR PIR · ZN-05)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(text = "640x480 · Detección de intrusos", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Ver", color = TextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = showCameraPreview,
                                onCheckedChange = { showCameraPreview = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = accent,
                                    checkedTrackColor = accent.copy(alpha = 0.3f),
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkSurfaceVariant
                                )
                            )
                        }
                    }

                    // Selector de lente: frontal o trasera principal
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("frontal" to "Frontal", "trasera" to "Trasera principal").forEach { (value, label) ->
                            val selected = cameraConfig.lente == value
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) accent.copy(alpha = 0.18f) else DarkSurfaceVariant)
                                    .border(1.dp, if (selected) accent else DarkBorder, RoundedCornerShape(8.dp))
                                    .clickable(enabled = !selected) { onSelectLens(value) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (selected) accent else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Surface CameraX: se recrea al cambiar de lente
                    key(cameraConfig.lente) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            val cameraExecutor = Executors.newSingleThreadExecutor()

                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setTargetResolution(Size(640, 480))
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                                    // Frontal: deja libre la trasera para el estroboscopio con su LED.
                                    // Trasera: DEFAULT_BACK_CAMERA es la cámara principal.
                                    val cameraSelector = if (useBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                                    cameraProvider.unbindAll()
                                    val camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
                                    // Con la trasera abierta, la linterna solo puede encenderse a través de CameraX
                                    actuatorManager.setCameraControl(if (useBackCamera) camera.cameraControl else null)
                                    analyzer.startCalibration(2)
                                    onCameraStatusChange("activa")
                                } catch (e: Exception) {
                                    onCameraStatusChange("error")
                                    onFallbackToSimulated()
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (showCameraPreview) 180.dp else 1.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    }
                }
            }
        }

        // 4. Actuadores Físicos y Control de Estroboscopio / Linterna
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(text = "ESTADO DE ACTUADORES FÍSICOS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)

                // Sirena
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Campaign,
                            contentDescription = null,
                            tint = if (siteState.sirenaActiva) AccentAlarm else AccentDisarmed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Sirena de Alarma Acústica", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Canal de audio USAGE_ALARM + vibración", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                    Text(
                        text = if (siteState.sirenaActiva) "SONANDO" else "SILENCIADA",
                        color = if (siteState.sirenaActiva) AccentAlarm else AccentDisarmed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Estroboscopio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.FlashOn,
                            contentDescription = null,
                            tint = if (siteState.estroboActivo) AccentAlarm else AccentDisarmed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Estroboscopio (Pantalla + Linterna)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Frecuencia segura a 3 Hz", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                    Text(
                        text = if (siteState.estroboActivo) "DESTELLANDO" else "APAGADO",
                        color = if (siteState.estroboActivo) AccentAlarm else AccentDisarmed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Ajuste amigable: Permitir linterna física como estrobo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Destello con linterna física", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Usa el flash LED del dispositivo", color = TextMuted, fontSize = 11.sp)
                    }

                    Switch(
                        checked = useTorchStrobe,
                        onCheckedChange = {
                            useTorchStrobe = it
                            if (!it) {
                                actuatorManager.stopTorchStrobe()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accent,
                            checkedTrackColor = accent.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkBackground
                        )
                    )
                }

                // Botón directo para apagar actuadores locales si están sonando
                if (siteState.sirenaActiva || siteState.estroboActivo) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, AccentAlarm, RoundedCornerShape(8.dp))
                            .clickable {
                                actuatorManager.stopAll()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FlashlightOff, contentDescription = null, tint = AccentAlarm, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Apagar Actuadores Locales en este Nodo", color = AccentAlarm, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SensorLiveStatusCard(
    name: String,
    subtext: String,
    isActive: Boolean,
    activeText: String,
    idleText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val badgeColor = if (isActive) AccentAlarm else AccentDisarmed
    val cardBg = if (isActive) DarkSurfaceVariant else DarkSurface
    val cardBorder = if (isActive) AccentAlarm.copy(alpha = 0.8f) else DarkBorder

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(20.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isActive) "ALERTA" else "OK",
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = name,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = if (isActive) activeText else idleText,
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = subtext,
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}
