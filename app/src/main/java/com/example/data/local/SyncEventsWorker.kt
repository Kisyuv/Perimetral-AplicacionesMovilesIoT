package com.example.data.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.PerimetralApplication

/** Sube los eventos pendientes de Room a Firestore cuando hay red (ver [com.example.data.repository.SiteAlarmRepository.syncPendingEvents]). */
class SyncEventsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as PerimetralApplication).container.siteAlarmRepository
        return if (repo.syncPendingEvents()) Result.success() else Result.retry()
    }
}
