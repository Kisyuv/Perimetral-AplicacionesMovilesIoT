package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

data class DiscoveredNode(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

class LocalNsdManager(private val context: Context) {

    companion object {
        const val SERVICE_TYPE = "_perimetral._tcp."
        const val DEFAULT_SERVICE_NAME = "PerimetralNode"
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _discoveredNodes = MutableStateFlow<List<DiscoveredNode>>(emptyList())
    val discoveredNodes: StateFlow<List<DiscoveredNode>> = _discoveredNodes.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // Registrar servicio mDNS (Rol Nodo)
    fun registerNodeService(port: Int, nodeName: String = DEFAULT_SERVICE_NAME) {
        unregisterNodeService()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = nodeName
            serviceType = "_perimetral._tcp"
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d("NSD", "Servicio mDNS Perimetral registrado en puerto $port: ${NsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NSD", "Fallo al registrar servicio mDNS: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d("NSD", "Servicio mDNS desregistrado")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e("NSD", "Error registrando servicio mDNS", e)
        }
    }

    fun unregisterNodeService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (_: Exception) {}
            registrationListener = null
        }
    }

    // Iniciar descubrimiento mDNS (Rol Panel)
    fun startDiscovery() {
        stopDiscovery()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NSD", "Descubrimiento de nodos iniciado para $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d("NSD", "Nodo encontrado: ${service.serviceName}")
                resolveService(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val current = _discoveredNodes.value.filter { it.serviceName != service.serviceName }
                _discoveredNodes.value = current
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("NSD", "Error iniciando descubrimiento", e)
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("NSD", "Resolución de servicio fallida: $errorCode")
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                val host = resolvedInfo.host?.hostAddress ?: return
                val port = resolvedInfo.port
                val name = resolvedInfo.serviceName

                val node = DiscoveredNode(
                    serviceName = name,
                    hostAddress = host,
                    port = port
                )

                val current = _discoveredNodes.value.filter { it.hostAddress != host }.toMutableList()
                current.add(node)
                _discoveredNodes.value = current
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e("NSD", "Error resolviendo servicio", e)
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (_: Exception) {}
            discoveryListener = null
        }
    }
}
