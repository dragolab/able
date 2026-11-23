package com.dragolabs.able.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dragolabs.able.core.model.ShortcutCollection
import com.dragolabs.able.core.model.ShortcutIconConfig
import com.dragolabs.able.core.model.ShortcutIconType
import com.dragolabs.able.core.model.ShortcutItem
import com.dragolabs.able.core.model.ShortcutTargetType

@Entity(tableName = "shortcuts")
data class ShortcutDefinitionEntity(
    @PrimaryKey
    val id: String,
    val label: String,
    val targetType: String,
    val targetData: String,
    val targetPackage: String?,
    val collectionId: String?,
    val iconType: String,
    val iconValue: String?,
    val iconBackgroundColorHex: String?,
    val sortOrder: Int,
    val isPinnedToLauncher: Boolean,
    val createdAtEpochMs: Long
) {
    fun toDomain(): ShortcutItem = ShortcutItem(
        id = id,
        label = label,
        targetType = ShortcutTargetType.valueOf(targetType),
        targetData = targetData,
        targetPackage = targetPackage,
        collectionId = collectionId,
        iconConfig = ShortcutIconConfig(
            iconType = ShortcutIconType.valueOf(iconType),
            iconValue = iconValue,
            backgroundColorHex = iconBackgroundColorHex
        ),
        sortOrder = sortOrder,
        isPinnedToLauncher = isPinnedToLauncher,
        createdAtEpochMs = createdAtEpochMs
    )

    companion object {
        fun fromDomain(domain: ShortcutItem): ShortcutDefinitionEntity = ShortcutDefinitionEntity(
            id = domain.id,
            label = domain.label,
            targetType = domain.targetType.name,
            targetData = domain.targetData,
            targetPackage = domain.targetPackage,
            collectionId = domain.collectionId,
            iconType = domain.iconConfig.iconType.name,
            iconValue = domain.iconConfig.iconValue,
            iconBackgroundColorHex = domain.iconConfig.backgroundColorHex,
            sortOrder = domain.sortOrder,
            isPinnedToLauncher = domain.isPinnedToLauncher,
            createdAtEpochMs = domain.createdAtEpochMs
        )
    }
}

@Entity(tableName = "shortcut_collections")
data class ShortcutCollectionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAtEpochMs: Long
) {
    fun toDomain(): ShortcutCollection = ShortcutCollection(
        id = id,
        name = name,
        sortOrder = sortOrder,
        createdAtEpochMs = createdAtEpochMs
    )

    companion object {
        fun fromDomain(domain: ShortcutCollection): ShortcutCollectionEntity = ShortcutCollectionEntity(
            id = domain.id,
            name = domain.name,
            sortOrder = domain.sortOrder,
            createdAtEpochMs = domain.createdAtEpochMs
        )
    }
}
