package com.example.data.repository

import com.example.data.model.ZoneEntity
import com.example.data.remote.IFirebaseDataSource
import com.example.domain.model.PerimeterZone
import com.example.domain.model.PerimeterZones
import com.example.domain.repository.IZonesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ZonesRepositoryImpl(
    private val firebaseDataSource: IFirebaseDataSource
) : IZonesRepository {

    private val defaultZones = PerimeterZones.DEFAULTS

    private val _zonesFlow = MutableStateFlow(defaultZones)

    override fun observeZones(): Flow<List<PerimeterZone>> = _zonesFlow.asStateFlow()

    override suspend fun updateZoneStatus(zoneId: String, isSecure: Boolean, isTampered: Boolean) {
        val updated = _zonesFlow.value.map { zone ->
            if (zone.id == zoneId) {
                zone.copy(isSecure = isSecure, isTampered = isTampered, lastSignalTime = System.currentTimeMillis())
            } else {
                zone
            }
        }
        _zonesFlow.value = updated
        firebaseDataSource.updateZone(
            ZoneEntity(
                id = zoneId,
                isSecure = isSecure,
                isTampered = isTampered,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun setZoneActive(zoneId: String, isActive: Boolean) {
        val updated = _zonesFlow.value.map { zone ->
            if (zone.id == zoneId) {
                zone.copy(isActive = isActive, lastSignalTime = System.currentTimeMillis())
            } else {
                zone
            }
        }
        _zonesFlow.value = updated
    }

    override suspend fun setAllZonesActive(isActive: Boolean) {
        val updated = _zonesFlow.value.map { zone ->
            zone.copy(isActive = isActive, lastSignalTime = System.currentTimeMillis())
        }
        _zonesFlow.value = updated
    }
}
