package com.example.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
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
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.example.network.ConnectionType
import com.example.network.DiscoveredNode
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
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
fun NetworkScreen(
    deviceRole: String?,
    connectionType: ConnectionType,
    discoveredNodes: List<DiscoveredNode>,
    onConnectNode: (host: String, port: Int) -> Unit,
    onRefreshDiscovery: () -> Unit,
    isBluetoothConnected: Boolean = false,
    connectedBluetoothDevice: String? = null,
    pairedBluetoothDevices: List<Pair<String, String>> = emptyList(),
    onConnectBluetooth: (String) -> Unit = {},
    onDisconnectBluetooth: () -> Unit = {},
    onActivateBluetooth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAlarmAccentColor.current
    val scrollState = rememberScrollState()

    var isConnectedToWifi by remember { mutableStateOf(false) }
    var currentSsid by remember { mutableStateOf("BODEGA-SEC-WIFI") }
    var localIpAddress by remember { mutableStateOf("127.0.0.1") }

    // Detección en tiempo real de conexión WiFi mediante NetworkCallback
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        fun updateWifiStatus() {
            val activeNetwork = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(activeNetwork)
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            isConnectedToWifi = isWifi

            if (isWifi) {
                try {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val info = wifiManager?.connectionInfo
                    val ssid = info?.ssid
                    if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                        currentSsid = ssid.removeSurrounding("\"")
                    } else {
                        currentSsid = "BODEGA-SEC-WIFI"
                    }
                } catch (_: Exception) {}
            } else {
                currentSsid = "Sin red WiFi"
            }

            // Obtener IP local de la interfaz de red
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            localIpAddress = addr.hostAddress ?: "127.0.0.1"
                            break
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        updateWifiStatus()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateWifiStatus()
            }

            override fun onLost(network: Network) {
                updateWifiStatus()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                updateWifiStatus()
            }
        }

        try {
            connectivityManager?.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            try {
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                connectivityManager?.registerNetworkCallback(request, callback)
            } catch (_: Exception) {}
        }

        onDispose {
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
            .testTag("network_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Encabezado
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RED LOCAL WIFI",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Monitoreo en tiempo real y enlace de respaldo P2P",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(DarkSurfaceVariant)
                    .clickable(onClick = onRefreshDiscovery)
                    .padding(8.dp)
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refrescar", tint = accent, modifier = Modifier.size(16.dp))
            }
        }

        // Indicador de Canal de Conexión Activo
        val channelColor = when (connectionType) {
            ConnectionType.LOCAL_WIFI -> AccentDisarmed
            ConnectionType.BLUETOOTH -> AccentArmed
            ConnectionType.CLOUD_BACKUP -> accent
        }

        val channelIcon = when (connectionType) {
            ConnectionType.LOCAL_WIFI -> Icons.Outlined.Wifi
            ConnectionType.BLUETOOTH -> Icons.Outlined.BluetoothConnected
            ConnectionType.CLOUD_BACKUP -> Icons.Outlined.CloudQueue
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, channelColor, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = channelIcon,
                        contentDescription = null,
                        tint = channelColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "CANAL DE TRANSMISIÓN ACTIVO", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                        Text(
                            text = connectionType.label,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (connectionType == ConnectionType.BLUETOOTH && connectedBluetoothDevice != null) {
                            Text(text = "Vinculado a: $connectedBluetoothDevice", color = AccentArmed, fontSize = 11.sp)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(channelColor)
                )
            }
        }

        // Estado del Adaptador de Red del Dispositivo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, if (isConnectedToWifi) DarkBorder else AccentAlarm, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "DATOS DE CONEXIÓN LOCAL", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isConnectedToWifi) AccentDisarmed.copy(alpha = 0.15f) else AccentAlarm.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isConnectedToWifi) "WIFI ACTIVO" else "SIN WIFI",
                            color = if (isConnectedToWifi) AccentDisarmed else AccentAlarm,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                NetworkInfoRow("Estado WiFi:", if (isConnectedToWifi) "Conectado a WiFi" else "WiFi desconectado")

                // Requerimiento: Al detectar que no hay conexión WiFi sale botón bajo el texto que dice "Activar bluetooth"
                if (!isConnectedToWifi) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, AccentAlarm.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.WifiOff,
                                    contentDescription = null,
                                    tint = AccentAlarm,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "No hay conexión WiFi disponible. Activa el enlace Bluetooth P2P para sincronización perimetral.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            // Botón requerido textualmente "Activar bluetooth"
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentArmed)
                                    .clickable { onActivateBluetooth() }
                                    .padding(vertical = 11.dp)
                                    .testTag("activate_bluetooth_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Bluetooth,
                                        contentDescription = null,
                                        tint = DarkBackground,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Activar bluetooth",
                                        color = DarkBackground,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                NetworkInfoRow("SSID detectado:", currentSsid)
                NetworkInfoRow("Dirección IP local:", localIpAddress)
                NetworkInfoRow("Puerto de enlace local:", "8765 (WebSocket)")
                NetworkInfoRow("Cifrado de paquete:", "AES-GCM (256 bits Keystore)")
            }
        }

        // ENLACE BLUETOOTH P2P (Respaldo directo sin WiFi)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, if (isBluetoothConnected) AccentArmed else DarkBorder, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = AccentArmed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "ENLACE BLUETOOTH P2P (SIN WIFI)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            Text(text = "Comunicación bidireccional cifrada AES-GCM", color = TextMuted, fontSize = 11.sp)
                        }
                    }

                    if (isBluetoothConnected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentArmed.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(text = "CONECTADO", color = AccentArmed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (deviceRole == "nodo") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Servidor Bluetooth RFCOMM activo en este nodo esperando vinculación directa del panel perimetral para operación autónoma offline.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    // Vista Panel
                    if (isBluetoothConnected) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Dispositivo enlazado:", color = TextMuted, fontSize = 11.sp)
                                Text(text = connectedBluetoothDevice ?: "Nodo Bodega Central (P2P)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, AccentAlarm, RoundedCornerShape(4.dp))
                                    .clickable { onDisconnectBluetooth() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(text = "Desconectar", color = AccentAlarm, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Botón directo para activar enlace Bluetooth
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentArmed)
                                    .clickable { onActivateBluetooth() }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Bluetooth,
                                        contentDescription = null,
                                        tint = DarkBackground,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Activar bluetooth",
                                        color = DarkBackground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(text = "Dispositivos Bluetooth detectados para enlace:", color = TextSecondary, fontSize = 11.sp)
                            pairedBluetoothDevices.forEach { (name, address) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceVariant)
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text(text = address, color = TextMuted, fontSize = 10.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentArmed)
                                            .clickable { onConnectBluetooth(address) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(text = "Enlazar", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Descubrimiento mDNS de Nodos en la Red (_perimetral._tcp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "DESCUBRIMIENTO MDNS (_perimetral._tcp)", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(text = if (deviceRole == "nodo") "Este dispositivo se anuncia como nodo" else "Nodos descubiertos en la misma red", color = TextMuted, fontSize = 11.sp)
                    }
                }

                if (deviceRole == "nodo") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Servidor WebSocket local activo en $localIpAddress:8765. Anunciando servicio '_perimetral._tcp' para enlace directo de paneles.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    if (discoveredNodes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Buscando nodos en la red WiFi local... Los comandos se sincronizan por Bluetooth P2P o la nube si el nodo no está en la misma red.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        discoveredNodes.forEach { node ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceVariant)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = node.serviceName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = "${node.hostAddress}:${node.port}", color = TextMuted, fontSize = 11.sp)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(accent)
                                        .clickable { onConnectNode(node.hostAddress, node.port) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = "Conectar", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun NetworkInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextMuted, fontSize = 12.sp)
        Text(text = value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
