package com.dragolabs.able.feature.battery.monitor

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.database.UserPreferences
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.BatteryStatus
import com.dragolabs.able.core.model.ProfileTrigger
import com.dragolabs.able.feature.battery.data.BatteryHistoryRepository
import com.dragolabs.able.feature.battery.domain.BatteryFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Opt-in live battery monitor (MON-001..MON-009). Started only by an explicit user action (or
 * resumed after reboot when the user left it running). It is event-driven: it listens to battery
 * and screen broadcasts and samples current at most every [SAMPLE_INTERVAL_MS] with a non-waking
 * handler. Only per-session summaries are persisted.
 *
 * All state is confined to the main thread.
 */
class BatteryMonitorService : Service() {

    private val container by lazy { applicationContext.ableContainer }
    private val store by lazy { MonitorStateStore.get(applicationContext) }
    private val history by lazy { BatteryHistoryRepository(container.database.batteryDao(), container.preferences) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var prefs = UserPreferences()
    private var initialized = false
    private var foreground = false
    private var receiverRegistered = false
    private var screenOn = true

    /** Elapsed-realtime when the screen last turned off; null while it is on (Notte screen-off trigger). */
    private var screenOffSinceElapsed: Long? = null
    private var lastElapsed = 0L
    private var session: SessionAccumulator? = null
    private var live: LiveStats? = null
    private var lastStatus: BatteryStatus? = null
    private var thresholdArmed = true
    private var profiles: List<BatteryProfile> = emptyList()
    private var lastActiveIds: Set<String>? = null
    private var lastNotificationContent: Pair<String, List<String>>? = null

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            advance()
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    screenOffSinceElapsed = null
                    handler.removeCallbacks(screenOffCheck)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    screenOffSinceElapsed = SystemClock.elapsedRealtime()
                    scheduleScreenOffCheck()
                }
                Intent.ACTION_BATTERY_CHANGED -> handleStatus(container.battery.parse(intent))
            }
            checkpoint()
            updateNotification()
        }
    }

    private val sampler = object : Runnable {
        override fun run() {
            advance()
            container.battery.current().takeIf { it.levelPercent != null }?.let(::handleStatus)
            checkpoint()
            updateNotification()
            handler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    /** Re-evaluates profiles when the next screen-off delay elapses (non-waking, best effort). */
    private val screenOffCheck = Runnable {
        updateNotification()
        scheduleScreenOffCheck()
    }

    /** Schedules a check at the next enabled screen-off delay that has not been reached yet. */
    private fun scheduleScreenOffCheck() {
        handler.removeCallbacks(screenOffCheck)
        val since = screenOffSinceElapsed ?: return
        val offForMs = SystemClock.elapsedRealtime() - since
        val next = profiles.asSequence()
            .filter { it.enabled }
            .flatMap { it.triggers.asSequence() }
            .filterIsInstance<ProfileTrigger.ScreenOffFor>()
            .filter { it.enabled && it.minutes > 0 }
            .map { it.minutes * 60_000L }
            .filter { it > offForMs }
            .minOrNull() ?: return
        handler.postDelayed(screenOffCheck, next - offForMs + 1_000L)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        MonitorNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring(userInitiated = true)
                return START_NOT_STICKY
            }
            ACTION_RESET -> {
                if (!foreground) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                advance()
                val reason = intent.getStringExtra(EXTRA_REASON)
                    ?.let { runCatching { ResetReason.valueOf(it) }.getOrNull() } ?: ResetReason.MANUAL
                resetLive(reason, force = true)
                checkpoint()
                updateNotification()
                return START_STICKY
            }
        }
        if (foreground) return START_STICKY

        // A null intent means Android restarted the service after killing it (START_STICKY).
        val status = container.battery.current()
        val (title, lines) = MonitorNotifications.ongoingContent(this, status, store.state.value.live, prefs, null)
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                MonitorNotifications.ID_MONITOR,
                MonitorNotifications.buildOngoing(this, title, lines, prefs.monitorNotificationLayout),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        }.isSuccess
        if (!started) {
            _running.value = false
            stopSelf(startId)
            return START_NOT_STICKY
        }
        foreground = true
        _running.value = true

        scope.launch {
            prefs = container.preferences.userPreferencesFlow.first()
            container.applicationScope.launch { container.preferences.setBatteryMonitorEnabled(true) }
            MonitorRecovery.recoverInterruptedSession(store, history, prefs.batteryThermalAlertThresholdCelsius)
            live = store.state.value.live ?: LiveStats(System.currentTimeMillis(), ResetReason.FIRST_START)
            screenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: true
            // Unknown how long the screen was already off: count from now (never earlier than measured).
            screenOffSinceElapsed = if (screenOn) null else SystemClock.elapsedRealtime()
            if (!screenOn) scheduleScreenOffCheck()
            lastElapsed = SystemClock.elapsedRealtime()
            handleStatus(container.battery.current())
            registerEvents()
            initialized = true
            checkpoint()
            updateNotification()
            handler.removeCallbacks(sampler)
            handler.postDelayed(sampler, SAMPLE_INTERVAL_MS)
            launch {
                container.batteryProfiles.profiles.collect {
                    profiles = it
                    if (!screenOn) scheduleScreenOffCheck()
                    updateNotification()
                }
            }
            container.preferences.userPreferencesFlow.collect {
                prefs = it
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // Not a user stop (e.g. Android stopped the service): keep the user's intent so the UI can
        // report the interruption, and store what was measured as a partial session.
        if (foreground) teardown(persistPartial = true)
        scope.cancel()
        super.onDestroy()
    }

    private fun registerEvents() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, eventReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    /** Adds the elapsed time to the current screen state. */
    private fun advance() {
        if (!initialized) return
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - lastElapsed).coerceAtLeast(0L)
        lastElapsed = now
        val epoch = System.currentTimeMillis()
        session = session?.let {
            if (screenOn) it.copy(screenOnMs = it.screenOnMs + elapsed, lastUpdateEpochMs = epoch)
            else it.copy(screenOffMs = it.screenOffMs + elapsed, lastUpdateEpochMs = epoch)
        }
        live = live?.let {
            if (screenOn) it.copy(screenOnMs = it.screenOnMs + elapsed) else it.copy(screenOffMs = it.screenOffMs + elapsed)
        }
    }

    private fun handleStatus(status: BatteryStatus) {
        lastStatus = status
        val level = status.levelPercent ?: return
        val onPower = status.isPluggedIn || status.isCharging
        val now = System.currentTimeMillis()

        var acc = session
        if (acc == null) {
            session = newSession(now, onPower, level).withReadings(status)
            thresholdArmed = prefs.batteryResetThresholdPercent?.let { level > it } ?: true
            return
        }

        val previousLevel = acc.lastLevel
        val delta = level - previousLevel
        if (!acc.isCharging && delta < 0) {
            val drop = -delta
            acc = if (screenOn) acc.copy(screenOnDrop = acc.screenOnDrop + drop) else acc.copy(screenOffDrop = acc.screenOffDrop + drop)
            live = live?.let {
                if (screenOn) it.copy(screenOnDropPercent = it.screenOnDropPercent + drop)
                else it.copy(screenOffDropPercent = it.screenOffDropPercent + drop)
            }
        } else if (acc.isCharging && delta > 0) {
            live = live?.let { it.copy(chargedPercent = it.chargedPercent + delta) }
        }
        acc = acc.copy(lastLevel = level, lastUpdateEpochMs = now).withReadings(status)

        if (onPower != acc.isCharging) {
            // Charging transitions always close the session (MON-002: no rates across transitions).
            finishSession(acc, partial = false)
            acc = newSession(now, onPower, level).withReadings(status)
            if (onPower && prefs.batteryResetOnPlug) resetLive(ResetReason.PLUG_IN)
        }

        val threshold = prefs.batteryResetThresholdPercent
        if (!onPower && threshold != null) {
            if (thresholdArmed && previousLevel > threshold && level <= threshold) {
                resetLive(ResetReason.THRESHOLD)
                thresholdArmed = false
            } else if (level > threshold) {
                thresholdArmed = true
            }
        }

        val temperature = status.temperatureCelsius
        if (temperature != null && temperature >= prefs.batteryThermalAlertThresholdCelsius && !acc.thermalAlertSent) {
            acc = acc.copy(thermalAlertSent = true)
            MonitorNotifications.postThermalAlert(this, BatteryFormat.temperature(resources, temperature, prefs.temperatureUnit))
        }
        session = acc
    }

    private fun newSession(now: Long, onPower: Boolean, level: Int) = SessionAccumulator(
        startEpochMs = now,
        lastUpdateEpochMs = now,
        isCharging = onPower,
        startLevel = level,
        lastLevel = level,
    )

    private fun SessionAccumulator.withReadings(status: BatteryStatus): SessionAccumulator {
        var acc = this
        status.temperatureCelsius?.let { t ->
            acc = acc.copy(
                temperatureSum = acc.temperatureSum + t,
                temperatureCount = acc.temperatureCount + 1,
                temperatureMax = maxOf(acc.temperatureMax ?: t, t),
            )
        }
        status.voltageMillivolts?.let { v ->
            acc = acc.copy(voltageSumMv = acc.voltageSumMv + v, voltageCount = acc.voltageCount + 1)
        }
        status.currentMicroAmperes?.let { i ->
            acc = acc.copy(currentSumMa = acc.currentSumMa + abs(i) / 1000.0, currentCount = acc.currentCount + 1)
        }
        status.powerMilliWatts?.let { p ->
            acc = acc.copy(powerSumMw = acc.powerSumMw + p, powerCount = acc.powerCount + 1)
        }
        return acc
    }

    /** Resets the live statistics once per event (MON-005: a reset is never counted twice). */
    private fun resetLive(reason: ResetReason, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val current = live
        if (!force && current != null && current.resetReason == reason && now - current.sinceEpochMs < RESET_DEDUPE_MS) return
        live = LiveStats(now, reason)
    }

    private fun finishSession(acc: SessionAccumulator, partial: Boolean) {
        if (!acc.isMeaningful) return
        val entity = acc.toEntity(acc.lastUpdateEpochMs, partial, prefs.batteryThermalAlertThresholdCelsius)
        val appContext = applicationContext
        val snapshot = prefs
        container.applicationScope.launch(Dispatchers.IO) {
            val saved = history.insert(entity)
            if (!partial) MonitorAlerts.evaluateCompletedSession(appContext, saved, history, snapshot)
        }
    }

    private fun checkpoint() {
        if (!initialized) return
        store.save(live, session)
    }

    /**
     * Evaluates the profiles (including screen-off triggers, which only the monitor can observe)
     * and shows the active ones. It never applies app actions: the automation engine for app
     * actions is not implemented yet, and the UI says so.
     */
    private fun updateNotification() {
        if (!foreground) return
        val evaluation = container.batteryProfiles.evaluate(
            profiles = profiles,
            status = lastStatus,
            prefs = prefs,
            now = LocalDateTime.now(),
            screenOffForMs = screenOffSinceElapsed?.let { SystemClock.elapsedRealtime() - it },
        )
        if (evaluation.activeIds != lastActiveIds) {
            lastActiveIds = evaluation.activeIds
            container.applicationScope.launch { container.batteryProfiles.syncLegacySelection(evaluation) }
        }
        val content = MonitorNotifications.ongoingContent(
            this,
            lastStatus,
            live,
            prefs,
            MonitorNotifications.activeProfilesLabel(this, evaluation.active.map { it.profile }),
        )
        if (content == lastNotificationContent) return
        lastNotificationContent = content
        runCatching {
            NotificationManagerCompat.from(this).notify(
                MonitorNotifications.ID_MONITOR,
                MonitorNotifications.buildOngoing(this, content.first, content.second, prefs.monitorNotificationLayout),
            )
        }
    }

    private fun teardown(persistPartial: Boolean) {
        advance()
        handler.removeCallbacks(sampler)
        handler.removeCallbacks(screenOffCheck)
        if (receiverRegistered) {
            runCatching { unregisterReceiver(eventReceiver) }
            receiverRegistered = false
        }
        if (persistPartial) session?.let { finishSession(it, partial = true) }
        session = null
        if (initialized) store.save(live, null)
        initialized = false
        foreground = false
        _running.value = false
    }

    private fun stopMonitoring(userInitiated: Boolean) {
        if (userInitiated) {
            container.applicationScope.launch { container.preferences.setBatteryMonitorEnabled(false) }
        }
        if (foreground) teardown(persistPartial = true)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.dragolabs.able.battery.MONITOR_START"
        const val ACTION_STOP = "com.dragolabs.able.battery.MONITOR_STOP"
        const val ACTION_RESET = "com.dragolabs.able.battery.MONITOR_RESET"
        const val EXTRA_REASON = "reset_reason"

        private const val SAMPLE_INTERVAL_MS = 5 * 60 * 1000L
        private const val RESET_DEDUPE_MS = 60 * 1000L

        private val _running = MutableStateFlow(false)

        /** True while the monitor runs in this process. */
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }
}
