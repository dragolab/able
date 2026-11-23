package com.dragolabs.able.feature.tools.fakegps

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** How the fake position is driven while the engine runs. */
enum class FakeMode { POINT, JOYSTICK, ROUTE }

/** Movement speed for the joystick and route simulation. */
enum class SpeedPreset(val metersPerSecond: Double) {
    WALK(1.4),
    BIKE(5.5),
    CAR(13.9),
}

/** User-chosen simulation settings. Written by the screen, read by [FakeGpsService]. */
data class FakeGpsConfig(
    val target: LatLon? = null,
    val mode: FakeMode = FakeMode.POINT,
    val waypoints: List<LatLon> = emptyList(),
    val speed: SpeedPreset = SpeedPreset.WALK,
    val repeat: RouteRepeat = RouteRepeat.LOOP,
    val jitter: Boolean = true,
    val altitudeMeters: Double = DEFAULT_ALTITUDE_M,
    val accuracyMeters: Float = DEFAULT_ACCURACY_M,
) {
    companion object {
        const val DEFAULT_ALTITUDE_M = 50.0
        const val DEFAULT_ACCURACY_M = 5f
        const val MIN_ACCURACY_M = 1f
        const val MAX_ACCURACY_M = 50f
        const val MIN_ALTITUDE_M = -100.0
        const val MAX_ALTITUDE_M = 4000.0
        const val MAX_WAYPOINTS = 25
    }
}

/** Why the mock engine could not start or stopped by itself. Reported honestly to the user. */
enum class EngineFailure {
    /** Able is not the selected mock location app (app-op `android:mock_location` not allowed). */
    NOT_MOCK_APP,

    /** Location permission is missing, which Android requires for a location foreground service. */
    LOCATION_PERMISSION,

    /** Android refused to start the foreground service. */
    FOREGROUND_NOT_ALLOWED,

    /** No test provider could be registered. */
    NO_PROVIDER,

    /** Mock permission was revoked while running. */
    REVOKED,
    GENERIC,
}

sealed interface EngineStatus {
    data object Idle : EngineStatus
    data object Starting : EngineStatus
    data object Running : EngineStatus
    data class Failed(val failure: EngineFailure, val detail: String? = null) : EngineStatus
}

data class EngineState(
    val status: EngineStatus = EngineStatus.Idle,
    /** Last location pushed to the test providers (with jitter applied). */
    val emitted: LatLon? = null,
    val speedMetersPerSecond: Float = 0f,
    val bearingDegrees: Float = 0f,
    val accuracyMeters: Float = 0f,
    val altitudeMeters: Double = 0.0,
    /** Test providers successfully registered, e.g. `gps`, `network`, `fused`. */
    val providers: List<String> = emptyList(),
    /** Whether Google Play services' fused provider accepted mock mode. Null while unknown. */
    val fusedMockMode: Boolean? = null,
    val routeProgress: Double = 0.0,
    val routeFinished: Boolean = false,
    val updates: Long = 0,
) {
    val isActive: Boolean get() = status == EngineStatus.Running || status == EngineStatus.Starting
}

/**
 * Process-wide state shared by the Fake GPS screen and [FakeGpsService]. The screen owns the
 * configuration; the service owns the engine state (and advances [FakeGpsConfig.target] while
 * the joystick moves it).
 */
internal object FakeGpsSession {
    private val _config = MutableStateFlow(FakeGpsConfig())
    val config: StateFlow<FakeGpsConfig> = _config.asStateFlow()

    private val _engine = MutableStateFlow(EngineState())
    val engine: StateFlow<EngineState> = _engine.asStateFlow()

    /** Joystick deflection, screen axes (x right, y down), each -1..1. */
    private val _joystick = MutableStateFlow(0f to 0f)
    val joystick: StateFlow<Pair<Float, Float>> = _joystick.asStateFlow()

    /** Bumped when the route must restart from its first waypoint. */
    private val _routeEpoch = MutableStateFlow(0L)
    val routeEpoch: StateFlow<Long> = _routeEpoch.asStateFlow()

    private var restored = false

    fun restoreOnce(store: FakeGpsStore) {
        if (restored) return
        restored = true
        _config.value = store.loadConfig()
    }

    fun updateConfig(transform: (FakeGpsConfig) -> FakeGpsConfig) {
        _config.update(transform)
    }

    fun updateEngine(transform: (EngineState) -> EngineState) {
        _engine.update(transform)
    }

    fun setJoystick(x: Float, y: Float) {
        _joystick.value = x.coerceIn(-1f, 1f) to y.coerceIn(-1f, 1f)
    }

    fun restartRoute() {
        _routeEpoch.update { it + 1 }
    }
}

/** Recent fake location (FGPS history). */
data class HistoryEntry(val position: LatLon, val epochMs: Long)

/**
 * Feature-local persistence for the simulation settings and the recent fake locations. Kept in
 * private SharedPreferences: small, device-local, never synced.
 */
internal class FakeGpsStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(readHistory())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    fun loadConfig(): FakeGpsConfig {
        val default = FakeGpsConfig()
        val lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull()
        val lon = prefs.getString(KEY_LON, null)?.toDoubleOrNull()
        return FakeGpsConfig(
            target = if (lat != null && lon != null) LatLon(lat, lon).takeIf { it.isValid } else null,
            mode = enumOr(prefs.getString(KEY_MODE, null), default.mode),
            waypoints = decodePoints(prefs.getString(KEY_WAYPOINTS, null)),
            speed = enumOr(prefs.getString(KEY_SPEED, null), default.speed),
            repeat = enumOr(prefs.getString(KEY_REPEAT, null), default.repeat),
            jitter = prefs.getBoolean(KEY_JITTER, default.jitter),
            altitudeMeters = prefs.getString(KEY_ALTITUDE, null)?.toDoubleOrNull()
                ?.coerceIn(FakeGpsConfig.MIN_ALTITUDE_M, FakeGpsConfig.MAX_ALTITUDE_M) ?: default.altitudeMeters,
            accuracyMeters = prefs.getFloat(KEY_ACCURACY, default.accuracyMeters)
                .coerceIn(FakeGpsConfig.MIN_ACCURACY_M, FakeGpsConfig.MAX_ACCURACY_M),
        )
    }

    fun saveConfig(config: FakeGpsConfig) {
        prefs.edit {
            if (config.target != null) {
                putString(KEY_LAT, config.target.latitude.toString())
                putString(KEY_LON, config.target.longitude.toString())
            } else {
                remove(KEY_LAT)
                remove(KEY_LON)
            }
            putString(KEY_MODE, config.mode.name)
            putString(KEY_WAYPOINTS, encodePoints(config.waypoints))
            putString(KEY_SPEED, config.speed.name)
            putString(KEY_REPEAT, config.repeat.name)
            putBoolean(KEY_JITTER, config.jitter)
            putString(KEY_ALTITUDE, config.altitudeMeters.toString())
            putFloat(KEY_ACCURACY, config.accuracyMeters)
        }
    }

    /** Adds [position] to the top of the history, merging entries closer than 15 m. */
    fun addHistory(position: LatLon, nowMs: Long = System.currentTimeMillis()) {
        val next = (
            listOf(HistoryEntry(position, nowMs)) +
                _history.value.filter { GeoMath.distanceMeters(it.position, position) > MERGE_DISTANCE_M }
            ).take(MAX_HISTORY)
        _history.value = next
        prefs.edit { putString(KEY_HISTORY, next.joinToString("|") { "${it.position.latitude};${it.position.longitude};${it.epochMs}" }) }
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit { remove(KEY_HISTORY) }
    }

    private fun readHistory(): List<HistoryEntry> =
        prefs.getString(KEY_HISTORY, null).orEmpty().split('|').mapNotNull { raw ->
            val parts = raw.split(';')
            if (parts.size != 3) return@mapNotNull null
            val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val ms = parts[2].toLongOrNull() ?: return@mapNotNull null
            LatLon(lat, lon).takeIf { it.isValid }?.let { HistoryEntry(it, ms) }
        }

    private fun encodePoints(points: List<LatLon>): String =
        points.joinToString("|") { "${it.latitude};${it.longitude}" }

    private fun decodePoints(raw: String?): List<LatLon> =
        raw.orEmpty().split('|').mapNotNull { item ->
            val parts = item.split(';')
            if (parts.size != 2) return@mapNotNull null
            val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            LatLon(lat, lon).takeIf { it.isValid }
        }.take(FakeGpsConfig.MAX_WAYPOINTS)

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback

    companion object {
        @Volatile
        private var instance: FakeGpsStore? = null

        fun get(context: Context): FakeGpsStore = instance ?: synchronized(this) {
            instance ?: FakeGpsStore(context.applicationContext).also { instance = it }
        }

        private const val PREFS = "able_fakegps"
        private const val KEY_LAT = "target_lat"
        private const val KEY_LON = "target_lon"
        private const val KEY_MODE = "mode"
        private const val KEY_WAYPOINTS = "waypoints"
        private const val KEY_SPEED = "speed"
        private const val KEY_REPEAT = "repeat"
        private const val KEY_JITTER = "jitter"
        private const val KEY_ALTITUDE = "altitude"
        private const val KEY_ACCURACY = "accuracy"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY = 15
        private const val MERGE_DISTANCE_M = 15.0
    }
}
