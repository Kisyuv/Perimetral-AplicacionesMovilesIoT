package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocalNetworkState(
    val isConnectedToWifi: Boolean = false,
    val ssid: String = "BODEGA-SEC-LAN",
    val ipAddress: String = "192.168.1.100",
    val gatewayStatus: String = "EN LÍNEA",
    val meshNodesActive: Int = 8,
    val signalStrengthDbm: Int = -58
)

class LocalWifiManager(private val context: Context) {

    private val _networkState = MutableStateFlow(
        LocalNetworkState(
            isConnectedToWifi = true,
            ssid = "BODEGA-PERIMETRO-WIFI",
            ipAddress = "192.168.10.45",
            gatewayStatus = "EN LÍNEA",
            meshNodesActive = 6,
            signalStrengthDbm = -54
        )
    )
    val networkState: StateFlow<LocalNetworkState> = _networkState.asStateFlow()

    fun refreshNetworkStatus(): LocalNetworkState {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork
        val caps = connectivityManager?.getNetworkCapabilities(activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val current = _networkState.value.copy(
            isConnectedToWifi = isWifi || true, // En entorno simulado mantenemos enlace a gateway local
            gatewayStatus = if (isWifi) "EN LÍNEA (WIFI LOCAL)" else "SIMULADOR ENLACE BODEGA"
        )
        _networkState.value = current
        return current
    }
}
