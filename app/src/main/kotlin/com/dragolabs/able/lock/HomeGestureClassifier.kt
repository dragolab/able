package com.dragolabs.able.lock

import kotlin.math.hypot

/** One recorded touch sample; [timeMs] is `MotionEvent.getEventTime()` (uptime). */
data class TouchSample(val x: Float, val y: Float, val timeMs: Long)

/** The path of one finger. */
data class TouchStroke(val samples: List<TouchSample>) {
    val startMs: Long get() = samples.first().timeMs
    val endMs: Long get() = samples.last().timeMs
}

/** A complete user gesture that the launcher must still receive. */
data class TouchGesture(
    val kind: Kind,
    val strokes: List<TouchStroke>,
    /** For [Kind.TAP] only: what the tap hit. */
    val target: HomeTarget = HomeTarget.UNKNOWN,
) {
    enum class Kind {
        TAP,
        LONG_PRESS,

        /** Moved before the long-press time: page scroll, app drawer, notification pull-down. */
        SWIPE,

        /** Held past the long-press time, then moved: drag and drop of icons and widgets. */
        LONG_PRESS_DRAG,
    }

    val downX: Float get() = strokes.first().samples.first().x
    val downY: Float get() = strokes.first().samples.first().y
    val downMs: Long get() = strokes.first().startMs
    val upMs: Long get() = strokes.maxOf { it.endMs }
}

/** What the Precise home overlay must do after a touch event. */
sealed interface HomeDecision {
    /** Give the gesture back to the launcher (replay it, or click the node it hit). */
    data class Replay(val gesture: TouchGesture) : HomeDecision

    /** Double tap on empty home-screen space: lock. Both taps are consumed. */
    data object Lock : HomeDecision
}

/**
 * Pure gesture classifier for the Precise home mode (LOCK-001), separated for unit tests.
 *
 * It follows the `GestureDetector` model: a tap is a touch that stays within [touchSlopPx] and
 * ends before [longPressMs]; a double tap is a second tap whose down comes at most `timeoutMs`
 * after the first tap's up and within [doubleTapSlopPx] of the first down. Both taps must hit
 * [HomeTarget.EMPTY]. Everything else is handed back to the launcher as soon as it is complete:
 *
 * - taps on controls immediately (no double-tap wait, so icons open without delay);
 * - taps on empty space when no second tap can follow any more (deadline from [deadlineMs]);
 * - swipes, long presses and drags on their last finger up.
 *
 * Gestures are handed back on up, not earlier, because Android cancels an injected gesture as
 * soon as a real touch event arrives (`MotionEventInjector.onMotionEvent` calls
 * `cancelAnyPendingInjectedEvents`, AOSP main, checked 2026-11-22): a replay started while the
 * finger is still down would be cut off by the next move event.
 */
class HomeGestureClassifier(
    private val touchSlopPx: Float,
    private val doubleTapSlopPx: Float,
    private val longPressMs: Long,
) {
    private class Pointer(val samples: MutableList<TouchSample>, var moved: Boolean = false)

    private val pointers = LinkedHashMap<Int, Pointer>()
    private val finished = ArrayList<TouchStroke>()
    private var gestureMoved = false
    private var gestureMovedAtMs = 0L
    private var multiTouch = false
    private var gestureDownMs = 0L

    /** First tap of a possible double tap, waiting for a second one. */
    private var pendingTap: TouchGesture? = null
    private var pendingDeadlineMs = 0L

    /** The current gesture started while [pendingTap] could still pair with it. */
    private var pairing = false

    /** True while at least one finger is down. */
    val inGesture: Boolean get() = pointers.isNotEmpty()

    /**
     * True once the current gesture can no longer be a tap (a finger moved past the touch slop,
     * or a second finger went down): it will be handed back as a replay when it ends, so the
     * overlay may already stop intercepting new touches.
     */
    val willReplay: Boolean get() = inGesture && (gestureMoved || multiTouch)

    /** Down time of the first tap waiting for a second one, or null. */
    val pendingTapDownMs: Long? get() = pendingTap?.downMs

    /**
     * Drops the tap waiting for a second one without handing it back (it completed a double
     * tap with a touch the overlay did not see). Returns false when there was none.
     */
    fun consumePendingTap(): Boolean {
        if (pendingTap == null || inGesture) return false
        pendingTap = null
        return true
    }

    /** Uptime at which [onTimeout] should be called, or null when nothing is waiting. */
    val deadlineMs: Long? get() = if (pendingTap != null && !inGesture) pendingDeadlineMs else null

    fun onDown(pointerId: Int, x: Float, y: Float, timeMs: Long, timeoutMs: Int): List<HomeDecision> {
        val out = ArrayList<HomeDecision>(1)
        if (pointers.isEmpty()) {
            finished.clear()
            gestureMoved = false
            multiTouch = false
            gestureDownMs = timeMs
            val pending = pendingTap
            pairing = false
            if (pending != null) {
                val inTime = timeMs - pending.upMs <= timeoutMs
                val near = hypot(x - pending.downX, y - pending.downY) <= doubleTapSlopPx
                if (inTime && near) {
                    pairing = true
                } else {
                    out += HomeDecision.Replay(pending)
                    pendingTap = null
                }
            }
        } else {
            multiTouch = true
        }
        pointers[pointerId] = Pointer(mutableListOf(TouchSample(x, y, timeMs)))
        return out
    }

    fun onMove(pointerId: Int, x: Float, y: Float, timeMs: Long) {
        val p = pointers[pointerId] ?: return
        record(p, x, y, timeMs, force = false)
    }

    /**
     * A finger went up. [targetAt] is asked only for a completed single-finger tap, so the tree
     * lookup costs nothing for swipes.
     */
    fun onUp(pointerId: Int, x: Float, y: Float, timeMs: Long, timeoutMs: Int, targetAt: (Float, Float) -> HomeTarget): List<HomeDecision> {
        val p = pointers.remove(pointerId) ?: return emptyList()
        record(p, x, y, timeMs, force = true)
        finished += TouchStroke(p.samples.toList())
        if (pointers.isNotEmpty()) return emptyList()

        val strokes = finished.toList()
        finished.clear()
        val duration = timeMs - gestureDownMs
        val kind = when {
            multiTouch -> TouchGesture.Kind.SWIPE
            gestureMoved && gestureMovedAtMs - gestureDownMs >= longPressMs -> TouchGesture.Kind.LONG_PRESS_DRAG
            gestureMoved -> TouchGesture.Kind.SWIPE
            duration >= longPressMs -> TouchGesture.Kind.LONG_PRESS
            else -> TouchGesture.Kind.TAP
        }
        val out = ArrayList<HomeDecision>(2)
        val pending = pendingTap
        if (kind != TouchGesture.Kind.TAP) {
            if (pending != null) out += HomeDecision.Replay(pending)
            pendingTap = null
            out += HomeDecision.Replay(TouchGesture(kind, strokes))
            return out
        }
        val first = strokes.first().samples.first()
        val target = targetAt(first.x, first.y)
        val tap = TouchGesture(kind, strokes, target)
        if (target != HomeTarget.EMPTY) {
            if (pending != null) out += HomeDecision.Replay(pending)
            pendingTap = null
            out += HomeDecision.Replay(tap)
            return out
        }
        if (pending != null && pairing) {
            pendingTap = null
            out += HomeDecision.Lock
            return out
        }
        if (pending != null) out += HomeDecision.Replay(pending)
        pendingTap = tap
        pendingDeadlineMs = timeMs + timeoutMs
        return out
    }

    private fun record(p: Pointer, x: Float, y: Float, timeMs: Long, force: Boolean) {
        val first = p.samples.first()
        val last = p.samples.last()
        // Keep the path compact: skip samples that add almost no time or distance.
        val worth = timeMs - last.timeMs >= MIN_SAMPLE_MS || hypot(x - last.x, y - last.y) >= MIN_SAMPLE_PX
        val changed = last.x != x || last.y != y || last.timeMs != timeMs
        if (changed && (worth || force)) p.samples += TouchSample(x, y, timeMs)
        if (!p.moved && hypot(x - first.x, y - first.y) > touchSlopPx) {
            p.moved = true
            if (!gestureMoved) {
                gestureMoved = true
                gestureMovedAtMs = timeMs
            }
        }
    }

    /** The double-tap window of the pending tap may have closed: hand the tap back if so. */
    fun onTimeout(nowMs: Long): List<HomeDecision> {
        val pending = pendingTap ?: return emptyList()
        if (inGesture || nowMs < pendingDeadlineMs) return emptyList()
        pendingTap = null
        return listOf(HomeDecision.Replay(pending))
    }

    /**
     * The touch stream was cancelled by Android (or the overlay went away). The partial gesture
     * is dropped; a pending first tap is returned so the caller can still hand it back.
     */
    fun onCancel(): List<HomeDecision> {
        pointers.clear()
        finished.clear()
        val pending = pendingTap ?: return emptyList()
        pendingTap = null
        return listOf(HomeDecision.Replay(pending))
    }

    private companion object {
        const val MIN_SAMPLE_MS = 8L
        const val MIN_SAMPLE_PX = 6f
    }
}
