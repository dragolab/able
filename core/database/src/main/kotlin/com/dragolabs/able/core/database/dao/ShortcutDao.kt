package com.dragolabs.able.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.dragolabs.able.core.database.entity.ShortcutCollectionEntity
import com.dragolabs.able.core.database.entity.ShortcutDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ShortcutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertShortcut(shortcut: ShortcutDefinitionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertShortcuts(shortcuts: List<ShortcutDefinitionEntity>)

    @Query("SELECT * FROM shortcuts ORDER BY sortOrder ASC")
    abstract fun getAllShortcuts(): Flow<List<ShortcutDefinitionEntity>>

    @Query("SELECT * FROM shortcuts WHERE collectionId = :collectionId ORDER BY sortOrder ASC")
    abstract fun getShortcutsByCollection(collectionId: String): Flow<List<ShortcutDefinitionEntity>>

    @Query("SELECT * FROM shortcuts WHERE collectionId IS NULL ORDER BY sortOrder ASC")
    abstract fun getUnsortedShortcuts(): Flow<List<ShortcutDefinitionEntity>>

    @Query("DELETE FROM shortcuts WHERE id = :id")
    abstract suspend fun deleteShortcutById(id: String): Int

    @Query("UPDATE shortcuts SET collectionId = NULL WHERE collectionId = :collectionId")
    abstract suspend fun moveShortcutsToUnsorted(collectionId: String): Int

    @Query("DELETE FROM shortcuts WHERE collectionId = :collectionId")
    abstract suspend fun deleteShortcutsInCollection(collectionId: String): Int

    // Collections
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCollection(collection: ShortcutCollectionEntity)

    @Query("SELECT * FROM shortcut_collections ORDER BY sortOrder ASC")
    abstract fun getAllCollections(): Flow<List<ShortcutCollectionEntity>>

    @Query("DELETE FROM shortcut_collections WHERE id = :collectionId")
    abstract suspend fun deleteCollectionById(collectionId: String): Int

    @Transaction
    open suspend fun deleteCollectionAndMoveShortcuts(collectionId: String) {
        moveShortcutsToUnsorted(collectionId)
        deleteCollectionById(collectionId)
    }

    @Transaction
    open suspend fun deleteCollectionAndShortcuts(collectionId: String) {
        deleteShortcutsInCollection(collectionId)
        deleteCollectionById(collectionId)
    }
}
