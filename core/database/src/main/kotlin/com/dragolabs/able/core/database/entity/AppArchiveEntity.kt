package com.dragolabs.able.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Able app archive (APP-004): one row per package that Able disabled or uninstalled for the
 * current user, so it can be restored even when Android no longer reports a label for it.
 *
 * [action] is the last Able action (`DISABLED` or `UNINSTALLED`). [priorEnabled] and
 * [priorInstalled] describe the package state before Able's first recorded action.
 */
@Entity(tableName = "app_archive")
data class AppArchiveEntity(
    @PrimaryKey
    val packageName: String,
    val label: String,
    val action: String,
    val timestampEpochMs: Long,
    val priorEnabled: Boolean,
    val priorInstalled: Boolean,
    val isSystem: Boolean,
)
