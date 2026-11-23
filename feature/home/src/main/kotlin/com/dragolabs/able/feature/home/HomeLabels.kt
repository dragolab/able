package com.dragolabs.able.feature.home

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shortcut
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.core.model.BatteryState
import com.dragolabs.able.core.model.HomeCardType
import com.dragolabs.able.core.model.PlugType

@StringRes
internal fun HomeCardType.titleRes(): Int = when (this) {
    HomeCardType.BATTERY_PROFILE -> R.string.home_card_battery_title
    HomeCardType.QUICK_ACTIONS -> R.string.home_card_quick_actions_title
    HomeCardType.CAPABILITY_STATUS -> R.string.home_card_capabilities_title
    HomeCardType.STORAGE_SUMMARY -> R.string.home_card_storage_title
    HomeCardType.FAVORITE_SHORTCUTS -> R.string.home_card_shortcuts_title
    HomeCardType.RECENT_WARNINGS -> R.string.home_card_recent_title
    HomeCardType.SENSORS_OVERVIEW -> R.string.home_card_sensors_title
    HomeCardType.GEO_POINT_QUICK -> R.string.home_card_geo_title
}

@StringRes
internal fun HomeCardType.descriptionRes(): Int = when (this) {
    HomeCardType.BATTERY_PROFILE -> R.string.home_card_battery_desc
    HomeCardType.QUICK_ACTIONS -> R.string.home_card_quick_actions_desc
    HomeCardType.CAPABILITY_STATUS -> R.string.home_card_capabilities_desc
    HomeCardType.STORAGE_SUMMARY -> R.string.home_card_storage_desc
    HomeCardType.FAVORITE_SHORTCUTS -> R.string.home_card_shortcuts_desc
    HomeCardType.RECENT_WARNINGS -> R.string.home_card_recent_desc
    HomeCardType.SENSORS_OVERVIEW -> R.string.home_card_sensors_desc
    HomeCardType.GEO_POINT_QUICK -> R.string.home_card_geo_desc
}

internal fun HomeCardType.icon(): ImageVector = when (this) {
    HomeCardType.BATTERY_PROFILE -> Icons.Rounded.BatteryChargingFull
    HomeCardType.QUICK_ACTIONS -> Icons.Rounded.Bolt
    HomeCardType.CAPABILITY_STATUS -> Icons.Rounded.VerifiedUser
    HomeCardType.STORAGE_SUMMARY -> Icons.Rounded.SdStorage
    HomeCardType.FAVORITE_SHORTCUTS -> Icons.Rounded.Shortcut
    HomeCardType.RECENT_WARNINGS -> Icons.Rounded.History
    HomeCardType.SENSORS_OVERVIEW -> Icons.Rounded.Sensors
    HomeCardType.GEO_POINT_QUICK -> Icons.Rounded.Place
}

@StringRes
internal fun BatteryState.labelRes(): Int = when (this) {
    BatteryState.CHARGING -> R.string.home_battery_state_charging
    BatteryState.FULL -> R.string.home_battery_state_full
    BatteryState.DISCHARGING -> R.string.home_battery_state_discharging
    BatteryState.NOT_CHARGING -> R.string.home_battery_state_not_charging
    BatteryState.UNKNOWN -> R.string.home_battery_state_unknown
}

@StringRes
internal fun PlugType.labelRes(): Int? = when (this) {
    PlugType.NONE -> null
    PlugType.AC -> R.string.home_battery_plug_ac
    PlugType.USB -> R.string.home_battery_plug_usb
    PlugType.WIRELESS -> R.string.home_battery_plug_wireless
    PlugType.DOCK -> R.string.home_battery_plug_dock
}

@StringRes
internal fun ActionExecutionStatus.labelRes(): Int = when (this) {
    ActionExecutionStatus.ATTEMPTED -> R.string.home_action_status_attempted
    ActionExecutionStatus.SUCCESS -> R.string.home_action_status_success
    ActionExecutionStatus.SKIPPED -> R.string.home_action_status_skipped
    ActionExecutionStatus.FAILED -> R.string.home_action_status_failed
}

internal fun ActionExecutionStatus.statusLevel(): StatusLevel = when (this) {
    ActionExecutionStatus.ATTEMPTED -> StatusLevel.WARNING
    ActionExecutionStatus.SUCCESS -> StatusLevel.SUCCESS
    ActionExecutionStatus.SKIPPED -> StatusLevel.INFO
    ActionExecutionStatus.FAILED -> StatusLevel.CRITICAL
}
