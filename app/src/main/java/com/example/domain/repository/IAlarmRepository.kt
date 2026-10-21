package com.example.domain.repository

import com.example.domain.model.AlarmStatus
import kotlinx.coroutines.flow.Flow

interface IAlarmRepository {
    fun observeAlarmStatus(): Flow<AlarmStatus>
    suspend fun setAlarmStatus(status: AlarmStatus)
    suspend fun triggerOffHoursAlarm(zoneId: String, zoneName: String, reason: String)
}
