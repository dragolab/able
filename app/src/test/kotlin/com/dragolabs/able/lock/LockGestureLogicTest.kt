package com.dragolabs.able.lock

import com.dragolabs.able.lock.SystemUiLockState.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockGestureLogicTest {
    @Test
    fun doubleTapWithinTimeoutTriggersOnce() {
        val detector = DoubleTapDetector { 0L }
        assertFalse(detector.onTap(300, tapTimeMs = 1_000))
        assertTrue(detector.onTap(300, tapTimeMs = 1_200))
        // The detector resets after a hit: a third tap starts a new pair.
        assertFalse(detector.onTap(300, tapTimeMs = 1_300))
    }

    @Test
    fun slowTapsDoNotTrigger() {
        val detector = DoubleTapDetector { 0L }
        assertFalse(detector.onTap(300, tapTimeMs = 1_000))
        assertFalse(detector.onTap(300, tapTimeMs = 1_400))
        assertTrue(detector.onTap(300, tapTimeMs = 1_600))
    }

    @Test
    fun lockScreenRecognisedOnlyWithKeyguardIds() {
        val ids = setOf("com.android.systemui:id/keyguard_status_view")
        assertEquals(Scene.LOCK_SCREEN, SystemUiLockState.classify(true, ids, emptyList()))
        assertEquals(Scene.UNKNOWN, SystemUiLockState.classify(true, emptySet(), emptyList()))
    }

    @Test
    fun bouncerOrShadeAlwaysWins() {
        val ids = setOf("com.android.systemui:id/keyguard_status_view", "com.android.systemui:id/pinEntry")
        assertEquals(Scene.PANEL_OR_BOUNCER, SystemUiLockState.classify(true, ids, emptyList()))
        assertEquals(
            Scene.PANEL_OR_BOUNCER,
            SystemUiLockState.classify(false, setOf("com.android.systemui:id/qs_panel"), emptyList()),
        )
        assertEquals(Scene.PANEL_OR_BOUNCER, SystemUiLockState.classify(false, emptySet(), listOf("NotificationShade")))
        assertEquals(Scene.NONE, SystemUiLockState.classify(false, emptySet(), listOf("StatusBar")))
    }
}
