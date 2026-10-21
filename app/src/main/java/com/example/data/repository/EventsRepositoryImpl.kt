package com.example.data.repository

import com.example.data.model.IntrusionEventEntity
import com.example.data.remote.IFirebaseDataSource
import com.example.domain.model.IntrusionEvent
import com.example.domain.repository.IEventsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class EventsRepositoryImpl(
    private val firebaseDataSource: IFirebaseDataSource
) : IEventsRepository {

    private val initialEvents = listOf(
        IntrusionEvent(
            id = "EV-101",
            zoneId = "ZN-02",
            zoneName = "Barrera Láser Perimetral Norte",
            timestamp = System.currentTimeMillis() - 3600_000L * 2,
            isOffHours = true,
            description = "Interrupción de haz óptico fuera del horario operativo.",
            severity = "CRITICA"
        ),
        IntrusionEvent(
            id = "EV-100",
            zoneId = "ZN-01",
            zoneName = "Cortina Metálica Principal",
            timestamp = System.currentTimeMillis() - 3600_000L * 5,
            isOffHours = false,
            description = "Apertura autorizada para recepción de turno.",
            severity = "INFO"
        )
    )

    private val _eventsFlow = MutableStateFlow(initialEvents)

    override fun observeEvents(): Flow<List<IntrusionEvent>> = _eventsFlow.asStateFlow()

    override suspend fun recordEvent(event: IntrusionEvent) {
        val current = _eventsFlow.value.toMutableList()
        current.add(0, event)
        _eventsFlow.value = current

        firebaseDataSource.logIntrusionEvent(
            IntrusionEventEntity(
                id = event.id,
                zoneId = event.zoneId,
                zoneName = event.zoneName,
                timestamp = event.timestamp,
                isOffHours = event.isOffHours,
                description = event.description,
                severity = event.severity
            )
        )
    }

    override suspend fun clearOldEvents() {
        _eventsFlow.value = emptyList()
    }
}
