package com.dragolabs.able.feature.tools.memory

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import com.dragolabs.able.core.capability.ShizukuAdapter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.data.ActionLogRepository
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.UiMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MemoryAppFilter { USER, SYSTEM, ALL }

/** Progress of a confirmed stop of one or more apps. */
@Immutable
data class StopProgress(val done: Int, val total: Int)

/** A stop the user asked for, waiting for confirmation. */
@Immutable
data class PendingStop(val apps: List<RunningApp>, val kind: StopKind)

@Immutable
data class MemoryUiState(
    val ram: RamSummary? = null,
    val shizuku: ShizukuConnectionState = ShizukuConnectionState.NOT_AVAILABLE,
    /** `null` until loaded; empty when nothing matched. */
    val apps: List<RunningApp>? = null,
    val loadingApps: Boolean = false,
    val loadFailed: Boolean = false,
    val filter: MemoryAppFilter = MemoryAppFilter.USER,
    val query: String = "",
    val selected: Set<String> = emptySet(),
    val busy: Set<String> = emptySet(),
    val pending: PendingStop? = null,
    /** Progress of a confirmed stop, shown in the selection bar; null when nothing runs. */
    val progress: StopProgress? = null,
    val message: UiMessage? = null,
) {
    val shizukuReady: Boolean get() = shizuku == ShizukuConnectionState.READY

    val visibleApps: List<RunningApp>
        get() {
            val q = query.trim()
            return apps.orEmpty().filter { app ->
                when (filter) {
                    MemoryAppFilter.USER -> !app.isSystem
                    MemoryAppFilter.SYSTEM -> app.isSystem
                    MemoryAppFilter.ALL -> true
                } && (q.isEmpty() || app.label.contains(q, ignoreCase = true) || app.packageName.contains(q, ignoreCase = true))
            }
        }

    val selectedApps: List<RunningApp> get() = apps.orEmpty().filter { it.key in selected && it.stoppable }
}

/** RAM and running apps (MEM tool). Stops only what the user selects and confirms. */
class MemoryViewModel(
    private val source: MemoryDataSource,
    private val shizuku: ShizukuAdapter,
    private val actionLog: ActionLogRepository,
    private val logScope: CoroutineScope,
    private val describe: (UiMessage) -> String,
    private val appContext: Context,
) : ViewModel() {

    private val shizukuState: StateFlow<ShizukuConnectionState> = shizuku.connectionState

    private val local = MutableStateFlow(MemoryUiState(ram = runCatching { source.ramSummary() }.getOrNull()))
    private var loadJob: Job? = null

    val uiState: StateFlow<MemoryUiState> = combine(local, shizukuState) { l, s -> l.copy(shizuku = s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        // Load the process list as soon as Shizuku becomes ready.
        viewModelScope.launch {
            shizukuState.map { it == ShizukuConnectionState.READY }.distinctUntilChanged().collect { ready ->
                if (ready) loadApps() else local.update { it.copy(apps = null, selected = emptySet()) }
            }
        }
    }

    fun refresh() {
        shizuku.updateState()
        refreshSummary()
        if (shizuku.isReady()) loadApps()
    }

    /** Re-reads only the RAM summary (cheap; polled while the screen is visible). */
    fun refreshSummary() {
        val ram = runCatching { source.ramSummary() }.getOrNull()
        local.update { it.copy(ram = ram) }
        if (ram != null) {
            viewModelScope.launch {
                val withSwap = runCatching { source.withShellSwap(ram) }.getOrDefault(ram)
                local.update { it.copy(ram = withSwap) }
            }
        }
    }

    private fun loadApps() {
        if (loadJob?.isActive == true) return
        local.update { it.copy(loadingApps = true) }
        loadJob = viewModelScope.launch {
            val apps = runCatching { source.runningApps() }.getOrNull()
            local.update { state ->
                val keys = apps?.map { it.key }?.toSet().orEmpty()
                state.copy(
                    apps = apps ?: state.apps,
                    loadingApps = false,
                    loadFailed = apps == null,
                    selected = state.selected intersect keys,
                )
            }
        }
    }

    fun setFilter(filter: MemoryAppFilter) = local.update { it.copy(filter = filter) }

    fun setQuery(query: String) = local.update { it.copy(query = query.take(80)) }

    fun toggleSelected(app: RunningApp) {
        if (!app.stoppable || local.value.progress != null) return
        local.update { s -> s.copy(selected = if (app.key in s.selected) s.selected - app.key else s.selected + app.key) }
    }

    fun clearSelection() {
        if (local.value.progress == null) local.update { it.copy(selected = emptySet()) }
    }

    fun requestStop(app: RunningApp, kind: StopKind) {
        if (!app.stoppable) return
        local.update { it.copy(pending = PendingStop(listOf(app), kind)) }
    }

    fun requestStopSelected() {
        val apps = uiState.value.selectedApps
        if (apps.isEmpty()) return
        local.update { it.copy(pending = PendingStop(apps, StopKind.FORCE_STOP)) }
    }

    fun dismissPending() = local.update { it.copy(pending = null) }

    fun confirmPending() {
        val pending = local.value.pending ?: return
        val total = pending.apps.size
        local.update {
            it.copy(pending = null, busy = it.busy + pending.apps.map(RunningApp::key), progress = StopProgress(0, total))
        }
        viewModelScope.launch {
            val outcomes = pending.apps.mapIndexed { index, app ->
                val outcome = runCatching { source.stop(app, pending.kind) }.getOrDefault(StopOutcome.FAILED)
                log(app, pending.kind, outcome)
                local.update { it.copy(busy = it.busy - app.key, progress = StopProgress(index + 1, total)) }
                app to outcome
            }
            val message = if (outcomes.size == 1) {
                val (app, outcome) = outcomes.single()
                singleMessage(app, outcome)
            } else {
                UiMessage(
                    R.string.memory_result_bulk,
                    outcomes.count { it.second == StopOutcome.STOPPED },
                    outcomes.count { it.second == StopOutcome.PARTIAL || it.second == StopOutcome.STILL_RUNNING },
                    outcomes.count { it.second == StopOutcome.FAILED },
                )
            }
            local.update {
                it.copy(message = message, progress = null, selected = it.selected - pending.apps.map(RunningApp::key).toSet())
            }
            refreshSummary()
            loadApps()
        }
    }

    private fun singleMessage(app: RunningApp, outcome: StopOutcome): UiMessage = when (outcome) {
        StopOutcome.STOPPED -> UiMessage(R.string.memory_result_stopped, app.label)
        StopOutcome.PARTIAL -> UiMessage(R.string.memory_result_partial, app.label)
        StopOutcome.STILL_RUNNING -> UiMessage(R.string.memory_result_still_running, app.label)
        StopOutcome.FAILED -> UiMessage(R.string.memory_result_failed, app.label)
    }

    private fun log(app: RunningApp, kind: StopKind, outcome: StopOutcome) {
        val detail = describe(singleMessage(app, outcome))
        val actionId = if (kind == StopKind.FORCE_STOP) "tools.memory.force_stop" else "tools.memory.kill_background"
        val status = if (outcome == StopOutcome.STOPPED) ActionExecutionStatus.SUCCESS else ActionExecutionStatus.FAILED
        logScope.launch { runCatching { actionLog.log(actionId, status, detail, targetPackage = app.packageName) } }
    }

    fun appInfoIntent(app: RunningApp): Intent = source.appInfoIntent(app.packageName)

    fun runningServicesIntents(): Array<Intent> = source.runningServicesIntents()

    /** Opens Shizuku when it is not running, otherwise asks for Able's Shizuku permission. */
    fun connectShizuku() {
        val handled = if (shizukuState.value == ShizukuConnectionState.NOT_AVAILABLE) {
            shizuku.openShizukuApp(appContext)
        } else {
            shizuku.requestPermission()
        }
        if (!handled) reportNoHandler()
    }

    fun reportNoHandler() = local.update { it.copy(message = UiMessage(com.dragolabs.able.core.designsystem.R.string.ds_no_handler)) }

    fun consumeMessage() = local.update { it.copy(message = null) }
}
