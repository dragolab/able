package com.dragolabs.able.feature.tools.shell

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.data.ActionLogRepository
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Outcome of a finished command, consumed once by the UI for haptics. */
@Immutable
data class ShellOutcome(val id: Long, val success: Boolean)

@Immutable
data class DangerConfirmation(val command: String, val risks: List<ShellRisk>)

@Immutable
data class ShellUiState(
    val shizuku: ShizukuConnectionState = ShizukuConnectionState.NOT_AVAILABLE,
    /** Null while loading the persisted flag. */
    val warningAcknowledged: Boolean? = null,
    val lines: List<ShellLine> = emptyList(),
    val droppedLines: Long = 0,
    val running: Boolean = false,
    val runningCommand: String? = null,
    /** Exit code of the last finished command; null when stopped, timed out or never run. */
    val lastExitCode: Int? = null,
    val lastEnd: ShellEnd? = null,
    val history: List<String> = emptyList(),
    val canHistoryPrevious: Boolean = false,
    val canHistoryNext: Boolean = false,
    val snippets: List<ShellSnippet> = emptyList(),
    val pendingDanger: DangerConfirmation? = null,
    val showWarning: Boolean = false,
    val outcome: ShellOutcome? = null,
    val message: UiMessage? = null,
)

private data class LocalState(
    val lines: List<ShellLine> = emptyList(),
    val droppedLines: Long = 0,
    val running: Boolean = false,
    val runningCommand: String? = null,
    val lastExitCode: Int? = null,
    val lastEnd: ShellEnd? = null,
    val canHistoryNext: Boolean = false,
    val pendingDanger: DangerConfirmation? = null,
    val warningRequested: Boolean = false,
    val outcome: ShellOutcome? = null,
    val message: UiMessage? = null,
)

class ShellViewModel(
    private val store: ShellStore,
    private val runner: ShellProcessRunner,
    shizukuState: StateFlow<ShizukuConnectionState>,
    private val isShizukuReady: () -> Boolean,
    private val actionLog: ActionLogRepository,
    private val logScope: CoroutineScope,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())
    private val buffer = ShellOutputBuffer()
    private val navigator = HistoryNavigator()
    private var job: Job? = null
    private var stopRequested = false
    private var outcomeCounter = 0L

    val uiState: StateFlow<ShellUiState> = combine(
        local,
        shizukuState,
        store.history,
        store.snippets,
        store.warningAcknowledged,
    ) { l, shizuku, history, snippets, acknowledged ->
        ShellUiState(
            shizuku = shizuku,
            warningAcknowledged = acknowledged,
            lines = l.lines,
            droppedLines = l.droppedLines,
            running = l.running,
            runningCommand = l.runningCommand,
            lastExitCode = l.lastExitCode,
            lastEnd = l.lastEnd,
            history = history,
            canHistoryPrevious = navigator.canGoPrevious(history),
            canHistoryNext = l.canHistoryNext,
            snippets = snippets,
            pendingDanger = l.pendingDanger,
            // One-time warning: on first use once Shizuku is ready, or when trying to run before accepting it.
            showWarning = acknowledged == false && (shizuku == ShizukuConnectionState.READY || l.warningRequested),
            outcome = l.outcome,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellUiState())

    /** Entry point for the Send button / IME action. Never called without a user gesture. */
    fun submit(command: String) {
        val text = command.trim()
        if (text.isEmpty() || local.value.running) return
        if (store.warningAcknowledged.value != true) {
            local.update { it.copy(warningRequested = true) }
            return
        }
        val risks = DangerousCommandMatcher.match(text)
        if (risks.isNotEmpty()) {
            local.update { it.copy(pendingDanger = DangerConfirmation(text, risks.toList())) }
            return
        }
        execute(text)
    }

    fun confirmDanger() {
        val pending = local.value.pendingDanger ?: return
        local.update { it.copy(pendingDanger = null) }
        execute(pending.command)
    }

    fun dismissDanger() = local.update { it.copy(pendingDanger = null) }

    fun acknowledgeWarning() {
        store.acknowledgeWarning()
        local.update { it.copy(warningRequested = false) }
    }

    fun dismissWarning() = local.update { it.copy(warningRequested = false) }

    fun stop() {
        if (job?.isActive == true) {
            stopRequested = true
            job?.cancel()
        }
    }

    fun clearOutput() {
        if (local.value.running) return
        buffer.clear()
        publish()
        local.update { it.copy(lastExitCode = null, lastEnd = null) }
    }

    /** Returns the older history entry to put in the input, or null. */
    fun historyPrevious(current: String): String? =
        navigator.previous(store.history.value, current).also { updateNavigation() }

    fun historyNext(): String? = navigator.next(store.history.value).also { updateNavigation() }

    fun clearHistory() {
        store.clearHistory()
        navigator.reset()
        local.update { it.copy(canHistoryNext = false) }
    }

    fun saveSnippet(id: String?, label: String, command: String) {
        if (command.isBlank()) return
        store.saveSnippet(id, label.ifBlank { command }, command)
        local.update { it.copy(message = UiMessage(R.string.shell_snippet_saved)) }
    }

    fun deleteSnippet(snippet: ShellSnippet) {
        store.deleteSnippet(snippet.id)
        local.update { it.copy(message = UiMessage(R.string.shell_snippet_deleted, snippet.label)) }
    }

    fun exportText(): Pair<String, Boolean> = ShellOutputBuffer.export(buffer.snapshot())

    fun showMessage(message: UiMessage) = local.update { it.copy(message = message) }

    fun consumeMessage() = local.update { it.copy(message = null) }

    fun consumeOutcome() = local.update { it.copy(outcome = null) }

    private fun updateNavigation() {
        local.update { it.copy(canHistoryNext = navigator.canGoNext()) }
    }

    private fun execute(command: String) {
        if (!isShizukuReady()) {
            local.update { it.copy(message = UiMessage(R.string.shell_shizuku_lost)) }
            return
        }
        store.record(command)
        navigator.reset()
        stopRequested = false
        buffer.append(ShellLineKind.COMMAND, command)
        local.update {
            it.copy(running = true, runningCommand = command, lastExitCode = null, lastEnd = null, canHistoryNext = false)
        }
        publish()

        job = viewModelScope.launch {
            var exitCode: Int? = null
            var end = ShellEnd.FAILED
            var dirty = false
            var lastPublish = 0L
            val ticker = launch {
                while (isActive) {
                    delay(PUBLISH_INTERVAL_MS)
                    if (dirty) {
                        dirty = false
                        lastPublish = System.currentTimeMillis()
                        publish()
                    }
                }
            }
            try {
                val finished = withTimeoutOrNull(timeoutMs) {
                    runner.run(command).collect { event ->
                        when (event) {
                            is ShellEvent.Stdout -> buffer.append(ShellLineKind.STDOUT, event.line)
                            is ShellEvent.Stderr -> buffer.append(ShellLineKind.STDERR, event.line)
                            is ShellEvent.Exit -> exitCode = event.code
                        }
                        dirty = true
                        val now = System.currentTimeMillis()
                        if (now - lastPublish >= PUBLISH_INTERVAL_MS) {
                            dirty = false
                            lastPublish = now
                            publish()
                        }
                    }
                    true
                }
                end = if (finished == null) ShellEnd.TIMED_OUT else ShellEnd.EXITED
            } catch (e: CancellationException) {
                end = ShellEnd.STOPPED
                if (!stopRequested) throw e // ViewModel cleared: nothing left to show.
            } catch (e: Exception) {
                end = ShellEnd.FAILED
                buffer.append(ShellLineKind.INFO, e.message ?: e.javaClass.simpleName)
            } finally {
                ticker.cancel()
                finish(command, end, exitCode)
            }
        }
    }

    private fun finish(command: String, end: ShellEnd, exitCode: Int?) {
        buffer.append(ShellLineKind.EXIT, "", exitCode = if (end == ShellEnd.EXITED) exitCode else null, end = end)
        val success = end == ShellEnd.EXITED && exitCode == 0
        publish()
        local.update {
            it.copy(
                running = false,
                runningCommand = null,
                lastExitCode = if (end == ShellEnd.EXITED) exitCode else null,
                lastEnd = end,
                outcome = ShellOutcome(++outcomeCounter, success),
            )
        }
        val status = when {
            success -> ActionExecutionStatus.SUCCESS
            end == ShellEnd.STOPPED -> ActionExecutionStatus.SKIPPED
            else -> ActionExecutionStatus.FAILED
        }
        val detail = command.take(MAX_LOGGED_COMMAND_CHARS) + " → " + when (end) {
            ShellEnd.EXITED -> "exit ${exitCode ?: "unknown"}"
            ShellEnd.STOPPED -> "stopped"
            ShellEnd.TIMED_OUT -> "timed out after ${timeoutMs / 1000}s"
            ShellEnd.FAILED -> "failed to run"
        }
        // Only the command and its exit status are logged; output never leaves the screen.
        logScope.launch { runCatching { actionLog.log(ACTION_ID, status, detail) } }
    }

    private fun publish() {
        val snapshot = buffer.snapshot()
        val dropped = buffer.droppedLines
        local.update { it.copy(lines = snapshot, droppedLines = dropped) }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    companion object {
        const val ACTION_ID = "shell_command"
        const val DEFAULT_TIMEOUT_MS = 10 * 60 * 1_000L
        private const val PUBLISH_INTERVAL_MS = 100L
        private const val MAX_LOGGED_COMMAND_CHARS = 300

    }
}
