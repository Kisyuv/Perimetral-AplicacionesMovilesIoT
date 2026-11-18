package com.example.data.repository

import android.util.Log
import com.example.security.KeystoreManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Distribuye la clave de enlace P2P (WiFi local) entre los dispositivos de la
 * misma cuenta usando el documento users/{uid}. Con la clave en caché local, el enlace
 * sigue funcionando sin internet.
 */
class PeerKeyRepository(
    private val firestore: FirebaseFirestore,
    private val keystoreManager: KeystoreManager
) {

    suspend fun ensurePeerKey(userId: String): Boolean {
        val hadCache = keystoreManager.loadCachedPeerKey()
        return try {
            val userDoc = firestore.collection("users").document(userId)
            val key = firestore.runTransaction { tx ->
                val existing = tx.get(userDoc).getString(FIELD)
                if (existing != null) {
                    existing
                } else {
                    val fresh = keystoreManager.generatePeerKeyBase64()
                    tx.set(userDoc, mapOf(FIELD to fresh), SetOptions.merge())
                    fresh
                }
            }.await()
            keystoreManager.setPeerKey(key)
            true
        } catch (e: Exception) {
            Log.w("PeerKey", "Sin conexión para sincronizar la clave de enlace: ${e.message}")
            hadCache
        }
    }

    private companion object {
        const val FIELD = "claveEnlaceP2P"
    }
}
