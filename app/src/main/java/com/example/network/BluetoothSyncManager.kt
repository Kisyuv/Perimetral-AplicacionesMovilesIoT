package com.example.network

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.security.KeystoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class DiscoveredBluetoothDevice(
    val name: String,
    val address: String,
    val isAppNode: Boolean = false,
    val isBonded: Boolean = false
)

class BluetoothSyncManager(
    private val context: Context,
    private val keystoreManager: KeystoreManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val tag = "BluetoothSync"

    // UUID específico para la red perimetral P2P
    val serviceUuid: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    val serviceName = "PerimetralNode"

    val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBluetoothDevice>> = _discoveredDevices.asStateFlow()

    private var serverSocket: BluetoothServerSocket? = null
    private var serverJob: Job? = null
    private var activeClientSocket: BluetoothSocket? = null
    private var clientJob: Job? = null

    var onCommandReceived: ((action: String, pin: String?, detail: String?) -> Unit)? = null
    var onResponseReceived: ((json: String) -> Unit)? = null

    private var discoveryReceiverRegistered = false
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    if (device != null) {
                        val devName = try {
                            if (hasRequiredPermissions()) device.name ?: "" else ""
                        } catch (_: SecurityException) {
                            ""
                        }
                        val address = device.address ?: ""
                        val displayName = if (devName.isNotBlank()) devName else "Dispositivo Bluetooth (${address.takeLast(5)})"
                        val isNode = devName.contains("Nodo", ignoreCase = true) ||
                                devName.contains("Perimetral", ignoreCase = true) ||
                                devName.contains("Bodega", ignoreCase = true)

                        val currentList = _discoveredDevices.value.toMutableList()
                        val existingIndex = currentList.indexOfFirst { it.address == address }
                        val item = DiscoveredBluetoothDevice(
                            name = displayName,
                            address = address,
                            isAppNode = isNode,
                            isBonded = device.bondState == BluetoothDevice.BOND_BONDED
                        )
                        if (existingIndex >= 0) {
                            currentList[existingIndex] = item
                        } else {
                            currentList.add(item)
                        }
                        _discoveredDevices.value = currentList.sortedByDescending { it.isAppNode }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isDiscovering.value = true
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isDiscovering.value = false
                }
            }
        }
    }

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun getEnableBluetoothIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

    fun activateBluetooth(onComplete: (Boolean) -> Unit = {}) {
        if (bluetoothAdapter == null) {
            onComplete(false)
            return
        }
        if (bluetoothAdapter?.isEnabled == true) {
            onComplete(true)
            return
        }
        try {
            @Suppress("DEPRECATION")
            val enabled = bluetoothAdapter?.enable() ?: false
            onComplete(enabled)
        } catch (_: SecurityException) {
            onComplete(false)
        }
    }

    fun getDiscoverableIntent(durationSeconds: Int = 300): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, durationSeconds)
        }
    }

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startDiscovery() {
        if (!hasRequiredPermissions() || bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            return
        }

        // Cargar dispositivos ya emparejados inicialmente
        val initialList = mutableListOf<DiscoveredBluetoothDevice>()
        try {
            bluetoothAdapter?.bondedDevices?.forEach { dev ->
                val name = dev.name ?: "Dispositivo Emparejado"
                val isNode = name.contains("Nodo", ignoreCase = true) ||
                        name.contains("Perimetral", ignoreCase = true) ||
                        name.contains("Bodega", ignoreCase = true)
                initialList.add(
                    DiscoveredBluetoothDevice(
                        name = name,
                        address = dev.address,
                        isAppNode = isNode,
                        isBonded = true
                    )
                )
            }
        } catch (_: SecurityException) {}

        _discoveredDevices.value = initialList.sortedByDescending { it.isAppNode }

        registerReceiverIfNeeded()

        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
            bluetoothAdapter?.startDiscovery()
            _isDiscovering.value = true
        } catch (e: SecurityException) {
            Log.w(tag, "No se pudo iniciar startDiscovery: ${e.message}")
        }
    }

    fun stopDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
        } catch (_: SecurityException) {}
        _isDiscovering.value = false
    }

    private fun registerReceiverIfNeeded() {
        if (!discoveryReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(discoveryReceiver, filter)
            discoveryReceiverRegistered = true
        }
    }

    fun getPairedDevices(): List<Pair<String, String>> {
        val bonded = if (hasRequiredPermissions() && bluetoothAdapter != null && bluetoothAdapter!!.isEnabled) {
            try {
                bluetoothAdapter?.bondedDevices?.map { device ->
                    (device.name ?: "Dispositivo Bluetooth") to device.address
                } ?: emptyList()
            } catch (_: SecurityException) {
                emptyList()
            }
        } else {
            emptyList()
        }

        return bonded
    }

    // 1. MODO SERVIDOR (Nodo central esperando conexión del panel por Bluetooth)
    fun startBluetoothServer() {
        if (!hasRequiredPermissions() || bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w(tag, "Permiso Bluetooth no otorgado o Bluetooth apagado para iniciar servidor")
            return
        }

        stopBluetoothServer()

        serverJob = scope.launch {
            try {
                @Suppress("MissingPermission")
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(serviceName, serviceUuid)
                _isServerRunning.value = true
                Log.d(tag, "Servidor Bluetooth RFCOMM activo esperando conexión")

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    _isConnected.value = true
                    try {
                        @Suppress("MissingPermission")
                        _connectedDeviceName.value = socket.remoteDevice?.name ?: "Panel Perimetral"
                    } catch (_: SecurityException) {}

                    handleIncomingSocket(socket)
                }
            } catch (e: Exception) {
                Log.w(tag, "Servidor Bluetooth detenido: ${e.message}")
            } finally {
                _isServerRunning.value = false
            }
        }
    }

    private fun handleIncomingSocket(socket: BluetoothSocket) {
        try {
            val input = socket.inputStream
            val output = socket.outputStream
            val buffer = ByteArray(2048)

            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                val encrypted = String(buffer, 0, bytesRead, Charsets.UTF_8).trim()

                try {
                    val decrypted = keystoreManager.decrypt(encrypted)
                    val json = JSONObject(decrypted)
                    val action = json.optString("action")
                    val pin = json.optString("pin", null)
                    val detail = json.optString("detail", null)

                    onCommandReceived?.invoke(action, pin, detail)

                    // Responder confirmación ACK cifrada con estado
                    val ack = JSONObject().apply {
                        put("status", "ok")
                        put("action", action)
                        put("timestamp", System.currentTimeMillis())
                    }
                    val encryptedAck = keystoreManager.encrypt(ack.toString())
                    output.write(encryptedAck.toByteArray(Charsets.UTF_8))
                    output.flush()
                } catch (_: Exception) {
                    Log.w(tag, "Mensaje Bluetooth no autenticado o corrupto")
                }
            }
        } catch (_: Exception) {
        } finally {
            _isConnected.value = false
            _connectedDeviceName.value = null
            try { socket.close() } catch (_: Exception) {}
        }
    }

    fun stopBluetoothServer() {
        serverJob?.cancel()
        serverJob = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        _isServerRunning.value = false
    }

    // 2. MODO CLIENTE (Panel enviando comandos al Nodo por Bluetooth)
    fun connectToNode(deviceAddress: String, onConnected: (Boolean) -> Unit = {}) {
        disconnect()

        if (!hasRequiredPermissions() || bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            onConnected(false)
            return
        }

        clientJob = scope.launch {
            try {
                val device: BluetoothDevice = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: run {
                    onConnected(false)
                    return@launch
                }

                @Suppress("MissingPermission")
                val socket = device.createRfcommSocketToServiceRecord(serviceUuid)
                @Suppress("MissingPermission")
                socket.connect()

                activeClientSocket = socket
                _isConnected.value = true
                @Suppress("MissingPermission")
                _connectedDeviceName.value = device.name ?: deviceAddress
                onConnected(true)

                // Leer respuestas del nodo
                val input = socket.inputStream
                val buffer = ByteArray(2048)
                while (isActive) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    val encrypted = String(buffer, 0, read, Charsets.UTF_8)
                    try {
                        val decrypted = keystoreManager.decrypt(encrypted)
                        Log.d(tag, "Respuesta Bluetooth de nodo recibida: $decrypted")
                        onResponseReceived?.invoke(decrypted)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(tag, "Fallo al conectar con nodo por Bluetooth físico: ${e.message}")
                _isConnected.value = false
                _connectedDeviceName.value = null
                onConnected(false)
            }
        }
    }

    fun sendCommand(action: String, pin: String? = null, detail: String? = null): Boolean {
        val socket = activeClientSocket ?: return false
        return try {
            val json = JSONObject().apply {
                put("action", action)
                if (pin != null) put("pin", pin)
                if (detail != null) put("detail", detail)
                put("timestamp", System.currentTimeMillis())
            }
            val encrypted = keystoreManager.encrypt(json.toString())
            val output = socket.outputStream
            output.write(encrypted.toByteArray(Charsets.UTF_8))
            output.flush()
            true
        } catch (e: Exception) {
            _isConnected.value = false
            false
        }
    }

    fun disconnect() {
        clientJob?.cancel()
        clientJob = null
        try { activeClientSocket?.close() } catch (_: Exception) {}
        activeClientSocket = null
        _isConnected.value = false
        _connectedDeviceName.value = null
    }

    fun cleanUp() {
        stopDiscovery()
        if (discoveryReceiverRegistered) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (_: Exception) {}
            discoveryReceiverRegistered = false
        }
        stopBluetoothServer()
        disconnect()
    }
}
