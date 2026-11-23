package com.dragolabs.able.feature.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.data.BatteryStatusRepository
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.database.MonitorNotificationLayout
import com.dragolabs.able.core.database.TemperatureUnit
import com.dragolabs.able.core.database.UserPreferencesRepository
import com.dragolabs.able.feature.battery.data.BatteryHistoryRepository
import com.dragolabs.able.feature.battery.data.SessionTag
import com.dragolabs.able.feature.battery.domain.IdleBaselineCalculator
import com.dragolabs.able.feature.battery.domain.rates
import com.dragolabs.able.feature.battery.monitor.BatteryMonitorController
import com.dragolabs.able.feature.battery.monitor.MonitorStateStore
import com.dragolabs.able.feature.battery.monitor.ResetReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Live monitor, reset rules, retention, idle baseline and session history (MON-*, BASE-*, BAT-CHG/NOTE). */
class BatteryMonitorViewModel(
    private val battery: BatteryStatusRepository,
    private val preferences: UserPreferencesRepository,
    private val history: BatteryHistoryRepository,
    private val controller: BatteryMonitorController,
    private val store: MonitorStateStore,
) : ViewModel() {

    private data class LocalState(
        val filter: SessionFilter = SessionFilter.ALL,
        val editingSessionId: Long? = null,
        val confirmDelete: Boolean = false,
        val message: BatteryMessage? = null,
    )

    private val local = MutableStateFlow(LocalState())

    private val sessionItems = combine(history.sessions, history.notes) { sessions, notes ->
        val byId = notes.associateBy { it.sessionId }
        val items = sessions.map { session ->
            SessionItem(session, session.rates(), byId[session.id]?.let(BatteryHistoryRepository::decode))
        }
        items to IdleBaselineCalculator.calculate(sessions, System.currentTimeMillis())
    }

    private val base = combine(
        controller.running,
        store.state,
        preferences.userPreferencesFlow,
        battery.status,
        sessionItems,
    ) { running, stored, prefs, status, (items, baseline) ->
        MonitorUiState(
            loading = false,
            running = running,
            interrupted = prefs.batteryMonitorEnabled && !running,
            status = status,
            live = stored.live,
            currentSession = if (running) stored.session else null,
            settings = MonitorSettingsUi(
                thermalThresholdCelsius = prefs.batteryThermalAlertThresholdCelsius,
                resetOnPlug = prefs.batteryResetOnPlug,
                resetOnBoot = prefs.batteryResetOnBoot,
                resetThresholdPercent = prefs.batteryResetThresholdPercent,
                retentionDays = prefs.batteryHistoryRetentionDays,
                temperatureUnit = prefs.temperatureUnit,
                notificationLayout = prefs.monitorNotificationLayout,
                notificationShowPower = prefs.monitorNotificationShowPower,
                idleAlertEnabled = prefs.idleDrainAlertEnabled,
                idleAlertThreshold = prefs.idleDrainAlertThresholdPercentPerHour,
                baselineAlertEnabled = prefs.baselineAlertEnabled,
                baselineOfferDismissed = prefs.baselineAlertOfferDismissed,
                baselineMinDifference = prefs.baselineAlertMinDifferencePercentPerHour,
            ),
            baseline = baseline,
            sessions = items,
            totalSessions = items.size,
        )
    }

    val uiState: StateFlow<MonitorUiState> = combine(base, local) { state, l ->
        val filtered = when (l.filter) {
            SessionFilter.ALL -> state.sessions
            SessionFilter.DISCHARGE -> state.sessions.filter { !it.session.isCharging }
            SessionFilter.CHARGING -> state.sessions.filter { it.session.isCharging }
        }
        state.copy(
            sessions = filtered,
            filter = l.filter,
            editingSession = l.editingSessionId?.let { id -> state.sessions.firstOrNull { it.session.id == id } },
            confirmDeleteHistory = l.confirmDelete,
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitorUiState())

    init {
        viewModelScope.launch { history.applyRetention() }
    }

    private fun setMessage(message: BatteryMessage?) {
        local.value = local.value.copy(message = message)
    }

    fun consumeMessage() = setMessage(null)

    fun startMonitor() {
        if (!controller.start()) {
            setMessage(BatteryMessage.MONITOR_START_FAILED)
            viewModelScope.launch { preferences.setBatteryMonitorEnabled(false) }
        }
    }

    fun stopMonitor() {
        controller.stop()
        // Also clears an "interrupted" state when the service is not running.
        viewModelScope.launch { preferences.setBatteryMonitorEnabled(false) }
    }

    fun onNotificationPermissionDenied() = setMessage(BatteryMessage.NOTIFICATION_DENIED)

    fun resetStatistics() {
        controller.resetStatistics(ResetReason.MANUAL)
        setMessage(BatteryMessage.STATS_RESET)
    }

    fun setThermalThreshold(celsius: Float) = viewModelScope.launch { preferences.setBatteryThermalAlertThreshold(celsius) }

    fun setResetOnPlug(enabled: Boolean) = viewModelScope.launch { preferences.setBatteryResetOnPlug(enabled) }

    fun setResetOnBoot(enabled: Boolean) = viewModelScope.launch { preferences.setBatteryResetOnBoot(enabled) }

    fun setResetThreshold(percent: Int?) = viewModelScope.launch { preferences.setBatteryResetThresholdPercent(percent) }

    fun setRetention(days: Int) = viewModelScope.launch {
        preferences.setRetentionDays(days)
        history.applyRetention()
    }

    fun requestDeleteHistory() {
        local.value = local.value.copy(confirmDelete = true)
    }

    fun dismissDeleteHistory() {
        local.value = local.value.copy(confirmDelete = false)
    }

    /** MON-009: removes summaries, notes and therefore the derived baseline; settings stay. */
    fun confirmDeleteHistory() {
        local.value = local.value.copy(confirmDelete = false, editingSessionId = null)
        viewModelScope.launch {
            history.deleteAll()
            controller.resetStatistics(ResetReason.HISTORY_DELETED)
            setMessage(BatteryMessage.HISTORY_DELETED)
        }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) = viewModelScope.launch { preferences.setTemperatureUnit(unit) }

    fun setNotificationLayout(layout: MonitorNotificationLayout) = viewModelScope.launch { preferences.setMonitorNotificationLayout(layout) }

    fun setNotificationShowPower(show: Boolean) = viewModelScope.launch { preferences.setMonitorNotificationShowPower(show) }

    /** Called after notification permission is confirmed (BASE-001: permission only after opt-in). */
    fun enableAlert(kind: AlertKind) = viewModelScope.launch {
        when (kind) {
            AlertKind.IDLE_THRESHOLD -> preferences.setIdleDrainAlert(true, preferences.userPreferencesFlow.first().idleDrainAlertThresholdPercentPerHour)
            AlertKind.BASELINE -> preferences.setBaselineAlertEnabled(true)
        }
    }

    fun disableAlert(kind: AlertKind) = viewModelScope.launch {
        when (kind) {
            AlertKind.IDLE_THRESHOLD -> preferences.setIdleDrainAlert(false, preferences.userPreferencesFlow.first().idleDrainAlertThresholdPercentPerHour)
            AlertKind.BASELINE -> preferences.setBaselineAlertEnabled(false)
        }
    }

    /** Permission denied: alerts stay off; the offer counts as answered so it is not repeated. */
    fun onAlertPermissionDenied(kind: AlertKind) {
        if (kind == AlertKind.BASELINE) viewModelScope.launch { preferences.dismissBaselineAlertOffer() }
        setMessage(BatteryMessage.ALERT_PERMISSION_DENIED)
    }

    fun setIdleAlertThreshold(value: Float) = viewModelScope.launch {
        preferences.setIdleDrainAlert(preferences.userPreferencesFlow.first().idleDrainAlertEnabled, value)
    }

    fun setBaselineMinDifference(value: Float) = viewModelScope.launch { preferences.setBaselineAlertMinDifference(value) }

    fun dismissBaselineOffer() = viewModelScope.launch { preferences.dismissBaselineAlertOffer() }

    fun setFilter(filter: SessionFilter) {
        local.value = local.value.copy(filter = filter)
    }

    fun editSession(id: Long) {
        local.value = local.value.copy(editingSessionId = id)
    }

    fun dismissNoteEditor() {
        local.value = local.value.copy(editingSessionId = null)
    }

    fun saveNote(sessionId: Long, text: String, tags: Set<SessionTag>) {
        local.value = local.value.copy(editingSessionId = null)
        viewModelScope.launch {
            history.saveNote(sessionId, text, tags)
            setMessage(BatteryMessage.NOTE_SAVED)
        }
    }

    companion object {
        val Factory = ableViewModelFactory { container ->
            BatteryMonitorViewModel(
                battery = container.battery,
                preferences = container.preferences,
                history = BatteryHistoryRepository(container.database.batteryDao(), container.preferences),
                controller = BatteryMonitorController(container.appContext),
                store = MonitorStateStore.get(container.appContext),
            )
        }
    }
}
