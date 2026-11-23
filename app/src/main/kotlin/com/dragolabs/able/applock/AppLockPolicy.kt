package com.dragolabs.able.applock

/** What the service should do after a foreground change (APPLOCK-001). */
enum class AppLockDecision { NONE, PROMPT, GO_HOME }

/**
 * Pure app-lock session rules, separated for unit tests. Times are `SystemClock.elapsedRealtime()`.
 *
 * - A locked package that comes to the foreground needs authentication unless it is still
 *   unlocked: it stays unlocked while it is in the foreground and for [graceMs] after the user
 *   leaves it (opens the launcher, recents or another launchable app). Dialogs from helper
 *   packages (permission controller, share sheet) do not count as leaving.
 * - Every unlock ends when the screen turns off ([onScreenOff]).
 * - Loop guards: while a prompt is pending for a package its events are ignored; if the package
 *   comes back right after the user cancelled, the service goes home again instead of stacking
 *   prompts.
 */
class AppLockPolicy {
    var lockedPackages: Set<String> = emptySet()
    var graceMs: Long = 0L

    /** Package currently in the foreground as far as app lock is concerned. */
    var current: String? = null
        private set

    private val unlockedUntil = HashMap<String, Long>()
    private var pending: String? = null
    private var pendingSince = 0L
    private var cancelled: String? = null
    private var cancelledAt = 0L

    /**
     * A window of [pkg] came to the foreground. [leavesPrevious] is true for the launcher and
     * launchable apps, false for helper dialogs shown on top of the current app.
     */
    fun onForeground(pkg: String, leavesPrevious: Boolean, nowMs: Long): AppLockDecision {
        val previous = current
        if (leavesPrevious && previous != null && previous != pkg) {
            unlockedUntil[previous]?.let { if (it == IN_FOREGROUND) unlockedUntil[previous] = nowMs + graceMs }
        }
        if (leavesPrevious || pkg in lockedPackages) current = pkg
        if (pkg !in lockedPackages) return AppLockDecision.NONE
        if (isUnlocked(pkg, nowMs)) {
            unlockedUntil[pkg] = IN_FOREGROUND
            return AppLockDecision.NONE
        }
        if (pending == pkg && nowMs - pendingSince < PENDING_TIMEOUT_MS) return AppLockDecision.NONE
        if (cancelled == pkg && nowMs - cancelledAt < CANCEL_GUARD_MS) return AppLockDecision.GO_HOME
        pending = pkg
        pendingSince = nowMs
        return AppLockDecision.PROMPT
    }

    fun isUnlocked(pkg: String, nowMs: Long): Boolean {
        val until = unlockedUntil[pkg] ?: return false
        return until == IN_FOREGROUND || nowMs < until
    }

    /** True when [pkg] would need a prompt now (used after unlocking the device). */
    fun needsPrompt(pkg: String, nowMs: Long): Boolean = pkg in lockedPackages && !isUnlocked(pkg, nowMs)

    fun onAuthenticated(pkg: String) {
        if (pending == pkg) pending = null
        cancelled = null
        unlockedUntil[pkg] = IN_FOREGROUND
    }

    fun onPromptCancelled(pkg: String, nowMs: Long) {
        if (pending == pkg) pending = null
        cancelled = pkg
        cancelledAt = nowMs
    }

    /** The prompt could not be shown at all (for example the activity did not start). */
    fun onPromptFailed(pkg: String) {
        if (pending == pkg) pending = null
    }

    fun onScreenOff() {
        unlockedUntil.clear()
        pending = null
        cancelled = null
    }

    fun clear() {
        onScreenOff()
        current = null
    }

    companion object {
        private const val IN_FOREGROUND = Long.MAX_VALUE

        /** A prompt that never reported back stops blocking new prompts after this time. */
        const val PENDING_TIMEOUT_MS = 5_000L

        /** Re-appearing within this time after a cancel sends the user home again. */
        const val CANCEL_GUARD_MS = 1_500L
    }
}
