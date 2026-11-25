package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/** Evento de seguridad aún no subido a Firestore. La clave es el id del evento: reinsertarlo no lo duplica. */
@Entity(tableName = "eventos_pendientes")
data class PendingEvent(
    @PrimaryKey val id: String,
    val siteId: String,
    val tipo: String,
    val detalle: String,
    val origen: String,
    val timestamp: Long
)

@Dao
interface PendingEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: PendingEvent)

    @Query("SELECT * FROM eventos_pendientes ORDER BY timestamp")
    suspend fun all(): List<PendingEvent>

    @Query("DELETE FROM eventos_pendientes WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [PendingEvent::class], version = 1, exportSchema = false)
abstract class PendingEventDb : RoomDatabase() {
    abstract fun pendingEvents(): PendingEventDao
}
