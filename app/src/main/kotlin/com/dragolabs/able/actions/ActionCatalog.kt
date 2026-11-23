package com.dragolabs.able.actions

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.nfc.NfcAdapter
import android.os.Vibrator
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.dragolabs.able.R
import com.dragolabs.able.core.capability.NfcToggleRoute
import com.dragolabs.able.core.capability.mapNfcAdapterState
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.model.ActionCategory
import com.dragolabs.able.core.model.ActionExecutionType
import com.dragolabs.able.core.model.DnsProfile
import com.dragolabs.able.core.model.PrivateDnsMode
import com.dragolabs.able.core.model.ToolId
import com.dragolabs.able.system.DeviceActionOutcome
import com.dragolabs.able.system.DeviceActions
import kotlinx.coroutines.delay

/** Stable action ids. Persisted in tile mappings, widget configs and backups: never rename. */
object ActionIds {
    const val TORCH = "torch"
    const val NFC = "nfc"
    const val AUTO_BRIGHTNESS = "auto_brightness"
    const val USB_DEBUGGING = "usb_debugging"
    const val WIRELESS_DEBUGGING = "wireless_debugging"
    const val PRIVATE_DNS = "private_dns"
    const val SCREEN_LOCK = "screen_lock"
    const val RINGER_MODE = "ringer_mode"
    const val GPS_SAVE = "gps_save"
    const val BATTERY_PROFILE = "battery_profile"
    const val HOTSPOT = "hotspot_settings"
    const val STORAGE = "storage"
    const val ACTIVITY_LAUNCHER = "tool_activities"
    const val SHORTCUT_MAKER = "tool_shortcuts"
    const val SYSTEM_TOGGLES = "tool_toggles"
    const val LEVEL = "tool_level"
    const val RULER = "tool_ruler"
    const val COMPASS = "tool_compass"
    const val OPEN_ABLE = "open_able"
}

/**
 * The Able Action Catalog (ADR-024): the single registry of executable actions that Quick
 * Settings tile slots (CUST-001) and multi-action widgets (CUST-002) can be bound to.
 */
object ActionCatalog {

    val all: List<CatalogAction> = listOf(
        TorchAction,
        NfcAction,
        AutoBrightnessAction,
        UsbDebuggingAction,
        WirelessDebuggingAction,
        PrivateDnsAction,
        ScreenLockAction,
        RingerModeAction,
        OpenAbleAction(ActionIds.GPS_SAVE, R.string.action_gps_save_title, R.string.action_gps_save_desc, R.drawable.ic_action_gps_save, ActionCategory.TOOLS, tool = ToolId.GPS),
        OpenAbleAction(ActionIds.BATTERY_PROFILE, R.string.action_battery_profile_title, R.string.action_battery_profile_desc, R.drawable.ic_action_battery, ActionCategory.BATTERY, deepLink = "battery"),
        OpenAbleAction(ActionIds.HOTSPOT, R.string.action_hotspot_title, R.string.action_hotspot_desc, R.drawable.ic_action_hotspot, ActionCategory.CONNECTIVITY, tool = ToolId.HOTSPOT),
        OpenAbleAction(ActionIds.STORAGE, R.string.action_storage_title, R.string.action_storage_desc, R.drawable.ic_action_storage, ActionCategory.TOOLS, tool = ToolId.STORAGE),
        OpenAbleAction(ActionIds.ACTIVITY_LAUNCHER, R.string.action_activities_title, R.string.action_activities_desc, R.drawable.ic_action_activities, ActionCategory.SHORTCUTS, tool = ToolId.ACTIVITY_LAUNCHER),
        OpenAbleAction(ActionIds.SHORTCUT_MAKER, R.string.action_shortcuts_title, R.string.action_shortcuts_desc, R.drawable.ic_action_shortcut, ActionCategory.SHORTCUTS, tool = ToolId.SHORTCUT_MAKER),
        OpenAbleAction(ActionIds.SYSTEM_TOGGLES, R.string.action_toggles_title, R.string.action_toggles_desc, R.drawable.ic_action_toggles, ActionCategory.SYSTEM, tool = ToolId.SYSTEM_TOGGLES),
        OpenAbleAction(ActionIds.LEVEL, R.string.action_level_title, R.string.action_level_desc, R.drawable.ic_action_level, ActionCategory.TOOLS, tool = ToolId.LEVEL),
        OpenAbleAction(ActionIds.RULER, R.string.action_ruler_title, R.string.action_ruler_desc, R.drawable.ic_action_ruler, ActionCategory.TOOLS, tool = ToolId.RULER),
        OpenAbleAction(ActionIds.COMPASS, R.string.action_compass_title, R.string.action_compass_desc, R.drawable.ic_action_compass, ActionCategory.TOOLS, tool = ToolId.COMPASS),
        OpenAbleAction(ActionIds.OPEN_ABLE, R.string.action_open_able_title, R.string.action_open_able_desc, R.drawable.ic_tile_able, ActionCategory.SHORTCUTS, deepLink = "home"),
    )

    private val byId = all.associateBy { it.id }

    fun find(id: String?): CatalogAction? = id?.let(byId::get)

    /** Category order used by pickers. */
    val categories: List<ActionCategory> = listOf(
        ActionCategory.CONNECTIVITY,
        ActionCategory.SYSTEM,
        ActionCategory.BATTERY,
        ActionCategory.TOOLS,
        ActionCategory.SHORTCUTS,
    )
}

/** Tile re-read interval for Settings.Global-backed actions (see CatalogAction.listeningPollMs). */
private const val SETTINGS_POLL_MS = 1_000L

private fun Boolean.onOffRes(): Int = if (this) R.string.action_state_on else R.string.action_state_off

// region Toggles

/** Torch (TORCH-002). State comes from CameraManager callbacks, never assumed. */
private object TorchAction : CatalogAction(
    id = ActionIds.TORCH,
    titleRes = R.string.action_torch_title,
    descriptionRes = R.string.action_torch_desc,
    iconRes = R.drawable.ic_tile_torch_on,
    category = ActionCategory.TOOLS,
    type = ActionExecutionType.IMMEDIATE_TOGGLE,
) {
    override fun isSupported(context: Context) = DeviceActions.torchCameraId(context) != null

    override suspend fun readState(context: Context): ActionState {
        if (!isSupported(context)) {
            return ActionState(null, false, R.drawable.ic_tile_torch_off, R.string.action_state_unsupported)
        }
        val on = DeviceActions.readTorchState(context)
        return ActionState(
            active = on,
            available = true,
            iconRes = if (on == true) R.drawable.ic_tile_torch_on else R.drawable.ic_tile_torch_off,
            statusRes = on?.onOffRes(),
        )
    }

    override fun route(context: Context): ActionRoute =
        if (isSupported(context)) ActionRoute.RunInPlace else ActionRoute.Unsupported(DeviceActions.openToolIntent(context, ToolId.TORCH))

    override suspend fun execute(context: Context): ActionOutcome {
        val current = DeviceActions.readTorchState(context) ?: return ActionOutcome.UNSUPPORTED
        return DeviceActions.setTorch(context, !current).toActionOutcome()
    }
}

/**
 * NFC (SYS-001). Advanced mode toggles in place from Able's own process (NfcAdapter with
 * WRITE_SECURE_SETTINGS, granted through ADB or self-granted through Shizuku); basic mode opens
 * Android's NFC settings page. Routes follow Paul Colby's "NFC Quick Settings"
 * (GPL-3.0-or-later, https://github.com/pcolby/nfc-quick-settings).
 */
private object NfcAction : CatalogAction(
    id = ActionIds.NFC,
    titleRes = R.string.action_nfc_title,
    descriptionRes = R.string.action_nfc_desc,
    iconRes = R.drawable.ic_tile_nfc,
    category = ActionCategory.CONNECTIVITY,
    type = ActionExecutionType.IMMEDIATE_TOGGLE,
    privilege = ActionPrivilege.SECURE_SETTINGS,
    requiresUnlock = true,
) {
    override val stateChangedActions: List<String> = listOf(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)

    override fun isSupported(context: Context) = context.ableContainer.secureSettings.isNfcSupported()

    override suspend fun readState(context: Context): ActionState {
        val settings = context.ableContainer.secureSettings
        val on = settings.isNfcEnabled()
            ?: return ActionState(null, false, iconRes, R.string.action_state_unsupported)
        val status = if (settings.canToggleNfcDirectly()) on.onOffRes() else R.string.action_state_opens_settings
        return ActionState(on, true, iconRes, status)
    }

    override fun transitionalState(context: Context, intent: Intent): ActionState? {
        if (intent.action != NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) return null
        val live = mapNfcAdapterState(intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1)) ?: return null
        if (!live.transitioning) return null
        return ActionState(live.enabled, true, iconRes).transitioningTo(live.enabled)
    }

    override fun route(context: Context): ActionRoute {
        val settings = context.ableContainer.secureSettings
        return when (settings.nfcToggleRoute()) {
            NfcToggleRoute.UNSUPPORTED -> ActionRoute.Unsupported(null)
            NfcToggleRoute.ADVANCED, NfcToggleRoute.SHIZUKU_SELF_GRANT -> ActionRoute.RunInPlace
            NfcToggleRoute.SETTINGS -> ActionRoute.OpenScreen(settings.getNfcSettingsIntent())
        }
    }

    override suspend fun execute(context: Context): ActionOutcome = DeviceActions.toggleNfc(context).toActionOutcome()
}

/** Auto brightness (SYS-002): needs "Modify system settings", requested through Android's screen. */
private object AutoBrightnessAction : CatalogAction(
    id = ActionIds.AUTO_BRIGHTNESS,
    titleRes = R.string.action_auto_brightness_title,
    descriptionRes = R.string.action_auto_brightness_desc,
    iconRes = R.drawable.ic_tile_brightness,
    category = ActionCategory.SYSTEM,
    type = ActionExecutionType.IMMEDIATE_TOGGLE,
    privilege = ActionPrivilege.WRITE_SETTINGS,
) {
    override suspend fun readState(context: Context): ActionState {
        val auto = context.ableContainer.secureSettings.isAutoBrightnessEnabled()
        return ActionState(auto, true, iconRes, if (auto) R.string.action_state_automatic else R.string.action_state_manual)
    }

    override fun route(context: Context): ActionRoute {
        val container = context.ableContainer
        return if (container.permissions.hasWriteSettingsPermission()) {
            ActionRoute.RunInPlace
        } else {
            ActionRoute.OpenScreen(container.permissions.getWriteSettingsIntent())
        }
    }

    override suspend fun execute(context: Context): ActionOutcome {
        val settings = context.ableContainer.secureSettings
        val target = !settings.isAutoBrightnessEnabled()
        if (settings.setAutoBrightnessEnabled(target) !is AbleResult.Success) return ActionOutcome.REJECTED
        return if (settings.isAutoBrightnessEnabled() == target) ActionOutcome.APPLIED else ActionOutcome.UNVERIFIED
    }
}

/** USB debugging (SYS-003). */
private object UsbDebuggingAction : CatalogAction(
    id = ActionIds.USB_DEBUGGING,
    titleRes = R.string.action_usb_debugging_title,
    descriptionRes = R.string.action_usb_debugging_desc,
    iconRes = R.drawable.ic_tile_adb,
    category = ActionCategory.SYSTEM,
    type = ActionExecutionType.IMMEDIATE_TOGGLE,
    privilege = ActionPrivilege.SECURE_SETTINGS,
    requiresUnlock = true,
) {
    override fun stateChangedUris(context: Context) = context.ableContainer.secureSettings.debuggingSettingUris()

    override val listeningPollMs: Long = SETTINGS_POLL_MS

    override suspend fun readState(context: Context): ActionState {
        val on = context.ableContainer.secureSettings.isUsbDebuggingEnabled()
        return ActionState(on, true, iconRes, on.onOffRes())
    }

    override fun route(context: Context): ActionRoute {
        val settings = context.ableContainer.secureSettings
        return if (settings.canToggleDirectly()) ActionRoute.RunInPlace else ActionRoute.OpenScreen(settings.getDevelopmentSettingsIntent())
    }

    override suspend fun execute(context: Context): ActionOutcome {
        val settings = context.ableContainer.secureSettings
        val target = !settings.isUsbDebuggingEnabled()
        DeviceActions.ensureSecureAccess(context)
        if (settings.setUsbDebuggingEnabled(target) !is AbleResult.Success) return ActionOutcome.REJECTED
        delay(300)
        return if (settings.isUsbDebuggingEnabled() == target) ActionOutcome.APPLIED else ActionOutcome.UNVERIFIED
    }
}

/**
 * Wireless debugging (SYS-003): toggles `adb_wifi_enabled` in place with WRITE_SECURE_SETTINGS
 * (self-granted through Shizuku when needed); otherwise opens Android's Wireless debugging screen.
 * Android turns it back off without Wi-Fi, so the result is always read back.
 */
private object WirelessDebuggingAction : CatalogAction(
    id = ActionIds.WIRELESS_DEBUGGING,
    titleRes = R.string.action_wireless_debugging_title,
    descriptionRes = R.string.action_wireless_debugging_desc,
    iconRes = R.drawable.ic_tile_wireless_adb,
    category = ActionCategory.SYSTEM,
    type = ActionExecutionType.IMMEDIATE_TOGGLE,
    privilege = ActionPrivilege.SECURE_SETTINGS,
    requiresUnlock = true,
) {
    override fun stateChangedUris(context: Context) = context.ableContainer.secureSettings.debuggingSettingUris()

    override val listeningPollMs: Long = SETTINGS_POLL_MS

    override suspend fun readState(context: Context): ActionState {
        val settings = context.ableContainer.secureSettings
        val on = runCatching { settings.isWirelessDebuggingEnabled() }.getOrDefault(false)
        val status = when {
            on -> R.string.action_state_on
            settings.canToggleDirectly() -> R.string.action_state_off
            else -> R.string.action_state_opens_settings
        }
        return ActionState(on, true, iconRes, status)
    }

    override fun route(context: Context): ActionRoute {
        val settings = context.ableContainer.secureSettings
        return if (settings.canToggleDirectly()) ActionRoute.RunInPlace else ActionRoute.OpenScreen(settings.getWirelessDebuggingIntent())
    }

    override suspend fun execute(context: Context): ActionOutcome {
        val settings = context.ableContainer.secureSettings
        val target = !settings.isWirelessDebuggingEnabled()
        DeviceActions.ensureSecureAccess(context)
        if (settings.setWirelessDebuggingEnabled(target) !is AbleResult.Success) return ActionOutcome.REJECTED
        repeat(10) {
            if (settings.isWirelessDebuggingEnabled() == target) return ActionOutcome.APPLIED
            delay(250)
        }
        return if (settings.isWirelessDebuggingEnabled() == target) ActionOutcome.APPLIED else ActionOutcome.UNVERIFIED
    }
}

/** Private DNS (SYS-004): Off <-> Automatic when privileged, DNS tool otherwise. */
private object PrivateDnsAction : CatalogAction(
    id = ActionIds.PRIVATE_DNS,
    titleRes = R.string.action_private_dns_title,
    descriptionRes = R.string.action_private_dns_desc,
    iconRes = R.drawable.ic_tile_dns,
    category = ActionCategory.CONNECTIVITY,
    type = ActionExecutionType.IMMEDIATE_TOGGLE,
    privilege = ActionPrivilege.SECURE_SETTINGS,
    requiresUnlock = true,
) {
    override fun stateChangedUris(context: Context) = context.ableContainer.secureSettings.privateDnsSettingUris()

    override val listeningPollMs: Long = SETTINGS_POLL_MS

    override suspend fun readState(context: Context): ActionState {
        val settings = context.ableContainer.secureSettings
        return when (settings.getPrivateDnsMode()) {
            PrivateDnsMode.OFF -> ActionState(false, true, iconRes, R.string.action_state_off)
            PrivateDnsMode.OPPORTUNISTIC -> ActionState(true, true, iconRes, R.string.action_state_automatic)
            PrivateDnsMode.HOSTNAME -> ActionState(
                active = true,
                available = true,
                iconRes = iconRes,
                statusRes = R.string.action_state_custom_dns,
                statusText = settings.getPrivateDnsSpecifier()?.takeIf { it.isNotBlank() },
            )
        }
    }

    override fun route(context: Context): ActionRoute =
        if (context.ableContainer.secureSettings.canToggleDirectly()) {
            ActionRoute.RunInPlace
        } else {
            ActionRoute.OpenScreen(DeviceActions.openToolIntent(context, ToolId.SYSTEM_TOGGLES))
        }

    override suspend fun execute(context: Context): ActionOutcome {
        val settings = context.ableContainer.secureSettings
        val next = when (settings.getPrivateDnsMode()) {
            PrivateDnsMode.OFF -> DnsProfile.Opportunistic
            PrivateDnsMode.OPPORTUNISTIC, PrivateDnsMode.HOSTNAME -> DnsProfile.Off
        }
        DeviceActions.ensureSecureAccess(context)
        if (settings.setPrivateDnsProfile(next) !is AbleResult.Success) return ActionOutcome.REJECTED
        delay(300)
        return if (settings.getPrivateDnsMode() == next.mode) ActionOutcome.APPLIED else ActionOutcome.UNVERIFIED
    }
}

/** Screen lock (LOCK-002) through the Accessibility service, setup screen otherwise. */
private object ScreenLockAction : CatalogAction(
    id = ActionIds.SCREEN_LOCK,
    titleRes = R.string.action_screen_lock_title,
    descriptionRes = R.string.action_screen_lock_desc,
    iconRes = R.drawable.ic_tile_lock,
    category = ActionCategory.SYSTEM,
    type = ActionExecutionType.TRIGGER_AUTOMATION,
    privilege = ActionPrivilege.ACCESSIBILITY,
    silentOnSuccess = true,
) {
    override suspend fun readState(context: Context): ActionState {
        val ready = context.ableContainer.screenLock.isServiceEnabled()
        return ActionState(null, true, iconRes, if (ready) R.string.action_state_lock_ready else R.string.action_state_setup_needed)
    }

    override fun route(context: Context): ActionRoute =
        if (context.ableContainer.screenLock.isServiceEnabled()) {
            ActionRoute.RunInPlace
        } else {
            ActionRoute.OpenScreen(DeviceActions.openToolIntent(context, ToolId.SCREEN_LOCK))
        }

    // Android reports whether the global action was accepted; the lock itself cannot be read back.
    override suspend fun execute(context: Context): ActionOutcome =
        if (context.ableContainer.screenLock.lockNow()) ActionOutcome.APPLIED else ActionOutcome.REJECTED
}

/**
 * Ringer mode: Sound -> Vibrate -> Silent -> Sound. Silent needs Do Not Disturb access; without
 * it the cycle skips Silent. Vibrate is skipped on devices without a vibrator.
 */
private object RingerModeAction : CatalogAction(
    id = ActionIds.RINGER_MODE,
    titleRes = R.string.action_ringer_title,
    descriptionRes = R.string.action_ringer_desc,
    iconRes = R.drawable.ic_action_ringer_normal,
    category = ActionCategory.SYSTEM,
    type = ActionExecutionType.IMMEDIATE_TOGGLE,
    privilege = ActionPrivilege.NOTIFICATION_POLICY,
) {
    override fun isSupported(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return !audio.isVolumeFixed
    }

    override suspend fun readState(context: Context): ActionState {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio == null || audio.isVolumeFixed) {
            return ActionState(null, false, iconRes, R.string.action_state_unsupported)
        }
        return when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> ActionState(false, true, R.drawable.ic_action_ringer_silent, R.string.action_state_ringer_silent)
            AudioManager.RINGER_MODE_VIBRATE -> ActionState(true, true, R.drawable.ic_action_ringer_vibrate, R.string.action_state_ringer_vibrate)
            else -> ActionState(true, true, R.drawable.ic_action_ringer_normal, R.string.action_state_ringer_normal)
        }
    }

    override fun route(context: Context): ActionRoute =
        if (isSupported(context)) ActionRoute.RunInPlace else ActionRoute.Unsupported(null)

    override suspend fun execute(context: Context): ActionOutcome {
        val audio = context.getSystemService(AudioManager::class.java) ?: return ActionOutcome.UNSUPPORTED
        val canSilence = context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
        val canVibrate = context.getSystemService(Vibrator::class.java)?.hasVibrator() == true
        val target = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> when {
                canVibrate -> AudioManager.RINGER_MODE_VIBRATE
                canSilence -> AudioManager.RINGER_MODE_SILENT
                else -> return ActionOutcome.REJECTED
            }
            AudioManager.RINGER_MODE_VIBRATE -> if (canSilence) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return try {
            audio.ringerMode = target
            delay(200)
            if (audio.ringerMode == target) ActionOutcome.APPLIED else ActionOutcome.UNVERIFIED
        } catch (_: SecurityException) {
            // Leaving Do Not Disturb-controlled modes needs notification policy access.
            ActionOutcome.REJECTED
        }
    }
}

// endregion

/** Opens an Able destination (deep link "route" extra, e.g. `tool/<key>`). */
private class OpenAbleAction(
    id: String,
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    @DrawableRes iconRes: Int,
    category: ActionCategory,
    private val tool: ToolId? = null,
    private val deepLink: String? = null,
) : CatalogAction(
    id = id,
    titleRes = titleRes,
    descriptionRes = descriptionRes,
    iconRes = iconRes,
    category = category,
    type = ActionExecutionType.LAUNCH_SCREEN,
) {
    override suspend fun readState(context: Context): ActionState =
        ActionState(null, true, iconRes, R.string.action_state_opens_able)

    override fun route(context: Context): ActionRoute = ActionRoute.OpenScreen(
        if (tool != null) DeviceActions.openToolIntent(context, tool) else DeviceActions.openAbleIntent(context, deepLink ?: "home"),
    )
}

private fun DeviceActionOutcome.toActionOutcome(): ActionOutcome = when (this) {
    DeviceActionOutcome.APPLIED -> ActionOutcome.APPLIED
    DeviceActionOutcome.REJECTED -> ActionOutcome.REJECTED
    DeviceActionOutcome.UNVERIFIED -> ActionOutcome.UNVERIFIED
    DeviceActionOutcome.UNSUPPORTED -> ActionOutcome.UNSUPPORTED
}
