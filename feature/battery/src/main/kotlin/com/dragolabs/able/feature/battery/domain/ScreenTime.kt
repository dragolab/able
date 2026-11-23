package com.dragolabs.able.feature.battery.domain

/** Screen-on/off time recorded by the live monitor since local midnight (an estimate, see [ScreenTimeMath]). */
data class ScreenTimeToday(
    val screenOnMs: Long,
    val screenOffMs: Long,
) {
    val isEmpty: Boolean get() = screenOnMs <= 0 && screenOffMs <= 0
}

/** A monitored interval with its recorded screen-on and screen-off durations. */
data class ScreenInterval(
    val startEpochMs: Long,
    val endEpochMs: Long,
    val screenOnMs: Long,
    val screenOffMs: Long,
)

object ScreenTimeMath {
    /**
     * Sums the screen time of [intervals] that falls inside `[dayStartEpochMs, nowEpochMs]`. The
     * monitor stores totals per session, not individual screen events, so a session that started
     * before midnight contributes in proportion to its overlap with today (an estimate). Returns
     * null when the monitor recorded nothing today.
     */
    fun today(intervals: List<ScreenInterval>, dayStartEpochMs: Long, nowEpochMs: Long): ScreenTimeToday? {
        var on = 0.0
        var off = 0.0
        var any = false
        for (interval in intervals) {
            val start = interval.startEpochMs
            val end = interval.endEpochMs.coerceAtMost(nowEpochMs)
            if (end <= dayStartEpochMs || start >= nowEpochMs || end <= start) continue
            val overlap = (end - maxOf(start, dayStartEpochMs)).toDouble()
            val fraction = (overlap / (end - start)).coerceIn(0.0, 1.0)
            on += interval.screenOnMs.coerceAtLeast(0) * fraction
            off += interval.screenOffMs.coerceAtLeast(0) * fraction
            any = true
        }
        if (!any) return null
        return ScreenTimeToday(on.toLong(), off.toLong())
    }
}
