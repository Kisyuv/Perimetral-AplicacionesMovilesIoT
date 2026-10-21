package com.example.domain.usecase

import com.example.domain.model.AlarmStatus
import com.example.domain.repository.IAlarmRepository

class ArmAlarmUseCase(
    private val alarmRepository: IAlarmRepository
) {
    suspend operator fun invoke() {
        alarmRepository.setAlarmStatus(AlarmStatus.ARMED)
    }
}
