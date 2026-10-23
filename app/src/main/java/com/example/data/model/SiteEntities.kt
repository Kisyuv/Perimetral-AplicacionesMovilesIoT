package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

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
    val estroboActivo: Boolean = false
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
