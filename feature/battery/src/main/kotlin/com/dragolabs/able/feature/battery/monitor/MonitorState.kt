package com.dragolabs.able.feature.battery.monitor

import android.content.Context
import com.dragolabs.able.core.database.entity.BatterySessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Why the live statistics were last reset (MON-005). */
enum class ResetReason { FIRST_START, MANUAL, PLUG_IN, THRESHOLD, REBOOT, HISTORY_DELETED }

/**
 * Running statistics since the last reset. They span charge/discharge sessions and are what the
 * notification and the live card show. Reset rules only clear these counters; stored session
 * summaries are never altered by a reset.
 */
data class LiveStats(
    val sinceEpochMs: Long,
    val resetReason: ResetReason,
    val screenOnMs: Long = 0,
    val screenOffMs: Long = 0,
    val screenOnDropPercent: Int = 0,
    val screenOffDropPercent: Int = 0,
    val chargedPercent: Int = 0,
) {
    fun toJson(): String = JSONObject()
        .put("since", sinceEpochMs)
        .put("reason", resetReason.name)
        .put("on", screenOnMs)
        .put("off", screenOffMs)
        .put("onDrop", screenOnDropPercent)
        .put("offDrop", screenOffDropPercent)
        .put("charged", chargedPercent)
        .toString()

    companion object {
        fun fromJson(json: String): LiveStats? = runCatching {
            val o = JSONObject(json)
            LiveStats(
                sinceEpochMs = o.getLong("since"),
                resetReason = runCatching { ResetReason.valueOf(o.getString("reason")) }.getOrDefault(ResetReason.FIRST_START),
                screenOnMs = o.optLong("on"),
                screenOffMs = o.optLong("off"),
                screenOnDropPercent = o.optInt("onDrop"),
                screenOffDropPercent = o.optInt("offDrop"),
                chargedPercent = o.optInt("charged"),
            )
        }.getOrNull()
    }
}

/**
 * Temporary accumulator for the session in progress. Only its summary is persisted when the
 * session ends (MON-009: high-frequency readings are not retained as long-term history).
 */
data class SessionAccumulator(
    val startEpochMs: Long,
    val lastUpdateEpochMs: Long,
    val isCharging: Boolean,
    val startLevel: Int,
    val lastLevel: Int,
    val screenOnMs: Long = 0,
    val screenOffMs: Long = 0,
    val screenOnDrop: Int = 0,
    val screenOffDrop: Int = 0,
    val temperatureSum: Double = 0.0,
    val temperatureCount: Int = 0,
    val temperatureMax: Float? = null,
    val currentSumMa: Double = 0.0,
    val currentCount: Int = 0,
    val voltageSumMv: Double = 0.0,
    val voltageCount: Int = 0,
    val powerSumMw: Double = 0.0,
    val powerCount: Int = 0,
    val thermalAlertSent: Boolean = false,
) {
    val durationMs: Long get() = (lastUpdateEpochMs - startEpochMs).coerceAtLeast(0)

    /** False for a session too short or unchanged to be worth storing. */
    val isMeaningful: Boolean get() = durationMs >= 60_000L || lastLevel != startLevel

    fun toEntity(endEpochMs: Long, partial: Boolean, thermalThresholdC: Float): BatterySessionEntity = BatterySessionEntity(
        startTimeEpochMs = startEpochMs,
        endTimeEpochMs = endEpochMs,
        isCharging = isCharging,
        startLevelPercent = startLevel,
        endLevelPercent = lastLevel,
        avgTemperatureCelsius = if (temperatureCount > 0) (temperatureSum / temperatureCount).toFloat() else 0f,
        maxTemperatureCelsius = temperatureMax ?: 0f,
        avgCurrentMilliAmperes = if (currentCount > 0) (currentSumMa / currentCount).toFloat() else null,
        avgVoltageMillivolts = if (voltageCount > 0) (voltageSumMv / voltageCount).toFloat() else null,
        avgPowerMilliWatts = if (powerCount > 0) (powerSumMw / powerCount).toFloat() else null,
        screenOnDurationMs = screenOnMs,
        screenOffDurationMs = screenOffMs,
        // MON-003: no deep-sleep source is read; the UI labels it unavailable instead of deriving it.
        deepSleepDurationMs = 0L,
        hasThermalAlert = (temperatureMax ?: 0f) >= thermalThresholdC,
        isPartial = partial,
        screenOnDropPercent = if (isCharging) null else screenOnDrop,
        screenOffDropPercent = if (isCharging) null else screenOffDrop,
    )

    fun toJson(): String = JSONObject()
        .put("start", startEpochMs)
        .put("last", lastUpdateEpochMs)
        .put("charging", isCharging)
        .put("startLevel", startLevel)
        .put("lastLevel", lastLevel)
        .put("on", screenOnMs)
        .put("off", screenOffMs)
        .put("onDrop", screenOnDrop)
        .put("offDrop", screenOffDrop)
        .put("tSum", temperatureSum)
        .put("tCount", temperatureCount)
        .put("tMax", temperatureMax?.toDouble() ?: -1.0)
        .put("iSum", currentSumMa)
        .put("iCount", currentCount)
        .put("vSum", voltageSumMv)
        .put("vCount", voltageCount)
        .put("pSum", powerSumMw)
        .put("pCount", powerCount)
        .put("thermal", thermalAlertSent)
        .toString()

    companion object {
        fun fromJson(json: String): SessionAccumulator? = runCatching {
            val o = JSONObject(json)
            SessionAccumulator(
                startEpochMs = o.getLong("start"),
                lastUpdateEpochMs = o.getLong("last"),
                isCharging = o.getBoolean("charging"),
                startLevel = o.getInt("startLevel"),
                lastLevel = o.getInt("lastLevel"),
                screenOnMs = o.optLong("on"),
                screenOffMs = o.optLong("off"),
                screenOnDrop = o.optInt("onDrop"),
                screenOffDrop = o.optInt("offDrop"),
                temperatureSum = o.optDouble("tSum", 0.0),
                temperatureCount = o.optInt("tCount"),
                temperatureMax = o.optDouble("tMax", -1.0).takeIf { it >= 0 }?.toFloat(),
                currentSumMa = o.optDouble("iSum", 0.0),
                currentCount = o.optInt("iCount"),
                voltageSumMv = o.optDouble("vSum", 0.0),
                voltageCount = o.optInt("vCount"),
                powerSumMw = o.optDouble("pSum", 0.0),
                powerCount = o.optInt("pCount"),
                thermalAlertSent = o.optBoolean("thermal"),
            )
        }.getOrNull()
    }
}

data class StoredMonitorState(
    val live: LiveStats? = null,
    val session: SessionAccumulator? = null,
)

/**
 * Small local checkpoint for the monitor so an interrupted session (reboot, process death) can be
 * stored as partial instead of being lost or presented as complete (MON-001). Process-wide
 * singleton so the service, boot receiver and ViewModel observe the same state.
 */
class MonitorStateStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<StoredMonitorState> = _state.asStateFlow()

    private fun read() = StoredMonitorState(
        live = prefs.getString(KEY_LIVE, null)?.let(LiveStats::fromJson),
        session = prefs.getString(KEY_SESSION, null)?.let(SessionAccumulator::fromJson),
    )

    @Synchronized
    fun save(live: LiveStats?, session: SessionAccumulator?) {
        prefs.edit().apply {
            if (live == null) remove(KEY_LIVE) else putString(KEY_LIVE, live.toJson())
            if (session == null) remove(KEY_SESSION) else putString(KEY_SESSION, session.toJson())
        }.apply()
        _state.value = StoredMonitorState(live, session)
    }

    fun saveSession(session: SessionAccumulator?) = save(_state.value.live, session)

    fun saveLive(live: LiveStats?) = save(live, _state.value.session)

    companion object {
        private const val FILE = "battery_monitor_state"
        private const val KEY_LIVE = "live"
        private const val KEY_SESSION = "session"

        @Volatile
        private var instance: MonitorStateStore? = null

        fun get(context: Context): MonitorStateStore = instance ?: synchronized(this) {
            instance ?: MonitorStateStore(context).also { instance = it }
        }
    }
}
