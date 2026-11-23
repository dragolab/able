package com.dragolabs.able.feature.tools.hotspot

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.core.data.ActionLogRepository
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.UiMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HotspotWork { TOGGLING, APPLYING, BLOCKING }

@Immutable
data class HotspotUiState(
    val status: HotspotState = HotspotState.UNKNOWN,
    val shizuku: ShizukuConnectionState = ShizukuConnectionState.NOT_AVAILABLE,
    /** Last state read through Shizuku; used when the broadcast does not report one. */
    val privilegedState: HotspotState? = null,
    val snapshot: HotspotSnapshot? = null,
    val loading: Boolean = false,
    val loadFailure: String? = null,
    val work: HotspotWork? = null,
    val draft: HotspotConfig? = null,
    val message: UiMessage? = null,
) {
    val canControl: Boolean get() = shizuku == ShizukuConnectionState.READY
    val busy: Boolean get() = work != null
    val capabilities: HotspotCapabilities get() = snapshot?.capabilities ?: HotspotCapabilities()
    val draftErrors: List<HotspotFieldError> get() = draft?.let { HotspotValidator.validate(it, capabilities) }.orEmpty()
}

class HotspotViewModel(
    private val dataSource: HotspotDataSource,
    private val shizukuState: StateFlow<ShizukuConnectionState>,
    private val actionLog: ActionLogRepository,
    private val logScope: CoroutineScope,
    private val describe: (UiMessage) -> String,
    private val refreshAccess: () -> Unit,
    private val requestShizukuPermission: () -> Boolean,
) : ViewModel() {

    private val local = MutableStateFlow(HotspotUiState())

    val uiState: StateFlow<HotspotUiState> = combine(local, dataSource.observeState(), shizukuState) { l, broadcast, shizuku ->
        val status = if (broadcast != HotspotState.UNKNOWN) broadcast else l.privilegedState ?: HotspotState.UNKNOWN
        l.copy(status = status, shizuku = shizuku)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HotspotUiState(status = dataSource.readState()))

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            shizukuState.collect { if (it == ShizukuConnectionState.READY) load() }
        }
    }

    fun refresh() {
        refreshAccess()
        if (dataSource.isShizukuReady) load()
    }

    fun authorizeShizuku() {
        if (!requestShizukuPermission()) local.update { it.copy(message = UiMessage(R.string.hotspot_shizuku_not_running)) }
    }

    fun load() {
        if (loadJob?.isActive == true || !dataSource.isShizukuReady) return
        loadJob = viewModelScope.launch {
            local.update { it.copy(loading = true) }
            when (val result = dataSource.snapshot()) {
                is AbleResult.Success -> local.update {
                    it.copy(
                        loading = false,
                        snapshot = result.data,
                        privilegedState = result.data.state.takeIf { s -> s != HotspotState.UNKNOWN } ?: it.privilegedState,
                        loadFailure = null,
                    )
                }
                is AbleResult.Failure -> local.update { it.copy(loading = false, loadFailure = result.error.message) }
                AbleResult.Loading -> local.update { it.copy(loading = false) }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // On / off
    // -----------------------------------------------------------------------------------------

    fun setEnabled(enabled: Boolean) {
        if (!begin(HotspotWork.TOGGLING)) return
        viewModelScope.launch {
            val (confirmed, outcome) = toggle(enabled)
            finish(outcome)
            log(if (enabled) ACTION_START else ACTION_STOP, confirmed, outcome)
            load()
        }
    }

    private suspend fun toggle(enabled: Boolean): Pair<Boolean, UiMessage> {
        val result = if (enabled) dataSource.start() else dataSource.stop()
        return when (result) {
            is HotspotCommandResult.Failed -> false to UiMessage(R.string.hotspot_command_failed, result.reason)
            is HotspotCommandResult.Answered -> when {
                result.code != 0 -> false to tetherError(result.code)
                awaitState(if (enabled) HotspotState.ENABLED else HotspotState.DISABLED) ->
                    true to UiMessage(if (enabled) R.string.hotspot_started else R.string.hotspot_stopped)
                else -> false to UiMessage(R.string.hotspot_unconfirmed)
            }
        }
    }

    private fun tetherError(code: Int): UiMessage = when (code) {
        TETHER_ERROR_UNSUPPORTED -> UiMessage(R.string.hotspot_error_unsupported)
        TETHER_ERROR_NO_PERMISSION -> UiMessage(R.string.hotspot_error_permission)
        TETHER_ERROR_DUPLICATE_REQUEST -> UiMessage(R.string.hotspot_error_duplicate)
        TETHER_ERROR_PROVISIONING_FAILED, TETHER_ERROR_ENTITLEMENT_UNKNOWN -> UiMessage(R.string.hotspot_error_entitlement)
        else -> UiMessage(R.string.hotspot_error_code, code)
    }

    /** Polls the privileged state until it reaches [target]; false on timeout or failure. */
    private suspend fun awaitState(target: HotspotState, timeoutMs: Long = 15_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val state = dataSource.readPrivilegedState()
            local.update { it.copy(privilegedState = state) }
            if (state == target) return true
            if (state == HotspotState.FAILED && target == HotspotState.ENABLED) return false
            delay(750)
        }
        return false
    }

    // -----------------------------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------------------------

    fun startEditing() {
        val config = local.value.snapshot?.config ?: return
        local.update { it.copy(draft = config) }
    }

    fun updateDraft(config: HotspotConfig) = local.update { it.copy(draft = config) }

    fun cancelEditing() = local.update { it.copy(draft = null) }

    /** True when applying the current draft restarts a running hotspot (clients disconnect). */
    fun draftNeedsRestart(): Boolean {
        val state = uiState.value
        val current = state.snapshot?.config ?: return false
        val draft = state.draft ?: return false
        return HotspotEdit.between(current, draft).needsRestart && state.status == HotspotState.ENABLED
    }

    fun applyDraft() {
        val state = uiState.value
        val current = state.snapshot?.config ?: return
        val draft = state.draft ?: return
        if (state.draftErrors.isNotEmpty()) {
            local.update { it.copy(message = UiMessage(R.string.hotspot_fix_errors)) }
            return
        }
        val edit = HotspotEdit.between(current, draft)
        if (edit.isEmpty) {
            local.update { it.copy(draft = null, message = UiMessage(R.string.hotspot_no_changes)) }
            return
        }
        val restart = edit.needsRestart && state.status == HotspotState.ENABLED
        if (!begin(HotspotWork.APPLYING)) return
        viewModelScope.launch {
            var (confirmed, outcome) = applyEdit(edit, R.string.hotspot_config_applied)
            if (confirmed) local.update { it.copy(draft = null) }
            if (confirmed && restart) {
                val stopped = toggle(enabled = false).first
                val started = stopped && toggle(enabled = true).first
                if (!started) {
                    confirmed = false
                    outcome = UiMessage(R.string.hotspot_restart_failed)
                } else {
                    outcome = UiMessage(R.string.hotspot_config_applied_restarted)
                }
            }
            finish(outcome)
            log(ACTION_CONFIG, confirmed, outcome)
            load()
        }
    }

    fun blockClient(mac: String) = changeBlocklist(mac, block = true)

    fun unblockClient(mac: String) = changeBlocklist(mac, block = false)

    private fun changeBlocklist(mac: String, block: Boolean) {
        val current = local.value.snapshot?.config ?: return
        val normalized = mac.lowercase()
        val list = current.blockedClients.map { it.lowercase() }.toMutableSet()
        if (block) list += normalized else list -= normalized
        if (!begin(HotspotWork.BLOCKING)) return
        viewModelScope.launch {
            val (confirmed, outcome) = applyEdit(
                HotspotEdit(blockedClients = list.toList()),
                if (block) R.string.hotspot_client_blocked else R.string.hotspot_client_unblocked,
            )
            finish(outcome)
            log(if (block) ACTION_BLOCK else ACTION_UNBLOCK, confirmed, outcome)
            load()
        }
    }

    private suspend fun applyEdit(edit: HotspotEdit, successRes: Int): Pair<Boolean, UiMessage> =
        when (val result = dataSource.apply(edit)) {
            is HotspotCommandResult.Failed -> false to UiMessage(R.string.hotspot_apply_failed, result.reason)
            is HotspotCommandResult.Answered -> {
                val readBack = HotspotWire.parse(result.output).config
                when {
                    result.code != 0 -> false to UiMessage(R.string.hotspot_apply_rejected)
                    readBack == null -> false to UiMessage(R.string.hotspot_unconfirmed)
                    edit.unconfirmedFields(readBack).isNotEmpty() -> false to UiMessage(R.string.hotspot_apply_partial)
                    else -> true to UiMessage(successRes)
                }
            }
        }

    // -----------------------------------------------------------------------------------------

    fun reportSettingsUnavailable() = local.update { it.copy(message = UiMessage(R.string.hotspot_settings_unavailable)) }

    fun reportShizukuMissing() = local.update { it.copy(message = UiMessage(R.string.hotspot_shizuku_not_installed)) }

    fun consumeMessage() = local.update { it.copy(message = null) }

    private fun begin(work: HotspotWork): Boolean {
        if (local.value.work != null) return false
        if (!dataSource.isShizukuReady) {
            local.update { it.copy(message = UiMessage(R.string.hotspot_shizuku_required)) }
            return false
        }
        local.update { it.copy(work = work) }
        return true
    }

    private fun finish(outcome: UiMessage) = local.update { it.copy(work = null, message = outcome) }

    private fun log(actionId: String, confirmed: Boolean, outcome: UiMessage) {
        val status = if (confirmed) ActionExecutionStatus.SUCCESS else ActionExecutionStatus.FAILED
        logScope.launch { actionLog.log(actionId, status, describe(outcome), reversible = true) }
    }

    private companion object {
        const val ACTION_START = "tools.hotspot.start"
        const val ACTION_STOP = "tools.hotspot.stop"
        const val ACTION_CONFIG = "tools.hotspot.config"
        const val ACTION_BLOCK = "tools.hotspot.block"
        const val ACTION_UNBLOCK = "tools.hotspot.unblock"

        // TetheringManager.TETHER_ERROR_* (system API).
        const val TETHER_ERROR_UNSUPPORTED = 3
        const val TETHER_ERROR_PROVISIONING_FAILED = 11
        const val TETHER_ERROR_ENTITLEMENT_UNKNOWN = 13
        const val TETHER_ERROR_NO_PERMISSION = 14
        const val TETHER_ERROR_DUPLICATE_REQUEST = 18
    }
}
