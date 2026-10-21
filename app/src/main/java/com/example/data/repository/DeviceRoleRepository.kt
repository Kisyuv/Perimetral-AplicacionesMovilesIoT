package com.example.data.repository

import com.example.data.remote.DeviceManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class DeviceRoleInfo(
    val role: String? = null,
    val model: String = "",
    val fcmToken: String = "",
    val deviceId: String = ""
)

data class ActiveNodeInfo(
    val exists: Boolean = false,
    val deviceId: String = "",
    val model: String = "",
    val userId: String = ""
)

class DeviceRoleRepository(
    private val firestore: FirebaseFirestore,
    private val deviceManager: DeviceManager
) {

    fun getLocalDeviceId(): String = deviceManager.getDeviceId()
    fun getLocalDeviceModel(): String = deviceManager.getDeviceModel()

    fun observeDeviceRole(userId: String): Flow<DeviceRoleInfo?> = callbackFlow {
        val deviceId = deviceManager.getDeviceId()
        val docRef = firestore.collection("users")
            .document(userId)
            .collection("dispositivos")
            .document(deviceId)

        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Si falla por red, usa el rol local en caché
                val cached = deviceManager.getCachedRole()
                trySend(if (cached != null) DeviceRoleInfo(role = cached, deviceId = deviceId) else null)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val rol = snapshot.getString("rol")
                val modelo = snapshot.getString("modelo") ?: deviceManager.getDeviceModel()
                val fcmToken = snapshot.getString("fcmToken") ?: ""
                deviceManager.saveCachedRole(rol)
                trySend(DeviceRoleInfo(role = rol, model = modelo, fcmToken = fcmToken, deviceId = deviceId))
            } else {
                val cached = deviceManager.getCachedRole()
                trySend(if (cached != null) DeviceRoleInfo(role = cached, deviceId = deviceId) else null)
            }
        }

        awaitClose { registration.remove() }
    }

    suspend fun checkExistingActiveNode(siteId: String = "bodega_central"): ActiveNodeInfo {
        return try {
            val doc = firestore.collection("nodos_activos").document(siteId).get().await()
            if (doc.exists() && doc.getBoolean("activo") == true) {
                val activeDeviceId = doc.getString("deviceId") ?: ""
                val model = doc.getString("modelo") ?: "Dispositivo desconocido"
                val ownerUserId = doc.getString("userId") ?: ""
                val isCurrentDevice = activeDeviceId == deviceManager.getDeviceId()

                ActiveNodeInfo(
                    exists = !isCurrentDevice, // Si es este mismo dispositivo, no cuenta como conflicto externo
                    deviceId = activeDeviceId,
                    model = model,
                    userId = ownerUserId
                )
            } else {
                ActiveNodeInfo(exists = false)
            }
        } catch (e: Exception) {
            ActiveNodeInfo(exists = false)
        }
    }

    suspend fun assignRole(
        userId: String,
        role: String,
        siteId: String = "bodega_central",
        forceReplaceNode: Boolean = false
    ): Result<Unit> {
        val deviceId = deviceManager.getDeviceId()
        val model = deviceManager.getDeviceModel()
        val fcmToken = deviceManager.getFcmToken()

        return try {
            if (role == "nodo") {
                // Verificar si ya existe otro nodo activo
                val existing = checkExistingActiveNode(siteId)
                if (existing.exists && !forceReplaceNode) {
                    return Result.failure(
                        IllegalStateException("NODE_ALREADY_EXISTS:${existing.model}")
                    )
                }

                // Registrar este dispositivo como el nodo activo único del sitio
                val nodePayload = mapOf(
                    "deviceId" to deviceId,
                    "userId" to userId,
                    "modelo" to model,
                    "activo" to true,
                    "ultimaConexion" to FieldValue.serverTimestamp()
                )
                firestore.collection("nodos_activos").document(siteId).set(nodePayload).await()
            } else if (role == "panel") {
                // Si este dispositivo antes era el nodo activo y cambia a panel, desregistrar de nodo activo
                try {
                    val nodeDoc = firestore.collection("nodos_activos").document(siteId).get().await()
                    if (nodeDoc.exists() && nodeDoc.getString("deviceId") == deviceId) {
                        firestore.collection("nodos_activos").document(siteId)
                            .update(mapOf("activo" to false)).await()
                    }
                } catch (_: Exception) {}
            }

            // Guardar en users/{uid}/dispositivos/{deviceId}
            val devicePayload = mapOf(
                "rol" to role,
                "modelo" to model,
                "fcmToken" to fcmToken,
                "ultimaConexion" to FieldValue.serverTimestamp()
            )

            firestore.collection("users")
                .document(userId)
                .collection("dispositivos")
                .document(deviceId)
                .set(devicePayload)
                .await()

            deviceManager.saveCachedRole(role)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearRole(userId: String) {
        val deviceId = deviceManager.getDeviceId()
        deviceManager.saveCachedRole(null)
        try {
            firestore.collection("users")
                .document(userId)
                .collection("dispositivos")
                .document(deviceId)
                .update(mapOf("rol" to null))
                .await()
        } catch (_: Exception) {}
    }
}
