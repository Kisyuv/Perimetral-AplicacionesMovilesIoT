package com.example.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.PendingEvent
import com.example.data.local.PendingEventDao
import com.example.data.local.SyncEventsWorker
import com.example.data.model.SecurityEvent
import com.example.data.model.SiteAlarmState
import com.example.data.model.SiteCameraConfig
import com.example.data.model.SiteSensorState
import com.example.domain.model.PerimeterZones
import com.example.security.PinSecurity
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.Calendar

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SiteAlarmRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val context: Context,
    private val pendingEvents: PendingEventDao
) {

    /**
     * uid de la sesión actual. Las escuchas se crean (y se recrean) solo con sesión iniciada:
     * una escucha abierta sin sesión recibe PERMISSION_DENIED y Firestore no la reintenta nunca.
     */
    private val sessionUid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    private fun <T> whenSignedIn(signedOut: T, block: () -> Flow<T>): Flow<T> =
        sessionUid.flatMapLatest { uid -> if (uid == null) flowOf(signedOut) else block() }

    private fun <T> Task<T>.logFailure(what: String): Task<T> = addOnFailureListener { e ->
        Log.w("SiteAlarmRepository", "Escritura rechazada ($what): ${e.message}")
    }

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
                firestore.collection("sitios").document(siteId).set(initialSite).logFailure("set")
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
                    ).logFailure("update")
            } else {
                firestore.collection("sitios").document(siteId)
                    .collection("sensores").document("estado")
                    .update(
                        mapOf(
                            "pirMovimiento" to false,
                            "reedPuertaAbierta" to false
                        )
                    ).logFailure("update")
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
                    ).logFailure("update")
            }
        } catch (_: Exception) {}
    }

    fun observeSiteState(siteId: String = "bodega_central"): Flow<SiteAlarmState> = whenSignedIn(_localSiteState.value) {
        callbackFlow {
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
    }

    fun observeSensorState(siteId: String = "bodega_central"): Flow<SiteSensorState> = whenSignedIn(SiteSensorState()) {
        callbackFlow {
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
    }

    fun observeCameraConfig(siteId: String = "bodega_central"): Flow<SiteCameraConfig> = whenSignedIn(SiteCameraConfig()) {
        callbackFlow {
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
    }

    fun observeEvents(siteId: String = "bodega_central", limitCount: Long = 30): Flow<List<SecurityEvent>> = whenSignedIn(emptyList()) {
        callbackFlow {
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
    }

    suspend fun armSystem(siteId: String = "bodega_central", origen: String = "panel", eventId: String? = null): Result<Unit> {
        _localSiteState.value = _localSiteState.value.copy(
            estado = "armada",
            sirenaActiva = false,
            estroboActivo = false,
            zonaAlarma = ""
        )
        val activeZones = _localSiteState.value.zonasActivas.size
        return try {
            firestore.collection("sitios").document(siteId).set(
                mapOf(
                    "estado" to "armada",
                    "sirenaActiva" to false,
                    "estroboActivo" to false,
                    "zonaAlarma" to "",
                    "ultimaActualizacion" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).logFailure("armar sitio")

            resetSensors(siteId)
            val zonesNote = if (activeZones == 0) " (sin zonas activas: ningún sensor disparará la alarma)" else " ($activeZones zonas activas)"
            logEvent(siteId, "armado", "Sistema armado por operador$zonesNote", origen, eventId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disarmSystem(siteId: String = "bodega_central", pinInput: String, origen: String = "panel", eventId: String? = null): Result<Unit> {
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
                estroboActivo = false,
                zonaAlarma = ""
            )

            firestore.collection("sitios").document(siteId).set(
                mapOf(
                    "estado" to "desarmada",
                    "sirenaActiva" to false,
                    "estroboActivo" to false,
                    "zonaAlarma" to "",
                    "ultimaActualizacion" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).logFailure("desarmar sitio")

            resetSensors(siteId)
            logEvent(siteId, "desarmado", "Sistema desarmado mediante PIN autorizado", origen, eventId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun silenceActuators(
        siteId: String = "bodega_central",
        pinInput: String,
        silenceSiren: Boolean,
        turnOffStrobe: Boolean,
        eventId: String? = null
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

            firestore.collection("sitios").document(siteId).update(updates).logFailure("silenciar actuadores")
            logEvent(siteId, "alarma", "Actuadores silenciados manualmente con PIN", "panel", eventId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordSensorTrigger(
        siteId: String = "bodega_central",
        sensorType: String, // "pir" | "reed"
        detail: String,
        origen: String = "nodo",
        eventId: String? = null,
        zoneId: String? = null
    ) {
        try {
            val now = System.currentTimeMillis()
            val siteDoc = firestore.collection("sitios").document(siteId).get().await()
            val estadoActual = siteDoc.getString("estado") ?: "desarmada"
            @Suppress("UNCHECKED_CAST")
            val activeZones = siteDoc.get("zonasActivas") as? List<String> ?: emptyList()
            val zone = zoneId?.let { id -> PerimeterZones.DEFAULTS.firstOrNull { it.id == id } }
                ?: PerimeterZones.zoneForSensor(sensorType)
            val zoneLabel = zone?.let { PerimeterZones.label(it) } ?: "zona sin asignar"
            val zoneActive = zone != null && zone.id in activeZones

            // Actualizar telemetría de sensores
            val sensorUpdates = mutableMapOf<String, Any>()
            if (sensorType == "pir") {
                sensorUpdates["pirMovimiento"] = true
                sensorUpdates["ultimoPir"] = now
            } else if (sensorType == "reed") {
                sensorUpdates["reedPuertaAbierta"] = true
                sensorUpdates["ultimoReed"] = now
            }
            if (sensorUpdates.isNotEmpty()) {
                firestore.collection("sitios").document(siteId)
                    .collection("sensores").document("estado")
                    .update(sensorUpdates).logFailure("telemetría de sensor")
            }

            // Solo dispara si está armada y el sensor pertenece a una zona activa
            if (estadoActual.equals("armada", ignoreCase = true) && zoneActive) {
                firestore.collection("sitios").document(siteId).update(
                    mapOf(
                        "estado" to "alarma",
                        "sirenaActiva" to true,
                        "estroboActivo" to true,
                        "zonaAlarma" to zoneLabel,
                        "ultimaActualizacion" to FieldValue.serverTimestamp()
                    )
                ).logFailure("disparo de alarma")

                logEvent(siteId, "alarma", "¡INTRUSIÓN en $zoneLabel! $detail", origen, eventId)
            } else if (estadoActual.equals("armada", ignoreCase = true)) {
                logEvent(siteId, sensorType, "$detail — $zoneLabel desactivada, no se dispara la alarma", origen, eventId)
            } else {
                // Si está desarmada, solo registrar el evento sin disparar alarma
                logEvent(siteId, sensorType, "$detail ($zoneLabel)", origen, eventId)
            }

            // Auto-restablecer el pulso del sensor a inactivo después de 4 segundos
            repoScope.launch {
                delay(4000L)
                resetSensorStatus(siteId, resetPir = (sensorType == "pir"), resetReed = (sensorType == "reed"))
            }
        } catch (_: Exception) {}
    }

    /** Simulación de una zona concreta; ZN-05 y ZN-01 además encienden su indicador de sensor. */
    suspend fun simulateZoneTrigger(siteId: String = "bodega_central", zoneId: String, origen: String, eventId: String? = null) {
        val sensorType = when (zoneId) {
            "ZN-05" -> "pir"
            "ZN-01" -> "reed"
            else -> "zona"
        }
        recordSensorTrigger(siteId, sensorType, "Activación simulada por operador", origen, eventId, zoneId)
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
                .update(updates).logFailure("update")
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
            ).logFailure("horario")
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
                ).logFailure("update")
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
                .update(mapOf("estadoCamara" to estadoCamara)).logFailure("update")
        } catch (_: Exception) {}
    }

    /**
     * Con eventId (el id del comando) el mismo evento escrito por panel y nodo queda en un único
     * documento, aunque el comando llegue por la nube y por el enlace local.
     */
    suspend fun logEvent(siteId: String = "bodega_central", tipo: String, detalle: String, origen: String, eventId: String? = null, timestamp: Long = System.currentTimeMillis()) {
        try {
            // Primero a Room: el evento sobrevive sin red o con la app cerrada, y el Worker lo sube después.
            pendingEvents.insert(
                PendingEvent(eventId ?: UUID.randomUUID().toString(), siteId, tipo, detalle, origen, timestamp)
            )
            enqueueSync()
        } catch (e: Exception) {
            Log.w("SiteAlarmRepo", "No se pudo guardar el evento $tipo: ${e.message}")
        }
    }

    /**
     * Encola la subida de pendientes; se ejecuta en cuanto haya red. Seguro llamarlo varias veces.
     * REPLACE: un trabajo nuevo reemplaza al que espera un reintento (con APPEND quedaría detrás de su espera
     * creciente, que llega a horas). Cortar una subida en curso no pierde nada: se borra de Room solo tras confirmar.
     */
    fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<SyncEventsWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync-eventos", ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Sube los pendientes con `document(id).set(...)` (idempotente) y los borra de Room.
     * Aquí sí se espera la confirmación del servidor: borrar antes perdería el evento si la subida falla.
     * Devuelve false si falta algo por subir, para que WorkManager reintente con espera creciente.
     */
    suspend fun syncPendingEvents(): Boolean {
        if (auth.currentUser == null) return false
        for (e in pendingEvents.all()) {
            try {
                val payload = mapOf("tipo" to e.tipo, "detalle" to e.detalle, "origen" to e.origen, "timestamp" to e.timestamp)
                withTimeout(20_000) {
                    firestore.collection("sitios").document(e.siteId).collection("eventos").document(e.id).set(payload).await()
                }
                pendingEvents.delete(e.id)
            } catch (ex: FirebaseFirestoreException) {
                if (ex.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.w("SiteAlarmRepo", "No se pudo subir el evento ${e.id}: ${ex.message}")
                    return false
                }
                // Las reglas solo permiten crear eventos: el documento ya existe (otro panel lo subió o una subida
                // anterior sí llegó) o el evento no es válido. Reintentar no sirve y bloquearía la cola.
                Log.w("SiteAlarmRepo", "Evento ${e.id} rechazado por las reglas (ya existe o no es válido); se descarta")
                pendingEvents.delete(e.id)
            } catch (ex: Exception) {
                Log.w("SiteAlarmRepo", "No se pudo subir el evento ${e.id}: ${ex.message}")
                return false
            }
        }
        return true
    }

    fun setZoneActive(siteId: String = "bodega_central", zoneId: String, active: Boolean, origen: String, eventId: String? = null) {
        val current = _localSiteState.value.zonasActivas
        _localSiteState.value = _localSiteState.value.copy(
            zonasActivas = if (active) (current + zoneId).distinct() else current - zoneId
        )
        firestore.collection("sitios").document(siteId).update(
            "zonasActivas",
            if (active) FieldValue.arrayUnion(zoneId) else FieldValue.arrayRemove(zoneId)
        ).logFailure("zona $zoneId")
        val label = PerimeterZones.DEFAULTS.firstOrNull { it.id == zoneId }?.let { PerimeterZones.label(it) } ?: zoneId
        repoScope.launch {
            logEvent(siteId, "config", if (active) "Zona activada: $label" else "Zona desactivada: $label", origen, eventId)
        }
    }

    fun setAllZonesActive(siteId: String = "bodega_central", active: Boolean, origen: String, eventId: String? = null) {
        val ids = if (active) PerimeterZones.DEFAULTS.map { it.id } else emptyList()
        _localSiteState.value = _localSiteState.value.copy(zonasActivas = ids)
        firestore.collection("sitios").document(siteId).update("zonasActivas", ids).logFailure("todas las zonas")
        repoScope.launch {
            logEvent(siteId, "config", if (active) "Todas las zonas activadas" else "Todas las zonas desactivadas", origen, eventId)
        }
    }

    fun updateCameraLens(siteId: String = "bodega_central", lente: String) {
        firestore.collection("sitios").document(siteId)
            .collection("config").document("general")
            .update(mapOf("lente" to lente)).logFailure("update")
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
