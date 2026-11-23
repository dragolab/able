package com.dragolabs.able.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.dragolabs.able.core.designsystem.R
import com.dragolabs.able.core.model.ToolId
import kotlinx.serialization.Serializable

/** Type-safe navigation routes (SHELL-001). */
@Serializable data object HomeRoute
@Serializable data object BatteryRoute
@Serializable data object AppsRoute
@Serializable data object ToolsRoute
/**
 * [origin] names the card/tile that opened the tool (`tools`, `home.quick`, ...) so the screen
 * can grow out of it (app-open shared transform). Null for deep links, search and shortcuts.
 */
@Serializable data class ToolDetailRoute(val tool: ToolId, val origin: String? = null)
@Serializable data object SettingsRoute
@Serializable data object OnboardingRoute
/** Guided tour (UX-001): after onboarding, and replayable from Settings → Help. */
@Serializable data object TourRoute
@Serializable data object TilesWidgetsRoute
@Serializable data object LicensesRoute
/** App-wide options search (SEARCH-001). */
@Serializable data object SearchRoute

/** The four primary destinations shown in the bar/rail/drawer. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** Key used by [com.dragolabs.able.core.designsystem.component.LocalAbleReselectEvents]. */
    val key: String,
) {
    HOME(HomeRoute, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "home"),
    BATTERY(BatteryRoute, R.string.nav_battery, Icons.Rounded.BatteryChargingFull, Icons.Outlined.BatteryChargingFull, "battery"),
    APPS(AppsRoute, R.string.nav_apps, Icons.Rounded.Apps, Icons.Outlined.Apps, "apps"),
    TOOLS(ToolsRoute, R.string.nav_tools, Icons.Rounded.Handyman, Icons.Outlined.Handyman, "tools"),
}

/**
 * Deep-link keys accepted in the launch intent extra [EXTRA_ROUTE] (tiles, widgets, shortcuts):
 * "home", "battery", "apps", "tools", "settings", or "tool/<ToolId.key>".
 */
const val EXTRA_ROUTE = "route"

fun routeFromDeepLink(value: String?): Any? = when {
    value == null -> null
    value == "home" -> HomeRoute
    value == "battery" -> BatteryRoute
    value == "apps" -> AppsRoute
    value == "tools" -> ToolsRoute
    value == "settings" -> SettingsRoute
    value == "tiles" -> TilesWidgetsRoute
    value.startsWith("tool/") -> ToolId.fromKey(value.removePrefix("tool/"))?.let(::ToolDetailRoute)
    else -> null
}

fun deepLinkForTool(tool: ToolId): String = "tool/${tool.key}"
