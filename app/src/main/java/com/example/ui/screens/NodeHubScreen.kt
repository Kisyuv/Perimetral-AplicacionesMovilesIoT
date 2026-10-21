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
import android.util.Size
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DoorSliding
import androidx.compose.material.icons.outlined.FlashOn
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accent = LocalAlarmAccentColor.current
    val scrollState = rememberScrollState()

    // Mantener la pantalla encendida (FLAG_KEEP_SCREEN_ON) en el nodo central
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var showCameraPreview by remember { mutableStateOf(false) }
    var showCameraRationaleDialog by remember { mutableStateOf(false) }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }
    var useTorchStrobe by remember { mutableStateOf(true) }
    var motionProgress by remember { mutableFloatStateOf(0f) }

    // Estado del parpadeo estroboscópico de pantalla (3 Hz)
    var strobeFlashState by remember { mutableStateOf(false) }
    val isAlarmActive = siteState.estado == "alarma" || siteState.estroboActivo

    LaunchedEffect(isAlarmActive) {
        if (isAlarmActive) {
            actuatorManager.startTorchStrobe(useTorchStrobe)
            while (true) {
                strobeFlashState = !strobeFlashState
                delay(166L) // 3 Hz máx por seguridad fotosensible
            }
        } else {
            actuatorManager.stopAll()
            strobeFlashState = false
        }
    }

    // Comprobación de optimización de batería al inicio
    LaunchedEffect(Unit) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            showBatteryOptimizationDialog = true
        }
    }

    // Instancia del analizador de movimiento CameraX
    val analyzer = remember {
        MotionDetectorAnalyzer(
            onMotionDetected = onMotionDetected,
            onMotionProgress = { motionProgress = it },
            onError = { error ->
                onCameraStatusChange("error")
                onFallbackToSimulated()
            }
        )
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

    // Launcher para pedir permiso de CAMERA
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onCameraStatusChange("activa")
        } else {
            onCameraStatusChange("sin_permiso")
            onFallbackToSimulated()
        }
    }

    // Si el modo configurado en Firestore es cámara, validar permisos
    LaunchedEffect(cameraConfig.modoPir) {
        if (cameraConfig.modoPir == "camara") {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) {
                showCameraRationaleDialog = true
            } else {
                onCameraStatusChange("activa")
            }
        } else {
            onCameraStatusChange("inactiva")
        }
    }

    // Diálogo Explicación de Optimización de Batería
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
                    text = "Para que la central de alarma de la bodega y los sensores continúen vigilando y detectando intrusos ininterrumpidamente con la pantalla apagada o en reposo, es necesario autorizar la exclusión de optimización de batería para Perimetral.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
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
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Configurar Batería", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { showBatteryOptimizationDialog = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Más tarde", color = TextSecondary, fontSize = 12.sp)
                }
            }
        )
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
            icon = { Icon(Icons.Outlined.Videocam, contentDescription = null, tint = accent, modifier = Modifier.size(32.dp)) },
            title = { Text("Sensor de Movimiento por Cámara", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Perimetral utiliza la cámara frontal exclusivamente para analizar diferencias de luminosidad en tiempo real y detectar intrusiones en la bodega.\n\nPrivacidad absoluta: los fotogramas se procesan únicamente en la memoria volátil del procesador, nunca se graban, no se almacenan y nunca se transmiten a internet.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent)
                        .clickable {
                            showCameraRationaleDialog = false
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Permitir Cámara", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .clickable {
                            showCameraRationaleDialog = false
                            onFallbackToSimulated()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Usar Simulado", color = TextSecondary, fontSize = 12.sp)
                }
            }
        )
    }

    // Fondo reactivo con efecto estroboscópico de seguridad (3 Hz máximo)
    val backgroundFlashColor = if (isAlarmActive && strobeFlashState) Color.White else DarkBackground

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundFlashColor)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
            .testTag("node_hub_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Encabezado del Nodo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NODO CENTRAL BODEGA",
                    color = if (isAlarmActive && strobeFlashState) Color.Black else TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Hardware: $deviceModel · Pantalla fija activa",
                    color = if (isAlarmActive && strobeFlashState) Color.Black else TextMuted,
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = siteState.estado.uppercase(),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Indicadores en Tiempo Real de Sensores (PIR y Reed Switch)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SensorLiveStatusCard(
                name = "Sensor PIR",
                subtext = if (cameraConfig.modoPir == "camara") "Cámara frontal activa" else "Modo simulado",
                isActive = sensorState.pirMovimiento,
                activeText = "MOVIMIENTO DETECTADO",
                idleText = "Sin movimiento",
                icon = Icons.Outlined.Sensors,
                activeColor = AccentAlarm,
                modifier = Modifier.weight(1f)
            )

            SensorLiveStatusCard(
                name = "Reed Switch",
                subtext = "Contacto magnético portón",
                isActive = sensorState.reedPuertaAbierta,
                activeText = "PUERTA ABIERTA",
                idleText = "Puerta cerrada",
                icon = Icons.Outlined.DoorSliding,
                activeColor = AccentAlarm,
                modifier = Modifier.weight(1f)
            )
        }

        // Progreso de confirmación de movimiento si está activa la cámara
        if (cameraConfig.modoPir == "camara" && motionProgress > 0f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = "Confirmando movimiento continuo...", color = AccentAlarm, fontSize = 11.sp)
                LinearProgressIndicator(
                    progress = { motionProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = AccentAlarm,
                    trackColor = DarkSurfaceVariant
                )
            }
        }

        // Módulo CameraX (ImageAnalysis de fondo + Vista previa opcional)
        if (cameraConfig.modoPir == "camara" && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Videocam, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "ANÁLISIS CAMERAX ACTIVO", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Vista previa", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = showCameraPreview,
                                onCheckedChange = { showCameraPreview = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = DarkSurfaceVariant)
                            )
                        }
                    }

                    // Surface CameraX
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
                                        .setTargetResolution(Size(320, 240)) // Baja resolución para procesamiento ágil
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
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
                            .height(if (showCameraPreview) 160.dp else 1.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                }
            }
        }

        // Estado de Actuadores Locales en el Nodo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "ESTADO DE ACTUADORES FÍSICOS", color = TextMuted, fontSize = 11.sp, letterSpacing = 1.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Campaign, contentDescription = null, tint = if (siteState.sirenaActiva) AccentAlarm else TextMuted)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Sirena Acústica USAGE_ALARM", color = TextPrimary, fontSize = 12.sp)
                    }
                    Text(
                        text = if (siteState.sirenaActiva) "SONANDO" else "SILENCIOSA",
                        color = if (siteState.sirenaActiva) AccentAlarm else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FlashOn, contentDescription = null, tint = if (siteState.estroboActivo) AccentAlarm else TextMuted)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Estroboscopio (Pantalla + Linterna)", color = TextPrimary, fontSize = 12.sp)
                    }
                    Text(
                        text = if (siteState.estroboActivo) "DESTELLANDO (3 Hz)" else "APAGADO",
                        color = if (siteState.estroboActivo) AccentAlarm else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
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
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isActive) activeColor else DarkBorder
    val contentColor = if (isActive) activeColor else AccentDisarmed

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(contentColor))
            }

            Text(text = name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = if (isActive) activeText else idleText, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(text = subtext, color = TextMuted, fontSize = 10.sp)
        }
    }
}
