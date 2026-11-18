package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Indica si el dispositivo tiene internet real (red validada), no solo WiFi conectado. */
class ConnectivityMonitor(context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(check())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = check()
        }

        override fun onLost(network: Network) {
            _isOnline.value = check()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _isOnline.value = check()
        }
    }

    init {
        try {
            connectivityManager?.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {}
    }

    private fun check(): Boolean {
        val caps = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
