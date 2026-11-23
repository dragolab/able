package com.dragolabs.able.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class HomeBrowseWindowTest {
    private fun window() = HomeBrowseWindow(afterSwipeMs = 1_000, afterDownMs = 1_200, afterScrollMs = 500)

    @Test
    fun closedUntilASwipe() {
        val w = window()
        assertFalse(w.isOpen(1_000))
        // A touch seen while closed is not browsing: it does not open the window.
        assertFalse(w.onTouchDown(1_000))
        assertFalse(w.isOpen(1_001))
    }

    @Test
    fun swipeOpensForTheQuietPeriod() {
        val w = window()
        w.onSwipe(5_000)
        assertTrue(w.isOpen(5_999))
        assertFalse(w.isOpen(6_000))
    }

    @Test
    fun consecutiveSwipesKeepItOpen() {
        val w = window()
        w.onSwipe(5_000)
        // Next swipes start every ~700 ms; each down extends the window.
        assertTrue(w.onTouchDown(5_700))
        assertTrue(w.onTouchDown(6_400))
        assertTrue(w.onTouchDown(7_100))
        assertTrue(w.isOpen(8_299))
        assertFalse(w.isOpen(8_300))
    }

    @Test
    fun launcherScrollExtendsOnlyWhileOpen() {
        val w = window()
        w.onLauncherScroll(1_000)
        assertFalse(w.isOpen(1_001))
        w.onSwipe(2_000)
        w.onLauncherScroll(2_900) // page settled late: keep open a little longer
        assertTrue(w.isOpen(3_399))
        assertFalse(w.isOpen(3_400))
    }

    @Test
    fun extensionsNeverShortenTheWindow() {
        val w = window()
        w.onSwipe(2_000) // until 3_000
        w.onLauncherScroll(2_100) // 2_600 < 3_000
        assertEquals(3_000L, w.untilMs)
    }

    @Test
    fun touchAfterQuietPeriodIsIntercepted() {
        val w = window()
        w.onSwipe(2_000)
        assertFalse(w.onTouchDown(3_200))
        assertFalse(w.isOpen(3_200))
    }

    @Test
    fun closeEndsImmediately() {
        val w = window()
        w.onSwipe(2_000)
        w.close()
        assertFalse(w.isOpen(2_001))
        assertEquals(0L, w.untilMs)
    }
}

class SwipeReplayPlanTest {
    /** A straight swipe sampled every 10 ms at [speed] px/ms from x = 900 leftwards. */
    private fun swipe(durationMs: Long, speed: Float, startMs: Long = 1_000): List<TouchSample> =
        (0..durationMs step 10).map { TouchSample(900f - speed * it, 1_000f, startMs + it) }

    private fun length(path: List<TouchSample>) =
        path.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()

    @Test
    fun shortSwipeIsASingleStrokeWithItsOwnDuration() {
        val plan = SwipeReplayPlan.of(swipe(60, 5f))
        assertNull(plan.head)
        assertEquals(60L, plan.tailMs)
        assertEquals(60L, plan.durationMs)
    }

    @Test
    fun tailKeepsTheRealTimingAndVelocity() {
        val samples = swipe(150, 3f)
        val plan = SwipeReplayPlan.of(samples)
        assertEquals(SwipeReplayPlan.TAIL_MS, plan.tailMs)
        // The tail covers the last 80 ms of the path: 3 px/ms × 80 ms.
        assertEquals(240f, length(plan.tail), 0.5f)
        assertEquals(samples.last(), plan.tail.last())
        // Head and tail meet exactly.
        assertEquals(plan.head!!.last(), plan.tail.first())
        // A constant-speed swipe keeps its speed in the head too (70 ms at 3 px/ms).
        assertEquals(70L, plan.headMs)
    }

    @Test
    fun longSlowDragHeadIsCompressedButTheTailIsNot() {
        // 1.5 s drag at 0.4 px/ms: the head is capped, the end velocity is kept.
        val plan = SwipeReplayPlan.of(swipe(1_500, 0.4f))
        assertEquals(SwipeReplayPlan.MAX_HEAD_MS, plan.headMs)
        assertEquals(SwipeReplayPlan.TAIL_MS, plan.tailMs)
        assertEquals(32f, length(plan.tail), 0.5f)
        assertTrue(plan.durationMs <= SwipeReplayPlan.MAX_HEAD_MS + SwipeReplayPlan.TAIL_MS)
    }

    @Test
    fun accelerationIsKeptAtTheEnd() {
        // Slow start, fast flick at the end: the head moves at the tail's (fast) speed.
        val slow = (0L..200L step 10).map { TouchSample(900f - 0.5f * it, 1_000f, 1_000 + it) }
        val fast = (10L..100L step 10).map { TouchSample(800f - 5f * it, 1_000f, 1_200 + it) }
        val plan = SwipeReplayPlan.of(slow + fast)
        assertEquals(400f, length(plan.tail), 0.5f) // 5 px/ms × 80 ms
        // Head length 100 + 5×20 = 200 px at 5 px/ms → 40 ms.
        assertEquals(40L, plan.headMs)
    }

    @Test
    fun fingerStoppedBeforeLiftingEndsWithNoVelocity() {
        val moving = swipe(300, 2f)
        val held = (10L..150L step 10).map { TouchSample(moving.last().x, 1_000f, moving.last().timeMs + it) }
        val plan = SwipeReplayPlan.of(moving + held)
        // The tail is a still hold, so the launcher sees no fling.
        assertEquals(0f, length(plan.tail), 0.01f)
        assertEquals(SwipeReplayPlan.TAIL_MS, plan.tailMs)
        assertEquals(SwipeReplayPlan.MAX_HEAD_MS, plan.headMs)
    }

    @Test
    fun splitPointIsInterpolatedBetweenSparseSamples() {
        val samples = listOf(
            TouchSample(900f, 1_000f, 1_000),
            TouchSample(700f, 1_000f, 1_100),
            TouchSample(300f, 1_000f, 1_200),
        )
        val plan = SwipeReplayPlan.of(samples)
        // Split at t = 1_120: 20 % of the way from 700 to 300.
        val split = plan.tail.first()
        assertEquals(620f, split.x, 0.01f)
        assertEquals(1_000f, split.y, 0.01f)
        assertEquals(1_120L, split.timeMs)
        assertEquals(3, plan.head!!.size)
    }
}
