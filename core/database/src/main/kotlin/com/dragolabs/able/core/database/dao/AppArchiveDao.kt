package com.dragolabs.able.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.dragolabs.able.core.database.entity.AppArchiveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppArchiveDao {
    @Query("SELECT * FROM app_archive ORDER BY timestampEpochMs DESC")
    fun observeAll(): Flow<List<AppArchiveEntity>>

    @Query("SELECT * FROM app_archive WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): AppArchiveEntity?

    @Upsert
    suspend fun upsert(entry: AppArchiveEntity)

    @Query("DELETE FROM app_archive WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM app_archive WHERE packageName IN (:packageNames)")
    suspend fun deleteAll(packageNames: List<String>)
}
