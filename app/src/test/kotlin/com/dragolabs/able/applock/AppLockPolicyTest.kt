package com.dragolabs.able.applock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    private fun policy(graceMs: Long = 0L) = AppLockPolicy().apply {
        lockedPackages = setOf(BANK)
        this.graceMs = graceMs
    }

    @Test
    fun lockedApp_prompts_unlockedAppDoesNot() {
        val p = policy()
        assertEquals(AppLockDecision.NONE, p.onForeground(OTHER, true, 0))
        assertEquals(AppLockDecision.PROMPT, p.onForeground(BANK, true, 10))
    }

    @Test
    fun pendingPrompt_isNotStacked() {
        val p = policy()
        p.onForeground(BANK, true, 0)
        assertEquals(AppLockDecision.NONE, p.onForeground(BANK, true, 100))
        // A prompt that never reported back stops blocking after the timeout.
        assertEquals(AppLockDecision.PROMPT, p.onForeground(BANK, true, AppLockPolicy.PENDING_TIMEOUT_MS + 1))
    }

    @Test
    fun afterUnlock_staysUnlockedWhileInForeground() {
        val p = policy()
        p.onForeground(BANK, true, 0)
        p.onAuthenticated(BANK)
        assertEquals(AppLockDecision.NONE, p.onForeground(BANK, true, 1_000))
        // A helper dialog on top does not count as leaving.
        assertEquals(AppLockDecision.NONE, p.onForeground(PERMISSIONS, false, 2_000))
        assertEquals(AppLockDecision.NONE, p.onForeground(BANK, true, 3_000))
    }

    @Test
    fun immediateGrace_locksAgainAfterLeaving() {
        val p = policy()
        p.onForeground(BANK, true, 0)
        p.onAuthenticated(BANK)
        p.onForeground(LAUNCHER, true, 1_000)
        assertEquals(AppLockDecision.PROMPT, p.onForeground(BANK, true, 1_001))
    }

    @Test
    fun graceWindow_keepsAppUnlockedThenExpires() {
        val p = policy(graceMs = 60_000)
        p.onForeground(BANK, true, 0)
        p.onAuthenticated(BANK)
        p.onForeground(OTHER, true, 1_000)
        assertEquals(AppLockDecision.NONE, p.onForeground(BANK, true, 30_000))
        p.onForeground(LAUNCHER, true, 40_000)
        assertEquals(AppLockDecision.PROMPT, p.onForeground(BANK, true, 100_001))
    }

    @Test
    fun screenOff_endsEveryUnlock() {
        val p = policy(graceMs = 15 * 60_000)
        p.onForeground(BANK, true, 0)
        p.onAuthenticated(BANK)
        p.onScreenOff()
        assertTrue(p.needsPrompt(BANK, 1_000))
        assertEquals(BANK, p.current)
    }

    @Test
    fun reappearingRightAfterCancel_goesHomeInsteadOfLooping() {
        val p = policy()
        p.onForeground(BANK, true, 0)
        p.onPromptCancelled(BANK, 500)
        assertEquals(AppLockDecision.GO_HOME, p.onForeground(BANK, true, 800))
        assertEquals(AppLockDecision.PROMPT, p.onForeground(BANK, true, 500 + AppLockPolicy.CANCEL_GUARD_MS + 1))
    }

    @Test
    fun failedPrompt_allowsRetry() {
        val p = policy()
        p.onForeground(BANK, true, 0)
        p.onPromptFailed(BANK)
        assertEquals(AppLockDecision.PROMPT, p.onForeground(BANK, true, 10))
    }

    @Test
    fun clear_forgetsCurrentApp() {
        val p = policy()
        p.onForeground(BANK, true, 0)
        p.clear()
        assertFalse(p.current == BANK)
    }

    private companion object {
        const val BANK = "com.example.bank"
        const val OTHER = "com.example.notes"
        const val LAUNCHER = "com.example.launcher"
        const val PERMISSIONS = "com.google.android.permissioncontroller"
    }
}
