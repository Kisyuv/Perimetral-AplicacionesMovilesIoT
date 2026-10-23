package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

data class LocalNetworkState(
    val isConnectedToWifi: Boolean = false,
    val ssid: String = "BODEGA-SEC-LAN",
    val ipAddress: String = "127.0.0.1",
    val gatewayStatus: String = "SIN CONEXIÓN",
    val meshNodesActive: Int = 0,
    val signalStrengthDbm: Int = -60
)

class LocalWifiManager(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _networkState = MutableStateFlow(checkCurrentWifiState())
    val networkState: StateFlow<LocalNetworkState> = _networkState.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
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

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        } catch (_: Exception) {
            try {
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                connectivityManager?.registerNetworkCallback(request, networkCallback)
            } catch (_: Exception) {}
        }
    }

    private fun checkCurrentWifiState(): LocalNetworkState {
        val activeNetwork = connectivityManager?.activeNetwork
        val caps = connectivityManager?.getNetworkCapabilities(activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        var localIp = "127.0.0.1"
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        localIp = addr.hostAddress ?: "127.0.0.1"
                        break
                    }
                }
            }
        } catch (_: Exception) {}

        return LocalNetworkState(
            isConnectedToWifi = isWifi,
            ssid = if (isWifi) "BODEGA-PERIMETRO-WIFI" else "Desconectado",
            ipAddress = localIp,
            gatewayStatus = if (isWifi) "EN LÍNEA (WIFI LOCAL)" else "DESCONECTADO",
            meshNodesActive = if (isWifi) 6 else 0,
            signalStrengthDbm = if (isWifi) -54 else 0
        )
    }

    fun updateWifiStatus(): LocalNetworkState {
        val current = checkCurrentWifiState()
        _networkState.value = current
        return current
    }

    fun refreshNetworkStatus(): LocalNetworkState = updateWifiStatus()
}
