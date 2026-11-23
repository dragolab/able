package com.dragolabs.able.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ShortcutTargetType {
    APPLICATION,
    ACTIVITY,
    FILE_OR_FOLDER,
    URL,
    ABLE_ACTION
}

@Serializable
enum class ShortcutIconType {
    DEFAULT,
    TEXT_OR_EMOJI,
    CUSTOM_GALLERY
}

@Serializable
data class ShortcutIconConfig(
    val iconType: ShortcutIconType = ShortcutIconType.DEFAULT,
    val iconValue: String? = null,
    val backgroundColorHex: String? = null
)

/**
 * Pinned or created shortcut definition (SHORT-001 - SHORT-003, ACT-001, ACT-002).
 */
@Serializable
data class ShortcutItem(
    val id: String,
    val label: String,
    val targetType: ShortcutTargetType,
    val targetData: String,
    val targetPackage: String? = null,
    val collectionId: String? = null,
    val iconConfig: ShortcutIconConfig = ShortcutIconConfig(),
    val sortOrder: Int = 0,
    val isPinnedToLauncher: Boolean = false,
    val createdAtEpochMs: Long
)

/**
 * Shortcut organization collection (SHORT-003, ADR-020).
 */
@Serializable
data class ShortcutCollection(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val createdAtEpochMs: Long
)

@Serializable
enum class CollectionDeletionPolicy {
    MOVE_TO_UNSORTED,
    DELETE_CONTAINED_SHORTCUTS
}
