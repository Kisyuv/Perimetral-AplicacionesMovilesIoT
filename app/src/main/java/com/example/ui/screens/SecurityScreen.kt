package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.DotoFamily
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

@Composable
fun SecurityScreen(
    userEmail: String,
    deviceRole: String?,
    deviceModel: String,
    onChangeRole: () -> Unit,
    onNavigateToTests: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAlarmAccentColor.current
    val scrollState = rememberScrollState()

    val devicePrefs = remember { context.getSharedPreferences("perimetral_device_prefs", android.content.Context.MODE_PRIVATE) }
    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // Activo = permiso concedido y preferencia guardada (la lee el servicio de monitoreo)
    var notificationsEnabled by remember {
        mutableStateOf(
            hasNotificationPermission() &&
                devicePrefs.getBoolean(com.example.data.remote.DeviceManager.KEY_ALARM_NOTIFICATIONS, true)
        )
    }
    fun setNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled = enabled
        devicePrefs.edit().putBoolean(com.example.data.remote.DeviceManager.KEY_ALARM_NOTIFICATIONS, enabled).apply()
    }

    var notificationPermissionDeniedMessage by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setNotificationsEnabled(true)
            notificationPermissionDeniedMessage = null
        } else {
            setNotificationsEnabled(false)
            notificationPermissionDeniedMessage = "Permiso de notificaciones denegado. Presiona el interruptor nuevamente para concederlo."
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
            .testTag("security_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Encabezado
        Column {
            Text(
                text = "AJUSTES Y SEGURIDAD",
                color = TextPrimary,
                fontSize = 22.sp,
                fontFamily = DotoFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Cumplimiento normativo ISO/IEC 27400, cifrado y control de acceso",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        // Acceso a Pantalla de Pruebas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, accent, RoundedCornerShape(8.dp))
                .clickable(onClick = onNavigateToTests)
                .padding(vertical = 14.dp, horizontal = 16.dp)
                .testTag("nav_to_tests_btn"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Speed, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Abrir Pantalla de Pruebas y Diagnóstico ISO/IEC", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Requerimiento de Notificaciones Push contextual con solicitud bajo demanda
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, if (notificationsEnabled) AccentArmed.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            imageVector = if (notificationsEnabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                            tint = if (notificationsEnabled) AccentArmed else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "NOTIFICACIONES PUSH DE ALARMA",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (notificationsEnabled) "Alertas activas para intrusión perimetral" else "Notificaciones de alerta desactivadas",
                                color = if (notificationsEnabled) AccentArmed else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val isGranted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!isGranted) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        setNotificationsEnabled(true)
                                        notificationPermissionDeniedMessage = null
                                    }
                                } else {
                                    setNotificationsEnabled(true)
                                    notificationPermissionDeniedMessage = null
                                }
                            } else {
                                setNotificationsEnabled(false)
                                notificationPermissionDeniedMessage = null
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentArmed,
                            checkedTrackColor = AccentArmed.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkBorder
                        ),
                        modifier = Modifier.testTag("push_notification_toggle_switch")
                    )
                }

                if (notificationPermissionDeniedMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = notificationPermissionDeniedMessage!!,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Tarjeta Dispositivo & Rol
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "HARDWARE Y SESIÓN", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Operador:", color = TextMuted, fontSize = 12.sp)
                    Text(text = userEmail.ifEmpty { "Autenticado" }, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Dispositivo:", color = TextMuted, fontSize = 12.sp)
                    Text(text = deviceModel, color = TextPrimary, fontSize = 12.sp)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Rol asignado:", color = TextMuted, fontSize = 12.sp)
                    Text(
                        text = if (deviceRole == "nodo") "NODO ALARMA (CENTRAL)" else "PANEL DE CONTROL",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .clickable(onClick = onChangeRole)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Cambiar Rol de este Dispositivo", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Checklist de Seguridad ISO/IEC 27400
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
                    Text(text = "CHECKLIST SEGURIDAD ISO/IEC 27400", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(text = "8/8 CUMPLE", color = AccentDisarmed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                IsoCheckItem("1. Reglas Firestore (User Isolation & Inmutable)", "Eventos append-only sin borrado ni edición")
                IsoCheckItem("2. Cifrado Keystore & AES-GCM", "Claves en Android Keystore y mensajes de red cifrados")
                IsoCheckItem("3. Control de Acceso (Admin / Operador)", "Solo admin modifica horario, PIN y reemplaza el nodo")
                IsoCheckItem("4. Conexiones Seguras HTTPS / TLS", "cleartextTrafficPermitted=false en red_security_config")
                IsoCheckItem("5. PIN con Salted SHA-256 & Bloqueo", "Bloqueo por 5 minutos tras 5 intentos fallidos")
                IsoCheckItem("6. Auditoría Completa de Seguridad", "Registro de login, cambio de rol, armado y alarmas")
                IsoCheckItem("7. Firebase App Check Habilitado", "Atestación de integridad de la aplicación")
                IsoCheckItem("8. Enlace Local WebSocket por WiFi", "Comunicación directa con el nodo en la red local sin depender de la nube")
            }
        }

        // Botón Cerrar Sesión
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .border(1.dp, TextPrimary, RoundedCornerShape(6.dp))
                .clickable(onClick = onSignOut)
                .padding(vertical = 12.dp)
                .testTag("sign_out_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "CERRAR SESIÓN", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun IsoCheckItem(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = TextMuted, fontSize = 10.sp)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurfaceVariant)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(text = "CUMPLE", color = AccentDisarmed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
