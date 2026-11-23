package com.dragolabs.able.feature.tools.shortcuts

import com.dragolabs.able.core.data.shortcut.ShortcutTarget
import com.dragolabs.able.core.data.shortcut.meta
import com.dragolabs.able.core.model.ShortcutItem

// Shortcut Maker-only definitions. The stored-target codec ([ShortcutTarget], `encode`,
// `decodeTarget`), URL/route validation and the icon metadata ([com.dragolabs.able.core.data.shortcut.ShortcutMeta])
// live in `:core:data` (`com.dragolabs.able.core.data.shortcut`) so Home decodes and launches
// definitions the same way.

/** Target categories offered in the editor's first step (SHORT-002). */
enum class TargetKind { APP, ACTIVITY, SETTINGS, FILE, FOLDER, WEBSITE, ABLE }

val ShortcutTarget.kind: TargetKind
    get() = when (this) {
        is ShortcutTarget.App -> TargetKind.APP
        is ShortcutTarget.Activity -> TargetKind.ACTIVITY
        is ShortcutTarget.SettingsPage -> TargetKind.SETTINGS
        is ShortcutTarget.Document -> if (isFolder) TargetKind.FOLDER else TargetKind.FILE
        is ShortcutTarget.Website -> TargetKind.WEBSITE
        is ShortcutTarget.AbleAction -> TargetKind.ABLE
    }

// ---------------------------------------------------------------------------------------------
// Android settings pages
// ---------------------------------------------------------------------------------------------

/**
 * Curated `Settings.ACTION_*` destinations (string values, so older constants do not need API
 * checks). The editor shows only those that resolve on this device ([availableSettingsActions]).
 */
internal val curatedSettingsActions = listOf(
    "android.settings.SETTINGS",
    "android.settings.WIFI_SETTINGS",
    "android.settings.BLUETOOTH_SETTINGS",
    "android.settings.panel.action.INTERNET_CONNECTIVITY",
    "android.settings.AIRPLANE_MODE_SETTINGS",
    "android.settings.DATA_USAGE_SETTINGS",
    "android.settings.NFC_SETTINGS",
    "android.settings.CAST_SETTINGS",
    "android.settings.VPN_SETTINGS",
    "android.settings.DISPLAY_SETTINGS",
    "android.settings.NIGHT_DISPLAY_SETTINGS",
    "android.settings.SOUND_SETTINGS",
    "android.settings.ZEN_MODE_PRIORITY_SETTINGS",
    "android.settings.BATTERY_SAVER_SETTINGS",
    "android.intent.action.POWER_USAGE_SUMMARY",
    "android.settings.INTERNAL_STORAGE_SETTINGS",
    "android.settings.APPLICATION_SETTINGS",
    "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
    "android.settings.ALL_APPS_NOTIFICATION_SETTINGS",
    "android.settings.LOCATION_SOURCE_SETTINGS",
    "android.settings.SECURITY_SETTINGS",
    "android.settings.PRIVACY_SETTINGS",
    "android.settings.ACCESSIBILITY_SETTINGS",
    "android.settings.LOCALE_SETTINGS",
    "android.settings.INPUT_METHOD_SETTINGS",
    "android.settings.DATE_SETTINGS",
    "android.settings.SYNC_SETTINGS",
    "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
    "android.settings.DEVICE_INFO_SETTINGS",
)

/**
 * Keeps actions that resolve on this device, in curated order, dropping actions that resolve to
 * the same screen as an earlier one. [resolve] returns the resolved component key or null.
 */
internal fun availableSettingsActions(actions: List<String>, resolve: (String) -> String?): List<String> {
    val seen = HashSet<String>()
    return actions.distinct().filter { action ->
        val component = resolve(action) ?: return@filter false
        seen.add(component)
    }
}

/** Last activity time used for the "Recent" history: last pin request, else creation. */
internal val ShortcutItem.lastActivityAtMs: Long get() = meta.lastPinRequestAtMs ?: createdAtEpochMs

/** Limits Text/emoji icons to a few user-perceived characters (code points). */
internal fun clampIconText(input: String, maxCodePoints: Int = 4): String {
    val trimmed = input.trim()
    if (trimmed.codePointCount(0, trimmed.length) <= maxCodePoints) return trimmed
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, maxCodePoints))
}

/** Launcher shortcut ID for a definition. Stable across edits so updates reach the pinned icon. */
internal fun launcherShortcutId(definitionId: String): String = "able-shortcut:$definitionId"

/** Reads "#AARRGGBB" / "#RRGGBB"; null when absent or malformed. */
internal fun parseArgbHex(hex: String?): Int? {
    val h = hex?.removePrefix("#") ?: return null
    return when (h.length) {
        6 -> h.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
        8 -> h.toLongOrNull(16)?.toInt()
        else -> null
    }
}

internal fun argbToHex(argb: Int): String = "#%08X".format(argb)
