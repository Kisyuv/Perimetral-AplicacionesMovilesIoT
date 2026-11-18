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

object PerimeterZones {
    val DEFAULTS = listOf(
        PerimeterZone(
            id = "ZN-01",
            name = "Cortina Metálica Principal",
            sector = "Acceso Carga / Descarga",
            sensorType = "Contacto Magnético Heavy-Duty",
            isSecure = true,
            isTampered = false,
            isActive = false
        ),
        PerimeterZone(
            id = "ZN-02",
            name = "Barrera Láser Perimetral Norte",
            sector = "Límite Exterior Norte",
            sensorType = "Haz Fotoeléctrico Doble",
            isSecure = true,
            isTampered = false,
            isActive = false
        ),
        PerimeterZone(
            id = "ZN-03",
            name = "Cerco Infrarrojo Sur",
            sector = "Perímetro Trasero Bodega",
            sensorType = "Sensor Infrarrojo Pasivo Exterior",
            isSecure = true,
            isTampered = false,
            isActive = false
        ),
        PerimeterZone(
            id = "ZN-04",
            name = "Puerta Acceso Peatonal Este",
            sector = "Control de Guardia",
            sensorType = "Cerradura Electromagnética / Contacto",
            isSecure = true,
            isTampered = false,
            isActive = false
        ),
        PerimeterZone(
            id = "ZN-05",
            name = "PIR Interior Pasillo Central",
            sector = "Nave Principal Almacén",
            sensorType = "Detector Volumétrico PIR 360°",
            isSecure = true,
            isTampered = false,
            isActive = false
        ),
        PerimeterZone(
            id = "ZN-06",
            name = "Ventanal Despacho Logístico",
            sector = "Oficinas Perimetrales",
            sensorType = "Detector de Rotura de Cristal",
            isSecure = true,
            isTampered = false,
            isActive = false
        )
    )

    /** Zona que cubre cada sensor físico o simulado. */
    private val SENSOR_ZONE = mapOf(
        "pir" to "ZN-05", // cámara del nodo / PIR simulado
        "reed" to "ZN-01" // contacto magnético de la cortina principal
    )

    fun zoneForSensor(sensorType: String): PerimeterZone? =
        SENSOR_ZONE[sensorType]?.let { id -> DEFAULTS.firstOrNull { it.id == id } }

    fun label(zone: PerimeterZone): String = "${zone.id} · ${zone.name}"
}
