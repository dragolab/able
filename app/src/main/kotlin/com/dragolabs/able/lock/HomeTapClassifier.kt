package com.dragolabs.able.lock

/**
 * Pure double-tap classifier for the home screen (LOCK-001), separated for unit tests.
 *
 * The service only learns *when* a touch starts on the launcher (an `ACTION_OUTSIDE` event;
 * Android does not give the position of touches on other apps' windows), never where. So "empty space" is
 * inferred from what the launcher reports through accessibility events instead: a tap on an icon
 * or widget produces `TYPE_VIEW_CLICKED`, a long press `TYPE_VIEW_LONG_CLICKED`, a page swipe
 * `TYPE_VIEW_SCROLLED`, opening the app drawer or an app a window change. If any such
 * interaction is reported between the first touch and the end of the settle window after the
 * second touch, the pair is not a double tap on empty space.
 *
 * The settle delay is 450 ms. On the API 36 emulator with Pixel Launcher (2026-11-22) a 300 ms
 * settle let one icon double tap lock the screen because the click event came late; with
 * 450 ms icon double taps were rejected and empty-space double taps still locked.
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

    /**
     * A touch started on the launcher. Returns the uptime at which [decide] should be called, or
     * null when this touch is (for now) only the first of a possible pair.
     */
    @Synchronized
    fun onTouchDown(timeMs: Long, timeoutMs: Int): Long? {
        val first = firstDownMs
        val gap = timeMs - first
        if (first != 0L && gap in MIN_GAP_MS until timeoutMs.toLong()) {
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

    /** The launcher reported a click, long click, scroll or window change at [timeMs]. */
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
        return lastInteractionMs < pairFirstMs - INTERACTION_MARGIN_MS
    }

    @Synchronized
    fun reset() {
        firstDownMs = 0L
        candidateDownMs = 0L
    }

    companion object {
        const val DEFAULT_SETTLE_MS = 450L

        /** Touches closer than this are treated as a bounce or a multi-finger touch. */
        const val MIN_GAP_MS = 40L

        /** Events stamped slightly before the first touch still belong to the same interaction. */
        const val INTERACTION_MARGIN_MS = 50L
    }
}
