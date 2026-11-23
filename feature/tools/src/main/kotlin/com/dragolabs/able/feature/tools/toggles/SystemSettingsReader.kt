package com.dragolabs.able.feature.tools.toggles

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import com.dragolabs.able.core.capability.SecureSettingsAdapter
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.core.common.getOrNull
import com.dragolabs.able.core.model.DnsProfile
import com.dragolabs.able.core.model.PrivateDnsMode
import java.net.Inet4Address

/** Snapshot of the system values shown by the System toggles screen. */
data class SystemSnapshot(
    val nfcSupported: Boolean,
    val nfcEnabled: Boolean,
    val writeSettingsGranted: Boolean,
    val autoBrightness: Boolean,
    val developerOptionsEnabled: Boolean,
    val usbDebugging: Boolean,
    val wirelessDebugging: Boolean,
    val wifiIpAddress: String?,
    val dnsMode: PrivateDnsMode,
    val dnsHostname: String,
    val writeSecureSettings: Boolean,
)

/**
 * Feature-local reader/writer for the settings the core adapter does not cover (wireless
 * debugging, developer options, Wi-Fi address). Reads never need privileges; writes go through
 * `WRITE_SECURE_SETTINGS` or Shizuku and must be read back by the caller.
 */
class SystemSettingsReader(
    private val context: Context,
    private val secureSettings: SecureSettingsAdapter,
    private val shizuku: ShizukuAdapter,
) {
    private val resolver = context.contentResolver

    fun snapshot(): SystemSnapshot = SystemSnapshot(
        nfcSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC) && secureSettings.isNfcSupported(),
        nfcEnabled = isNfcEnabled(),
        writeSettingsGranted = Settings.System.canWrite(context),
        autoBrightness = secureSettings.isAutoBrightnessEnabled(),
        developerOptionsEnabled = globalInt(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1,
        usbDebugging = secureSettings.isUsbDebuggingEnabled(),
        wirelessDebugging = isWirelessDebuggingEnabled(),
        wifiIpAddress = wifiIpAddress(),
        dnsMode = secureSettings.getPrivateDnsMode(),
        dnsHostname = secureSettings.getPrivateDnsHostname(),
        writeSecureSettings = secureSettings.hasWriteSecureSettings(),
    )

    fun isNfcEnabled(): Boolean = secureSettings.isNfcEnabled() == true

    fun isWirelessDebuggingEnabled(): Boolean = globalInt(ADB_WIFI_ENABLED) == 1

    private fun globalInt(name: String): Int = runCatching { Settings.Global.getInt(resolver, name, 0) }.getOrDefault(0)

    /** IPv4 address of the current Wi-Fi network, shown next to the wireless debugging port. */
    private fun wifiIpAddress(): String? = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching null
        cm.allNetworks.firstNotNullOfOrNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@firstNotNullOfOrNull null
            cm.getLinkProperties(network)?.linkAddresses
                ?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }
    }.getOrNull()

    /** The TLS port of wireless debugging is only readable through the shell (Shizuku). */
    suspend fun wirelessDebuggingPort(): String? {
        if (!shizuku.isReady()) return null
        return shizuku.executeCommand("getprop service.adb.tls.port").getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "0" && it.all(Char::isDigit) }
    }

    suspend fun setWirelessDebugging(enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        if (secureSettings.hasWriteSecureSettings()) {
            return runCatching { Settings.Global.putInt(resolver, ADB_WIFI_ENABLED, value) }.getOrDefault(false)
        }
        if (shizuku.isReady()) {
            return shizuku.executeCommand("settings put global $ADB_WIFI_ENABLED $value") is AbleResult.Success
        }
        return false
    }

    fun matchesDns(profile: DnsProfile): Boolean {
        val mode = secureSettings.getPrivateDnsMode()
        if (mode != profile.mode) return false
        return profile.mode != PrivateDnsMode.HOSTNAME || secureSettings.getPrivateDnsHostname().equals(profile.hostname, ignoreCase = true)
    }

    private companion object {
        const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    }
}

/** RFC 1123 hostname with at least one dot. Also guards the shell command used by Shizuku. */
internal fun isValidDnsHostname(hostname: String): Boolean {
    if (hostname.length !in 4..253) return false
    val label = "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
    return Regex("^$label(?:\\.$label)+$").matches(hostname)
}
