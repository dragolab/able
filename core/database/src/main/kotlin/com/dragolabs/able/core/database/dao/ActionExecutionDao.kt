package com.dragolabs.able.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dragolabs.able.core.database.entity.ActionExecutionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionExecutionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ActionExecutionEntity): Long

    @Query("SELECT * FROM action_audit_log ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun getRecentActions(limit: Int = 50): Flow<List<ActionExecutionEntity>>

    @Query("SELECT * FROM action_audit_log WHERE status = 'FAILED' ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun getRecentFailures(limit: Int = 20): Flow<List<ActionExecutionEntity>>

    @Query("DELETE FROM action_audit_log WHERE timestampEpochMs < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long): Int

    @Query("DELETE FROM action_audit_log")
    suspend fun clearAll(): Int
}
