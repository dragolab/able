package com.dragolabs.able.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTapClassifierTest {
    private val timeout = 300

    @Test
    fun twoQuickTouchesWithoutLauncherEvents_isDoubleTap() {
        val c = HomeTapClassifier(settleMs = 300)
        assertNull(c.onTouchDown(1_000, timeout))
        assertEquals(1_500L, c.onTouchDown(1_200, timeout))
        assertTrue(c.decide())
    }

    @Test
    fun slowTouches_areNotADoubleTap() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        assertNull(c.onTouchDown(1_400, timeout))
        assertFalse(c.decide())
    }

    @Test
    fun bounce_isIgnored() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        assertNull(c.onTouchDown(1_010, timeout))
    }

    @Test
    fun iconClickDuringPair_cancels() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        c.onLauncherInteraction(1_080)
        c.onTouchDown(1_200, timeout)
        assertFalse(c.decide())
    }

    @Test
    fun lateScrollEventAfterSecondTouch_cancels() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        c.onTouchDown(1_200, timeout)
        c.onLauncherInteraction(1_350)
        assertFalse(c.decide())
    }

    @Test
    fun oldInteraction_doesNotCancel() {
        val c = HomeTapClassifier()
        c.onLauncherInteraction(500)
        c.onTouchDown(1_000, timeout)
        c.onTouchDown(1_200, timeout)
        assertTrue(c.decide())
    }

    @Test
    fun thirdTouchBeforeDecision_cancels() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        c.onTouchDown(1_200, timeout)
        c.onTouchDown(1_350, timeout)
        assertFalse(c.decide())
    }

    @Test
    fun decisionConsumesThePair() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        c.onTouchDown(1_200, timeout)
        assertTrue(c.decide())
        assertFalse(c.decide())
    }

    @Test
    fun reset_forgetsFirstTouch() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        c.reset()
        assertNull(c.onTouchDown(1_200, timeout))
    }

    @Test
    fun afterADoubleTap_aQuickThirdPairIsIgnored() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        c.onTouchDown(1_200, timeout)
        assertTrue(c.decide())
        assertNull(c.onTouchDown(1_300, timeout))
        assertNull(c.onTouchDown(1_400, timeout))
    }

    @Test
    fun settleDefault_isShortAfterSecondTouch() {
        val c = HomeTapClassifier()
        c.onTouchDown(1_000, timeout)
        assertEquals(1_200L + HomeTapClassifier.DEFAULT_SETTLE_MS, c.onTouchDown(1_200, timeout))
    }
}
