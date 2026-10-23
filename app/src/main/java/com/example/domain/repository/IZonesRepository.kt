package com.example.domain.repository

import com.example.domain.model.PerimeterZone
import kotlinx.coroutines.flow.Flow

interface IZonesRepository {
    fun observeZones(): Flow<List<PerimeterZone>>
    suspend fun updateZoneStatus(zoneId: String, isSecure: Boolean, isTampered: Boolean)
    suspend fun setZoneActive(zoneId: String, isActive: Boolean)
    suspend fun setAllZonesActive(isActive: Boolean)
}
