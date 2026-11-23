package com.dragolabs.able.feature.tools.toggles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.dragolabs.able.core.capability.SecureSettingsAdapter
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.core.common.getOrNull
import com.dragolabs.able.core.model.DnsProfile
import com.dragolabs.able.core.model.PrivateDnsMode
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
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
    /** WRITE_SECURE_SETTINGS was granted by Able itself through Shizuku. */
    val selfGranted: Boolean = false,
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
        developerOptionsEnabled = secureSettings.isDeveloperOptionsEnabled(),
        usbDebugging = secureSettings.isUsbDebuggingEnabled(),
        wirelessDebugging = isWirelessDebuggingEnabled(),
        wifiIpAddress = wifiIpAddress(),
        dnsMode = secureSettings.getPrivateDnsMode(),
        dnsHostname = secureSettings.getPrivateDnsHostname(),
        writeSecureSettings = secureSettings.hasWriteSecureSettings(),
        selfGranted = secureSettings.wasSelfGranted(),
    )

    fun isNfcEnabled(): Boolean = secureSettings.isNfcEnabled() == true

    fun isWirelessDebuggingEnabled(): Boolean = secureSettings.isWirelessDebuggingEnabled()

    /**
     * Emits whenever a value shown on the screen changes outside Able's control: debugging and
     * Private DNS keys in `Settings.Global` (ContentObserver), screen brightness mode, and NFC
     * adapter broadcasts. Collected only while the screen is visible (WhileSubscribed).
     */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val uris = secureSettings.debuggingSettingUris() + secureSettings.privateDnsSettingUris() +
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE)
        uris.forEach { uri -> runCatching { resolver.registerContentObserver(uri, false, observer) } }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val receiverRegistered = runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.isSuccess
        awaitClose {
            runCatching { resolver.unregisterContentObserver(observer) }
            if (receiverRegistered) runCatching { context.unregisterReceiver(receiver) }
        }
    }.conflate()

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

    suspend fun setWirelessDebugging(enabled: Boolean): Boolean =
        secureSettings.setWirelessDebuggingEnabled(enabled) is AbleResult.Success

    fun matchesDns(profile: DnsProfile): Boolean {
        val mode = secureSettings.getPrivateDnsMode()
        if (mode != profile.mode) return false
        return profile.mode != PrivateDnsMode.HOSTNAME || secureSettings.getPrivateDnsHostname().equals(profile.hostname, ignoreCase = true)
    }

}

/** RFC 1123 hostname with at least one dot. Also guards the shell command used by Shizuku. */
internal fun isValidDnsHostname(hostname: String): Boolean {
    if (hostname.length !in 4..253) return false
    val label = "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
    return Regex("^$label(?:\\.$label)+$").matches(hostname)
}
