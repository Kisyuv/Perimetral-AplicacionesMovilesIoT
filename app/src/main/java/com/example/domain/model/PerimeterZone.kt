package com.example.domain.model

data class PerimeterZone(
    val id: String = "",
    val name: String = "",
    val sector: String = "",
    val sensorType: String = "",
    val isSecure: Boolean = true,
    val isTampered: Boolean = false,
    val isActive: Boolean = false, // Desactivada por defecto
    val lastSignalTime: Long = System.currentTimeMillis()
)
