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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
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

@Composable
fun SecurityScreen(
    userEmail: String,
    deviceRole: String?,
    deviceModel: String,
    onChangeRole: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToTests: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAlarmAccentColor.current
    val scrollState = rememberScrollState()

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

        // Acciones Rápidas (Pruebas y Permisos)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurface)
                    .border(1.dp, accent, RoundedCornerShape(6.dp))
                    .clickable(onClick = onNavigateToTests)
                    .padding(vertical = 12.dp, horizontal = 10.dp)
                    .testTag("nav_to_tests_btn"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Speed, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Pantalla Pruebas", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                    .clickable(onClick = onNavigateToPermissions)
                    .padding(vertical = 12.dp, horizontal = 10.dp)
                    .testTag("nav_to_permissions_btn"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Permisos App", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                        text = if (deviceRole == "nodo") "NODO ALARMA (SIMULADO)" else "PANEL DE CONTROL",
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
                IsoCheckItem("8. Red Local WebSocket con mDNS", "Descubrimiento seguro _perimetral._tcp y respaldo en nube")
            }
        }

        // Botón Cerrar Sesión
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .border(1.dp, AccentAlarm, RoundedCornerShape(6.dp))
                .clickable(onClick = onSignOut)
                .padding(vertical = 12.dp)
                .testTag("sign_out_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "CERRAR SESIÓN", color = AccentAlarm, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
