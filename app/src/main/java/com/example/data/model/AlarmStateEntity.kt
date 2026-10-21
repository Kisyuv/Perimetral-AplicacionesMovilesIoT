package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class AlarmStateEntity(
    @DocumentId val id: String = "bodega_central",
    val status: String = "DISARMED",
    val armedBy: String = "",
    val offHoursIntrusionDetected: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ZoneEntity(
    @DocumentId val id: String = "",
    val name: String = "",
    val sector: String = "",
    val sensorType: String = "",
    val isSecure: Boolean = true,
    val isTampered: Boolean = false,
    val timestamp: Long = 0L
)

@IgnoreExtraProperties
data class IntrusionEventEntity(
    @DocumentId val id: String = "",
    val zoneId: String = "",
    val zoneName: String = "",
    val timestamp: Long = 0L,
    val isOffHours: Boolean = true,
    val description: String = "",
    val severity: String = "CRITICA",
    @ServerTimestamp val createdAt: Timestamp? = null
)
