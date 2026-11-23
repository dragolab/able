package com.dragolabs.able.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.dragolabs.able.core.database.entity.BatteryProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryProfileDao {
    @Query("SELECT * FROM battery_profiles ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<BatteryProfileEntity>>

    @Query("SELECT * FROM battery_profiles ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<BatteryProfileEntity>

    @Query("SELECT * FROM battery_profiles WHERE id = :id LIMIT 1")
    suspend fun get(id: String): BatteryProfileEntity?

    @Query("SELECT COUNT(*) FROM battery_profiles")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM battery_profiles")
    suspend fun maxSortOrder(): Int

    /** Seeding: never overwrites a row that already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(profiles: List<BatteryProfileEntity>)

    @Upsert
    suspend fun upsert(profile: BatteryProfileEntity)

    @Query("UPDATE battery_profiles SET enabled = :enabled, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    @Query("UPDATE battery_profiles SET manualActive = :active, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun setManualActive(id: String, active: Boolean, now: Long)

    /** Deletes a custom profile; built-in rows are never deleted. Returns the number of removed rows. */
    @Query("DELETE FROM battery_profiles WHERE id = :id AND builtInType IS NULL")
    suspend fun deleteCustom(id: String): Int
}
