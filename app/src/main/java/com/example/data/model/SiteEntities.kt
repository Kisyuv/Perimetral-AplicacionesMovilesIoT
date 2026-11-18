package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import org.json.JSONArray
import org.json.JSONObject

@IgnoreExtraProperties
data class SiteSchedule(
    val inicio: String = "08:00",
    val fin: String = "19:00",
    val dias: List<Long> = listOf(1L, 2L, 3L, 4L, 5L) // Lunes a Viernes
)

@IgnoreExtraProperties
data class SiteAlarmState(
    val estado: String = "desarmada", // "desarmada" | "armada" | "alarma"
    val horario: Map<String, Any> = mapOf("inicio" to "08:00", "fin" to "19:00", "dias" to listOf(1L, 2L, 3L, 4L, 5L)),
    val armadoAutomatico: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val sirenaActiva: Boolean = false,
    val estroboActivo: Boolean = false,
    val zonasActivas: List<String> = emptyList(),
    val zonaAlarma: String = "" // "ZN-05 · PIR Interior Pasillo Central" cuando estado == "alarma"
)

@IgnoreExtraProperties
data class SiteSensorState(
    val pirMovimiento: Boolean = false,
    val reedPuertaAbierta: Boolean = false,
    val ultimoPir: Long = 0L,
    val ultimoReed: Long = 0L
)

@IgnoreExtraProperties
data class SiteCameraConfig(
    val modoPir: String = "simulado", // "camara" | "simulado"
    val sensibilidad: Long = 5L, // 1..10
    val tiempoConfirmacion: Long = 2L, // segundos
    val estadoCamara: String = "inactiva", // "activa" | "inactiva" | "error" | "sin_permiso"
    val lente: String = "frontal", // "frontal" | "trasera"
    val ultimoMovimientoDetectado: Long = 0L
)

@IgnoreExtraProperties
data class SecurityEvent(
    val id: String = "",
    val tipo: String = "", // "pir" | "reed" | "armado" | "desarmado" | "alarma" | "login"
    val detalle: String = "",
    val origen: String = "", // "nodo" | "panel"
    val timestamp: Long = System.currentTimeMillis()
)

/** Estado que el nodo empuja a los paneles por el enlace local (WiFi). */
fun peerStateJson(site: SiteAlarmState, sensors: SiteSensorState, nodeOnline: Boolean): JSONObject =
    JSONObject()
        .put("type", "state")
        .put("online", nodeOnline)
        .put("estado", site.estado)
        .put("sirenaActiva", site.sirenaActiva)
        .put("estroboActivo", site.estroboActivo)
        .put("zonaAlarma", site.zonaAlarma)
        .put("zonasActivas", JSONArray(site.zonasActivas))
        .put("pirMovimiento", sensors.pirMovimiento)
        .put("reedPuertaAbierta", sensors.reedPuertaAbierta)
        .put("ultimoPir", sensors.ultimoPir)
        .put("ultimoReed", sensors.ultimoReed)
        .put("timestamp", System.currentTimeMillis())

fun SiteAlarmState.withPeer(peer: JSONObject?): SiteAlarmState {
    if (peer == null) return this
    val zones = peer.optJSONArray("zonasActivas")
    return copy(
        estado = peer.optString("estado", estado),
        sirenaActiva = peer.optBoolean("sirenaActiva", sirenaActiva),
        estroboActivo = peer.optBoolean("estroboActivo", estroboActivo),
        zonaAlarma = peer.optString("zonaAlarma", zonaAlarma),
        zonasActivas = if (zones != null) List(zones.length()) { zones.getString(it) } else zonasActivas
    )
}

fun SiteSensorState.withPeer(peer: JSONObject?): SiteSensorState {
    if (peer == null) return this
    return copy(
        pirMovimiento = peer.optBoolean("pirMovimiento", pirMovimiento),
        reedPuertaAbierta = peer.optBoolean("reedPuertaAbierta", reedPuertaAbierta),
        ultimoPir = peer.optLong("ultimoPir", ultimoPir),
        ultimoReed = peer.optLong("ultimoReed", ultimoReed)
    )
}
