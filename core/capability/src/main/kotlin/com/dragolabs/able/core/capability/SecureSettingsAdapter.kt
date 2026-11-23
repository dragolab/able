package com.dragolabs.able.core.capability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.provider.Settings
import com.dragolabs.able.core.common.AbleError
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.core.model.DnsProfile
import com.dragolabs.able.core.model.PrivateDnsMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationTargetException

class SecureSettingsAdapter(
    private val context: Context,
    private val shizukuAdapter: ShizukuAdapter
) {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    fun hasWriteSecureSettings(): Boolean {
        return context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    /** Secure settings can be changed now, or right after Able grants itself the permission via Shizuku. */
    fun canToggleDirectly(): Boolean = secureAccessRoute() != SecureAccessRoute.SETTINGS

    fun secureAccessRoute(): SecureAccessRoute =
        selectSecureAccessRoute(hasWriteSecureSettings = hasWriteSecureSettings(), shizukuReady = shizukuAdapter.isReady())

    /** `true` when the current WRITE_SECURE_SETTINGS grant was made by Able itself through Shizuku. */
    fun wasSelfGranted(): Boolean {
        if (hasWriteSecureSettings()) return prefs.getBoolean(KEY_SELF_GRANTED, false)
        // Revoked since: a later ADB grant must not be reported as Able's own.
        if (prefs.getBoolean(KEY_SELF_GRANTED, false)) prefs.edit().remove(KEY_SELF_GRANTED).apply()
        return false
    }

    /**
     * Makes sure Able holds WRITE_SECURE_SETTINGS. When it does not and Shizuku is connected, Able
     * grants the permission to itself with `pm grant` from the shell user (the same command the
     * ADB instructions use) and reads it back with checkSelfPermission. Only called from a user
     * action that needs the permission. Callers log [SelfGrantResult.GRANTED_VIA_SHIZUKU] and
     * [SelfGrantResult.GRANT_FAILED] in the action log.
     */
    suspend fun ensureWriteSecureSettings(): SelfGrantResult {
        if (hasWriteSecureSettings()) return SelfGrantResult.ALREADY_GRANTED
        if (!shizukuAdapter.isReady()) return SelfGrantResult.UNAVAILABLE
        val pkg = context.packageName
        if (!PACKAGE_NAME_PATTERN.matches(pkg)) return SelfGrantResult.GRANT_FAILED
        shizukuAdapter.executeCommand("pm grant $pkg $WRITE_SECURE_SETTINGS")
        // Read back: the exit code alone is not proof that the grant happened.
        return if (hasWriteSecureSettings()) {
            prefs.edit().putBoolean(KEY_SELF_GRANTED, true).apply()
            SelfGrantResult.GRANTED_VIA_SHIZUKU
        } else {
            SelfGrantResult.GRANT_FAILED
        }
    }

    /** ADB command that enables NFC advanced mode (and the other secure-settings toggles). */
    fun writeSecureSettingsAdbCommand(): String =
        "adb shell pm grant ${context.packageName} $WRITE_SECURE_SETTINGS"

    /** Shell command that withdraws the permission again (ADB or Shizuku shell). */
    fun writeSecureSettingsRevokeCommand(): String =
        "adb shell pm revoke ${context.packageName} $WRITE_SECURE_SETTINGS"

    // --- NFC ---
    //
    // Modelled on Paul Colby's "NFC Quick Settings" (GPL-3.0-or-later,
    // https://github.com/pcolby/nfc-quick-settings, NfcTileService.kt, read 2026-11-22):
    // 1. Advanced mode: with WRITE_SECURE_SETTINGS, Able's own process calls NfcAdapter.enable()/
    //    disable() by reflection. On Android 16 these are flagged public APIs
    //    (Flags.FLAG_NFC_STATE_CHANGE) that pass mContext.getPackageName() to INfcAdapter
    //    (AOSP packages/modules/Nfc, framework/java/android/nfc/NfcAdapter.java, main, read
    //    2026-11-23); older releases expose the same no-argument signatures as hidden/system APIs.
    // 2. Shizuku connected but no permission: Able grants itself WRITE_SECURE_SETTINGS through the
    //    Shizuku shell, then uses route 1. `svc nfc` is not used: on Android 16 it calls
    //    NfcAdapter with the package "android" from uid 2000 and the NFC service throws
    //    "SecurityException: Package android does not belong to 2000" (observed on a Pixel 8 Pro,
    //    Android 16, 2026-11-23). The self-grant works on every supported release.
    // 3. Basic mode: open Android's NFC settings page (ACTION_NFC_SETTINGS). The NFC settings
    //    panel is not used: AOSP retires slices and redirects it to the same page.

    fun nfcAdapter(): NfcAdapter? = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()

    fun isNfcSupported(): Boolean = nfcAdapter() != null

    /** Real adapter state, or `null` when there is no adapter or the NFC service is unavailable. */
    fun isNfcEnabled(): Boolean? = nfcAdapter()?.let { adapter -> runCatching { adapter.isEnabled }.getOrNull() }

    /** How Able can change NFC right now. */
    fun nfcToggleRoute(): NfcToggleRoute = selectNfcRoute(
        nfcSupported = isNfcSupported(),
        hasWriteSecureSettings = hasWriteSecureSettings(),
        shizukuReady = shizukuAdapter.isReady(),
    )

    fun canToggleNfcDirectly(): Boolean = nfcToggleRoute().isDirect

    fun getNfcSettingsIntent(): Intent = Intent(Settings.ACTION_NFC_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    /**
     * Requests the NFC state change from Able's own process. Success means Android accepted the
     * request; the caller must still read the adapter state back (enabling is asynchronous).
     */
    suspend fun setNfcEnabled(enabled: Boolean): AbleResult<Unit> {
        val adapter = nfcAdapter() ?: return AbleResult.Failure(AbleError.HardwareUnavailable("NFC"))
        if (ensureWriteSecureSettings() !in GRANTED) {
            return AbleResult.Failure(
                AbleError.PermissionMissing(WRITE_SECURE_SETTINGS, "NFC direct toggle requires WRITE_SECURE_SETTINGS (ADB) or Shizuku"),
            )
        }
        return when (val result = withContext(Dispatchers.IO) { invokeNfcAdapter(adapter, enabled) }) {
            NfcInvokeResult.ACCEPTED -> AbleResult.Success(Unit)
            NfcInvokeResult.REFUSED -> AbleResult.Failure(AbleError.Security("NfcAdapter refused the change"))
            NfcInvokeResult.SECURITY_EXCEPTION -> AbleResult.Failure(AbleError.Security("NfcAdapter threw SecurityException"))
            NfcInvokeResult.NO_METHOD -> AbleResult.Failure(AbleError.Security("NfcAdapter.${if (enabled) "enable" else "disable"} is not available"))
            NfcInvokeResult.ERROR -> AbleResult.Failure(AbleError.Security("NfcAdapter call failed: $result"))
        }
    }

    private fun invokeNfcAdapter(adapter: NfcAdapter, enable: Boolean): NfcInvokeResult {
        val cls = NfcAdapter::class.java
        // Prefer the no-argument form; fall back to disable(boolean persist) where only it exists.
        val call: (() -> Any?)? = if (enable) {
            runCatching { cls.getMethod("enable") }.getOrNull()?.let { m -> { m.invoke(adapter) } }
        } else {
            runCatching { cls.getMethod("disable") }.getOrNull()?.let { m -> { m.invoke(adapter) } }
                ?: runCatching { cls.getMethod("disable", java.lang.Boolean.TYPE) }.getOrNull()?.let { m -> { m.invoke(adapter, true) } }
        }
        if (call == null) return NfcInvokeResult.NO_METHOD
        return try {
            mapNfcInvokeReturn(call())
        } catch (e: InvocationTargetException) {
            if (e.targetException is SecurityException) NfcInvokeResult.SECURITY_EXCEPTION else NfcInvokeResult.ERROR
        } catch (_: SecurityException) {
            NfcInvokeResult.SECURITY_EXCEPTION
        } catch (_: Exception) {
            NfcInvokeResult.ERROR
        }
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
    fun isUsbDebuggingEnabled(): Boolean = globalInt(Settings.Global.ADB_ENABLED) == 1

    fun isDeveloperOptionsEnabled(): Boolean = globalInt(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1

    suspend fun setUsbDebuggingEnabled(enabled: Boolean): AbleResult<Unit> =
        putGlobal(Settings.Global.ADB_ENABLED, if (enabled) "1" else "0")

    // --- Wireless debugging ---

    fun isWirelessDebuggingEnabled(): Boolean = globalInt(ADB_WIFI_ENABLED) == 1

    /** Android turns wireless debugging back off when no Wi-Fi network is connected: read it back. */
    suspend fun setWirelessDebuggingEnabled(enabled: Boolean): AbleResult<Unit> =
        putGlobal(ADB_WIFI_ENABLED, if (enabled) "1" else "0")

    /** `Settings.Global` keys whose changes the debugging toggles observe (ContentObserver). */
    fun debuggingSettingUris(): List<Uri> = listOf(
        Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
        Settings.Global.getUriFor(ADB_WIFI_ENABLED),
        Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
    )

    fun privateDnsSettingUris(): List<Uri> = listOf(
        Settings.Global.getUriFor(PRIVATE_DNS_MODE),
        Settings.Global.getUriFor(PRIVATE_DNS_SPECIFIER),
    )

    private fun globalInt(name: String): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, name, 0) }.getOrDefault(0)

    /**
     * Writes a `Settings.Global` value: directly with WRITE_SECURE_SETTINGS (self-granted through
     * Shizuku when needed), or through the Shizuku shell `settings` command if the grant failed.
     */
    private suspend fun putGlobal(name: String, value: String): AbleResult<Unit> {
        val access = ensureWriteSecureSettings()
        if (access in GRANTED) {
            val ok = runCatching { Settings.Global.putString(context.contentResolver, name, value) }.getOrDefault(false)
            return if (ok) AbleResult.Success(Unit) else AbleResult.Failure(AbleError.Security("Failed to write $name"))
        }
        if (shizukuAdapter.isReady()) {
            val res = shizukuAdapter.executeCommand("settings put global $name $value")
            return if (res is AbleResult.Success) AbleResult.Success(Unit) else AbleResult.Failure(AbleError.Security("Failed to write $name via Shizuku"))
        }
        return AbleResult.Failure(AbleError.PermissionMissing(WRITE_SECURE_SETTINGS))
    }

    fun getDevelopmentSettingsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    // --- Wireless debugging screen ---

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
        val modeStr = Settings.Global.getString(context.contentResolver, PRIVATE_DNS_MODE) ?: "off"
        return when (modeStr) {
            "hostname" -> PrivateDnsMode.HOSTNAME
            "opportunistic" -> PrivateDnsMode.OPPORTUNISTIC
            else -> PrivateDnsMode.OFF
        }
    }

    fun getPrivateDnsHostname(): String = Settings.Global.getString(context.contentResolver, PRIVATE_DNS_SPECIFIER) ?: ""
    fun getPrivateDnsSpecifier(): String? = Settings.Global.getString(context.contentResolver, PRIVATE_DNS_SPECIFIER)

    suspend fun setPrivateDnsProfile(profile: DnsProfile): AbleResult<Unit> {
        // The hostname is validated by the caller (RFC 1123) before it can reach the shell fallback.
        if (profile.mode == PrivateDnsMode.HOSTNAME) {
            val host = putGlobal(PRIVATE_DNS_SPECIFIER, profile.hostname)
            if (host !is AbleResult.Success) return host
        }
        return putGlobal(PRIVATE_DNS_MODE, profile.mode.systemValue)
    }

    fun getNetworkSettingsIntent(): Intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    private companion object {
        const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
        const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
        const val PRIVATE_DNS_MODE = "private_dns_mode"
        const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"
        const val PREFS_NAME = "able_secure_access"
        const val KEY_SELF_GRANTED = "wss_self_granted"
        val PACKAGE_NAME_PATTERN = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
        val GRANTED = setOf(SelfGrantResult.ALREADY_GRANTED, SelfGrantResult.GRANTED_VIA_SHIZUKU)
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val ACTION_QS_TILE_PREFERENCES = "android.service.quicksettings.action.QS_TILE_PREFERENCES"
        const val WIRELESS_DEBUGGING_TILE = "com.android.settings.development.qstile.DevelopmentTiles\$WirelessDebugging"
        const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        const val WIRELESS_DEBUGGING_KEY = "toggle_adb_wireless"
    }
}

/** How NFC can be changed (SYS-001). */
enum class NfcToggleRoute(val isDirect: Boolean) {
    /** No NFC adapter. */
    UNSUPPORTED(false),

    /** WRITE_SECURE_SETTINGS held: NfcAdapter.enable()/disable() in Able's own process. */
    ADVANCED(true),

    /** Shizuku connected: Able grants itself WRITE_SECURE_SETTINGS, then uses [ADVANCED]. */
    SHIZUKU_SELF_GRANT(true),

    /** Basic mode: Android's NFC settings page. */
    SETTINGS(false),
}

/** How Able can change `Settings.Global` secure values (USB/wireless debugging, Private DNS). */
enum class SecureAccessRoute { DIRECT, SHIZUKU_SELF_GRANT, SETTINGS }

/** Result of [SecureSettingsAdapter.ensureWriteSecureSettings]. */
enum class SelfGrantResult { ALREADY_GRANTED, GRANTED_VIA_SHIZUKU, GRANT_FAILED, UNAVAILABLE }

/** Outcome of the reflective NfcAdapter call. */
enum class NfcInvokeResult { ACCEPTED, REFUSED, SECURITY_EXCEPTION, NO_METHOD, ERROR }

/** Live NFC adapter state for tiles and rows, including Android's asynchronous transitions. */
enum class NfcLiveState(val enabled: Boolean, val transitioning: Boolean) {
    OFF(false, false),
    TURNING_ON(true, true),
    ON(true, false),
    TURNING_OFF(false, true),
}

fun selectSecureAccessRoute(hasWriteSecureSettings: Boolean, shizukuReady: Boolean): SecureAccessRoute = when {
    hasWriteSecureSettings -> SecureAccessRoute.DIRECT
    shizukuReady -> SecureAccessRoute.SHIZUKU_SELF_GRANT
    else -> SecureAccessRoute.SETTINGS
}

fun selectNfcRoute(nfcSupported: Boolean, hasWriteSecureSettings: Boolean, shizukuReady: Boolean): NfcToggleRoute = when {
    !nfcSupported -> NfcToggleRoute.UNSUPPORTED
    else -> when (selectSecureAccessRoute(hasWriteSecureSettings, shizukuReady)) {
        SecureAccessRoute.DIRECT -> NfcToggleRoute.ADVANCED
        SecureAccessRoute.SHIZUKU_SELF_GRANT -> NfcToggleRoute.SHIZUKU_SELF_GRANT
        SecureAccessRoute.SETTINGS -> NfcToggleRoute.SETTINGS
    }
}

/** NfcAdapter.enable()/disable() return `true` when the request was accepted. */
fun mapNfcInvokeReturn(value: Any?): NfcInvokeResult =
    if (value == true) NfcInvokeResult.ACCEPTED else NfcInvokeResult.REFUSED

/**
 * Maps `NfcAdapter.EXTRA_ADAPTER_STATE` (STATE_OFF 1, STATE_TURNING_ON 2, STATE_ON 3,
 * STATE_TURNING_OFF 4) to [NfcLiveState]; `null` for unknown values.
 */
fun mapNfcAdapterState(state: Int): NfcLiveState? = when (state) {
    1 -> NfcLiveState.OFF
    2 -> NfcLiveState.TURNING_ON
    3 -> NfcLiveState.ON
    4 -> NfcLiveState.TURNING_OFF
    else -> null
}
