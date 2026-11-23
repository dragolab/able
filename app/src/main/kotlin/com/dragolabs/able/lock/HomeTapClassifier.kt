package com.dragolabs.able.lock

/**
 * Pure double-tap classifier for the home screen (LOCK-001), separated for unit tests.
 *
 * The service only learns *when* a touch starts (an `ACTION_OUTSIDE` event; Android does not
 * give the position of touches on other apps' windows), never where. Two touch-downs closer than
 * the user's timeout (and farther apart than [MIN_GAP_MS], so a bounce or a two-finger touch is
 * ignored) form a candidate pair. "Empty space" is inferred from what the launcher reports: a tap
 * on an icon or widget produces `TYPE_VIEW_CLICKED`, a long press `TYPE_VIEW_LONG_CLICKED`, a
 * page swipe `TYPE_VIEW_SCROLLED`. If any of those is reported from shortly before the first
 * touch until the decision, the pair is rejected.
 *
 * The decision waits [DEFAULT_SETTLE_MS] after the second touch, so the click event of the first
 * tap (sent when the finger lifts) has arrived; the service asks for these events with no
 * notification delay while the feature is on. Window-level checks (launcher still in front, home
 * pages visible, no shade) are done live by [HomeDoubleTapController] at decision time, as the
 * reference app "Double Tap Screen Off" does at the second touch.
 *
 * All times are `SystemClock.uptimeMillis()` (the time base of `MotionEvent.getEventTime()` and
 * `AccessibilityEvent.getEventTime()`).
 */
class HomeTapClassifier(
    /** Delay after the second touch before deciding, so late accessibility events can arrive. */
    private val settleMs: Long = DEFAULT_SETTLE_MS,
) {
    private var firstDownMs = 0L
    private var candidateDownMs = 0L
    private var pairFirstMs = 0L
    private var lastInteractionMs = 0L
    private var lastTriggerMs = 0L

    /**
     * A touch started. Returns the uptime at which [decide] should be called, or null when this
     * touch is (for now) only the first of a possible pair.
     */
    @Synchronized
    fun onTouchDown(timeMs: Long, timeoutMs: Int): Long? {
        val first = firstDownMs
        val gap = timeMs - first
        if (first != 0L && gap in MIN_GAP_MS until timeoutMs.toLong() && timeMs - lastTriggerMs > REARM_MS) {
            firstDownMs = 0L
            pairFirstMs = first
            candidateDownMs = timeMs
            return timeMs + settleMs
        }
        // Too slow, too fast (bounce) or a third touch: start a new pair.
        firstDownMs = timeMs
        candidateDownMs = 0L
        return null
    }

    /** The launcher reported a click, long click or scroll at [timeMs]. */
    @Synchronized
    fun onLauncherInteraction(timeMs: Long) {
        if (timeMs > lastInteractionMs) lastInteractionMs = timeMs
    }

    /**
     * Called at the time returned by [onTouchDown]. True when the pair was a double tap with no
     * launcher interaction from shortly before the first touch until now. Consumes the pair.
     */
    @Synchronized
    fun decide(): Boolean {
        val second = candidateDownMs
        candidateDownMs = 0L
        if (second == 0L) return false
        val ok = lastInteractionMs < pairFirstMs - INTERACTION_MARGIN_MS
        if (ok) lastTriggerMs = second
        return ok
    }

    @Synchronized
    fun reset() {
        firstDownMs = 0L
        candidateDownMs = 0L
    }

    companion object {
        const val DEFAULT_SETTLE_MS = 150L

        /** Touches closer than this are treated as a bounce or a multi-finger touch. */
        const val MIN_GAP_MS = 60L

        /** Events stamped slightly before the first touch still belong to the same interaction. */
        const val INTERACTION_MARGIN_MS = 50L

        /** After a recognised double tap, ignore further pairs for this long. */
        const val REARM_MS = 300L
    }
}
