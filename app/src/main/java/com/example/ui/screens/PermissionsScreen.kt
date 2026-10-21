package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.AccentAlarm
import com.example.ui.theme.AccentDisarmed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.LocalAlarmAccentColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

data class PermissionItem(
    val title: String,
    val permission: String,
    val description: String,
    val whyNeeded: String,
    val icon: ImageVector,
    val minApi: Int = 0
)

@Composable
fun PermissionsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAlarmAccentColor.current
    val scrollState = rememberScrollState()

    var permissionToExplain by remember { mutableStateOf<PermissionItem?>(null) }
    var showAppSettingsDialog by remember { mutableStateOf(false) }
    var deniedPermissionName by remember { mutableStateOf("") }
    var isBleScanning by remember { mutableStateOf(false) }
    var scanResultsCount by remember { mutableStateOf(0) }

    // Launcher genérico de permisos
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            deniedPermissionName = permissionToExplain?.title ?: "Permiso requerido"
            showAppSettingsDialog = true
        }
        permissionToExplain = null
    }

    val permissionList = remember {
        listOf(
            PermissionItem(
                title = "Cámara Frontal",
                permission = Manifest.permission.CAMERA,
                description = "Requerido para la detección de movimiento por diferencia de fotogramas.",
                whyNeeded = "La central del nodo analiza variaciones de luz localmente para detectar intrusos sin almacenar ni enviar fotos.",
                icon = Icons.Outlined.Videocam
            ),
            PermissionItem(
                title = "Notificaciones de Alarma",
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else "",
                description = "Permite alertar al operador de forma inmediata ante un disparo de alarma.",
                whyNeeded = "Asegura la recepción de alertas prioritarias con sonido y vibración incluso con la app cerrada.",
                icon = Icons.Outlined.Notifications,
                minApi = Build.VERSION_CODES.TIRAMISU
            ),
            PermissionItem(
                title = "Red WiFi Local y SSID",
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.NEARBY_WIFI_DEVICES
                } else {
                    Manifest.permission.ACCESS_FINE_LOCATION
                },
                description = "Identificación de la red WiFi de la bodega para enlace WebSocket directo.",
                whyNeeded = "Permite validar que el panel y el nodo están en la misma red local para comunicación cifrada punto a punto.",
                icon = Icons.Outlined.Wifi
            ),
            PermissionItem(
                title = "Bluetooth BLE (Opcional)",
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.BLUETOOTH,
                description = "Escaneo de sensores perimetrales y balizas BLE cercanas en la bodega.",
                whyNeeded = "Permite descubrir nodos sensores periféricos inalámbricos de bajo consumo energético.",
                icon = Icons.Outlined.Bluetooth
            )
        ).filter { it.minApi == 0 || Build.VERSION.SDK_INT >= it.minApi }
    }

    // Diálogo Rationale previo a solicitar permiso
    permissionToExplain?.let { item ->
        AlertDialog(
            onDismissRequest = { permissionToExplain = null },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            icon = { Icon(item.icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp)) },
            title = { Text(item.title, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.whyNeeded, fontSize = 12.sp, lineHeight = 18.sp, color = TextPrimary)
                    Text("Conforme a la norma ISO/IEC 27400, solo se utilizará para la función declarada y de forma local.", fontSize = 11.sp, color = TextMuted)
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent)
                        .clickable {
                            val perm = item.permission
                            permissionToExplain = null
                            if (perm.isNotEmpty()) {
                                permissionLauncher.launch(perm)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Continuar y Solicitar", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { permissionToExplain = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Cancelar", color = TextSecondary, fontSize = 12.sp)
                }
            }
        )
    }

    // Diálogo si el permiso fue denegado para ir a Ajustes del Sistema
    if (showAppSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showAppSettingsDialog = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Permiso Denegado", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "El permiso '$deniedPermissionName' fue denegado o requiere habilitación manual en los ajustes del dispositivo para funcionar.",
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
                            showAppSettingsDialog = false
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Abrir Ajustes", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { showAppSettingsDialog = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Cerrar", color = TextSecondary, fontSize = 12.sp)
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
            .testTag("permissions_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Column {
            Text(
                text = "PERMISOS DE SEGURIDAD",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Control granular de acceso a hardware y red conforme a ISO/IEC 27400",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        // Lista de Permisos
        permissionList.forEach { item ->
            val isGranted = item.permission.isEmpty() || ContextCompat.checkSelfPermission(
                context,
                item.permission
            ) == PackageManager.PERMISSION_GRANTED

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .border(1.dp, if (isGranted) DarkBorder else AccentAlarm, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(item.icon, contentDescription = null, tint = if (isGranted) AccentDisarmed else TextMuted, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isGranted) DarkSurfaceVariant else DarkSurfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isGranted) Icons.Outlined.Check else Icons.Outlined.Close,
                                    contentDescription = null,
                                    tint = if (isGranted) AccentDisarmed else AccentAlarm,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isGranted) "CONCEDIDO" else "PENDIENTE",
                                    color = if (isGranted) AccentDisarmed else AccentAlarm,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(text = item.description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)

                    if (!isGranted) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent)
                                .clickable { permissionToExplain = item }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Solicitar Permiso", color = DarkBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Sección de Escaneo BLE Opcional
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "DISPOSITIVOS BLE CERCANOS (SIMULADO)", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(text = "Búsqueda de balizas y periféricos perimetrales de bodega.", color = TextSecondary, fontSize = 12.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurfaceVariant)
                            .clickable {
                                isBleScanning = !isBleScanning
                                if (isBleScanning) scanResultsCount = 3 else scanResultsCount = 0
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isBleScanning) "Detener Escaneo" else "Escanear BLE",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (isBleScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = accent, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "$scanResultsCount balizas encontradas", color = accent, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
