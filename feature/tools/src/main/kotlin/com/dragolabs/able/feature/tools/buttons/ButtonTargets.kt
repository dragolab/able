package com.dragolabs.able.feature.tools.buttons

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.applock.LauncherAppsSource

/**
 * Able catalog actions offered for button gestures. The ids are the stable Action Catalog ids
 * from `com.dragolabs.able.actions.ActionIds` in `:app` (this module cannot depend on `:app`);
 * keep them in sync. The service runs them through the catalog, which reads state back and
 * reports unsupported or blocked actions honestly.
 */
@Immutable
data class CatalogOption(val actionId: String, @StringRes val labelRes: Int, val icon: ImageVector)

object ButtonTargets {
    val catalogOptions: List<CatalogOption> = listOf(
        CatalogOption("torch", R.string.buttons_action_torch, Icons.Rounded.FlashlightOn),
        CatalogOption("screen_lock", R.string.buttons_action_screen_lock, Icons.Rounded.Lock),
        CatalogOption("ringer_mode", R.string.buttons_action_ringer, Icons.Rounded.NotificationsActive),
        CatalogOption("nfc", R.string.buttons_action_nfc, Icons.Rounded.Nfc),
        CatalogOption("auto_brightness", R.string.buttons_action_auto_brightness, Icons.Rounded.BrightnessAuto),
        CatalogOption("private_dns", R.string.buttons_action_private_dns, Icons.Rounded.Dns),
        CatalogOption("tool_level", R.string.buttons_action_level, Icons.Rounded.Straighten),
        CatalogOption("tool_compass", R.string.buttons_action_compass, Icons.Rounded.Explore),
        CatalogOption("tool_ruler", R.string.buttons_action_ruler, Icons.Rounded.Straighten),
        CatalogOption("gps_save", R.string.buttons_action_gps, Icons.Rounded.MyLocation),
        CatalogOption("hotspot_settings", R.string.buttons_action_hotspot, Icons.Rounded.WifiTethering),
        CatalogOption("tool_toggles", R.string.buttons_action_toggles, Icons.Rounded.Tune),
        CatalogOption("tool_activities", R.string.buttons_action_activities, Icons.Rounded.Window),
        CatalogOption("open_able", R.string.buttons_action_open_able, Icons.Rounded.Home),
    )

    private val byId = catalogOptions.associateBy { it.actionId }

    fun catalog(id: String): CatalogOption? = byId[id]

    fun icon(target: ButtonTarget): ImageVector = when (target) {
        is ButtonTarget.Catalog -> catalog(target.actionId)?.icon ?: Icons.Rounded.OpenInNew
        ButtonTarget.Camera -> Icons.Rounded.CameraAlt
        is ButtonTarget.App -> Icons.Rounded.Apps
        is ButtonTarget.Activity -> Icons.Rounded.Window
    }
}

@StringRes
fun ButtonGesture.titleRes(): Int = when (this) {
    ButtonGesture.VOLUME_BOTH -> R.string.buttons_gesture_both
    ButtonGesture.DOUBLE_UP -> R.string.buttons_gesture_double_up
    ButtonGesture.DOUBLE_DOWN -> R.string.buttons_gesture_double_down
    ButtonGesture.LONG_UP -> R.string.buttons_gesture_long_up
    ButtonGesture.LONG_DOWN -> R.string.buttons_gesture_long_down
}

@StringRes
fun ButtonGesture.hintRes(): Int = when (this) {
    ButtonGesture.VOLUME_BOTH -> R.string.buttons_gesture_both_hint
    ButtonGesture.DOUBLE_UP, ButtonGesture.DOUBLE_DOWN -> R.string.buttons_gesture_double_hint
    ButtonGesture.LONG_UP, ButtonGesture.LONG_DOWN -> R.string.buttons_gesture_long_hint
}

/** Human label for an assigned target; package names when an app is no longer installed. */
@Composable
fun targetLabel(target: ButtonTarget): String {
    val context = LocalContext.current
    return when (target) {
        is ButtonTarget.Catalog -> ButtonTargets.catalog(target.actionId)?.let { stringResource(it.labelRes) }
            ?: stringResource(R.string.buttons_target_unknown_action, target.actionId)
        ButtonTarget.Camera -> stringResource(R.string.buttons_target_camera)
        is ButtonTarget.App -> {
            val label = remember(target) { LauncherAppsSource(context).label(target.packageName) }
            label ?: stringResource(R.string.buttons_target_missing_app, target.packageName)
        }
        is ButtonTarget.Activity -> {
            val app = remember(target) { LauncherAppsSource(context).label(target.packageName) } ?: target.packageName
            stringResource(R.string.buttons_target_activity, target.className.substringAfterLast('.'), app)
        }
    }
}
