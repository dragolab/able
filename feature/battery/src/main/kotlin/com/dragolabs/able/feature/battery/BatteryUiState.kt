package com.dragolabs.able.feature.battery

import android.os.Build
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.database.MonitorNotificationLayout
import com.dragolabs.able.core.database.TemperatureUnit
import com.dragolabs.able.core.database.entity.BatterySessionEntity
import com.dragolabs.able.core.model.BatteryStatus
import com.dragolabs.able.core.data.battery.ActiveProfile
import com.dragolabs.able.core.data.battery.ProfileEvaluation
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.feature.battery.data.AdvancedBatteryReading
import com.dragolabs.able.feature.battery.data.AppUsageSummary
import com.dragolabs.able.feature.battery.data.SessionNote
import com.dragolabs.able.feature.battery.data.SessionTag
import com.dragolabs.able.feature.battery.domain.IdleBaseline
import com.dragolabs.able.feature.battery.domain.ScreenTimeToday
import com.dragolabs.able.feature.battery.domain.SessionRates
import com.dragolabs.able.feature.battery.monitor.LiveStats
import com.dragolabs.able.feature.battery.monitor.SessionAccumulator

enum class BatteryTab { OVERVIEW, MONITOR, HEALTH, DIAGNOSTICS }

/** One-off feedback shown in a snackbar and cleared with `consumeMessage()`. */
enum class BatteryMessage {
    PROFILE_SAVED,
    PROFILE_DELETED,
    PROFILE_DUPLICATED,
    PROFILE_RESET,
    PROFILE_NOT_FOUND,
    SETTINGS_OPEN_FAILED,
    MONITOR_START_FAILED,
    NOTIFICATION_DENIED,
    ALERT_PERMISSION_DENIED,
    HISTORY_DELETED,
    NOTE_SAVED,
    STATS_RESET,
    SHIZUKU_NOT_INSTALLED,
}

/** Android settings pages Able links to instead of changing system settings itself. */
sealed interface SettingsTarget {
    data object BatteryUsage : SettingsTarget
    data object BatterySaver : SettingsTarget
    data object Network : SettingsTarget
    data object Hotspot : SettingsTarget
    data object Display : SettingsTarget
    data object UsageAccess : SettingsTarget
    data object AppNotifications : SettingsTarget
    data class AppDetails(val packageName: String) : SettingsTarget
}

// ---------------------------------------------------------------------------------------------
// Overview, health and diagnostics

/**
 * Status tab (owner decision 2026-11-22): several profiles can be active at once; the most
 * restrictive app action wins. Nothing is active by default (ADR-005).
 */
data class ProfilesUiState(
    val profiles: List<BatteryProfile> = emptyList(),
    val evaluation: ProfileEvaluation = ProfileEvaluation(),
    val automationPaused: Boolean = false,
    /** Package name to display label. */
    val protectedApps: List<Pair<String, String>> = emptyList(),
    /** Labels for the packages in [ProfileEvaluation.appActions]. */
    val appLabels: Map<String, String> = emptyMap(),
) {
    /** True when at least one enabled profile has an enabled automatic trigger. */
    val anyTriggerEnabled: Boolean
        get() = profiles.any { it.enabled && it.hasEnabledTrigger }

    val active: List<ActiveProfile> get() = evaluation.active

    fun activeFor(profileId: String): ActiveProfile? = evaluation.active.firstOrNull { it.profile.id == profileId }

    /** Active profiles that suggest Android Battery Saver. */
    val batterySaverSuggestedBy: List<BatteryProfile>
        get() = evaluation.active.map { it.profile }.filter { it.offerBatterySaver }
}

sealed interface AdvancedHealthState {
    data object Idle : AdvancedHealthState
    data object Reading : AdvancedHealthState
    data class Result(val reading: AdvancedBatteryReading) : AdvancedHealthState
    data object Failed : AdvancedHealthState
}

data class HealthUiState(
    val cycleCountApiSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    val shizuku: ShizukuConnectionState = ShizukuConnectionState.NOT_AVAILABLE,
    val advanced: AdvancedHealthState = AdvancedHealthState.Idle,
)

enum class ChecklistItem { RECENT_DRAIN, SIGNAL, HOTSPOT, SCREEN, BACKGROUND_APPS }

data class DiagnosticsUiState(
    val reviewed: Set<ChecklistItem> = emptySet(),
    val latestDischarge: SessionItem? = null,
    val usageAccessGranted: Boolean = false,
    val usageLoading: Boolean = false,
    val topApps: List<AppUsageSummary>? = null,
)

data class BatteryUiState(
    val loading: Boolean = true,
    val hasBattery: Boolean = true,
    val status: BatteryStatus = BatteryStatus.Unavailable,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val profiles: ProfilesUiState = ProfilesUiState(),
    val health: HealthUiState = HealthUiState(),
    val diagnostics: DiagnosticsUiState = DiagnosticsUiState(),
    val message: BatteryMessage? = null,
)

// ---------------------------------------------------------------------------------------------
// Live monitor and history

data class SessionItem(
    val session: BatterySessionEntity,
    val rates: SessionRates,
    val note: SessionNote?,
)

enum class SessionFilter { ALL, DISCHARGE, CHARGING }

data class MonitorSettingsUi(
    val thermalThresholdCelsius: Float = 45f,
    val resetOnPlug: Boolean = false,
    val resetOnBoot: Boolean = false,
    val resetThresholdPercent: Int? = null,
    val retentionDays: Int = 90,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val notificationLayout: MonitorNotificationLayout = MonitorNotificationLayout.DETAILED,
    val notificationShowPower: Boolean = true,
    val idleAlertEnabled: Boolean = false,
    val idleAlertThreshold: Float = 2f,
    val baselineAlertEnabled: Boolean = false,
    val baselineOfferDismissed: Boolean = false,
    val baselineMinDifference: Float = 0.5f,
)

data class MonitorUiState(
    val loading: Boolean = true,
    val running: Boolean = false,
    /** The user started the monitor but it is no longer running (process killed, start refused). */
    val interrupted: Boolean = false,
    val status: BatteryStatus = BatteryStatus.Unavailable,
    val live: LiveStats? = null,
    val currentSession: SessionAccumulator? = null,
    val settings: MonitorSettingsUi = MonitorSettingsUi(),
    val baseline: IdleBaseline = IdleBaseline.Empty,
    val sessions: List<SessionItem> = emptyList(),
    val totalSessions: Int = 0,
    /** Screen time recorded by the monitor since local midnight; null when nothing was recorded. */
    val screenToday: ScreenTimeToday? = null,
    /** Most recent saved charging session (independent of [filter]), for the charging-speed tile. */
    val latestCharging: SessionItem? = null,
    val filter: SessionFilter = SessionFilter.ALL,
    val editingSession: SessionItem? = null,
    val confirmDeleteHistory: Boolean = false,
    val message: BatteryMessage? = null,
) {
    /** BASE-001: offer alerts once, only after the baseline is established. */
    val showBaselineOffer: Boolean
        get() = baseline.status == com.dragolabs.able.feature.battery.domain.BaselineStatus.ESTABLISHED &&
            !settings.baselineOfferDismissed && !settings.baselineAlertEnabled
}

enum class AlertKind { IDLE_THRESHOLD, BASELINE }

/** User intents from the stateless screens. */
sealed interface BatteryEvent {
    // Status & profiles
    data class SetProfileEnabled(val profileId: String, val enabled: Boolean) : BatteryEvent
    data class SetManualActive(val profileId: String, val active: Boolean) : BatteryEvent
    data class EditProfile(val profileId: String) : BatteryEvent
    data object NewProfile : BatteryEvent
    data class SetAutomationPaused(val paused: Boolean) : BatteryEvent
    data object OpenApps : BatteryEvent
    data class OpenSettings(val target: SettingsTarget) : BatteryEvent

    // Monitor
    data object StartMonitor : BatteryEvent
    data object StopMonitor : BatteryEvent
    data object ResetStatistics : BatteryEvent
    data class SetThermalThreshold(val celsius: Float) : BatteryEvent
    data class SetResetOnPlug(val enabled: Boolean) : BatteryEvent
    data class SetResetOnBoot(val enabled: Boolean) : BatteryEvent
    data class SetResetThreshold(val percent: Int?) : BatteryEvent
    data class SetRetention(val days: Int) : BatteryEvent
    data object RequestDeleteHistory : BatteryEvent
    data object ConfirmDeleteHistory : BatteryEvent
    data object DismissDeleteHistory : BatteryEvent
    data class SetTemperatureUnit(val unit: TemperatureUnit) : BatteryEvent
    data class SetNotificationLayout(val layout: MonitorNotificationLayout) : BatteryEvent
    data class SetNotificationShowPower(val show: Boolean) : BatteryEvent
    data class EnableAlert(val kind: AlertKind) : BatteryEvent
    data class DisableAlert(val kind: AlertKind) : BatteryEvent
    data class SetIdleAlertThreshold(val percentPerHour: Float) : BatteryEvent
    data class SetBaselineMinDifference(val percentPerHour: Float) : BatteryEvent
    data object DismissBaselineOffer : BatteryEvent
    data class SetSessionFilter(val filter: SessionFilter) : BatteryEvent
    data class EditSession(val sessionId: Long) : BatteryEvent
    data object DismissNoteEditor : BatteryEvent
    data class SaveNote(val sessionId: Long, val text: String, val tags: Set<SessionTag>) : BatteryEvent

    // Health
    data object ReadAdvancedHealth : BatteryEvent
    data object ConnectShizuku : BatteryEvent

    // Diagnostics
    data class ToggleReviewed(val item: ChecklistItem) : BatteryEvent
    data object RefreshUsage : BatteryEvent
}
