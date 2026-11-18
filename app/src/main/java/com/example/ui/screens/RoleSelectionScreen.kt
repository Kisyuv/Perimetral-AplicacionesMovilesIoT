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
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.DeviceRoleRepository
import com.example.ui.theme.DotoFamily
import com.example.ui.theme.AccentAlarm
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.LocalAlarmAccentColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun RoleSelectionScreen(
    userId: String,
    deviceRoleRepository: DeviceRoleRepository,
    onRoleAssigned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAlarmAccentColor.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var conflictNodeModel by remember { mutableStateOf<String?>(null) }
    var showConflictDialog by remember { mutableStateOf(false) }

    fun selectRole(role: String, forceReplace: Boolean = false) {
        isProcessing = true
        errorMessage = null
        coroutineScope.launch {
            val result = deviceRoleRepository.assignRole(
                userId = userId,
                role = role,
                forceReplaceNode = forceReplace
            )

            result.fold(
                onSuccess = {
                    isProcessing = false
                    onRoleAssigned(role)
                },
                onFailure = { error ->
                    isProcessing = false
                    val msg = error.message ?: ""
                    if (msg.startsWith("NODE_ALREADY_EXISTS:")) {
                        conflictNodeModel = msg.removePrefix("NODE_ALREADY_EXISTS:")
                        showConflictDialog = true
                    } else {
                        errorMessage = error.localizedMessage ?: "Error al configurar rol de dispositivo."
                    }
                }
            )
        }
    }

    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Nodo Activo Existente",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            text = {
                Text(
                    text = "Ya existe un nodo alarma activo para este sitio (Dispositivo: ${conflictNodeModel ?: "Central Bodega"}).\n\nSolo puede haber UN nodo alarma activo por sitio. ¿Deseas reemplazar el nodo anterior con este dispositivo?",
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, TextPrimary, RoundedCornerShape(4.dp))
                        .clickable {
                            showConflictDialog = false
                            selectRole("nodo", forceReplace = true)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Reemplazar Nodo",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { showConflictDialog = false }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Cancelar",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "ROL DE DISPOSITIVO",
                color = TextPrimary,
                fontSize = 24.sp,
                fontFamily = DotoFamily,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Selecciona la función operativa de este teléfono para el perímetro de la bodega.",
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurface)
                    .border(1.dp, TextSecondary, RoundedCornerShape(6.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = errorMessage!!,
                    color = TextPrimary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Tarjeta 1: Nodo alarma (simulado)
        RoleOptionCard(
            title = "Nodo alarma (simulado)",
            tag = "CENTRAL LOCAL BODEGA",
            description = "Este celular actúa como la central de la bodega con sensores y actuadores simulados (haces ópticos, sirena 110dB, cortinas y enlaces perimetrales).",
            icon = Icons.Outlined.Hub,
            accentColor = accent,
            isLoading = isProcessing,
            onClick = { selectRole("nodo") },
            testTag = "role_card_nodo"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tarjeta 2: Panel de control
        RoleOptionCard(
            title = "Panel de control",
            tag = "CONTROL REMOTO OPERADOR",
            description = "Este celular arma/desarma y controla el sistema, recibe alertas push de intrusiones fuera de horario y supervisa los sensores en tiempo real.",
            icon = Icons.Outlined.Smartphone,
            accentColor = accent,
            isLoading = isProcessing,
            onClick = { selectRole("panel") },
            testTag = "role_card_panel"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceVariant)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dispositivo detectado: ${deviceRoleRepository.getLocalDeviceModel()}",
                color = TextMuted,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RoleOptionCard(
    title: String,
    tag: String,
    description: String,
    icon: ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    isLoading: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(20.dp)
            .testTag(testTag)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, accentColor, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tag,
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                }
            }

            Text(
                text = title,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = description,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
