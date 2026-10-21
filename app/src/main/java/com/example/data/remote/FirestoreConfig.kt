package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

object FirestoreConfig {

    /**
     * Inicializa y configura Firebase Firestore utilizando el ID de base de datos específico
     * y activando la persistencia offline mediante PersistentCacheSettings.
     */
    fun createFirestoreInstance(context: Context): FirebaseFirestore {
        val dbId = context.getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(dbId)

        val cacheSettings = PersistentCacheSettings.newBuilder()
            .setSizeBytes(100L * 1024 * 1024) // 100 MB de caché local offline
            .build()

        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(cacheSettings)
            .build()

        firestore.firestoreSettings = settings
        return firestore
    }
}
