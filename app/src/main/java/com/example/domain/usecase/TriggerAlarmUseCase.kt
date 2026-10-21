package com.example.domain.usecase

import com.example.domain.model.AlarmStatus
import com.example.domain.model.IntrusionEvent
import com.example.domain.repository.IAlarmRepository
import com.example.domain.repository.IEventsRepository

class TriggerAlarmUseCase(
    private val alarmRepository: IAlarmRepository,
    private val eventsRepository: IEventsRepository
) {
    suspend operator fun invoke(zoneId: String, zoneName: String, reason: String, isOffHours: Boolean = true) {
        alarmRepository.setAlarmStatus(AlarmStatus.ALARM)
        eventsRepository.recordEvent(
            IntrusionEvent(
                id = System.currentTimeMillis().toString(),
                zoneId = zoneId,
                zoneName = zoneName,
                timestamp = System.currentTimeMillis(),
                isOffHours = isOffHours,
                description = reason,
                severity = if (isOffHours) "CRITICA - FUERA DE HORARIO" else "ALERTA"
            )
        )
    }
}
