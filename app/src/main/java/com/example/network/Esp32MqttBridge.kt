package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.example.data.repository.SiteAlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.UUID
import javax.net.ssl.SSLSocketFactory

/** Qué dispositivo hace de nodo de sensores. En los modos ESP32 este teléfono es siempre panel. */
enum class NodeMode(val label: String) {
    TELEFONO("Teléfono"),
    ESP32_WOKWI("ESP32 Wokwi"),
    ESP32_FISICO("ESP32 físico")
}

const val DEFAULT_MQTT_CHANNEL = "perimetral/a2b39e2e"

/** Lo último que informó el ESP32 por MQTT. */
data class Esp32Estado(
    val broker: Boolean = false,
    val nodoOnline: Boolean = false,
    val pir: Boolean = false,
    val puerta: Boolean = false,
    val rssi: Int? = null
)

/**
 * Enlace con el nodo ESP32 (hardware/esp32-bodega/sketch.ino) por MQTT.
 * Recibe `<canal>/evento|sensores|nodo` y publica en `<canal>/estado` el estado del sitio,
 * para que el ESP32 maneje sirena, estrobo y LED de armado. Los eventos entran por
 * [SiteAlarmRepository.recordSensorTrigger], igual que los de cualquier otro nodo.
 */
class Esp32MqttBridge(private val siteAlarmRepository: SiteAlarmRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var client: MqttAsyncClient? = null
    private var channel = ""

    // Último estado del sitio: se reenvía en cada (re)conexión, porque sin conexión el publish falla.
    @Volatile private var ultimoEstado: String? = null

    private val _estado = MutableStateFlow(Esp32Estado())
    val estado: StateFlow<Esp32Estado> = _estado.asStateFlow()

    @Synchronized
    fun start(canal: String) {
        stop()
        channel = canal
        job = scope.launch {
            launch {
                siteAlarmRepository.observeSiteState()
                    .map { Triple(it.estado, it.sirenaActiva, it.estroboActivo) }
                    .distinctUntilChanged()
                    .collect { (estado, sirena, estrobo) ->
                        ultimoEstado = JSONObject()
                            .put("estado", estado).put("sirena", sirena).put("estrobo", estrobo).toString()
                        publicarEstado()
                    }
            }
            conectar()
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        client?.let {
            try { it.disconnectForcibly(500, 500) } catch (_: Exception) {}
            try { it.close() } catch (_: Exception) {}
        }
        client = null
        ultimoEstado = null
        _estado.value = Esp32Estado()
    }

    // Paho solo reintenta solo tras una primera conexión lograda; el primer intento lo repetimos aquí.
    private suspend fun conectar() {
        if (BuildConfig.MQTT_HOST.isEmpty()) {
            Log.e(TAG, "Falta mqtt.host en local.properties: el bridge MQTT no conecta")
            return
        }
        val mqtt = MqttAsyncClient("ssl://${BuildConfig.MQTT_HOST}:8883", "perimetral-app-" + UUID.randomUUID().toString().take(8), MemoryPersistence())
        mqtt.setCallback(callback)
        client = mqtt
        val opciones = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 30
            userName = BuildConfig.MQTT_USER
            password = BuildConfig.MQTT_PASSWORD.toCharArray()
            // El trust store de Android ya incluye DigiCert Global Root G2 (CA de EMQX Cloud).
            socketFactory = SSLSocketFactory.getDefault()
            isHttpsHostnameVerificationEnabled = true
        }
        while (!mqtt.isConnected) {
            try {
                mqtt.connect(opciones).waitForCompletion(15_000)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo conectar al broker: ${e.message}")
                delay(5_000)
            }
        }
    }

    private val callback = object : MqttCallbackExtended {
        // También se llama tras cada reconexión automática: la sesión es limpia, hay que re-suscribirse.
        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            _estado.value = _estado.value.copy(broker = true)
            try {
                client?.subscribe(arrayOf("$channel/evento", "$channel/sensores", "$channel/nodo"), intArrayOf(1, 1, 1))
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al suscribirse: ${e.message}")
            }
            publicarEstado()
        }

        override fun connectionLost(cause: Throwable?) {
            // Sin conexión no sabemos nada del ESP32: no mostrar datos viejos como actuales.
            _estado.value = Esp32Estado()
        }

        override fun messageArrived(topic: String, message: MqttMessage) {
            procesar(topic.removePrefix("$channel/"), message.toString())
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
    }

    private fun procesar(tema: String, texto: String) {
        try {
            when (tema) {
                "nodo" -> _estado.value = _estado.value.copy(nodoOnline = texto.trim() == "online")
                "sensores" -> JSONObject(texto).let {
                    _estado.value = _estado.value.copy(
                        pir = it.optBoolean("pir"),
                        puerta = it.optBoolean("puerta"),
                        rssi = if (it.has("rssi")) it.optInt("rssi") else null
                    )
                }
                "evento" -> JSONObject(texto).let {
                    val sensor = it.optString("sensor")
                    if (sensor == "pir" || sensor == "reed") {
                        val detalle = it.optString("detalle").ifEmpty { "Evento del ESP32" }
                        // El id lo pone el ESP32: si hay varios paneles, todos escriben el mismo evento una sola vez.
                        val eventId = it.optString("id").ifEmpty { null }?.let { id -> "esp32-$id" }
                        scope.launch {
                            siteAlarmRepository.recordSensorTrigger("bodega_central", sensor, detalle, "esp32", eventId = eventId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Mensaje MQTT inválido en $tema: ${e.message}")
        }
    }

    private fun publicarEstado() {
        val json = ultimoEstado ?: return
        val mqtt = client?.takeIf { it.isConnected } ?: return
        try {
            // retained + QoS 1: un ESP32 que arranca después recibe el estado vigente.
            mqtt.publish("$channel/estado", json.toByteArray(), 1, true)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo publicar el estado: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "Esp32MqttBridge"
    }
}
