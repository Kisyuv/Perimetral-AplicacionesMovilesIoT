package com.example.network

import android.util.Base64
import android.util.Log
import com.example.security.KeystoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class ConnectionType(val label: String) {
    LOCAL_WIFI("Red local (WiFi Bodega)"),
    BLUETOOTH("Enlace Bluetooth P2P"),
    CLOUD_BACKUP("Nube (Firebase Backup)")
}

class LocalMeshManager(
    private val keystoreManager: KeystoreManager,
    var bluetoothSyncManager: BluetoothSyncManager? = null
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private var clientWebSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    private val _connectionType = MutableStateFlow(ConnectionType.CLOUD_BACKUP)
    val connectionType: StateFlow<ConnectionType> = _connectionType.asStateFlow()

    private val _lastReceivedLocalCommand = MutableStateFlow<String?>(null)
    val lastReceivedLocalCommand: StateFlow<String?> = _lastReceivedLocalCommand.asStateFlow()

    var onLocalCommandReceived: ((action: String, pin: String?, detail: String?) -> Unit)? = null

    init {
        // Enlazar eventos recibidos por Bluetooth si no hay WiFi
        bluetoothSyncManager?.onCommandReceived = { action, pin, detail ->
            _lastReceivedLocalCommand.value = "$action [AES-GCM BT OK]"
            onLocalCommandReceived?.invoke(action, pin, detail)
        }
    }

    // 1. SERVIDOR WEBSOCKET LOCAL (Se ejecuta en el NODO)
    fun startLocalServer(port: Int = 8765) {
        stopLocalServer()

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d("LocalMesh", "Servidor local iniciado en puerto $port")

                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch { handleClientSocket(clientSocket) }
                }
            } catch (e: Exception) {
                Log.e("LocalMesh", "Error en servidor local", e)
            }
        }
    }

    private fun handleClientSocket(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. Realizar handshake WebSocket RFC 6455
            val reader = input.bufferedReader()
            var line = reader.readLine()
            var secKey = ""

            while (!line.isNullOrEmpty()) {
                if (line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) {
                    secKey = line.substringAfter(":").trim()
                }
                line = reader.readLine()
            }

            if (secKey.isNotEmpty()) {
                val acceptKey = generateWebSocketAccept(secKey)
                val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"
                output.write(response.toByteArray(Charsets.UTF_8))
                output.flush()

                // 2. Leer frames de WebSocket
                readWebSocketFrames(input, output)
            }
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun generateWebSocketAccept(key: String): String {
        val magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest((key + magic).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun readWebSocketFrames(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(4096)
        while (true) {
            val b0 = input.read()
            if (b0 == -1) break
            val b1 = input.read()
            if (b1 == -1) break

            val opcode = b0 and 0x0F
            if (opcode == 8) break // Close frame

            val isMasked = (b1 and 0x80) != 0
            var payloadLen = b1 and 0x7F

            if (payloadLen == 126) {
                val byteA = input.read()
                val byteB = input.read()
                payloadLen = (byteA shl 8) or byteB
            }

            val maskingKey = ByteArray(4)
            if (isMasked) {
                input.read(maskingKey)
            }

            val payload = ByteArray(payloadLen)
            var bytesRead = 0
            while (bytesRead < payloadLen) {
                val read = input.read(payload, bytesRead, payloadLen - bytesRead)
                if (read == -1) break
                bytesRead += read
            }

            if (isMasked) {
                for (i in 0 until payloadLen) {
                    payload[i] = (payload[i].toInt() xor maskingKey[i % 4].toInt()).toByte()
                }
            }

            val encryptedPayload = String(payload, Charsets.UTF_8)

            // Descifrar mensaje con AES-GCM (rechaza si viene de dispositivo no vinculado)
            try {
                val decryptedJson = keystoreManager.decrypt(encryptedPayload)
                val json = JSONObject(decryptedJson)
                val action = json.optString("action")
                val pin = json.optString("pin", null)
                val detail = json.optString("detail", null)

                _lastReceivedLocalCommand.value = "$action [AES-GCM OK]"
                onLocalCommandReceived?.invoke(action, pin, detail)

                // Enviar respuesta cifrada ACK
                val ackJson = JSONObject().put("status", "ok").put("action", action).toString()
                val encryptedAck = keystoreManager.encrypt(ackJson)
                sendWebSocketFrame(output, encryptedAck)
            } catch (e: Exception) {
                Log.w("LocalMesh", "Mensaje rechazado: fallo de autenticación GCM (Dispositivo no vinculado)")
            }
        }
    }

    private fun sendWebSocketFrame(output: OutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        output.write(0x81) // Text frame
        if (bytes.size <= 125) {
            output.write(bytes.size)
        } else {
            output.write(126)
            output.write((bytes.size shr 8) and 0xFF)
            output.write(bytes.size and 0xFF)
        }
        output.write(bytes)
        output.flush()
    }

    fun stopLocalServer() {
        serverJob?.cancel()
        serverJob = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    // 2. CLIENTE WEBSOCKET LOCAL (Se ejecuta en el PANEL)
    fun connectToNode(host: String, port: Int = 8765) {
        disconnectClient()

        val request = Request.Builder()
            .url("ws://$host:$port")
            .build()

        clientWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("LocalMesh", "Conectado a Nodo local en $host:$port")
                _connectionType.value = ConnectionType.LOCAL_WIFI
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val decrypted = keystoreManager.decrypt(text)
                    Log.d("LocalMesh", "Respuesta cifrada recibida: $decrypted")
                } catch (_: Exception) {}
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionType.value = ConnectionType.CLOUD_BACKUP
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("LocalMesh", "Fallo enlace local, usando respaldo por Nube: ${t.message}")
                _connectionType.value = ConnectionType.CLOUD_BACKUP
            }
        })
    }

    fun sendLocalCommand(action: String, pin: String? = null, detail: String? = null): Boolean {
        // 1. Intentar por WebSocket si está conectado
        val ws = clientWebSocket
        if (ws != null) {
            try {
                val json = JSONObject().apply {
                    put("action", action)
                    if (pin != null) put("pin", pin)
                    if (detail != null) put("detail", detail)
                    put("timestamp", System.currentTimeMillis())
                }

                // Cifrar con AES-GCM
                val encrypted = keystoreManager.encrypt(json.toString())
                val sent = ws.send(encrypted)
                if (sent) return true
            } catch (_: Exception) {}
        }

        // 2. Si no hay WebSocket LAN, intentar por Bluetooth P2P
        val btManager = bluetoothSyncManager
        if (btManager != null && btManager.isConnected.value) {
            val btSent = btManager.sendCommand(action, pin, detail)
            if (btSent) {
                _connectionType.value = ConnectionType.BLUETOOTH
                return true
            }
        }

        _connectionType.value = ConnectionType.CLOUD_BACKUP
        return false
    }

    fun updateConnectionType(type: ConnectionType) {
        _connectionType.value = type
    }

    fun disconnectClient() {
        try {
            clientWebSocket?.close(1000, "Panel desconectado")
        } catch (_: Exception) {}
        clientWebSocket = null
        if (bluetoothSyncManager?.isConnected?.value == true) {
            _connectionType.value = ConnectionType.BLUETOOTH
        } else {
            _connectionType.value = ConnectionType.CLOUD_BACKUP
        }
    }
}
