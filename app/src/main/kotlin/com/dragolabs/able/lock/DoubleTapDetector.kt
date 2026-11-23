package com.dragolabs.able.lock

import android.os.SystemClock

/**
 * Counts taps and reports when two arrive within the configured timeout (LOCK-001).
 * Idea adapted from TapLock's DoubleTapDetector (Apache-2.0, github.com/modelorona/TapLock).
 * Uses the monotonic clock so wall-clock changes cannot fake or break a double tap.
 */
class DoubleTapDetector(private val now: () -> Long = SystemClock::uptimeMillis) {
    private var lastTapMs = 0L

    /** Returns true on the second tap of a double tap; the detector then resets. */
    @Synchronized
    fun onTap(timeoutMs: Int, tapTimeMs: Long = now()): Boolean {
        val delta = tapTimeMs - lastTapMs
        if (lastTapMs != 0L && delta in 1 until timeoutMs) {
            lastTapMs = 0L
            return true
        }
        lastTapMs = tapTimeMs
        return false
    }

    @Synchronized
    fun reset() {
        lastTapMs = 0L
    }
}
