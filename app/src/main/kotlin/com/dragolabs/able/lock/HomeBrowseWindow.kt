package com.dragolabs.able.lock

/**
 * "Browsing" pass-through window of the Precise home mode (LOCK-001), separated for unit tests.
 *
 * A swipe on the overlay can reach the launcher only as a replay after the finger lifts, which
 * makes page scrolling feel late (owner feedback, Pixel 8 Pro, 2026-11-23). Once the user has
 * swiped, the next touches are very likely more swipes, so the overlay stops intercepting for a
 * short while: those touches go to the launcher natively, with no delay. The window
 *
 * - opens when a swipe is recognised on the overlay ([onSwipe]);
 * - is extended by every touch that starts while it is open ([onTouchDown], learned from the
 *   1×1 `FLAG_WATCH_OUTSIDE_TOUCH` watcher: only the time of the down is known) and by the
 *   launcher reporting a page scroll ([onLauncherScroll]);
 * - closes after a quiet period with no such activity; the overlay then intercepts again, so a
 *   double tap made after a short pause is recognised with real coordinates.
 *
 * While the window is open a double tap reaches the launcher instead of Able;
 * [HomePreciseController] then falls back to the timing-only rule of [HomeTapClassifier].
 * All times are `SystemClock.uptimeMillis()`.
 */
class HomeBrowseWindow(
    /** Quiet time after the swipe that opened the window (measured from its finger up). */
    private val afterSwipeMs: Long = AFTER_SWIPE_MS,
    /** Quiet time after a touch down seen while open (the touch itself lasts ~0.1–0.3 s). */
    private val afterDownMs: Long = AFTER_DOWN_MS,
    /** Quiet time after the launcher reported a page scroll (sent when the page settles). */
    private val afterScrollMs: Long = AFTER_SCROLL_MS,
) {
    /** Uptime at which the window closes; 0 when closed. */
    var untilMs: Long = 0L
        private set

    fun isOpen(nowMs: Long): Boolean = nowMs < untilMs

    /** A swipe (movement past the touch slop) was recognised on the overlay at [nowMs]. */
    fun onSwipe(nowMs: Long) = extend(nowMs + afterSwipeMs)

    /**
     * A real touch started on the launcher at [timeMs] while the overlay was not intercepting.
     * Returns true when it extended an open window.
     */
    fun onTouchDown(timeMs: Long): Boolean {
        if (!isOpen(timeMs)) return false
        extend(timeMs + afterDownMs)
        return true
    }

    /** The launcher reported a scroll (page settled) at [timeMs]. */
    fun onLauncherScroll(timeMs: Long) {
        if (isOpen(timeMs)) extend(timeMs + afterScrollMs)
    }

    fun close() {
        untilMs = 0L
    }

    private fun extend(toMs: Long) {
        if (toMs > untilMs) untilMs = toMs
    }

    companion object {
        /**
         * Chosen on the API 36 emulator with Pixel Launcher (2026-11-23): long enough that page
         * swipes made one after the other (up to about 1.3 s apart, each page settles in about
         * 0.9 s) pass through natively, short enough that a double tap made after a ~1.5 s pause
         * is intercepted again; a double tap inside the window uses the timing-only fallback.
         */
        const val AFTER_SWIPE_MS = 1_200L
        const val AFTER_DOWN_MS = 1_500L
        const val AFTER_SCROLL_MS = 500L
    }
}

/**
 * How a recorded single-finger swipe is replayed (LOCK-001, Precise home mode), separated for
 * unit tests.
 *
 * `GestureDescription` moves along a stroke at constant speed, so replaying a whole swipe as one
 * stroke loses the finger's final velocity, which is what the launcher uses to decide whether a
 * page flings. The swipe is therefore split in two strokes of one continued pointer:
 *
 * - the **tail**: the last [TAIL_MS] of the real path with its original duration, so the stroke
 *   ends with (the average of) the real final velocity;
 * - the **head**: the rest of the path, moved at least as fast as the tail but never slower than
 *   the original and at most [MAX_HEAD_MS] long, so long, slow drags do not delay the replay.
 *
 * A swipe shorter than the tail plus [MIN_STROKE_MS] is replayed as a single stroke with its
 * original duration.
 */
data class SwipeReplayPlan(
    /** Path of the first stroke, or null when the swipe is replayed as a single stroke. */
    val head: List<TouchSample>?,
    val headMs: Long,
    val tail: List<TouchSample>,
    val tailMs: Long,
) {
    /** Total replay time. */
    val durationMs: Long get() = (if (head != null) headMs else 0L) + tailMs

    companion object {
        const val TAIL_MS = 80L
        const val MAX_HEAD_MS = 120L
        const val MIN_STROKE_MS = 16L

        fun of(samples: List<TouchSample>, tailMs: Long = TAIL_MS, maxHeadMs: Long = MAX_HEAD_MS): SwipeReplayPlan {
            require(samples.isNotEmpty())
            val first = samples.first()
            val last = samples.last()
            val total = last.timeMs - first.timeMs
            if (total < tailMs + MIN_STROKE_MS || samples.size < 2) {
                return SwipeReplayPlan(null, 0L, samples, total.coerceAtLeast(MIN_STROKE_MS))
            }
            val splitMs = last.timeMs - tailMs
            val head = ArrayList<TouchSample>()
            val tail = ArrayList<TouchSample>()
            for (i in samples.indices) {
                val s = samples[i]
                if (s.timeMs < splitMs) {
                    head += s
                    continue
                }
                if (tail.isEmpty()) {
                    // Interpolate the split point so both strokes meet exactly.
                    val prev = samples[i - 1]
                    val f = if (s.timeMs == prev.timeMs) 1f else (splitMs - prev.timeMs).toFloat() / (s.timeMs - prev.timeMs)
                    val split = TouchSample(prev.x + (s.x - prev.x) * f, prev.y + (s.y - prev.y) * f, splitMs)
                    head += split
                    tail += split
                }
                if (s != tail.last()) tail += s
            }
            val tailLength = length(tail)
            val headLength = length(head)
            val originalHeadMs = splitMs - first.timeMs
            val headLimit = minOf(originalHeadMs, maxHeadMs).coerceAtLeast(MIN_STROKE_MS)
            val headMs = if (tailLength > 0.5f) {
                // At the tail's speed, but not slower than the original and not longer than the cap.
                (headLength / (tailLength / tailMs)).toLong().coerceIn(MIN_STROKE_MS, headLimit)
            } else {
                headLimit
            }
            return SwipeReplayPlan(head, headMs, tail, tailMs)
        }

        private fun length(path: List<TouchSample>): Float {
            var sum = 0f
            for (i in 1 until path.size) sum += kotlin.math.hypot(path[i].x - path[i - 1].x, path[i].y - path[i - 1].y)
            return sum
        }
    }
}
