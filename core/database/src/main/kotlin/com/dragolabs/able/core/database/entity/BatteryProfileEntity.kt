package com.dragolabs.able.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One user-editable battery profile (owner decision 2026-11-22). Triggers and app rules are
 * stored as JSON written and read by `:core:data` (`BatteryProfileMapper`), so this module needs
 * no serialization plugin.
 *
 * [builtInType] is the `ProfileType` name of a built-in profile, or null for a custom one.
 */
@Entity(tableName = "battery_profiles")
data class BatteryProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val builtInType: String?,
    val enabled: Boolean,
    val manualActive: Boolean,
    val triggersJson: String,
    val appRulesJson: String,
    val offerBatterySaver: Boolean,
    val sortOrder: Int,
    val updatedAtEpochMs: Long,
)
