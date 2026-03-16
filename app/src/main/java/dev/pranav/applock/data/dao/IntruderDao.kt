package dev.pranav.applock.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.pranav.applock.data.model.IntruderLog
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [IntruderLog] operations.
 */
@Dao
interface IntruderDao {

    /** Insert a new intruder log entry and return its auto-generated id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: IntruderLog): Long

    /** Update an existing log entry (used to fill in media paths after capture). */
    @Update
    suspend fun update(log: IntruderLog)

    /** Observe all logs ordered by most recent first. */
    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<IntruderLog>>

    /** One-shot fetch of all logs, newest first. */
    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsOnce(): List<IntruderLog>

    /** Delete all recorded intruder logs. */
    @Query("DELETE FROM intruder_logs")
    suspend fun deleteAll()
}
