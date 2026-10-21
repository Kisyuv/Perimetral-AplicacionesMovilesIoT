package com.example.domain.usecase

import com.example.domain.model.AlarmStatus
import com.example.domain.repository.IAlarmRepository
import com.example.security.SecurityCodeManager

class DisarmAlarmUseCase(
    private val alarmRepository: IAlarmRepository,
    private val securityCodeManager: SecurityCodeManager
) {
    suspend operator fun invoke(pinInput: String): Boolean {
        if (securityCodeManager.validatePin(pinInput)) {
            alarmRepository.setAlarmStatus(AlarmStatus.DISARMED)
            return true
        }
        return false
    }
}
