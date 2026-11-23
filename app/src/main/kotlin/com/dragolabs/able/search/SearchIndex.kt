package com.dragolabs.able.search

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.WhereToVote
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tour
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.dragolabs.able.R
import com.dragolabs.able.core.model.ToolId
import com.dragolabs.able.core.designsystem.R as DsR
import com.dragolabs.able.feature.apps.R as AppsR
import com.dragolabs.able.feature.battery.R as BatteryR
import com.dragolabs.able.feature.home.R as HomeR
import com.dragolabs.able.feature.settings.R as SettingsR
import com.dragolabs.able.feature.tools.R as ToolsR

/** Top-level groups of the options search, in display order (ties keep this order). */
enum class SearchSection(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    /** Shell deep link of the section (see `routeFromDeepLink`). */
    val deepLink: String,
) {
    HOME(DsR.string.nav_home, Icons.Rounded.Home, "home"),
    BATTERY(DsR.string.nav_battery, Icons.Rounded.BatteryChargingFull, "battery"),
    APPS(DsR.string.nav_apps, Icons.Rounded.Apps, "apps"),
    TOOLS(DsR.string.nav_tools, Icons.Rounded.Handyman, "tools"),
    SETTINGS(DsR.string.nav_settings, Icons.Rounded.Settings, "settings"),
    TILES(R.string.tiles_screen_title, Icons.Rounded.Widgets, "tiles"),
}

/**
 * One searchable option.
 *
 * @property id stable identifier (persisted nowhere yet, but keep it stable for analytics-free
 *   features such as pinning or "recently opened").
 * @property deepLink shell deep link accepted by `routeFromDeepLink` ("battery", "tool/torch").
 * @property areaRes optional second breadcrumb level ("Monitor" in "Battery › Monitor").
 * @property anchor optional fragment `<area>[.<item>]` for in-screen scrolling; see [SearchDeepLink].
 */
@Immutable
data class SearchEntry(
    val id: String,
    @param:StringRes val titleRes: Int,
    val section: SearchSection,
    val icon: ImageVector,
    val deepLink: String = section.deepLink,
    @param:StringRes val keywordsRes: Int? = null,
    @param:StringRes val areaRes: Int? = null,
    val anchor: String? = null,
    val popular: Boolean = false,
) {
    /** Deep link including the anchor fragment, e.g. `battery#monitor.alerts`. */
    val deepLinkWithAnchor: String get() = SearchDeepLink.compose(deepLink, anchor)
}

/** Static index of every option reachable from the app-wide search. */
object SearchIndex {

    val entries: List<SearchEntry> by lazy { buildEntries() }

    val popular: List<SearchEntry> get() = entries.filter { it.popular }

    fun byId(id: String): SearchEntry? = entries.firstOrNull { it.id == id }

    /** Tool title and icon, mirroring the Tools screen. */
    private fun toolPresentation(tool: ToolId): Pair<Int, ImageVector> = when (tool) {
        ToolId.TORCH -> ToolsR.string.tools_torch_title to Icons.Rounded.FlashlightOn
        ToolId.HOTSPOT -> ToolsR.string.tools_hotspot_title to Icons.Rounded.WifiTethering
        ToolId.STORAGE -> ToolsR.string.tools_storage_title to Icons.Rounded.FolderOpen
        ToolId.MEMORY -> ToolsR.string.tools_memory_title to Icons.Rounded.Memory
        ToolId.FAKE_GPS -> ToolsR.string.tools_fakegps_title to Icons.Rounded.WhereToVote
        ToolId.SCREEN_LOCK -> ToolsR.string.tools_lock_title to Icons.Rounded.Lock
        ToolId.ACTIVITY_LAUNCHER -> ToolsR.string.tools_activities_title to Icons.Rounded.Apps
        ToolId.SHORTCUT_MAKER -> ToolsR.string.tools_shortcuts_title to Icons.AutoMirrored.Rounded.AddToHomeScreen
        ToolId.SYSTEM_TOGGLES -> ToolsR.string.tools_toggles_title to Icons.Rounded.Tune
        ToolId.SHELL -> ToolsR.string.tools_shell_title to Icons.Rounded.Terminal
        ToolId.APP_LOCK -> ToolsR.string.tools_applock_title to Icons.Rounded.Fingerprint
        ToolId.BUTTON_MAPPER -> ToolsR.string.tools_buttons_title to Icons.Rounded.SmartButton
        ToolId.GPS -> ToolsR.string.tools_gps_title to Icons.Rounded.MyLocation
        ToolId.LEVEL -> ToolsR.string.tools_level_title to Icons.Rounded.Balance
        ToolId.RULER -> ToolsR.string.tools_ruler_title to Icons.Rounded.Straighten
        ToolId.COMPASS -> ToolsR.string.tools_compass_title to Icons.Rounded.Explore
    }

    private fun toolKeywords(tool: ToolId): Int = when (tool) {
        ToolId.TORCH -> R.string.search_kw_tool_torch
        ToolId.HOTSPOT -> R.string.search_kw_tool_hotspot
        ToolId.STORAGE -> R.string.search_kw_tool_storage
        ToolId.MEMORY -> R.string.search_kw_tool_memory
        ToolId.FAKE_GPS -> R.string.search_kw_tool_fakegps
        ToolId.SCREEN_LOCK -> R.string.search_kw_tool_lock
        ToolId.ACTIVITY_LAUNCHER -> R.string.search_kw_tool_activities
        ToolId.SHORTCUT_MAKER -> R.string.search_kw_tool_shortcuts
        ToolId.SYSTEM_TOGGLES -> R.string.search_kw_tool_toggles
        ToolId.SHELL -> R.string.search_kw_tool_shell
        ToolId.APP_LOCK -> R.string.search_kw_tool_applock
        ToolId.BUTTON_MAPPER -> R.string.search_kw_tool_buttons
        ToolId.GPS -> R.string.search_kw_tool_gps
        ToolId.LEVEL -> R.string.search_kw_tool_level
        ToolId.RULER -> R.string.search_kw_tool_ruler
        ToolId.COMPASS -> R.string.search_kw_tool_compass
    }

    private val popularTools = setOf(ToolId.TORCH, ToolId.STORAGE, ToolId.SCREEN_LOCK)

    private fun toolDeepLink(tool: ToolId) = "tool/${tool.key}"

    private fun buildEntries(): List<SearchEntry> = buildList {
        val home = SearchSection.HOME
        add(SearchEntry("home.customize", HomeR.string.home_customize_title, home, Icons.Rounded.Edit, keywordsRes = R.string.search_kw_home_customize, anchor = "customize", popular = true))
        add(SearchEntry("home.quick_actions", HomeR.string.home_card_quick_actions_title, home, Icons.Rounded.Bolt, keywordsRes = R.string.search_kw_home_quick_actions, anchor = "quick_actions"))
        add(SearchEntry("home.shortcuts", HomeR.string.home_card_shortcuts_title, home, Icons.Rounded.Star, keywordsRes = R.string.search_kw_home_shortcuts, anchor = "shortcuts"))
        add(SearchEntry("home.recent", HomeR.string.home_card_recent_title, home, Icons.Rounded.History, keywordsRes = R.string.search_kw_home_recent, anchor = "recent"))
        add(SearchEntry("home.access", HomeR.string.home_card_capabilities_title, home, Icons.Rounded.VerifiedUser, keywordsRes = R.string.search_kw_access, anchor = "capabilities"))
        add(SearchEntry("home.sensors", HomeR.string.home_card_sensors_title, home, Icons.Rounded.Sensors, keywordsRes = R.string.search_kw_home_sensors, anchor = "sensors"))

        val battery = SearchSection.BATTERY
        val overview = BatteryR.string.battery_tab_overview
        val monitor = BatteryR.string.battery_tab_monitor
        val health = BatteryR.string.battery_tab_health
        val diagnostics = BatteryR.string.battery_tab_diagnostics
        add(SearchEntry("battery.overview", BatteryR.string.battery_hero_title, battery, Icons.Rounded.BatteryChargingFull, keywordsRes = R.string.search_kw_battery_overview, areaRes = overview, anchor = "overview"))
        add(SearchEntry("battery.profiles", BatteryR.string.battery_profiles_title, battery, Icons.Rounded.Dashboard, keywordsRes = R.string.search_kw_battery_profiles, areaRes = overview, anchor = "overview.profiles", popular = true))
        add(SearchEntry("battery.triggers", BatteryR.string.battery_battery_triggers_title, battery, Icons.Rounded.Bolt, keywordsRes = R.string.search_kw_battery_triggers, areaRes = overview, anchor = "overview.triggers"))
        add(SearchEntry("battery.night", BatteryR.string.battery_night_triggers, battery, Icons.Rounded.Bedtime, keywordsRes = R.string.search_kw_battery_night, areaRes = overview, anchor = "overview.night"))
        add(SearchEntry("battery.thresholds", BatteryR.string.battery_thresholds_title, battery, Icons.Rounded.Percent, keywordsRes = R.string.search_kw_battery_thresholds, areaRes = overview, anchor = "overview.thresholds"))
        add(SearchEntry("battery.pause_triggers", BatteryR.string.battery_automation_pause, battery, Icons.Rounded.PauseCircle, keywordsRes = R.string.search_kw_battery_pause, areaRes = overview, anchor = "overview.pause"))
        add(SearchEntry("battery.protected", BatteryR.string.battery_protected_title, battery, Icons.Rounded.Shield, keywordsRes = R.string.search_kw_battery_protected, areaRes = overview, anchor = "overview.protected"))
        add(SearchEntry("battery.saver", BatteryR.string.battery_saver_title, battery, Icons.Rounded.BatterySaver, keywordsRes = R.string.search_kw_battery_saver, areaRes = overview, anchor = "overview.saver"))
        add(SearchEntry("battery.monitor", BatteryR.string.battery_monitor_title, battery, Icons.Rounded.MonitorHeart, keywordsRes = R.string.search_kw_battery_monitor, areaRes = monitor, anchor = "monitor", popular = true))
        add(SearchEntry("battery.alerts", BatteryR.string.battery_alerts_title, battery, Icons.Rounded.NotificationsActive, keywordsRes = R.string.search_kw_battery_alerts, areaRes = monitor, anchor = "monitor.alerts"))
        add(SearchEntry("battery.reset_rules", BatteryR.string.battery_reset_title, battery, Icons.Rounded.RestartAlt, keywordsRes = R.string.search_kw_battery_reset, areaRes = monitor, anchor = "monitor.reset_rules"))
        add(SearchEntry("battery.outputs", BatteryR.string.battery_outputs_title, battery, Icons.Rounded.Thermostat, keywordsRes = R.string.search_kw_battery_outputs, areaRes = monitor, anchor = "monitor.outputs"))
        add(SearchEntry("battery.history", BatteryR.string.battery_history_title, battery, Icons.Rounded.History, keywordsRes = R.string.search_kw_battery_history, areaRes = monitor, anchor = "monitor.history"))
        add(SearchEntry("battery.retention", BatteryR.string.battery_retention, battery, Icons.Rounded.AutoDelete, keywordsRes = R.string.search_kw_battery_retention, areaRes = monitor, anchor = "monitor.retention"))
        add(SearchEntry("battery.health", BatteryR.string.battery_tab_health, battery, Icons.Rounded.HealthAndSafety, keywordsRes = R.string.search_kw_battery_health, anchor = "health"))
        add(SearchEntry("battery.cycles", BatteryR.string.battery_cycles_title, battery, Icons.Rounded.Loop, keywordsRes = R.string.search_kw_battery_cycles, areaRes = health, anchor = "health.cycles"))
        add(SearchEntry("battery.advanced", BatteryR.string.battery_advanced_title, battery, Icons.Rounded.Memory, keywordsRes = R.string.search_kw_battery_advanced, areaRes = health, anchor = "health.advanced"))
        add(SearchEntry("battery.diagnostics", BatteryR.string.battery_diag_title, battery, Icons.Rounded.Troubleshoot, keywordsRes = R.string.search_kw_battery_diagnostics, areaRes = diagnostics, anchor = "diagnostics"))
        add(SearchEntry("battery.usage", BatteryR.string.battery_diag_battery_usage, battery, Icons.Rounded.QueryStats, keywordsRes = R.string.search_kw_battery_usage, areaRes = diagnostics, anchor = "diagnostics.usage"))

        val apps = SearchSection.APPS
        add(SearchEntry("apps.search", R.string.search_entry_apps_search, apps, Icons.Rounded.Search, keywordsRes = R.string.search_kw_apps_search, anchor = "search", popular = true))
        add(SearchEntry("apps.archive", AppsR.string.apps_state_able_archive, apps, Icons.Rounded.Archive, keywordsRes = R.string.search_kw_apps_archive, anchor = "archive"))
        add(SearchEntry("apps.filters", AppsR.string.apps_filters_sheet_title, apps, Icons.Rounded.FilterList, keywordsRes = R.string.search_kw_apps_filters, anchor = "filters"))
        add(SearchEntry("apps.removal_levels", AppsR.string.apps_help_removal_title, apps, Icons.AutoMirrored.Rounded.Rule, keywordsRes = R.string.search_kw_apps_removal, anchor = "filters.removal"))
        add(SearchEntry("apps.disable", R.string.search_entry_apps_disable, apps, Icons.Rounded.Block, keywordsRes = R.string.search_kw_apps_disable))
        add(SearchEntry("apps.force_stop", AppsR.string.apps_help_force_stop_title, apps, Icons.Rounded.StopCircle, keywordsRes = R.string.search_kw_apps_force_stop))
        add(SearchEntry("apps.profile_rules", AppsR.string.apps_section_profiles, apps, Icons.Rounded.Dashboard, keywordsRes = R.string.search_kw_apps_profile_rules))

        val tools = SearchSection.TOOLS
        ToolId.entries.forEach { tool ->
            val (title, icon) = toolPresentation(tool)
            add(
                SearchEntry(
                    id = "tool.${tool.key}",
                    titleRes = title,
                    section = tools,
                    icon = icon,
                    deepLink = toolDeepLink(tool),
                    keywordsRes = toolKeywords(tool),
                    popular = tool in popularTools,
                ),
            )
        }
        add(SearchEntry("tool.lock.double_tap", ToolsR.string.tools_lock_double_tap_title, tools, Icons.Rounded.TouchApp, deepLink = toolDeepLink(ToolId.SCREEN_LOCK), keywordsRes = R.string.search_kw_tool_lock_double_tap, areaRes = ToolsR.string.tools_lock_title, anchor = "double_tap"))
        add(SearchEntry("tool.hotspot.qr", ToolsR.string.tools_hotspot_qr_title, tools, Icons.Rounded.QrCode2, deepLink = toolDeepLink(ToolId.HOTSPOT), keywordsRes = R.string.search_kw_tool_hotspot_qr, areaRes = ToolsR.string.tools_hotspot_title, anchor = "qr"))
        add(SearchEntry("tool.toggles.dns", R.string.search_entry_private_dns, tools, Icons.Rounded.Dns, deepLink = toolDeepLink(ToolId.SYSTEM_TOGGLES), keywordsRes = R.string.search_kw_tool_private_dns, areaRes = ToolsR.string.tools_toggles_title, anchor = "private_dns"))

        val settings = SearchSection.SETTINGS
        val appearance = SettingsR.string.settings_section_appearance
        val access = SettingsR.string.settings_section_access
        val privacy = SettingsR.string.settings_section_privacy
        val backup = SettingsR.string.settings_section_backup
        val homeLayout = SettingsR.string.settings_section_home
        val help = SettingsR.string.settings_section_help
        val about = SettingsR.string.settings_section_about
        add(SearchEntry("settings.theme", SettingsR.string.settings_theme_title, settings, Icons.Rounded.DarkMode, keywordsRes = R.string.search_kw_settings_theme, areaRes = appearance, anchor = "appearance.theme", popular = true))
        add(SearchEntry("settings.language", SettingsR.string.settings_language_title, settings, Icons.Rounded.Language, keywordsRes = R.string.search_kw_settings_language, areaRes = appearance, anchor = "appearance.language"))
        add(SearchEntry("settings.dynamic_color", SettingsR.string.settings_dynamic_color_title, settings, Icons.Rounded.Wallpaper, keywordsRes = R.string.search_kw_settings_dynamic_color, areaRes = appearance, anchor = "appearance.dynamic_color"))
        add(SearchEntry("settings.haptics", SettingsR.string.settings_haptics_title, settings, Icons.Rounded.Vibration, keywordsRes = R.string.search_kw_settings_haptics, areaRes = appearance, anchor = "appearance.haptics"))
        add(SearchEntry("settings.access", SettingsR.string.settings_section_access, settings, Icons.Rounded.VerifiedUser, keywordsRes = R.string.search_kw_access, anchor = "access"))
        add(SearchEntry("settings.access.shizuku", SettingsR.string.settings_access_shizuku_title, settings, Icons.Rounded.Security, keywordsRes = R.string.search_kw_access_shizuku, areaRes = access, anchor = "access.shizuku"))
        add(SearchEntry("settings.access.usage", SettingsR.string.settings_access_usage_title, settings, Icons.Rounded.QueryStats, keywordsRes = R.string.search_kw_access_usage, areaRes = access, anchor = "access.usage"))
        add(SearchEntry("settings.access.accessibility", SettingsR.string.settings_access_accessibility_title, settings, Icons.Rounded.AccessibilityNew, keywordsRes = R.string.search_kw_access_accessibility, areaRes = access, anchor = "access.accessibility"))
        add(SearchEntry("settings.access.notifications", SettingsR.string.settings_access_notifications_title, settings, Icons.Rounded.Notifications, keywordsRes = R.string.search_kw_access_notifications, areaRes = access, anchor = "access.notifications"))
        add(SearchEntry("settings.access.write_settings", SettingsR.string.settings_access_write_settings_title, settings, Icons.Rounded.Tune, keywordsRes = R.string.search_kw_access_write_settings, areaRes = access, anchor = "access.write_settings"))
        add(SearchEntry("settings.access.secure", SettingsR.string.settings_access_secure_title, settings, Icons.Rounded.Security, keywordsRes = R.string.search_kw_access_secure, areaRes = access, anchor = "access.secure_settings"))
        add(SearchEntry("settings.access.location", SettingsR.string.settings_access_location_title, settings, Icons.Rounded.LocationOn, keywordsRes = R.string.search_kw_access_location, areaRes = access, anchor = "access.location"))
        add(SearchEntry("settings.crash_reports", SettingsR.string.settings_telemetry_title, settings, Icons.Rounded.PrivacyTip, keywordsRes = R.string.search_kw_settings_crash_reports, areaRes = privacy, anchor = "privacy.crash_reports"))
        add(SearchEntry("settings.backup.export", SettingsR.string.settings_backup_export_title, settings, Icons.Rounded.Upload, keywordsRes = R.string.search_kw_settings_export, areaRes = backup, anchor = "backup.export"))
        add(SearchEntry("settings.backup.import", SettingsR.string.settings_backup_import_title, settings, Icons.Rounded.Download, keywordsRes = R.string.search_kw_settings_import, areaRes = backup, anchor = "backup.import"))
        add(SearchEntry("settings.backup.restore", SettingsR.string.settings_backup_restore_title, settings, Icons.Rounded.Restore, keywordsRes = R.string.search_kw_settings_restore, areaRes = backup, anchor = "backup.restore"))
        add(SearchEntry("settings.home_reset", SettingsR.string.settings_home_reset_title, settings, Icons.Rounded.RestartAlt, keywordsRes = R.string.search_kw_settings_home_reset, areaRes = homeLayout, anchor = "home.reset"))
        add(SearchEntry("settings.tour", SettingsR.string.settings_help_tour_title, settings, Icons.Rounded.Tour, keywordsRes = R.string.search_kw_settings_tour, areaRes = help, anchor = "help.tour"))
        add(SearchEntry("settings.licenses", SettingsR.string.settings_about_licenses, settings, Icons.Rounded.Gavel, keywordsRes = R.string.search_kw_settings_licenses, areaRes = about, anchor = "about.licenses"))
        add(SearchEntry("settings.about", SettingsR.string.settings_section_about, settings, Icons.Rounded.Info, keywordsRes = R.string.search_kw_settings_about, anchor = "about"))

        val tiles = SearchSection.TILES
        add(SearchEntry("tiles.overview", R.string.tiles_screen_title, tiles, Icons.Rounded.Widgets, keywordsRes = R.string.search_kw_tiles, popular = true))
        add(SearchEntry("tiles.custom", R.string.tiles_section_custom, tiles, Icons.Rounded.GridView, keywordsRes = R.string.search_kw_tiles_custom, anchor = "custom"))
        add(SearchEntry("tiles.builtin", R.string.tiles_section_builtin, tiles, Icons.Rounded.Apps, keywordsRes = R.string.search_kw_tiles_builtin, anchor = "builtin"))
        add(SearchEntry("tiles.widgets", R.string.tiles_section_widgets, tiles, Icons.Rounded.Widgets, keywordsRes = R.string.search_kw_tiles_widgets, anchor = "widgets"))
        add(SearchEntry("tiles.custom_widget", R.string.tiles_widget_config_title, tiles, Icons.Rounded.Palette, keywordsRes = R.string.search_kw_tiles_custom_widget, anchor = "widgets.custom"))
    }
}
