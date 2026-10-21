package com.example.domain.model

data class WarehouseSchedule(
    val openingHour: Int = 7,
    val closingHour: Int = 19,
    val activeDays: List<Int> = listOf(1, 2, 3, 4, 5), // Lunes a Viernes
    val autoArmOffHours: Boolean = true
)
