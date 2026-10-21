package com.example.data.repository

import com.example.data.model.ZoneEntity
import com.example.data.remote.IFirebaseDataSource
import com.example.domain.model.PerimeterZone
import com.example.domain.repository.IZonesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ZonesRepositoryImpl(
    private val firebaseDataSource: IFirebaseDataSource
) : IZonesRepository {

    private val defaultZones = listOf(
        PerimeterZone(
            id = "ZN-01",
            name = "Cortina Metálica Principal",
            sector = "Acceso Carga / Descarga",
            sensorType = "Contacto Magnético Heavy-Duty",
            isSecure = true,
            isTampered = false
        ),
        PerimeterZone(
            id = "ZN-02",
            name = "Barrera Láser Perimetral Norte",
            sector = "Límite Exterior Norte",
            sensorType = "Haz Fotoeléctrico Doble",
            isSecure = true,
            isTampered = false
        ),
        PerimeterZone(
            id = "ZN-03",
            name = "Cerco Infrarrojo Sur",
            sector = "Perímetro Trasero Bodega",
            sensorType = "Sensor Infrarrojo Pasivo Exterior",
            isSecure = true,
            isTampered = false
        ),
        PerimeterZone(
            id = "ZN-04",
            name = "Puerta Acceso Peatonal Este",
            sector = "Control de Guardia",
            sensorType = "Cerradura Electromagnética / Contacto",
            isSecure = true,
            isTampered = false
        ),
        PerimeterZone(
            id = "ZN-05",
            name = "PIR Interior Pasillo Central",
            sector = "Nave Principal Almacén",
            sensorType = "Detector Volumétrico PIR 360°",
            isSecure = true,
            isTampered = false
        ),
        PerimeterZone(
            id = "ZN-06",
            name = "Ventanal Despacho Logístico",
            sector = "Oficinas Perimetrales",
            sensorType = "Detector de Rotura de Cristal",
            isSecure = true,
            isTampered = false
        )
    )

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
}
