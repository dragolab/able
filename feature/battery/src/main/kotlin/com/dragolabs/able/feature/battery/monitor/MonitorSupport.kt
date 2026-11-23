package com.dragolabs.able.feature.battery.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.database.UserPreferences
import com.dragolabs.able.core.database.entity.BatterySessionEntity
import com.dragolabs.able.feature.battery.R
import com.dragolabs.able.feature.battery.data.BatteryHistoryRepository
import com.dragolabs.able.feature.battery.domain.BaselineStatus
import com.dragolabs.able.feature.battery.domain.BatteryFormat
import com.dragolabs.able.feature.battery.domain.IdleBaselineCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Starts/stops the monitor service and resets its statistics. */
class BatteryMonitorController(context: Context) {
    private val appContext = context.applicationContext

    val running: StateFlow<Boolean> get() = BatteryMonitorService.running

    /** Returns false when Android refused to start the foreground service. */
    fun start(): Boolean = runCatching {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, BatteryMonitorService::class.java).setAction(BatteryMonitorService.ACTION_START),
        )
    }.isSuccess

    fun stop() {
        if (running.value) {
            runCatching {
                appContext.startService(Intent(appContext, BatteryMonitorService::class.java).setAction(BatteryMonitorService.ACTION_STOP))
            }.onFailure { appContext.stopService(Intent(appContext, BatteryMonitorService::class.java)) }
        } else {
            appContext.stopService(Intent(appContext, BatteryMonitorService::class.java))
        }
    }

    /** Manual reset, or reset after history deletion. Works whether or not the monitor runs. */
    fun resetStatistics(reason: ResetReason = ResetReason.MANUAL) {
        val store = MonitorStateStore.get(appContext)
        val sent = running.value && runCatching {
            appContext.startService(
                Intent(appContext, BatteryMonitorService::class.java)
                    .setAction(BatteryMonitorService.ACTION_RESET)
                    .putExtra(BatteryMonitorService.EXTRA_REASON, reason.name),
            )
        }.isSuccess
        if (!sent && store.state.value.live != null) store.saveLive(LiveStats(System.currentTimeMillis(), reason))
    }
}

internal object MonitorRecovery {
    /**
     * Stores a session left behind by a reboot or process death as partial (MON-001) and clears
     * the checkpoint. Returns true when a session was recovered.
     */
    suspend fun recoverInterruptedSession(
        store: MonitorStateStore,
        history: BatteryHistoryRepository,
        thermalThresholdC: Float,
    ): Boolean {
        val stored = store.state.value.session ?: return false
        store.saveSession(null)
        if (!stored.isMeaningful) return false
        withContext(Dispatchers.IO) {
            history.insert(stored.toEntity(stored.lastUpdateEpochMs, partial = true, thermalThresholdC = thermalThresholdC))
        }
        return true
    }
}

internal object MonitorAlerts {
    /**
     * MON-007 / BASE-001: at most one alert after a completed valid idle session. The alert
     * reports the measurement window and never names an app as the cause.
     */
    suspend fun evaluateCompletedSession(
        context: Context,
        session: BatterySessionEntity,
        history: BatteryHistoryRepository,
        prefs: UserPreferences,
    ) {
        val rate = IdleBaselineCalculator.idleRateIfValid(session) ?: return
        val res = context.resources
        val window = BatteryFormat.duration(res, session.endTimeEpochMs - session.startTimeEpochMs)
        val rateText = BatteryFormat.ratePerHour(res, rate)
        if (prefs.idleDrainAlertEnabled && rate > prefs.idleDrainAlertThresholdPercentPerHour) {
            MonitorNotifications.postIdleDrainAlert(
                context,
                context.getString(
                    R.string.battery_alert_idle_threshold_body,
                    rateText,
                    window,
                    BatteryFormat.ratePerHour(res, prefs.idleDrainAlertThresholdPercentPerHour),
                ),
            )
            return
        }
        if (prefs.baselineAlertEnabled) {
            val now = System.currentTimeMillis()
            val baseline = IdleBaselineCalculator.calculate(history.baselineCandidates(now), now)
            val max = baseline.maximumRatePercentPerHour ?: return
            if (baseline.status == BaselineStatus.ESTABLISHED && baseline.latestSessionId == session.id &&
                rate > max + prefs.baselineAlertMinDifferencePercentPerHour
            ) {
                MonitorNotifications.postIdleDrainAlert(
                    context,
                    context.getString(
                        R.string.battery_alert_idle_baseline_body,
                        rateText,
                        window,
                        BatteryFormat.ratePerHour(res, max),
                    ),
                )
            }
        }
    }
}

/**
 * After a reboot: stores the interrupted session as partial, applies the optional
 * reset-after-reboot rule (MON-005) and resumes the monitor only if the user left it running.
 */
class BatteryMonitorBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val container = context.applicationContext.ableContainer
        container.applicationScope.launch {
            try {
                val prefs = container.preferences.userPreferencesFlow.first()
                val store = MonitorStateStore.get(context)
                val history = BatteryHistoryRepository(container.database.batteryDao(), container.preferences)
                MonitorRecovery.recoverInterruptedSession(store, history, prefs.batteryThermalAlertThresholdCelsius)
                if (prefs.batteryResetOnBoot && store.state.value.live != null) {
                    store.saveLive(LiveStats(System.currentTimeMillis(), ResetReason.REBOOT))
                }
                if (prefs.batteryMonitorEnabled) {
                    val started = withContext(Dispatchers.Main) { BatteryMonitorController(context).start() }
                    if (!started) container.preferences.setBatteryMonitorEnabled(false)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
