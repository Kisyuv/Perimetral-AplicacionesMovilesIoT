package com.example.domain.repository

import com.example.domain.model.IntrusionEvent
import kotlinx.coroutines.flow.Flow

interface IEventsRepository {
    fun observeEvents(): Flow<List<IntrusionEvent>>
    suspend fun recordEvent(event: IntrusionEvent)
    suspend fun clearOldEvents()
}
