package com.dragolabs.able.feature.battery.domain

import com.dragolabs.able.core.database.entity.BatterySessionEntity
import kotlin.math.abs

/**
 * Pure drain/charge calculations (MON-002). A rate is only produced across a valid elapsed
 * interval with a real whole-percent change; otherwise the caller shows "not enough data".
 */
object DrainMath {
    const val MIN_RATE_WINDOW_MS = 15 * 60 * 1000L

    /** Percent per hour, or null when the interval or the change is too small to be meaningful. */
    fun ratePerHour(changePercent: Int?, durationMs: Long): Float? {
        if (changePercent == null || changePercent <= 0 || durationMs < MIN_RATE_WINDOW_MS) return null
        return changePercent * HOUR_MS.toFloat() / durationMs
    }
}

/** Derived, display-ready values for one stored session. */
data class SessionRates(
    val durationMs: Long,
    /** Discharge: overall drop per hour. Charging: gain per hour. */
    val overallRatePerHour: Float?,
    /** Screen-on drop per hour (discharge only, null for older rows without split drops). */
    val activeRatePerHour: Float?,
    /** Screen-off drop per hour (discharge only, null for older rows without split drops). */
    val idleRatePerHour: Float?,
)

fun BatterySessionEntity.rates(): SessionRates {
    val duration = (endTimeEpochMs - startTimeEpochMs).coerceAtLeast(0L)
    return if (isCharging) {
        SessionRates(
            durationMs = duration,
            overallRatePerHour = DrainMath.ratePerHour(endLevelPercent - startLevelPercent, duration),
            activeRatePerHour = null,
            idleRatePerHour = null,
        )
    } else {
        SessionRates(
            durationMs = duration,
            overallRatePerHour = DrainMath.ratePerHour(startLevelPercent - endLevelPercent, duration),
            activeRatePerHour = DrainMath.ratePerHour(screenOnDropPercent, screenOnDurationMs),
            idleRatePerHour = DrainMath.ratePerHour(screenOffDropPercent, screenOffDurationMs),
        )
    }
}

enum class BaselineStatus {
    /** Fewer than [IdleBaselineCalculator.MIN_VALID_SESSIONS] valid sessions in the window. */
    COLLECTING,

    /** Enough valid sessions, but too few comparable to the latest one. */
    INSUFFICIENT_COMPARABLE,

    /** A personal range is available. */
    ESTABLISHED,
}

/** Personal idle-drain baseline (BASE-001 detail). Every value is an estimate from the user's own data. */
data class IdleBaseline(
    val status: BaselineStatus,
    val validSessionsInWindow: Int,
    val latestSessionId: Long?,
    val latestRatePercentPerHour: Float?,
    val latestDurationMs: Long,
    val comparableCount: Int,
    val medianRatePercentPerHour: Float?,
    val minimumRatePercentPerHour: Float?,
    val maximumRatePercentPerHour: Float?,
    /** Valid session rates in chronological order (oldest first) for the trend chart. */
    val trend: List<Float>,
) {
    val latestAboveRange: Boolean
        get() = status == BaselineStatus.ESTABLISHED &&
            latestRatePercentPerHour != null && maximumRatePercentPerHour != null &&
            latestRatePercentPerHour > maximumRatePercentPerHour

    companion object {
        val Empty = IdleBaseline(BaselineStatus.COLLECTING, 0, null, null, 0, 0, null, null, null, emptyList())
    }
}

object IdleBaselineCalculator {
    const val DAY_MS = 24 * 60 * 60 * 1000L
    const val WINDOW_MS = 14 * DAY_MS
    const val MIN_SESSION_MS = 4 * 60 * 60 * 1000L
    const val MIN_SCREEN_OFF_SHARE = 0.9
    const val START_LEVEL_TOLERANCE_PERCENT = 15
    const val MIN_DURATION_RATIO = 0.75
    const val MAX_DURATION_RATIO = 1.25
    const val MIN_VALID_SESSIONS = 5
    const val MIN_COMPARABLE_SESSIONS = 4

    private data class Valid(val session: BatterySessionEntity, val durationMs: Long, val rate: Float)

    /** Screen-off, unplugged, complete and long enough to reduce whole-percent rounding noise. */
    fun idleRateIfValid(session: BatterySessionEntity): Float? {
        val durationMs = session.endTimeEpochMs - session.startTimeEpochMs
        if (session.isCharging || session.isPartial || durationMs < MIN_SESSION_MS) return null
        val screenOffShare = session.screenOffDurationMs.toDouble() / durationMs
        if (screenOffShare < MIN_SCREEN_OFF_SHARE) return null
        val drop = session.startLevelPercent - session.endLevelPercent
        if (drop <= 0) return null
        return drop * HOUR_MS.toFloat() / durationMs
    }

    fun calculate(sessions: List<BatterySessionEntity>, nowEpochMs: Long): IdleBaseline {
        val valid = sessions.mapNotNull { session ->
            val age = nowEpochMs - session.endTimeEpochMs
            if (age !in 0..WINDOW_MS) return@mapNotNull null
            val rate = idleRateIfValid(session) ?: return@mapNotNull null
            Valid(session, session.endTimeEpochMs - session.startTimeEpochMs, rate)
        }.sortedByDescending { it.session.endTimeEpochMs }

        val latest = valid.firstOrNull() ?: return IdleBaseline.Empty
        val comparable = valid.drop(1).filter { candidate ->
            val ratio = candidate.durationMs.toDouble() / latest.durationMs
            abs(candidate.session.startLevelPercent - latest.session.startLevelPercent) <= START_LEVEL_TOLERANCE_PERCENT &&
                ratio in MIN_DURATION_RATIO..MAX_DURATION_RATIO
        }
        val rates = comparable.map { it.rate }.sorted()
        val median = when {
            rates.isEmpty() -> null
            rates.size % 2 == 1 -> rates[rates.size / 2]
            else -> (rates[rates.size / 2 - 1] + rates[rates.size / 2]) / 2f
        }
        val status = when {
            valid.size < MIN_VALID_SESSIONS -> BaselineStatus.COLLECTING
            comparable.size < MIN_COMPARABLE_SESSIONS -> BaselineStatus.INSUFFICIENT_COMPARABLE
            else -> BaselineStatus.ESTABLISHED
        }
        return IdleBaseline(
            status = status,
            validSessionsInWindow = valid.size,
            latestSessionId = latest.session.id,
            latestRatePercentPerHour = latest.rate,
            latestDurationMs = latest.durationMs,
            comparableCount = comparable.size,
            medianRatePercentPerHour = median,
            minimumRatePercentPerHour = rates.firstOrNull(),
            maximumRatePercentPerHour = rates.lastOrNull(),
            trend = valid.take(14).map { it.rate }.reversed(),
        )
    }
}

internal const val HOUR_MS = 60 * 60 * 1000L
