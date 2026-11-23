package com.dragolabs.able.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dragolabs.able.core.database.entity.CustomTileMappingEntity
import com.dragolabs.able.core.database.entity.CustomWidgetConfigEntity
import com.dragolabs.able.core.database.entity.DnsProfileEntity
import com.dragolabs.able.core.database.entity.GpsPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GpsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: GpsPointEntity): Long

    @Query("SELECT * FROM gps_points ORDER BY timestampEpochMs DESC")
    fun getAllPoints(): Flow<List<GpsPointEntity>>

    @Query("SELECT * FROM gps_points WHERE id = :id")
    suspend fun getPointById(id: Long): GpsPointEntity?

    @Query("DELETE FROM gps_points WHERE id = :id")
    suspend fun deletePointById(id: Long): Int

    @Query("DELETE FROM gps_points")
    suspend fun clearAll(): Int
}

@Dao
interface DnsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<DnsProfileEntity>)

    @Query("SELECT * FROM dns_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<DnsProfileEntity>>

    @Query("DELETE FROM dns_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String): Int
}

@Dao
interface CustomTileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTile(tile: CustomTileMappingEntity)

    @Query("SELECT * FROM custom_tile_mappings ORDER BY slotIndex ASC")
    fun getAllTiles(): Flow<List<CustomTileMappingEntity>>

    @Query("SELECT * FROM custom_tile_mappings WHERE slotIndex = :slotIndex")
    suspend fun getTileBySlot(slotIndex: Int): CustomTileMappingEntity?
}

@Dao
interface CustomWidgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateWidget(widget: CustomWidgetConfigEntity)

    @Query("SELECT * FROM custom_widget_configs WHERE widgetId = :widgetId")
    suspend fun getWidgetById(widgetId: Int): CustomWidgetConfigEntity?

    @Query("DELETE FROM custom_widget_configs WHERE widgetId = :widgetId")
    suspend fun deleteWidgetById(widgetId: Int): Int
}
