package com.dragolabs.able.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dragolabs.able.core.database.entity.HomeCardConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeCardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCards(cards: List<HomeCardConfigEntity>)

    @Query("SELECT * FROM home_card_configs ORDER BY displayOrder ASC")
    fun getAllCards(): Flow<List<HomeCardConfigEntity>>

    @Query("SELECT * FROM home_card_configs WHERE isVisible = 1 ORDER BY displayOrder ASC")
    fun getVisibleCards(): Flow<List<HomeCardConfigEntity>>

    @Query("DELETE FROM home_card_configs")
    suspend fun clearAll()
}
