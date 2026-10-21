package com.example.data.repository

import com.example.data.model.AlarmStateEntity
import com.example.data.remote.IFirebaseDataSource
import com.example.domain.model.AlarmStatus
import com.example.domain.repository.IAlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlarmRepositoryImpl(
    private val firebaseDataSource: IFirebaseDataSource
) : IAlarmRepository {

    private val _statusFlow = MutableStateFlow(AlarmStatus.DISARMED)

    override fun observeAlarmStatus(): Flow<AlarmStatus> = _statusFlow.asStateFlow()

    override suspend fun setAlarmStatus(status: AlarmStatus) {
        _statusFlow.value = status
        firebaseDataSource.saveAlarmState(
            AlarmStateEntity(
                id = "bodega_central",
                status = status.name,
                offHoursIntrusionDetected = (status == AlarmStatus.ALARM)
            )
        )
    }

    override suspend fun triggerOffHoursAlarm(zoneId: String, zoneName: String, reason: String) {
        setAlarmStatus(AlarmStatus.ALARM)
        firebaseDataSource.sendCloudAlert(
            title = "¡INTRUSIÓN FUERA DE HORARIO!",
            message = "Zona comprometida: $zoneName - $reason"
        )
    }
}
