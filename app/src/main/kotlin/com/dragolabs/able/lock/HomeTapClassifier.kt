package com.dragolabs.able.lock

/**
 * Pure timing rule for the home-screen double tap (LOCK-001), the same rule as the reference app
 * "Double Tap Screen Off" (jamworks, v1.22, `d30.onTouch`): every touch-down anywhere on screen
 * arrives as one `ACTION_OUTSIDE`; when a touch-down follows the previous one by more than
 * [MIN_GAP_MS] and less than the double-tap limit, and the last recognised double tap is more
 * than [REARM_MS] old, it is a double tap — decided immediately at the second touch-down, with no
 * waiting and no guessing from launcher events.
 *
 * The limit is the user's double-tap time plus [TAP_ALLOWANCE_MS]: the setting is measured, like
 * Android's double-tap timeout and Able's practice area, from lifting the first finger to the
 * second touch, while only touch-downs are seen here.
 *
 * Times are `MotionEvent.getEventTime()` (uptime milliseconds).
 */
class HomeTapClassifier {
    sealed interface Result {
        /** First touch of a possible pair (or a bounce). */
        data object None : Result

        /** Second touch within the limit: a double tap. */
        data class DoubleTap(val gapMs: Long) : Result

        /** Second touch that came too late, reported so the user can raise the time. */
        data class TooSlow(val gapMs: Long) : Result
    }

    private var lastDownMs = 0L
    private var lastTriggerMs = 0L

    @Synchronized
    fun onTouchDown(timeMs: Long, timeoutMs: Int): Result {
        val previous = lastDownMs
        lastDownMs = timeMs
        if (previous == 0L) return Result.None
        val gap = timeMs - previous
        val limit = timeoutMs.toLong() + TAP_ALLOWANCE_MS
        return when {
            timeMs - lastTriggerMs <= REARM_MS -> Result.None
            gap in (MIN_GAP_MS + 1) until limit -> {
                lastTriggerMs = timeMs
                // A third quick touch must not pair with the second one.
                lastDownMs = 0L
                Result.DoubleTap(gap)
            }
            gap >= limit && gap < limit + SLOW_REPORT_MS -> Result.TooSlow(gap)
            else -> Result.None
        }
    }

    /** Forgets the double tap that was not acted on (e.g. not on the home pages). */
    @Synchronized
    fun cancelTrigger() {
        lastTriggerMs = 0L
    }

    @Synchronized
    fun reset() {
        lastDownMs = 0L
    }

    companion object {
        /** Same lower bound as the reference app: closer touches are a bounce or two fingers. */
        const val MIN_GAP_MS = 80L

        /** Added to the user's double-tap time: only touch-downs are seen. */
        const val TAP_ALLOWANCE_MS = 150L

        /** Same as the reference app: ignore pairs within this time after a double tap. */
        const val REARM_MS = 200L

        /** A second touch up to this much later than the limit is reported as "too slow". */
        const val SLOW_REPORT_MS = 400L
    }
}
