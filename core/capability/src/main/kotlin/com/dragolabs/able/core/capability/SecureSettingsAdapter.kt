package com.dragolabs.able.core.capability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.provider.Settings
import com.dragolabs.able.core.common.AbleError
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.core.model.DnsProfile
import com.dragolabs.able.core.model.PrivateDnsMode

class SecureSettingsAdapter(
    private val context: Context,
    private val shizukuAdapter: ShizukuAdapter
) {

    fun hasWriteSecureSettings(): Boolean {
        return context.checkCallingOrSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    fun canToggleDirectly(): Boolean = hasWriteSecureSettings() || shizukuAdapter.isReady()

    // --- NFC ---
    //
    // Three routes, modelled on Paul Colby's "NFC Quick Settings" (GPL-3.0-or-later,
    // https://github.com/pcolby/nfc-quick-settings, NfcTileService.kt, read 2026-11-22):
    // 1. Advanced mode: with WRITE_SECURE_SETTINGS granted through ADB, call the non-SDK
    //    NfcAdapter.enable()/disable() methods by reflection (they enforce that permission).
    // 2. Shizuku: `svc nfc enable|disable` from the shell user.
    // 3. Basic mode: open Android's NFC settings page (ACTION_NFC_SETTINGS). The NFC settings
    //    panel is not used: AOSP retires slices and redirects it to the same page.

    fun nfcAdapter(): NfcAdapter? = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()

    fun isNfcSupported(): Boolean = nfcAdapter() != null

    /** Real adapter state, or `null` when there is no adapter or the NFC service is unavailable. */
    fun isNfcEnabled(): Boolean? = nfcAdapter()?.let { adapter -> runCatching { adapter.isEnabled }.getOrNull() }

    /** How Able can change NFC right now. */
    fun nfcToggleRoute(): NfcToggleRoute = when {
        !isNfcSupported() -> NfcToggleRoute.UNSUPPORTED
        hasWriteSecureSettings() -> NfcToggleRoute.ADVANCED
        shizukuAdapter.isReady() -> NfcToggleRoute.SHIZUKU
        else -> NfcToggleRoute.SETTINGS
    }

    fun canToggleNfcDirectly(): Boolean = nfcToggleRoute().let { it == NfcToggleRoute.ADVANCED || it == NfcToggleRoute.SHIZUKU }

    /** ADB command that enables NFC advanced mode (and the other secure-settings toggles). */
    fun writeSecureSettingsAdbCommand(): String =
        "adb shell pm grant ${context.packageName} $WRITE_SECURE_SETTINGS"

    fun getNfcSettingsIntent(): Intent = Intent(Settings.ACTION_NFC_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    /**
     * Requests the NFC state change. Success means Android accepted the request; the caller must
     * still read the adapter state back (enabling is asynchronous).
     */
    suspend fun setNfcEnabled(enabled: Boolean): AbleResult<Unit> {
        val adapter = nfcAdapter() ?: return AbleResult.Failure(AbleError.HardwareUnavailable("NFC"))
        if (hasWriteSecureSettings() && invokeNfcAdapter(adapter, enabled)) {
            return AbleResult.Success(Unit)
        }
        if (shizukuAdapter.isReady()) {
            val stateCmd = if (enabled) "enable" else "disable"
            val res = shizukuAdapter.executeCommand("svc nfc $stateCmd")
            return if (res is AbleResult.Success) AbleResult.Success(Unit) else AbleResult.Failure(AbleError.Security("Failed to toggle NFC via Shizuku"))
        }
        return AbleResult.Failure(
            AbleError.PermissionMissing(WRITE_SECURE_SETTINGS, "NFC direct toggle requires WRITE_SECURE_SETTINGS (ADB) or Shizuku"),
        )
    }

    private fun invokeNfcAdapter(adapter: NfcAdapter, enable: Boolean): Boolean = try {
        val result = NfcAdapter::class.java.getMethod(if (enable) "enable" else "disable").invoke(adapter)
        result is Boolean && result
    } catch (_: Exception) {
        false
    }

    // --- Auto-brightness ---
    fun isAutoBrightnessEnabled(): Boolean {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) {
            false
        }
    }

    fun setAutoBrightnessEnabled(enabled: Boolean): AbleResult<Unit> {
        if (!Settings.System.canWrite(context)) {
            return AbleResult.Failure(AbleError.PermissionMissing("WRITE_SETTINGS"))
        }
        val mode = if (enabled) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        }
        val success = Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            mode
        )
        return if (success) AbleResult.Success(Unit) else AbleResult.Failure(AbleError.Security("Failed to update auto brightness"))
    }

    // --- USB Debugging ---
    fun isUsbDebuggingEnabled(): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    }

    suspend fun setUsbDebuggingEnabled(enabled: Boolean): AbleResult<Unit> {
        val value = if (enabled) 1 else 0
        if (hasWriteSecureSettings()) {
            val success = Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, value)
            return if (success) AbleResult.Success(Unit) else AbleResult.Failure(AbleError.Security("Failed to write ADB_ENABLED"))
        }
        if (shizukuAdapter.isReady()) {
            val res = shizukuAdapter.executeCommand("settings put global adb_enabled $value")
            return if (res is AbleResult.Success) AbleResult.Success(Unit) else AbleResult.Failure(AbleError.Security("Failed to toggle ADB via Shizuku"))
        }
        return AbleResult.Failure(AbleError.PermissionMissing("WRITE_SECURE_SETTINGS"))
    }

    fun getDevelopmentSettingsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    // --- Wireless debugging ---

    /**
     * Destinations for "Wireless debugging", most specific first. There is no public action for
     * the screen. AOSP Settings (DevelopmentSettingsDashboardFragment.handleQsTileLongPressActionIfAny,
     * Android 11+, checked on main 2026-11-22 and on an API 36 emulator) opens the Wireless
     * debugging page when Developer options receive ACTION_QS_TILE_PREFERENCES with the component
     * of its own Wireless debugging tile. Fallbacks: Developer options with the switch highlighted,
     * Developer options, then Settings.
     */
    fun getWirelessDebuggingIntents(): List<Intent> = listOf(
        Intent(ACTION_QS_TILE_PREFERENCES)
            .setPackage(SETTINGS_PACKAGE)
            .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(SETTINGS_PACKAGE, WIRELESS_DEBUGGING_TILE))
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, WIRELESS_DEBUGGING_KEY),
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).putExtra(EXTRA_FRAGMENT_ARG_KEY, WIRELESS_DEBUGGING_KEY),
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    ).onEach { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** First [getWirelessDebuggingIntents] entry Android can resolve (for tiles and widgets). */
    fun getWirelessDebuggingIntent(): Intent {
        val intents = getWirelessDebuggingIntents()
        return intents.firstOrNull { intent ->
            runCatching { context.packageManager.resolveActivity(intent, 0) != null }.getOrDefault(false)
        } ?: intents.last()
    }

    // --- Private DNS ---
    fun getPrivateDnsMode(): PrivateDnsMode {
        val modeStr = Settings.Global.getString(context.contentResolver, "private_dns_mode") ?: "off"
        return when (modeStr) {
            "hostname" -> PrivateDnsMode.HOSTNAME
            "opportunistic" -> PrivateDnsMode.OPPORTUNISTIC
            else -> PrivateDnsMode.OFF
        }
    }

    fun getPrivateDnsHostname(): String = Settings.Global.getString(context.contentResolver, "private_dns_specifier") ?: ""
    fun getPrivateDnsSpecifier(): String? = Settings.Global.getString(context.contentResolver, "private_dns_specifier")

    suspend fun setPrivateDnsProfile(profile: DnsProfile): AbleResult<Unit> {
        if (hasWriteSecureSettings()) {
            val modeSuccess = Settings.Global.putString(context.contentResolver, "private_dns_mode", profile.mode.systemValue)
            if (profile.mode == PrivateDnsMode.HOSTNAME) {
                Settings.Global.putString(context.contentResolver, "private_dns_specifier", profile.hostname)
            }
            return if (modeSuccess) AbleResult.Success(Unit) else AbleResult.Failure(AbleError.Security("Failed to set private DNS"))
        }
        if (shizukuAdapter.isReady()) {
            val modeRes = shizukuAdapter.executeCommand("settings put global private_dns_mode ${profile.mode.systemValue}")
            if (profile.mode == PrivateDnsMode.HOSTNAME) {
                shizukuAdapter.executeCommand("settings put global private_dns_specifier ${profile.hostname}")
            }
            return if (modeRes is AbleResult.Success) AbleResult.Success(Unit) else AbleResult.Failure(AbleError.Security("Failed to set private DNS via Shizuku"))
        }
        return AbleResult.Failure(AbleError.PermissionMissing("WRITE_SECURE_SETTINGS"))
    }

    fun getNetworkSettingsIntent(): Intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    private companion object {
        const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val ACTION_QS_TILE_PREFERENCES = "android.service.quicksettings.action.QS_TILE_PREFERENCES"
        const val WIRELESS_DEBUGGING_TILE = "com.android.settings.development.qstile.DevelopmentTiles\$WirelessDebugging"
        const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        const val WIRELESS_DEBUGGING_KEY = "toggle_adb_wireless"
    }
}

/** How NFC can be changed (SYS-001). */
enum class NfcToggleRoute {
    /** No NFC adapter. */
    UNSUPPORTED,

    /** WRITE_SECURE_SETTINGS granted through ADB: NfcAdapter.enable()/disable(). */
    ADVANCED,

    /** Shizuku shell: `svc nfc enable|disable`. */
    SHIZUKU,

    /** Basic mode: Android's NFC settings page. */
    SETTINGS,
}
