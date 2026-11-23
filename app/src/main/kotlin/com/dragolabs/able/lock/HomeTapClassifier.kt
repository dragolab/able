package com.dragolabs.able.lock

/**
 * Pure double-tap classifier for the home screen (LOCK-001), separated for unit tests.
 *
 * The service only learns *when* a touch starts (an `ACTION_OUTSIDE` event; Android does not
 * give the position of touches on other apps' windows), never where. Two touch-downs form a
 * candidate pair when the second starts within the user's double-tap time **plus**
 * [TAP_ALLOWANCE_MS]: the setting (like Android's own double-tap timeout and Able's practice area)
 * measures from lifting the first finger to the second touch, while Able only sees touch-downs,
 * so the duration of the first tap is added. Touches closer than [MIN_GAP_MS] are a bounce or a
 * two-finger touch.
 *
 * "Empty space" is inferred from what the launcher reports: a tap on an icon or widget produces
 * `TYPE_VIEW_CLICKED`, a long press `TYPE_VIEW_LONG_CLICKED` (rejected from shortly before the
 * first touch until the decision); a page swipe `TYPE_VIEW_SCROLLED` (rejected only while it
 * happens between the two touches, so late or unrelated scroll events do not cancel a real
 * double tap).
 *
 * The decision waits [DEFAULT_SETTLE_MS] after the second touch so the first tap's click event
 * (sent when the finger lifts) has arrived. Window-level checks (launcher in front, home pages
 * visible, no shade) are done live by [HomeDoubleTapController].
 *
 * All times are `SystemClock.uptimeMillis()` (the time base of `MotionEvent.getEventTime()` and
 * `AccessibilityEvent.getEventTime()`).
 */
class HomeTapClassifier(
    /** Delay after the second touch before deciding, so late accessibility events can arrive. */
    private val settleMs: Long = DEFAULT_SETTLE_MS,
) {
    enum class Decision { NONE, LAUNCHER_INTERACTION, DOUBLE_TAP }

    private var firstDownMs = 0L
    private var candidateDownMs = 0L
    private var pairFirstMs = 0L
    private var lastClickMs = 0L
    private var lastScrollMs = 0L
    private var lastTriggerMs = 0L

    /** Gap of the last touch that came after a first touch but too late to pair, or 0. */
    var lastSlowGapMs = 0L
        private set

    /** Down-to-down gap of the last candidate pair. */
    var lastPairGapMs = 0L
        private set

    /**
     * A touch started. Returns the uptime at which [decide] should be called, or null when this
     * touch is (for now) only the first of a possible pair.
     */
    @Synchronized
    fun onTouchDown(timeMs: Long, timeoutMs: Int): Long? {
        val first = firstDownMs
        val gap = timeMs - first
        val limit = timeoutMs.toLong() + TAP_ALLOWANCE_MS
        lastSlowGapMs = 0L
        if (first != 0L && gap in MIN_GAP_MS until limit && timeMs - lastTriggerMs > REARM_MS) {
            firstDownMs = 0L
            pairFirstMs = first
            candidateDownMs = timeMs
            lastPairGapMs = gap
            return timeMs + settleMs
        }
        if (first != 0L && gap >= limit && gap < limit + SLOW_REPORT_MS) lastSlowGapMs = gap
        // Too slow, too fast (bounce) or a third touch: start a new pair.
        firstDownMs = timeMs
        candidateDownMs = 0L
        return null
    }

    /** The launcher reported a click or long click at [timeMs]. */
    @Synchronized
    fun onLauncherClick(timeMs: Long) {
        if (timeMs > lastClickMs) lastClickMs = timeMs
    }

    /** The launcher reported a scroll at [timeMs]. */
    @Synchronized
    fun onLauncherScroll(timeMs: Long) {
        if (timeMs > lastScrollMs) lastScrollMs = timeMs
    }

    /** Kept for callers that do not distinguish clicks and scrolls; treated as a click. */
    fun onLauncherInteraction(timeMs: Long) = onLauncherClick(timeMs)

    /** Called at the time returned by [onTouchDown]. Consumes the pair. */
    @Synchronized
    fun decideDetailed(): Decision {
        val second = candidateDownMs
        candidateDownMs = 0L
        if (second == 0L) return Decision.NONE
        val from = pairFirstMs - INTERACTION_MARGIN_MS
        val clicked = lastClickMs >= from
        val scrolledBetween = lastScrollMs in from..second
        if (clicked || scrolledBetween) return Decision.LAUNCHER_INTERACTION
        lastTriggerMs = second
        return Decision.DOUBLE_TAP
    }

    fun decide(): Boolean = decideDetailed() == Decision.DOUBLE_TAP

    @Synchronized
    fun reset() {
        firstDownMs = 0L
        candidateDownMs = 0L
    }

    companion object {
        const val DEFAULT_SETTLE_MS = 150L

        /** Added to the user's double-tap time: Able measures touch-down to touch-down. */
        const val TAP_ALLOWANCE_MS = 150L

        /** Touches closer than this are treated as a bounce or a multi-finger touch. */
        const val MIN_GAP_MS = 60L

        /** Events stamped slightly before the first touch still belong to the same interaction. */
        const val INTERACTION_MARGIN_MS = 50L

        /** After a recognised double tap, ignore further pairs for this long. */
        const val REARM_MS = 300L

        /** A second touch up to this much later than the limit is reported as "too slow". */
        const val SLOW_REPORT_MS = 400L
    }
}
