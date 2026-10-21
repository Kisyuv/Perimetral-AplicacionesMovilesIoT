package com.example.data.remote

import com.example.data.model.AlarmStateEntity
import com.example.data.model.IntrusionEventEntity
import com.example.data.model.ZoneEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface IFirebaseDataSource {
    fun streamAlarmState(): Flow<AlarmStateEntity>
    suspend fun saveAlarmState(entity: AlarmStateEntity)
    fun streamZones(): Flow<List<ZoneEntity>>
    suspend fun updateZone(entity: ZoneEntity)
    fun streamIntrusionEvents(): Flow<List<IntrusionEventEntity>>
    suspend fun logIntrusionEvent(entity: IntrusionEventEntity)
    suspend fun sendCloudAlert(title: String, message: String)
}

class FirebaseDataSource(
    private val firestore: FirebaseFirestore
) : IFirebaseDataSource {

    override fun streamAlarmState(): Flow<AlarmStateEntity> = flow {
        // Estructura lista para snapshot listener de Firestore
        emit(AlarmStateEntity(id = "bodega_central", status = "DISARMED"))
    }

    override suspend fun saveAlarmState(entity: AlarmStateEntity) {
        // Guardado con persistencia offline de Firestore
    }

    override fun streamZones(): Flow<List<ZoneEntity>> = flow {
        emit(emptyList())
    }

    override suspend fun updateZone(entity: ZoneEntity) {
        // Actualización de zona en Firestore
    }

    override fun streamIntrusionEvents(): Flow<List<IntrusionEventEntity>> = flow {
        emit(emptyList())
    }

    override suspend fun logIntrusionEvent(entity: IntrusionEventEntity) {
        // Registro de evento de intrusión en Firestore
    }

    override suspend fun sendCloudAlert(title: String, message: String) {
        // Simulación / integración de Firebase Cloud Messaging
    }
}
