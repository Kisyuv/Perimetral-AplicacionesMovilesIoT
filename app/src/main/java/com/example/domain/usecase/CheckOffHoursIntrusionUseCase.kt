package com.example.domain.usecase

import com.example.domain.model.WarehouseSchedule
import java.util.Calendar

class CheckOffHoursIntrusionUseCase {
    operator fun invoke(schedule: WarehouseSchedule = WarehouseSchedule()): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        // Convert Calendar day (Sunday = 1, Monday = 2, ...) to ISO day (Monday = 1, ... Sunday = 7)
        val isoDay = when (currentDayOfWeek) {
            Calendar.SUNDAY -> 7
            else -> currentDayOfWeek - 1
        }

        // Es fin de semana o fuera del horario laboral configurado
        val isWorkDay = schedule.activeDays.contains(isoDay)
        val isWorkingHours = currentHour in schedule.openingHour until schedule.closingHour

        return !(isWorkDay && isWorkingHours)
    }
}
