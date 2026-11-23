package com.dragolabs.able.feature.tools.toggles

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.capability.SecureSettingsAdapter
import com.dragolabs.able.core.capability.SelfGrantResult
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.core.data.ActionLogRepository
import com.dragolabs.able.core.database.dao.DnsDao
import com.dragolabs.able.core.database.entity.DnsProfileEntity
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.core.model.DnsProfile
import com.dragolabs.able.core.model.PrivateDnsMode
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.UiMessage
import com.dragolabs.able.feature.tools.common.awaitReadBack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** What a toggle row shows: the requested value while Android is still applying it. */
@Immutable
data class ToggleDisplay(val checked: Boolean, val transitioning: Boolean)

/** Maps the real value and the pending request (if any) to what the row shows. */
internal fun toggleDisplay(actual: Boolean, target: Boolean?): ToggleDisplay = when {
    target == null || target == actual -> ToggleDisplay(checked = actual, transitioning = false)
    else -> ToggleDisplay(checked = target, transitioning = true)
}

private const val SELF_GRANT_LOG_ID = "system.write_secure_settings.self_grant"

enum class ToggleKey { NFC, AUTO_BRIGHTNESS, USB_DEBUGGING, WIRELESS_DEBUGGING, PRIVATE_DNS }

/** Android settings screen the route should open (fallback tier). */
enum class SettingsTarget { NFC, WRITE_SETTINGS, DEVELOPER_OPTIONS, WIRELESS_DEBUGGING, PRIVATE_DNS }

/** Changes that may cut Able's own privileged connection and need explicit confirmation. */
enum class PendingConfirm { USB_DEBUGGING_OFF, WIRELESS_DEBUGGING_OFF }

@Immutable
data class SystemTogglesUiState(
    val snapshot: SystemSnapshot? = null,
    val shizuku: ShizukuConnectionState = ShizukuConnectionState.NOT_AVAILABLE,
    val wirelessPort: String? = null,
    val presetProfiles: List<DnsProfile> = DnsProfile.presets(),
    val customProfiles: List<DnsProfile> = emptyList(),
    val busy: Set<ToggleKey> = emptySet(),
    /** Value requested by the last tap, shown ("Turning on…") until the read-back settles. */
    val targets: Map<ToggleKey, Boolean> = emptyMap(),
    val pendingConfirm: PendingConfirm? = null,
    val showAddDnsDialog: Boolean = false,
    val openSettings: SettingsTarget? = null,
    val message: UiMessage? = null,
) {
    val shizukuReady: Boolean get() = shizuku == ShizukuConnectionState.READY

    /** Direct tier for secure settings: `WRITE_SECURE_SETTINGS` granted or Shizuku connected. */
    val directSecure: Boolean get() = shizukuReady || snapshot?.writeSecureSettings == true

    /**
     * NFC has no settings key: advanced mode calls NfcAdapter from Able's process with
     * `WRITE_SECURE_SETTINGS` (granted through ADB, or self-granted through Shizuku on first use).
     */
    val directNfc: Boolean get() = snapshot?.nfcSupported == true && directSecure

    fun display(key: ToggleKey): ToggleDisplay? {
        val s = snapshot ?: return null
        val actual = when (key) {
            ToggleKey.NFC -> s.nfcEnabled
            ToggleKey.AUTO_BRIGHTNESS -> s.autoBrightness
            ToggleKey.USB_DEBUGGING -> s.usbDebugging
            ToggleKey.WIRELESS_DEBUGGING -> s.wirelessDebugging
            ToggleKey.PRIVATE_DNS -> return null
        }
        return toggleDisplay(actual, targets[key])
    }

    /** Saved profile matching the current system configuration, if any. */
    val activeProfileId: String?
        get() {
            val s = snapshot ?: return null
            return (presetProfiles + customProfiles).firstOrNull { p ->
                p.mode == s.dnsMode && (p.mode != PrivateDnsMode.HOSTNAME || p.hostname.equals(s.dnsHostname, ignoreCase = true))
            }?.id
        }
}

class SystemTogglesViewModel(
    private val reader: SystemSettingsReader,
    private val secureSettings: SecureSettingsAdapter,
    shizukuState: StateFlow<ShizukuConnectionState>,
    private val dnsDao: DnsDao,
    private val actionLog: ActionLogRepository,
    private val logScope: CoroutineScope,
    private val describe: (UiMessage) -> String,
    private val refreshAccess: () -> Unit,
) : ViewModel() {

    private val local = MutableStateFlow(SystemTogglesUiState(snapshot = runCatching { reader.snapshot() }.getOrNull()))

    private val customProfiles = dnsDao.getAllProfiles()
        .map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }
        .catch { emit(emptyList()) }

    /**
     * Live system values: re-read whenever Android reports a change (adb_enabled,
     * adb_wifi_enabled, development_settings_enabled, Private DNS, brightness mode, NFC adapter),
     * whether it came from Android settings, from Able or from the system itself (wireless
     * debugging turns off when Wi-Fi changes). Active only while the screen collects [uiState].
     */
    private val liveChanges = reader.changes()
        .onEach { refreshSnapshot(withPort = true) }
        .onStart { emit(Unit) }
        .catch { emit(Unit) }

    val uiState: StateFlow<SystemTogglesUiState> = combine(local, shizukuState, customProfiles, liveChanges) { l, shizuku, custom, _ ->
        l.copy(shizuku = shizuku, customProfiles = custom)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    private fun refreshSnapshot(withPort: Boolean) {
        local.update { it.copy(snapshot = runCatching { reader.snapshot() }.getOrNull() ?: it.snapshot) }
        if (withPort) {
            viewModelScope.launch {
                val port = if (reader.isWirelessDebuggingEnabled()) reader.wirelessDebuggingPort() else null
                local.update { it.copy(wirelessPort = port) }
            }
        }
    }

    /** Re-read every value on resume: Android does not broadcast most of these changes. */
    fun refresh() {
        refreshAccess()
        refreshSnapshot(withPort = true)
    }

    // --- NFC (SYS-001) ---

    fun setNfc(enabled: Boolean) {
        if (!uiState.value.directNfc) return openSettings(SettingsTarget.NFC)
        apply(ToggleKey.NFC, "tools.system.nfc", readBackTimeoutMs = 6_000, expected = enabled, target = enabled, read = reader::isNfcEnabled) {
            ensureSecureAccess() && secureSettings.setNfcEnabled(enabled) is AbleResult.Success
        }
    }

    // --- Auto-brightness (SYS-002) ---

    fun setAutoBrightness(enabled: Boolean) {
        if (local.value.snapshot?.writeSettingsGranted != true) {
            local.update { it.copy(message = UiMessage(R.string.tools_toggles_write_settings_needed)) }
            return openSettings(SettingsTarget.WRITE_SETTINGS)
        }
        apply(ToggleKey.AUTO_BRIGHTNESS, "tools.system.auto_brightness", expected = enabled, target = enabled, read = secureSettings::isAutoBrightnessEnabled) {
            secureSettings.setAutoBrightnessEnabled(enabled) is AbleResult.Success
        }
    }

    // --- USB / wireless debugging (SYS-003) ---

    fun setUsbDebugging(enabled: Boolean, confirmed: Boolean = false) {
        val state = uiState.value
        if (!state.directSecure) return openSettings(SettingsTarget.DEVELOPER_OPTIONS)
        if (!enabled && state.shizukuReady && !confirmed) {
            local.update { it.copy(pendingConfirm = PendingConfirm.USB_DEBUGGING_OFF) }
            return
        }
        apply(ToggleKey.USB_DEBUGGING, "tools.system.usb_debugging", expected = enabled, target = enabled, read = secureSettings::isUsbDebuggingEnabled) {
            ensureSecureAccess()
            secureSettings.setUsbDebuggingEnabled(enabled) is AbleResult.Success
        }
    }

    fun setWirelessDebugging(enabled: Boolean, confirmed: Boolean = false) {
        val state = uiState.value
        if (!state.directSecure) return openSettings(SettingsTarget.WIRELESS_DEBUGGING)
        if (!enabled && state.shizukuReady && !confirmed) {
            local.update { it.copy(pendingConfirm = PendingConfirm.WIRELESS_DEBUGGING_OFF) }
            return
        }
        apply(ToggleKey.WIRELESS_DEBUGGING, "tools.system.wireless_debugging", readBackTimeoutMs = 5_000, expected = enabled, target = enabled, read = reader::isWirelessDebuggingEnabled) {
            ensureSecureAccess()
            reader.setWirelessDebugging(enabled)
        }
    }

    fun confirmPending() {
        when (local.value.pendingConfirm) {
            PendingConfirm.USB_DEBUGGING_OFF -> setUsbDebugging(enabled = false, confirmed = true)
            PendingConfirm.WIRELESS_DEBUGGING_OFF -> setWirelessDebugging(enabled = false, confirmed = true)
            null -> Unit
        }
        local.update { it.copy(pendingConfirm = null) }
    }

    fun dismissPending() = local.update { it.copy(pendingConfirm = null) }

    fun openDeveloperOptions() = openSettings(SettingsTarget.DEVELOPER_OPTIONS)

    fun openWirelessDebugging() = openSettings(SettingsTarget.WIRELESS_DEBUGGING)

    /** Wireless debugging screen first, then broader fallbacks (see SecureSettingsAdapter). */
    fun wirelessDebuggingIntents() = secureSettings.getWirelessDebuggingIntents()

    /** ADB command that grants `WRITE_SECURE_SETTINGS` (NFC advanced mode). */
    val adbGrantCommand: String get() = secureSettings.writeSecureSettingsAdbCommand()

    /** Command that withdraws `WRITE_SECURE_SETTINGS` again. */
    val adbRevokeCommand: String get() = secureSettings.writeSecureSettingsRevokeCommand()

    /**
     * With Shizuku connected and `WRITE_SECURE_SETTINGS` missing, Able grants itself the
     * permission (read back with checkSelfPermission) and records it in the action log.
     * Returns whether the permission is held now.
     */
    private suspend fun ensureSecureAccess(): Boolean {
        val result = runCatching { secureSettings.ensureWriteSecureSettings() }.getOrDefault(SelfGrantResult.GRANT_FAILED)
        when (result) {
            SelfGrantResult.GRANTED_VIA_SHIZUKU -> {
                logScope.launch { actionLog.log(SELF_GRANT_LOG_ID, ActionExecutionStatus.SUCCESS, "pm grant via Shizuku; checkSelfPermission = granted") }
                refreshAccess()
            }
            SelfGrantResult.GRANT_FAILED ->
                logScope.launch { actionLog.log(SELF_GRANT_LOG_ID, ActionExecutionStatus.FAILED, "pm grant via Shizuku; checkSelfPermission = denied") }
            SelfGrantResult.ALREADY_GRANTED, SelfGrantResult.UNAVAILABLE -> Unit
        }
        return result == SelfGrantResult.ALREADY_GRANTED || result == SelfGrantResult.GRANTED_VIA_SHIZUKU
    }

    fun reportCopied() = local.update { it.copy(message = UiMessage(com.dragolabs.able.core.designsystem.R.string.ds_copied)) }

    // --- Private DNS (SYS-004) ---

    fun applyDnsProfile(profile: DnsProfile) {
        if (!uiState.value.directSecure) return openSettings(SettingsTarget.PRIVATE_DNS)
        if (profile.mode == PrivateDnsMode.HOSTNAME && !isValidDnsHostname(profile.hostname)) {
            local.update { it.copy(message = UiMessage(R.string.tools_dns_invalid_hostname)) }
            return
        }
        apply(ToggleKey.PRIVATE_DNS, "tools.system.private_dns", expected = true, read = { reader.matchesDns(profile) }) {
            ensureSecureAccess()
            secureSettings.setPrivateDnsProfile(profile) is AbleResult.Success
        }
    }

    fun openPrivateDnsSettings() = openSettings(SettingsTarget.PRIVATE_DNS)

    fun showAddDns(show: Boolean) = local.update { it.copy(showAddDnsDialog = show) }

    fun addDnsProfile(name: String, hostname: String) {
        val host = hostname.trim().lowercase()
        if (!isValidDnsHostname(host)) {
            local.update { it.copy(message = UiMessage(R.string.tools_dns_invalid_hostname)) }
            return
        }
        val profile = DnsProfile(
            id = "custom-${UUID.randomUUID()}",
            name = name.trim().ifEmpty { host },
            mode = PrivateDnsMode.HOSTNAME,
            hostname = host,
        )
        local.update { it.copy(showAddDnsDialog = false) }
        viewModelScope.launch {
            val saved = runCatching { dnsDao.insertProfiles(listOf(DnsProfileEntity.fromDomain(profile))) }.isSuccess
            if (!saved) local.update { it.copy(message = UiMessage(R.string.tools_dns_save_failed)) }
        }
    }

    fun deleteDnsProfile(profile: DnsProfile) {
        viewModelScope.launch {
            runCatching { dnsDao.deleteProfileById(profile.id) }
            local.update { it.copy(message = UiMessage(R.string.tools_dns_deleted, profile.name)) }
        }
    }

    fun consumeOpenSettings() = local.update { it.copy(openSettings = null) }

    fun reportSettingsUnavailable() = local.update {
        it.copy(message = UiMessage(com.dragolabs.able.core.designsystem.R.string.ds_no_handler))
    }

    fun consumeMessage() = local.update { it.copy(message = null) }

    private fun openSettings(target: SettingsTarget) = local.update { it.copy(openSettings = target) }

    /**
     * Applies a privileged change, then reads the real value back. The outcome is reported as
     * applied (confirmed), rejected (Android refused) or not confirmed (accepted but the read-back
     * did not match), and logged in the Able action history.
     */
    private fun <T> apply(
        key: ToggleKey,
        actionId: String,
        expected: T,
        readBackTimeoutMs: Long = 3_000,
        target: Boolean? = null,
        read: () -> T,
        write: suspend () -> Boolean,
    ) {
        if (key in local.value.busy) return
        local.update { state ->
            state.copy(busy = state.busy + key, targets = if (target != null) state.targets + (key to target) else state.targets)
        }
        viewModelScope.launch {
            val accepted = runCatching { write() }.getOrDefault(false)
            val confirmed = accepted && awaitReadBack(expected, readBackTimeoutMs, read = read)
            val (message, status) = when {
                !accepted -> UiMessage(R.string.tools_result_rejected) to ActionExecutionStatus.FAILED
                confirmed -> UiMessage(R.string.tools_result_applied) to ActionExecutionStatus.SUCCESS
                else -> UiMessage(R.string.tools_result_unconfirmed) to ActionExecutionStatus.FAILED
            }
            local.update { it.copy(busy = it.busy - key, targets = it.targets - key, message = message) }
            refresh()
            val detail = describe(message)
            logScope.launch { actionLog.log(actionId, status, detail) }
        }
    }
}
