package com.dragolabs.able.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGestureClassifierTest {
    private val timeout = 300
    private val empty: (Float, Float) -> HomeTarget = { _, _ -> HomeTarget.EMPTY }
    private val control: (Float, Float) -> HomeTarget = { _, _ -> HomeTarget.CONTROL }

    private fun classifier() = HomeGestureClassifier(touchSlopPx = 20f, doubleTapSlopPx = 260f, longPressMs = 400)

    private fun HomeGestureClassifier.tap(x: Float, y: Float, downMs: Long, target: (Float, Float) -> HomeTarget = empty, upAfter: Long = 60): List<HomeDecision> =
        onDown(0, x, y, downMs, timeout) + onUp(0, x, y, downMs + upAfter, timeout, target)

    private fun replayKinds(decisions: List<HomeDecision>) =
        decisions.filterIsInstance<HomeDecision.Replay>().map { it.gesture.kind }

    @Test
    fun doubleTapOnEmptySpace_locksWithoutReplayingTheTaps() {
        val c = classifier()
        assertEquals(emptyList<HomeDecision>(), c.tap(500f, 900f, 1_000))
        assertEquals(1_360L, c.deadlineMs)
        assertEquals(listOf(HomeDecision.Lock), c.tap(510f, 905f, 1_200))
        assertNull(c.deadlineMs)
    }

    @Test
    fun singleTapOnEmptySpace_isReplayedAfterTheTimeout() {
        val c = classifier()
        c.tap(500f, 900f, 1_000)
        assertEquals(emptyList<HomeDecision>(), c.onTimeout(1_200))
        val out = c.onTimeout(1_360)
        assertEquals(listOf(TouchGesture.Kind.TAP), replayKinds(out))
        assertEquals(HomeTarget.EMPTY, (out.single() as HomeDecision.Replay).gesture.target)
    }

    @Test
    fun tapOnIcon_isReplayedImmediately() {
        val c = classifier()
        val out = c.tap(150f, 1_650f, 1_000, control)
        val replay = out.single() as HomeDecision.Replay
        assertEquals(TouchGesture.Kind.TAP, replay.gesture.kind)
        assertEquals(HomeTarget.CONTROL, replay.gesture.target)
        assertNull(c.deadlineMs)
    }

    @Test
    fun doubleTapOnIcon_neverLocks() {
        val c = classifier()
        c.tap(150f, 1_650f, 1_000, control)
        val out = c.tap(150f, 1_650f, 1_150, control)
        assertTrue(out.none { it == HomeDecision.Lock })
    }

    @Test
    fun emptyThenIcon_replaysBothAndDoesNotLock() {
        val c = classifier()
        c.tap(500f, 900f, 1_000)
        val out = c.tap(520f, 1_000f, 1_150, control)
        assertEquals(listOf(TouchGesture.Kind.TAP, TouchGesture.Kind.TAP), replayKinds(out))
        assertTrue(out.none { it == HomeDecision.Lock })
    }

    @Test
    fun slowSecondTap_startsANewPair() {
        val c = classifier()
        c.tap(500f, 900f, 1_000)
        // Second down 400 ms after the first up (> 300 ms timeout): first tap is handed back.
        val down = c.onDown(0, 500f, 900f, 1_460, timeout)
        assertEquals(listOf(TouchGesture.Kind.TAP), replayKinds(down))
        val up = c.onUp(0, 500f, 900f, 1_520, timeout, empty)
        assertEquals(emptyList<HomeDecision>(), up)
        // ...and a quick third tap completes the new pair.
        assertEquals(listOf(HomeDecision.Lock), c.tap(500f, 900f, 1_700))
    }

    @Test
    fun farSecondTap_doesNotLock() {
        val c = classifier()
        c.tap(100f, 500f, 1_000)
        val out = c.tap(900f, 1_500f, 1_150)
        assertTrue(out.none { it == HomeDecision.Lock })
        assertEquals(listOf(TouchGesture.Kind.TAP), replayKinds(out))
    }

    @Test
    fun swipe_isReplayedWithItsPath() {
        val c = classifier()
        c.onDown(0, 900f, 1_000f, 1_000, timeout)
        c.onMove(0, 700f, 1_000f, 1_050)
        c.onMove(0, 400f, 1_000f, 1_100)
        val out = c.onUp(0, 200f, 1_000f, 1_150, timeout) { _, _ -> error("no hit test for swipes") }
        val gesture = (out.single() as HomeDecision.Replay).gesture
        assertEquals(TouchGesture.Kind.SWIPE, gesture.kind)
        val samples = gesture.strokes.single().samples
        assertEquals(900f, samples.first().x)
        assertEquals(200f, samples.last().x)
        assertEquals(150L, samples.last().timeMs - samples.first().timeMs)
    }

    @Test
    fun smallJitter_isStillATap() {
        val c = classifier()
        c.onDown(0, 500f, 900f, 1_000, timeout)
        c.onMove(0, 508f, 905f, 1_030)
        c.onUp(0, 505f, 903f, 1_060, timeout, empty)
        assertEquals(listOf(HomeDecision.Lock), c.tap(505f, 903f, 1_200))
    }

    @Test
    fun longPress_isReplayedAsLongPress() {
        val c = classifier()
        c.onDown(0, 500f, 900f, 1_000, timeout)
        val out = c.onUp(0, 500f, 900f, 1_600, timeout, empty)
        assertEquals(listOf(TouchGesture.Kind.LONG_PRESS), replayKinds(out))
    }

    @Test
    fun holdThenMove_isADrag() {
        val c = classifier()
        c.onDown(0, 150f, 1_650f, 1_000, timeout)
        c.onMove(0, 152f, 1_651f, 1_300)
        c.onMove(0, 400f, 1_200f, 1_700)
        val out = c.onUp(0, 600f, 1_000f, 1_900, timeout, empty)
        assertEquals(listOf(TouchGesture.Kind.LONG_PRESS_DRAG), replayKinds(out))
    }

    @Test
    fun secondTouchBecomingASwipe_replaysFirstTapThenSwipe() {
        val c = classifier()
        c.tap(500f, 900f, 1_000)
        assertEquals(emptyList<HomeDecision>(), c.onDown(0, 500f, 900f, 1_150, timeout))
        c.onMove(0, 500f, 600f, 1_200)
        val out = c.onUp(0, 500f, 300f, 1_250, timeout, empty)
        assertEquals(listOf(TouchGesture.Kind.TAP, TouchGesture.Kind.SWIPE), replayKinds(out))
        assertTrue(out.none { it == HomeDecision.Lock })
    }

    @Test
    fun twoFingers_areReplayedAsOneMultiStrokeGesture() {
        val c = classifier()
        c.onDown(0, 300f, 900f, 1_000, timeout)
        c.onDown(1, 700f, 900f, 1_010, timeout)
        assertEquals(emptyList<HomeDecision>(), c.onUp(1, 700f, 900f, 1_080, timeout, empty))
        val out = c.onUp(0, 300f, 900f, 1_090, timeout, empty)
        val gesture = (out.single() as HomeDecision.Replay).gesture
        assertEquals(TouchGesture.Kind.SWIPE, gesture.kind)
        assertEquals(2, gesture.strokes.size)
    }

    @Test
    fun unknownTarget_isNeverTreatedAsEmpty() {
        val c = classifier()
        c.tap(500f, 900f, 1_000, target = { _, _ -> HomeTarget.UNKNOWN })
        val out = c.tap(500f, 900f, 1_150, target = { _, _ -> HomeTarget.UNKNOWN })
        assertTrue(out.none { it == HomeDecision.Lock })
    }

    @Test
    fun cancel_returnsPendingTapAndResets() {
        val c = classifier()
        c.tap(500f, 900f, 1_000)
        c.onDown(0, 500f, 900f, 1_150, timeout)
        assertEquals(listOf(TouchGesture.Kind.TAP), replayKinds(c.onCancel()))
        assertEquals(emptyList<HomeDecision>(), c.tap(500f, 900f, 2_000))
    }

    @Test
    fun consumePendingTap_dropsTheFirstTapWithoutReplay() {
        val c = classifier()
        assertNull(c.pendingTapDownMs)
        c.tap(500f, 900f, 1_000)
        assertEquals(1_000L, c.pendingTapDownMs)
        assertTrue(c.consumePendingTap())
        assertNull(c.pendingTapDownMs)
        assertNull(c.deadlineMs)
        assertEquals(emptyList<HomeDecision>(), c.onTimeout(2_000))
        assertFalse(c.consumePendingTap())
    }

    @Test
    fun willReplay_turnsOnWhenTheTouchMovesPastTheSlop() {
        val c = classifier()
        c.onDown(0, 500f, 900f, 1_000, timeout)
        c.onMove(0, 510f, 900f, 1_010)
        assertFalse(c.willReplay)
        c.onMove(0, 540f, 900f, 1_020)
        assertTrue(c.willReplay)
        c.onUp(0, 600f, 900f, 1_060, timeout, empty)
        assertFalse(c.willReplay)
    }

    @Test
    fun willReplay_turnsOnForASecondFinger() {
        val c = classifier()
        c.onDown(0, 500f, 900f, 1_000, timeout)
        c.onDown(1, 700f, 900f, 1_010, timeout)
        assertTrue(c.willReplay)
    }
}
