package com.dragolabs.able.feature.tools.fakegps

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.feature.tools.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.min

/**
 * Mock location engine. A user-started foreground service (type `location`) that registers
 * Android test providers (`gps`, `network`, `fused` where allowed), enables Google Play
 * services' fused mock mode when available, and pushes the fake position about once per
 * second (four times per second while the joystick moves). Stopping removes every test
 * provider, so apps get the real location again at their next update.
 *
 * All state is confined to the main thread.
 */
class FakeGpsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val locationManager by lazy { getSystemService(LocationManager::class.java) }
    private val container by lazy { applicationContext.ableContainer }

    private var loop: Job? = null
    private var fusedClient: FusedLocationProviderClient? = null
    private var fusedMock = false
    private val providers = mutableListOf<String>()
    private val jitter = JitterModel()

    private var running = false
    private var lastTickElapsed = 0L
    private var travelled = 0.0
    private var routeKey: Any? = null
    private var lastBearing = 0f
    private var ticks = 0L
    private var notifiedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEngine()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        start()
        return START_NOT_STICKY
    }

    private fun start() {
        FakeGpsSession.updateEngine { EngineState(status = EngineStatus.Starting) }
        ensureChannel(this)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(null),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: SecurityException) {
            fail(EngineFailure.LOCATION_PERMISSION, e.message)
            return
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+) extends IllegalStateException.
            fail(EngineFailure.FOREGROUND_NOT_ALLOWED, e.message)
            return
        }

        if (!MockLocationAccess.isMockAppSelected(this)) {
            fail(EngineFailure.NOT_MOCK_APP, null)
            return
        }
        val security = registerProviders()
        if (providers.isEmpty()) {
            fail(if (security) EngineFailure.NOT_MOCK_APP else EngineFailure.NO_PROVIDER, null)
            return
        }
        enableFusedMock()

        running = true
        travelled = 0.0
        routeKey = null
        ticks = 0
        lastTickElapsed = SystemClock.elapsedRealtime()
        FakeGpsSession.updateEngine {
            it.copy(status = EngineStatus.Running, providers = providers.toList(), routeFinished = false, routeProgress = 0.0)
        }
        val config = FakeGpsSession.config.value
        FakeGpsSession.config.value.target?.let { FakeGpsStore.get(this).addHistory(it) }
        log(
            ACTION_ID_START,
            ActionExecutionStatus.SUCCESS,
            getString(
                R.string.fakegps_log_started,
                config.target?.format() ?: "-",
                providers.joinToString(", "),
            ),
        )
        loop = scope.launch {
            while (isActive) {
                tick()
                val (x, y) = FakeGpsSession.joystick.value
                val moving = FakeGpsSession.config.value.mode == FakeMode.JOYSTICK && hypot(x, y) > JOYSTICK_DEADZONE
                delay(if (moving) FAST_INTERVAL_MS else INTERVAL_MS)
            }
        }
    }

    /** Registers the test providers. Returns true when Android rejected them for security reasons. */
    private fun registerProviders(): Boolean {
        val lm = locationManager ?: return false
        var securityRejected = false
        for (name in PROVIDERS) {
            // A provider left over from a killed process would make addTestProvider fail.
            runCatching { lm.removeTestProvider(name) }
            try {
                lm.addTestProvider(name, properties(name))
                lm.setTestProviderEnabled(name, true)
                providers += name
            } catch (_: SecurityException) {
                securityRejected = true
            } catch (_: IllegalArgumentException) {
                // Provider name not allowed on this Android build; skip it.
            }
        }
        return securityRejected
    }

    private fun properties(name: String): ProviderProperties = ProviderProperties.Builder()
        .setHasNetworkRequirement(name == LocationManager.NETWORK_PROVIDER)
        .setHasSatelliteRequirement(name == LocationManager.GPS_PROVIDER)
        .setHasCellRequirement(false)
        .setHasMonetaryCost(false)
        .setHasAltitudeSupport(true)
        .setHasSpeedSupport(true)
        .setHasBearingSupport(true)
        .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
        .setAccuracy(
            if (name == LocationManager.NETWORK_PROVIDER) ProviderProperties.ACCURACY_COARSE else ProviderProperties.ACCURACY_FINE,
        )
        .build()

    @SuppressLint("MissingPermission")
    private fun enableFusedMock() {
        val client = runCatching { LocationServices.getFusedLocationProviderClient(this) }.getOrNull()
        if (client == null) {
            FakeGpsSession.updateEngine { it.copy(fusedMockMode = false) }
            return
        }
        fusedClient = client
        try {
            client.setMockMode(true)
                .addOnSuccessListener {
                    fusedMock = running
                    FakeGpsSession.updateEngine { it.copy(fusedMockMode = running) }
                }
                .addOnFailureListener {
                    fusedMock = false
                    FakeGpsSession.updateEngine { it.copy(fusedMockMode = false) }
                }
        } catch (_: Exception) {
            FakeGpsSession.updateEngine { it.copy(fusedMockMode = false) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun tick() {
        val config = FakeGpsSession.config.value
        val now = SystemClock.elapsedRealtime()
        val dt = ((now - lastTickElapsed) / 1000.0).coerceIn(0.0, MAX_STEP_S)
        lastTickElapsed = now
        val preset = config.speed.metersPerSecond

        var speed = 0.0
        var bearing: Double? = null
        var progress = 0.0
        var finished = false
        val base: LatLon = when (config.mode) {
            FakeMode.POINT -> config.target
            FakeMode.JOYSTICK -> {
                val (x, y) = FakeGpsSession.joystick.value
                val stickBearing = GeoMath.joystickBearing(x, y)
                val current = config.target
                if (stickBearing != null && current != null) {
                    val magnitude = min(1.0, hypot(x.toDouble(), y.toDouble()))
                    speed = preset * magnitude
                    bearing = stickBearing
                    val next = GeoMath.destination(current, stickBearing, speed * dt)
                    FakeGpsSession.updateConfig { it.copy(target = next) }
                    next
                } else {
                    current
                }
            }
            FakeMode.ROUTE -> {
                if (config.waypoints.size < 2) {
                    config.target ?: config.waypoints.firstOrNull()
                } else {
                    val key = Triple(config.waypoints, config.repeat, FakeGpsSession.routeEpoch.value)
                    if (key != routeKey) {
                        routeKey = key
                        travelled = 0.0
                    } else {
                        travelled += preset * dt
                    }
                    val sample = RoutePath.sample(config.waypoints, config.repeat, travelled)
                    if (sample == null) {
                        config.target
                    } else {
                        finished = sample.finished
                        progress = sample.progress
                        speed = if (finished) 0.0 else preset
                        bearing = sample.bearingDegrees
                        sample.position
                    }
                }
            }
        } ?: return

        val stationary = speed == 0.0
        val position = if (config.jitter && stationary) {
            val (north, east) = jitter.next(JitterModel.radiusFor(config.accuracyMeters))
            GeoMath.offset(base, north, east)
        } else {
            base
        }
        val accuracy = if (config.jitter) jitter.accuracy(config.accuracyMeters) else config.accuracyMeters
        val altitude = if (config.jitter) jitter.altitude(config.altitudeMeters) else config.altitudeMeters
        bearing?.let { lastBearing = it.toFloat() }

        val lm = locationManager ?: return
        for (name in providers.toList()) {
            try {
                lm.setTestProviderLocation(name, buildLocation(name, position, accuracy, altitude, speed, bearing))
            } catch (e: SecurityException) {
                fail(EngineFailure.REVOKED, e.message)
                return
            } catch (_: IllegalArgumentException) {
                providers.remove(name)
            }
        }
        if (fusedMock) {
            runCatching {
                fusedClient?.setMockLocation(buildLocation(FUSED, position, accuracy, altitude, speed, bearing))
            }
        }
        ticks++
        FakeGpsSession.updateEngine {
            it.copy(
                emitted = position,
                speedMetersPerSecond = speed.toFloat(),
                bearingDegrees = lastBearing,
                accuracyMeters = accuracy,
                altitudeMeters = altitude,
                providers = providers.toList(),
                routeProgress = progress,
                routeFinished = finished,
                updates = ticks,
            )
        }
        if (now - notifiedAt >= NOTIFICATION_REFRESH_MS) {
            notifiedAt = now
            runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(position)) }
        }
    }

    private fun buildLocation(
        provider: String,
        position: LatLon,
        accuracy: Float,
        altitude: Double,
        speed: Double,
        bearing: Double?,
    ): Location = Location(provider).apply {
        latitude = position.latitude
        longitude = position.longitude
        this.accuracy = accuracy
        this.altitude = altitude
        verticalAccuracyMeters = accuracy * 1.5f
        this.speed = speed.toFloat()
        speedAccuracyMetersPerSecond = 0.5f
        if (bearing != null) {
            this.bearing = bearing.toFloat()
            bearingAccuracyDegrees = 10f
        }
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        if (provider == LocationManager.GPS_PROVIDER) {
            extras = Bundle().apply { putInt("satellites", 9) }
        }
    }

    /** Removes the test providers and fused mock mode. Safe to call repeatedly. */
    private fun cleanup(): Int {
        loop?.cancel()
        loop = null
        var removed = 0
        val lm = locationManager
        for (name in providers) {
            runCatching { lm?.setTestProviderEnabled(name, false) }
            if (runCatching { lm?.removeTestProvider(name) }.isSuccess) removed++
        }
        providers.clear()
        if (fusedMock || fusedClient != null) {
            runCatching { fusedClient?.setMockMode(false) }
        }
        fusedMock = false
        fusedClient = null
        FakeGpsSession.setJoystick(0f, 0f)
        return removed
    }

    private fun stopEngine() {
        if (running) {
            running = false
            val removed = cleanup()
            FakeGpsStore.get(this).saveConfig(FakeGpsSession.config.value)
            FakeGpsSession.updateEngine { EngineState(status = EngineStatus.Idle) }
            log(ACTION_ID_STOP, ActionExecutionStatus.SUCCESS, getString(R.string.fakegps_log_stopped, removed))
        } else {
            cleanup()
            if (FakeGpsSession.engine.value.status !is EngineStatus.Failed) {
                FakeGpsSession.updateEngine { EngineState(status = EngineStatus.Idle) }
            }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(failure: EngineFailure, detail: String?) {
        val wasRunning = running
        running = false
        cleanup()
        FakeGpsSession.updateEngine { EngineState(status = EngineStatus.Failed(failure, detail)) }
        log(
            if (wasRunning) ACTION_ID_STOP else ACTION_ID_START,
            ActionExecutionStatus.FAILED,
            getString(R.string.fakegps_log_failed, failure.name + (detail?.let { ": $it" } ?: "")),
        )
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        if (running) {
            running = false
            val removed = cleanup()
            FakeGpsStore.get(this).saveConfig(FakeGpsSession.config.value)
            FakeGpsSession.updateEngine { EngineState(status = EngineStatus.Idle) }
            log(ACTION_ID_STOP, ActionExecutionStatus.SUCCESS, getString(R.string.fakegps_log_stopped, removed))
        } else {
            cleanup()
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun log(actionId: String, status: ActionExecutionStatus, detail: String) {
        val c = container
        c.applicationScope.launch { runCatching { c.actionLog.log(actionId, status, detail, reversible = true) } }
    }

    private fun buildNotification(position: LatLon?): Notification {
        val text = position?.let { getString(R.string.fakegps_notif_text, it.format()) }
            ?: getString(R.string.fakegps_notif_starting)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.fakegps_ic_notification)
            .setContentTitle(getString(R.string.fakegps_notif_title))
            .setContentText(text)
            .setContentIntent(openToolIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                0,
                getString(R.string.fakegps_notif_stop),
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, FakeGpsService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    private fun openToolIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?.putExtra("route", "tool/fakegps")
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return null
        return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACTION_STOP = "com.dragolabs.able.fakegps.STOP"
        private const val CHANNEL_ID = "fake_gps"
        private const val NOTIFICATION_ID = 7301
        private const val INTERVAL_MS = 1_000L
        private const val FAST_INTERVAL_MS = 250L
        private const val MAX_STEP_S = 5.0
        private const val NOTIFICATION_REFRESH_MS = 5_000L
        private const val JOYSTICK_DEADZONE = 0.05f
        private const val FUSED = "fused"
        const val ACTION_ID_START = "tools.fakegps.start"
        const val ACTION_ID_STOP = "tools.fakegps.stop"
        const val ACTION_ID_MOCK_APP = "tools.fakegps.mockapp"

        private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FakeGpsService::class.java))
        }

        fun stop(context: Context) {
            if (FakeGpsSession.engine.value.isActive) {
                context.startService(Intent(context, FakeGpsService::class.java).setAction(ACTION_STOP))
            }
        }

        /**
         * Android keeps test providers registered after the owning process dies (verified on the
         * API 36 emulator: a killed Able process left gps/network/fused as `[mock]` with the last
         * fake fix). Called when the screen opens with no engine running in this process, so a
         * crash or force-stop never leaves the device stuck on a fake position.
         * Returns the number of providers removed.
         */
        @SuppressLint("MissingPermission")
        fun clearStaleProviders(context: Context): Int {
            if (FakeGpsSession.engine.value.isActive) return 0
            if (!MockLocationAccess.isMockAppSelected(context)) return 0
            val lm = context.getSystemService(LocationManager::class.java) ?: return 0
            var removed = 0
            for (name in PROVIDERS) {
                if (runCatching { lm.removeTestProvider(name) }.isSuccess) removed++
            }
            runCatching { LocationServices.getFusedLocationProviderClient(context).setMockMode(false) }
            return removed
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.fakegps_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.fakegps_channel_desc) },
            )
        }
    }
}
