package com.example.data.repository

import com.example.data.model.SecurityEvent
import com.example.data.model.SiteAlarmState
import com.example.data.model.SiteCameraConfig
import com.example.data.model.SiteSensorState
import com.example.security.PinSecurity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class SiteAlarmRepository(
    private val firestore: FirebaseFirestore
) {

    private val repoScope = CoroutineScope(Dispatchers.IO)
    private val _localSiteState = kotlinx.coroutines.flow.MutableStateFlow(SiteAlarmState())

    suspend fun initializeSiteIfNeeded(siteId: String = "bodega_central") {
        try {
            val siteDoc = firestore.collection("sitios").document(siteId).get().await()
            if (!siteDoc.exists()) {
                val (defaultHash, defaultSalt) = PinSecurity.createDefaultPinCredentials()
                val initialSite = mapOf(
                    "estado" to "desarmada",
                    "horario" to mapOf("inicio" to "08:00", "fin" to "19:00", "dias" to listOf(1L, 2L, 3L, 4L, 5L)),
                    "armadoAutomatico" to false,
                    "pinHash" to defaultHash,
                    "pinSalt" to defaultSalt,
                    "sirenaActiva" to false,
                    "estroboActivo" to false,
                    "ultimaActualizacion" to FieldValue.serverTimestamp()
                )
                firestore.collection("sitios").document(siteId).set(initialSite).await()
            }

            // Inicializar documento de sensores si no existe, o resetear estado si ya existe
            val sensorDoc = firestore.collection("sitios").document(siteId)
                .collection("sensores").document("estado").get().await()
            if (!sensorDoc.exists()) {
                firestore.collection("sitios").document(siteId)
                    .collection("sensores").document("estado")
                    .set(
                        mapOf(
                            "pirMovimiento" to false,
                            "reedPuertaAbierta" to false,
                            "ultimoPir" to 0L,
                            "ultimoReed" to 0L
                        )
                    ).await()
            } else {
                firestore.collection("sitios").document(siteId)
                    .collection("sensores").document("estado")
                    .update(
                        mapOf(
                            "pirMovimiento" to false,
                            "reedPuertaAbierta" to false
                        )
                    ).await()
            }

            // Inicializar documento de configuración de cámara si no existe
            val configDoc = firestore.collection("sitios").document(siteId)
                .collection("config").document("general").get().await()
            if (!configDoc.exists()) {
                firestore.collection("sitios").document(siteId)
                    .collection("config").document("general")
                    .set(
                        mapOf(
                            "modoPir" to "simulado",
                            "sensibilidad" to 5L,
                            "tiempoConfirmacion" to 2L,
                            "estadoCamara" to "inactiva",
                            "ultimoMovimientoDetectado" to 0L
                        )
                    ).await()
            }
        } catch (_: Exception) {}
    }

    fun observeSiteState(siteId: String = "bodega_central"): Flow<SiteAlarmState> = callbackFlow {
        trySend(_localSiteState.value)
        val docRef = firestore.collection("sitios").document(siteId)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(_localSiteState.value)
                return@addSnapshotListener
            }
            val state = snapshot.toObject(SiteAlarmState::class.java) ?: SiteAlarmState()
            _localSiteState.value = state
            trySend(state)
        }
        awaitClose { registration.remove() }
    }

    fun observeSensorState(siteId: String = "bodega_central"): Flow<SiteSensorState> = callbackFlow {
        val docRef = firestore.collection("sitios").document(siteId)
            .collection("sensores").document("estado")
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(SiteSensorState())
                return@addSnapshotListener
            }
            val state = snapshot.toObject(SiteSensorState::class.java) ?: SiteSensorState()
            trySend(state)
        }
        awaitClose { registration.remove() }
    }

    fun observeCameraConfig(siteId: String = "bodega_central"): Flow<SiteCameraConfig> = callbackFlow {
        val docRef = firestore.collection("sitios").document(siteId)
            .collection("config").document("general")
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(SiteCameraConfig())
                return@addSnapshotListener
            }
            val config = snapshot.toObject(SiteCameraConfig::class.java) ?: SiteCameraConfig()
            trySend(config)
        }
        awaitClose { registration.remove() }
    }

    fun observeEvents(siteId: String = "bodega_central", limitCount: Long = 30): Flow<List<SecurityEvent>> = callbackFlow {
        val query = firestore.collection("sitios").document(siteId)
            .collection("eventos")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limitCount)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val events = snapshot.documents.mapNotNull { doc ->
                val id = doc.id
                val tipo = doc.getString("tipo") ?: ""
                val detalle = doc.getString("detalle") ?: ""
                val origen = doc.getString("origen") ?: ""
                val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                SecurityEvent(id = id, tipo = tipo, detalle = detalle, origen = origen, timestamp = ts)
            }
            trySend(events)
        }
        awaitClose { registration.remove() }
    }

    suspend fun armSystem(siteId: String = "bodega_central", origen: String = "panel"): Result<Unit> {
        _localSiteState.value = _localSiteState.value.copy(
            estado = "armada",
            sirenaActiva = false,
            estroboActivo = false
        )
        return try {
            firestore.collection("sitios").document(siteId).set(
                mapOf(
                    "estado" to "armada",
                    "sirenaActiva" to false,
                    "estroboActivo" to false,
                    "ultimaActualizacion" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            resetSensors(siteId)
            logEvent(siteId, "armado", "Sistema armado por operador", origen)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disarmSystem(siteId: String = "bodega_central", pinInput: String, origen: String = "panel"): Result<Unit> {
        return try {
            val siteDoc = firestore.collection("sitios").document(siteId).get().await()
            val pinHash = siteDoc.getString("pinHash") ?: ""
            val pinSalt = siteDoc.getString("pinSalt") ?: ""

            if (!PinSecurity.verifyPin(pinInput, pinSalt, pinHash)) {
                return Result.failure(IllegalArgumentException("PIN de 4 dígitos incorrecto."))
            }

            _localSiteState.value = _localSiteState.value.copy(
                estado = "desarmada",
                sirenaActiva = false,
                estroboActivo = false
            )

            firestore.collection("sitios").document(siteId).set(
                mapOf(
                    "estado" to "desarmada",
                    "sirenaActiva" to false,
                    "estroboActivo" to false,
                    "ultimaActualizacion" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            resetSensors(siteId)
            logEvent(siteId, "desarmado", "Sistema desarmado mediante PIN autorizado", origen)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun silenceActuators(
        siteId: String = "bodega_central",
        pinInput: String,
        silenceSiren: Boolean,
        turnOffStrobe: Boolean
    ): Result<Unit> {
        return try {
            val siteDoc = firestore.collection("sitios").document(siteId).get().await()
            val pinHash = siteDoc.getString("pinHash") ?: ""
            val pinSalt = siteDoc.getString("pinSalt") ?: ""

            if (!PinSecurity.verifyPin(pinInput, pinSalt, pinHash)) {
                return Result.failure(IllegalArgumentException("PIN de seguridad incorrecto."))
            }

            val updates = mutableMapOf<String, Any>(
                "ultimaActualizacion" to FieldValue.serverTimestamp()
            )
            if (silenceSiren) updates["sirenaActiva"] = false
            if (turnOffStrobe) updates["estroboActivo"] = false

            firestore.collection("sitios").document(siteId).update(updates).await()
            logEvent(siteId, "alarma", "Actuadores silenciados manualmente con PIN", "panel")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordSensorTrigger(
        siteId: String = "bodega_central",
        sensorType: String, // "pir" | "reed"
        detail: String,
        origen: String = "nodo"
    ) {
        try {
            val now = System.currentTimeMillis()
            val siteDoc = firestore.collection("sitios").document(siteId).get().await()
            val estadoActual = siteDoc.getString("estado") ?: "desarmada"

            // Actualizar telemetría de sensores
            val sensorUpdates = mutableMapOf<String, Any>()
            if (sensorType == "pir") {
                sensorUpdates["pirMovimiento"] = true
                sensorUpdates["ultimoPir"] = now
            } else if (sensorType == "reed") {
                sensorUpdates["reedPuertaAbierta"] = true
                sensorUpdates["ultimoReed"] = now
            }
            firestore.collection("sitios").document(siteId)
                .collection("sensores").document("estado")
                .update(sensorUpdates).await()

            // Si está armada, disparar alarma y encender actuadores
            if (estadoActual.equals("armada", ignoreCase = true)) {
                firestore.collection("sitios").document(siteId).update(
                    mapOf(
                        "estado" to "alarma",
                        "sirenaActiva" to true,
                        "estroboActivo" to true,
                        "ultimaActualizacion" to FieldValue.serverTimestamp()
                    )
                ).await()

                logEvent(siteId, "alarma", "¡INTRUSIÓN! Activación de sensor: $detail", origen)
            } else {
                // Si está desarmada, solo registrar el evento sin disparar alarma
                logEvent(siteId, sensorType, detail, origen)
            }

            // Auto-restablecer el pulso del sensor a inactivo después de 4 segundos
            repoScope.launch {
                delay(4000L)
                resetSensorStatus(siteId, resetPir = (sensorType == "pir"), resetReed = (sensorType == "reed"))
            }
        } catch (_: Exception) {}
    }

    suspend fun resetSensors(siteId: String = "bodega_central") {
        resetSensorStatus(siteId, resetPir = true, resetReed = true)
    }

    suspend fun resetSensorStatus(siteId: String = "bodega_central", resetPir: Boolean = true, resetReed: Boolean = false) {
        try {
            val updates = mutableMapOf<String, Any>()
            if (resetPir) updates["pirMovimiento"] = false
            if (resetReed) updates["reedPuertaAbierta"] = false
            firestore.collection("sitios").document(siteId)
                .collection("sensores").document("estado")
                .update(updates).await()
        } catch (_: Exception) {}
    }

    suspend fun updateSchedule(
        siteId: String = "bodega_central",
        inicio: String,
        fin: String,
        dias: List<Long>,
        armadoAutomatico: Boolean
    ): Result<Unit> {
        val horarioMap = mapOf("inicio" to inicio, "fin" to fin, "dias" to dias)
        // Optimización clave: actualizar estado en memoria de inmediato para que el switch
        // funcione sin trabas desde la primera ejecución sin reiniciar la app
        _localSiteState.value = _localSiteState.value.copy(
            horario = horarioMap,
            armadoAutomatico = armadoAutomatico
        )
        return try {
            firestore.collection("sitios").document(siteId).set(
                mapOf(
                    "horario" to horarioMap,
                    "armadoAutomatico" to armadoAutomatico,
                    "ultimaActualizacion" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            logEvent(siteId, "armado", "Horario actualizado: $inicio a $fin (Auto-armado: $armadoAutomatico)", "panel")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCameraConfig(
        siteId: String = "bodega_central",
        modoPir: String, // "camara" | "simulado"
        sensibilidad: Long,
        tiempoConfirmacion: Long
    ): Result<Unit> {
        return try {
            firestore.collection("sitios").document(siteId)
                .collection("config").document("general")
                .update(
                    mapOf(
                        "modoPir" to modoPir,
                        "sensibilidad" to sensibilidad,
                        "tiempoConfirmacion" to tiempoConfirmacion
                    )
                ).await()
            logEvent(siteId, "config", "Modo sensor movimiento cambiado a: $modoPir (Sensibilidad: $sensibilidad)", "panel")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCameraStatus(siteId: String = "bodega_central", estadoCamara: String) {
        try {
            firestore.collection("sitios").document(siteId)
                .collection("config").document("general")
                .update(mapOf("estadoCamara" to estadoCamara)).await()
        } catch (_: Exception) {}
    }

    suspend fun logEvent(siteId: String = "bodega_central", tipo: String, detalle: String, origen: String) {
        try {
            val eventPayload = mapOf(
                "tipo" to tipo,
                "detalle" to detalle,
                "origen" to origen,
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("sitios").document(siteId)
                .collection("eventos")
                .add(eventPayload)
                .await()
        } catch (_: Exception) {}
    }

    fun isOffHours(inicio: String, fin: String, dias: List<Long>): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTimeMinutes = currentHour * 60 + currentMinute

        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        val isoDay = if (currentDay == Calendar.SUNDAY) 7L else (currentDay - 1).toLong()

        if (!dias.contains(isoDay)) return true

        val startParts = inicio.split(":").mapNotNull { it.toIntOrNull() }
        val endParts = fin.split(":").mapNotNull { it.toIntOrNull() }

        val startMinutes = if (startParts.size >= 2) startParts[0] * 60 + startParts[1] else 8 * 60
        val endMinutes = if (endParts.size >= 2) endParts[0] * 60 + endParts[1] else 19 * 60

        return currentTimeMinutes < startMinutes || currentTimeMinutes >= endMinutes
    }
}
