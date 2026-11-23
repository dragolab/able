package com.dragolabs.able.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dragolabs.able.core.database.entity.BatteryNoteEntity
import com.dragolabs.able.core.database.entity.BatterySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: BatterySessionEntity): Long

    @Query("SELECT * FROM battery_sessions ORDER BY startTimeEpochMs DESC")
    fun getAllSessions(): Flow<List<BatterySessionEntity>>

    /** Bulk insert (configuration import); returns the generated row ids in input order. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<BatterySessionEntity>): List<Long>

    @Query("SELECT * FROM battery_sessions WHERE isCharging = 1 ORDER BY startTimeEpochMs DESC LIMIT :limit")
    fun getChargingSessions(limit: Int = 30): Flow<List<BatterySessionEntity>>

    @Query("SELECT * FROM battery_sessions WHERE startTimeEpochMs >= :fromEpochMs ORDER BY startTimeEpochMs DESC")
    suspend fun getSessionsSince(fromEpochMs: Long): List<BatterySessionEntity>

    @Query("DELETE FROM battery_sessions WHERE endTimeEpochMs < :cutoffEpochMs")
    suspend fun deleteSessionsOlderThan(cutoffEpochMs: Long): Int

    @Query("DELETE FROM battery_notes WHERE sessionId NOT IN (SELECT id FROM battery_sessions)")
    suspend fun deleteOrphanedNotes(): Int

    @Query("DELETE FROM battery_sessions")
    suspend fun clearAllSessions(): Int

    // Notes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNote(note: BatteryNoteEntity)

    @Query("SELECT * FROM battery_notes WHERE sessionId = :sessionId")
    suspend fun getNoteForSession(sessionId: Long): BatteryNoteEntity?

    @Query("DELETE FROM battery_notes WHERE sessionId = :sessionId")
    suspend fun deleteNote(sessionId: Long): Int

    @Query("DELETE FROM battery_notes")
    suspend fun clearAllNotes(): Int

    @Query("SELECT * FROM battery_notes ORDER BY createdAtEpochMs DESC")
    fun getAllNotes(): Flow<List<BatteryNoteEntity>>

    /** Bulk insert (configuration import). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<BatteryNoteEntity>)
}
