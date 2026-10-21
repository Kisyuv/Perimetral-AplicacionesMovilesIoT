package com.example.domain.model

data class IntrusionEvent(
    val id: String = "",
    val zoneId: String = "",
    val zoneName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isOffHours: Boolean = true,
    val description: String = "",
    val severity: String = "CRITICA"
)
