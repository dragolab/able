package com.dragolabs.able.feature.tools.shortcuts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.dragolabs.able.core.data.shortcut.IconSource
import com.dragolabs.able.core.data.shortcut.ShortcutMeta
import com.dragolabs.able.core.data.shortcut.ShortcutTarget
import com.dragolabs.able.core.data.shortcut.ableToolRoute
import com.dragolabs.able.core.model.ToolId
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.presentation
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Curated Material symbol set for shortcut icons. Keys are stored, so never rename them. */
internal val materialIconCatalog: List<Pair<String, ImageVector>> = listOf(
    "star" to Icons.Rounded.Star,
    "favorite" to Icons.Rounded.Favorite,
    "home" to Icons.Rounded.Home,
    "work" to Icons.Rounded.Work,
    "bookmark" to Icons.Rounded.Bookmark,
    "bolt" to Icons.Rounded.Bolt,
    "lightbulb" to Icons.Rounded.Lightbulb,
    "settings" to Icons.Rounded.Settings,
    "build" to Icons.Rounded.Build,
    "code" to Icons.Rounded.Code,
    "terminal" to Icons.Rounded.Terminal,
    "folder" to Icons.Rounded.Folder,
    "description" to Icons.Rounded.Description,
    "article" to Icons.AutoMirrored.Rounded.Article,
    "photo" to Icons.Rounded.Photo,
    "camera" to Icons.Rounded.CameraAlt,
    "music" to Icons.Rounded.MusicNote,
    "games" to Icons.Rounded.Games,
    "public" to Icons.Rounded.Public,
    "language" to Icons.Rounded.Language,
    "email" to Icons.Rounded.Email,
    "chat" to Icons.AutoMirrored.Rounded.Chat,
    "phone" to Icons.Rounded.Phone,
    "account" to Icons.Rounded.AccountCircle,
    "calendar" to Icons.Rounded.CalendarMonth,
    "alarm" to Icons.Rounded.Alarm,
    "notifications" to Icons.Rounded.Notifications,
    "map" to Icons.Rounded.Map,
    "location" to Icons.Rounded.LocationOn,
    "run" to Icons.AutoMirrored.Rounded.DirectionsRun,
    "restaurant" to Icons.Rounded.Restaurant,
    "cart" to Icons.Rounded.ShoppingCart,
    "wallet" to Icons.Rounded.AccountBalanceWallet,
    "school" to Icons.Rounded.School,
    "pets" to Icons.Rounded.Pets,
    "palette" to Icons.Rounded.Palette,
    "cloud" to Icons.Rounded.Cloud,
    "lock" to Icons.Rounded.Lock,
    "wifi" to Icons.Rounded.Wifi,
    "bluetooth" to Icons.Rounded.Bluetooth,
    "battery" to Icons.Rounded.BatteryChargingFull,
    "night" to Icons.Rounded.NightsStay,
)

internal fun materialIcon(key: String?): ImageVector? = materialIconCatalog.firstOrNull { it.first == key }?.second

/** Label and symbol for a curated settings action. */
internal data class SettingsPresentation(@StringRes val labelRes: Int, val icon: ImageVector)

internal fun settingsPresentation(action: String): SettingsPresentation = when (action) {
    "android.settings.WIFI_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_wifi, Icons.Rounded.Wifi)
    "android.settings.BLUETOOTH_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_bluetooth, Icons.Rounded.Bluetooth)
    "android.settings.panel.action.INTERNET_CONNECTIVITY" -> SettingsPresentation(R.string.shortcuts_settings_internet_panel, Icons.Rounded.Public)
    "android.settings.AIRPLANE_MODE_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_airplane, Icons.Rounded.AirplanemodeActive)
    "android.settings.DATA_USAGE_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_data_usage, Icons.Rounded.DataUsage)
    "android.settings.NFC_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_nfc, Icons.Rounded.Nfc)
    "android.settings.CAST_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_cast, Icons.Rounded.Cast)
    "android.settings.VPN_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_vpn, Icons.Rounded.VpnKey)
    "android.settings.DISPLAY_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_display, Icons.Rounded.Brightness6)
    "android.settings.NIGHT_DISPLAY_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_night_light, Icons.Rounded.NightsStay)
    "android.settings.SOUND_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_sound, Icons.AutoMirrored.Rounded.VolumeUp)
    "android.settings.ZEN_MODE_PRIORITY_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_dnd, Icons.Rounded.DoNotDisturbOn)
    "android.settings.BATTERY_SAVER_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_battery_saver, Icons.Rounded.BatterySaver)
    "android.intent.action.POWER_USAGE_SUMMARY" -> SettingsPresentation(R.string.shortcuts_settings_battery_usage, Icons.Rounded.BatteryChargingFull)
    "android.settings.INTERNAL_STORAGE_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_storage, Icons.Rounded.Storage)
    "android.settings.APPLICATION_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_apps, Icons.Rounded.Apps)
    "android.settings.MANAGE_DEFAULT_APPS_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_default_apps, Icons.Rounded.SettingsApplications)
    "android.settings.ALL_APPS_NOTIFICATION_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_notifications, Icons.Rounded.Notifications)
    "android.settings.LOCATION_SOURCE_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_location, Icons.Rounded.LocationOn)
    "android.settings.SECURITY_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_security, Icons.Rounded.Security)
    "android.settings.PRIVACY_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_privacy, Icons.Rounded.PrivacyTip)
    "android.settings.ACCESSIBILITY_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_accessibility, Icons.Rounded.AccessibilityNew)
    "android.settings.LOCALE_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_language, Icons.Rounded.Translate)
    "android.settings.INPUT_METHOD_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_keyboard, Icons.Rounded.Keyboard)
    "android.settings.DATE_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_date, Icons.Rounded.DateRange)
    "android.settings.SYNC_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_accounts, Icons.Rounded.Sync)
    "android.settings.APPLICATION_DEVELOPMENT_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_developer, Icons.Rounded.Code)
    "android.settings.DEVICE_INFO_SETTINGS" -> SettingsPresentation(R.string.shortcuts_settings_about, Icons.Rounded.Info)
    else -> SettingsPresentation(R.string.shortcuts_settings_main, Icons.Rounded.Settings)
}

/** Able destinations offered as "Able actions". */
internal data class AbleActionOption(val route: String, @StringRes val labelRes: Int, val icon: ImageVector)

internal val ableActionOptions: List<AbleActionOption> by lazy {
    listOf(
        AbleActionOption("battery", R.string.shortcuts_able_battery, Icons.Rounded.BatteryChargingFull),
        AbleActionOption("apps", R.string.shortcuts_able_apps, Icons.Rounded.Apps),
        AbleActionOption("tools", R.string.shortcuts_able_tools, Icons.Rounded.Handyman),
        AbleActionOption("home", R.string.shortcuts_able_home, Icons.Rounded.Home),
    ) + ToolId.entries.filter { it != ToolId.SHORTCUT_MAKER }.map { tool ->
        val p = tool.presentation()
        AbleActionOption(ableToolRoute(tool), p.labelRes, p.icon)
    } + ToolId.SHORTCUT_MAKER.presentation().let { AbleActionOption(ableToolRoute(ToolId.SHORTCUT_MAKER), it.labelRes, it.icon) }
}

internal fun ableActionOption(route: String): AbleActionOption? = ableActionOptions.firstOrNull { it.route == route }

/** Material symbol used when a target has no drawable icon of its own. */
internal fun ShortcutTarget.symbol(): ImageVector = when (this) {
    is ShortcutTarget.App, is ShortcutTarget.Activity -> Icons.Rounded.Apps
    is ShortcutTarget.SettingsPage -> settingsPresentation(action).icon
    is ShortcutTarget.Document -> if (isFolder) Icons.Rounded.Folder else Icons.Rounded.Description
    is ShortcutTarget.Website -> Icons.Rounded.Language
    is ShortcutTarget.AbleAction -> ableActionOption(route)?.icon ?: Icons.Rounded.Handyman
}

/** Background palette derived from the current Material theme; stored as ARGB hex. */
internal fun shortcutPalette(scheme: ColorScheme): List<Color> = listOf(
    scheme.primary,
    scheme.primaryContainer,
    scheme.secondary,
    scheme.secondaryContainer,
    scheme.tertiary,
    scheme.tertiaryContainer,
    scheme.error,
    scheme.errorContainer,
    scheme.inverseSurface,
    scheme.surfaceContainerHighest,
    Color(0xFFFFFFFF),
    Color(0xFF000000),
).distinctBy { it.toArgb() }

/** Everything the icon preview needs, independent of whether a definition is saved yet. */
data class IconSpec(
    val target: ShortcutTarget?,
    val meta: ShortcutMeta,
    val backgroundArgb: Int?,
)

internal fun interface TargetDrawableLoader {
    suspend fun load(target: ShortcutTarget): Drawable?
}

/** Rasterizes a vector painter as a white alpha mask, tinted later by the renderer. */
internal fun VectorPainter.toMask(sizePx: Int, density: Density): Bitmap {
    val image = ImageBitmap(sizePx, sizePx)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(sizePx.toFloat(), sizePx.toFloat())) {
        with(this@toMask) { draw(size, colorFilter = ColorFilter.tint(Color.White)) }
    }
    return image.asAndroidBitmap()
}

/**
 * Renders the exact bitmap that will be sent to the launcher (preview = result, SHORT-003).
 * Returns null while loading.
 */
@Composable
internal fun rememberShortcutIconBitmap(
    spec: IconSpec,
    iconDir: File,
    loader: TargetDrawableLoader,
    sizePx: Int,
): ImageBitmap? {
    val density = LocalDensity.current
    val vector = when (spec.meta.source) {
        IconSource.MATERIAL -> materialIcon(spec.meta.value) ?: Icons.Rounded.Star
        else -> spec.target?.symbol() ?: Icons.Rounded.Star
    }
    val painter = rememberVectorPainter(vector)
    val bitmap by produceState<ImageBitmap?>(null, spec, sizePx, vector) {
        value = withContext(Dispatchers.Default) {
            val drawable = if (spec.meta.source == IconSource.TARGET) spec.target?.let { withContext(Dispatchers.IO) { loader.load(it) } } else null
            val image = if (spec.meta.source == IconSource.IMAGE) {
                spec.meta.value?.let { name ->
                    withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(File(iconDir, name).path) }.getOrNull() }
                }
            } else {
                null
            }
            val mask = if (spec.meta.source == IconSource.MATERIAL || (spec.meta.source == IconSource.TARGET && drawable == null)) {
                withContext(Dispatchers.Main) { painter.toMask(MASK_PX, density) }
            } else {
                null
            }
            ShortcutIconRenderer.render(
                IconRenderInput(
                    source = spec.meta.source,
                    targetDrawable = drawable,
                    glyphMask = mask,
                    text = spec.meta.value,
                    image = image,
                    backgroundArgb = spec.backgroundArgb,
                    shape = spec.meta.shape,
                    glyphSize = spec.meta.glyphSize,
                ),
                sizePx,
            ).asImageBitmap()
        }
    }
    return bitmap
}

private const val MASK_PX = 192
