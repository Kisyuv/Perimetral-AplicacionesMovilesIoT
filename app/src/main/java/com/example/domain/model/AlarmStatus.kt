package com.example.domain.model

enum class AlarmStatus(val title: String, val description: String) {
    DISARMED("DESARMADA", "Acceso libre permitido. Perímetro en monitoreo pasivo."),
    ARMED("ARMADA", "Vigilancia activa. Detección perimetral de intrusión habilitada."),
    ALARM("ALARMA", "¡Intrusión detectada! Protocolo de emergencia de bodega activado.")
}
