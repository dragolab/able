package com.dragolabs.able.lock

import com.dragolabs.able.lock.HomeTapClassifier.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTapClassifierTest {
    private val timeout = 300

    @Test
    fun twoQuickTouches_areADoubleTapAtTheSecondTouch() {
        val c = HomeTapClassifier()
        assertEquals(Result.None, c.onTouchDown(1_000, timeout))
        assertEquals(Result.DoubleTap(200), c.onTouchDown(1_200, timeout))
    }

    @Test
    fun firstTapDuration_isAllowedOnTopOfTheTimeout() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        assertEquals(Result.DoubleTap(420), c.onTouchDown(1_420, timeout))
    }

    @Test
    fun slowTouches_areReportedTooSlow() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        assertEquals(Result.TooSlow(500), c.onTouchDown(1_500, timeout))
    }

    @Test
    fun verySlowTouches_areNothing() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        assertEquals(Result.None, c.onTouchDown(3_000, timeout))
    }

    @Test
    fun bounce_isIgnored() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        assertEquals(Result.None, c.onTouchDown(1_050, timeout))
    }

    @Test
    fun thirdQuickTouch_doesNotTriggerAgain() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        assertEquals(Result.DoubleTap(150), c.onTouchDown(1_150, timeout))
        assertEquals(Result.None, c.onTouchDown(1_300, timeout))
    }

    @Test
    fun cancelledTrigger_allowsAnImmediateRetry() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        c.onTouchDown(1_150, timeout)
        c.cancelTrigger()
        c.onTouchDown(1_300, timeout)
        assertEquals(Result.DoubleTap(150), c.onTouchDown(1_450, timeout))
    }

    @Test
    fun reset_forgetsFirstTouch() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        c.reset()
        assertEquals(Result.None, c.onTouchDown(1_200, timeout))
    }
}
