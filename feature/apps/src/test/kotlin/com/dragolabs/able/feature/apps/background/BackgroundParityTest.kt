package com.dragolabs.able.feature.apps.background

import com.dragolabs.able.feature.apps.AppTypeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Three-state mode, background mobile data and reviewed snapshots (APP-007 revision, 2026-11-24).
 * Output formats follow AOSP DeviceIdleController / NetworkPolicyManagerShellCommand (main).
 */
class BackgroundParityTest {

    private val pkg = "dragolabs.calciolive"
    private val uid = 10230

    private fun snapshot(
        any: AppOpMode? = AppOpMode.ALLOW,
        run: AppOpMode? = AppOpMode.ALLOW,
        kinds: Set<PowerAllowlistKind> = emptySet(),
        allowlistKnown: Boolean = true,
        data: Boolean? = false,
    ) = BackgroundSnapshot(
        runAnyInBackground = any,
        runInBackground = run,
        bucket = null,
        allowlist = kinds.maxByOrNull { it.ordinal },
        allowlistKnown = allowlistKnown,
        allowlistKinds = kinds,
        backgroundDataBlocked = data,
    )

    @Test
    fun derivesThreeStateMode() {
        assertEquals(BackgroundMode.OPTIMIZED, snapshot().mode)
        assertEquals(BackgroundMode.OPTIMIZED, snapshot(any = AppOpMode.DEFAULT).mode)
        assertEquals(BackgroundMode.RESTRICTED, snapshot(any = AppOpMode.IGNORE).mode)
        assertEquals(BackgroundMode.UNRESTRICTED, snapshot(kinds = setOf(PowerAllowlistKind.USER)).mode)
        assertEquals(BackgroundMode.UNRESTRICTED, snapshot(kinds = setOf(PowerAllowlistKind.SYSTEM)).mode)
        // system-excidle only exempts from app standby, not from battery optimization.
        assertEquals(BackgroundMode.OPTIMIZED, snapshot(kinds = setOf(PowerAllowlistKind.SYSTEM_EXCEPT_IDLE)).mode)
        assertEquals(BackgroundMode.CUSTOM, snapshot(any = AppOpMode.IGNORE, kinds = setOf(PowerAllowlistKind.USER)).mode)
        assertNull(snapshot(any = null).mode)
        assertNull(snapshot(allowlistKnown = false).mode)

        val both = snapshot(kinds = setOf(PowerAllowlistKind.SYSTEM, PowerAllowlistKind.USER))
        assertTrue(both.systemAllowlisted)
        assertEquals(true, both.userAllowlisted)
        assertFalse(snapshot(kinds = setOf(PowerAllowlistKind.USER)).systemAllowlisted)
    }

    @Test
    fun buildsModeCommandsInSafeOrder() {
        assertEquals(
            listOf(
                "dumpsys deviceidle whitelist -$pkg",
                "cmd appops set --user 0 $pkg RUN_ANY_IN_BACKGROUND ignore",
                "cmd appops set --user 0 $pkg RUN_IN_BACKGROUND ignore",
            ),
            BackgroundCommands.commandsFor(BackgroundChange.SetMode(BackgroundMode.RESTRICTED), 0, pkg),
        )
        assertEquals(
            listOf(
                "dumpsys deviceidle whitelist -$pkg",
                "cmd appops set --user 0 $pkg RUN_IN_BACKGROUND allow",
                "cmd appops set --user 0 $pkg RUN_ANY_IN_BACKGROUND allow",
            ),
            BackgroundCommands.commandsFor(BackgroundChange.SetMode(BackgroundMode.OPTIMIZED), 0, pkg),
        )
        assertEquals(
            listOf(
                "cmd appops set --user 0 $pkg RUN_IN_BACKGROUND allow",
                "cmd appops set --user 0 $pkg RUN_ANY_IN_BACKGROUND allow",
                "dumpsys deviceidle whitelist +$pkg",
            ),
            BackgroundCommands.commandsFor(BackgroundChange.SetMode(BackgroundMode.UNRESTRICTED), 0, pkg),
        )
        assertNull(BackgroundCommands.commandsFor(BackgroundChange.SetMode(BackgroundMode.RESTRICTED), 0, "bad;rm"))
        assertNull(BackgroundCommands.setUserAllowlisted("com.example app", true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun customIsNotSelectable() {
        BackgroundChange.SetMode(BackgroundMode.CUSTOM)
    }

    @Test
    fun confirmsModeFromReadBack() {
        val restrict = BackgroundChange.SetMode(BackgroundMode.RESTRICTED)
        assertTrue(BackgroundCommands.isConfirmed(restrict, snapshot(any = AppOpMode.IGNORE, run = AppOpMode.IGNORE)))
        assertFalse(BackgroundCommands.isConfirmed(restrict, snapshot(any = AppOpMode.IGNORE, run = AppOpMode.ALLOW)))
        assertFalse(BackgroundCommands.isConfirmed(restrict, snapshot(any = AppOpMode.IGNORE, run = AppOpMode.IGNORE, kinds = setOf(PowerAllowlistKind.SYSTEM))))
        val unrestricted = BackgroundChange.SetMode(BackgroundMode.UNRESTRICTED)
        assertTrue(BackgroundCommands.isConfirmed(unrestricted, snapshot(kinds = setOf(PowerAllowlistKind.USER))))
        assertFalse(BackgroundCommands.isConfirmed(unrestricted, snapshot()))
        assertFalse(BackgroundCommands.isConfirmed(unrestricted, snapshot(allowlistKnown = false)))
        val optimized = BackgroundChange.SetMode(BackgroundMode.OPTIMIZED)
        assertTrue(BackgroundCommands.isConfirmed(optimized, snapshot(kinds = setOf(PowerAllowlistKind.SYSTEM_EXCEPT_IDLE))))
        assertFalse(BackgroundCommands.isConfirmed(optimized, snapshot(kinds = setOf(PowerAllowlistKind.SYSTEM))))
    }

    @Test
    fun parsesAllowlistKinds() {
        val kinds = BackgroundCommands.parsePowerAllowlistKinds(
            """
            system-excidle,com.android.vending,10153
            system,com.google.android.gms,10120
            user,com.google.android.gms,10120
            user,$pkg,$uid
            """.trimIndent(),
        )!!
        assertEquals(setOf(PowerAllowlistKind.SYSTEM, PowerAllowlistKind.USER), kinds["com.google.android.gms"])
        assertEquals(setOf(PowerAllowlistKind.USER), kinds[pkg])
        assertNull(BackgroundCommands.parsePowerAllowlistKinds("Error: permission denied"))
    }

    @Test
    fun backgroundDataCommandsAndParsing() {
        assertEquals("cmd netpolicy list restrict-background-blacklist", BackgroundCommands.BACKGROUND_DATA_DENYLIST)
        assertEquals("cmd netpolicy add restrict-background-blacklist 10321", BackgroundCommands.setBackgroundDataBlocked(10321, true))
        assertEquals("cmd netpolicy remove restrict-background-blacklist 1010321", BackgroundCommands.setBackgroundDataBlocked(1010321, false))
        assertNull(BackgroundCommands.setBackgroundDataBlocked(1000, true))
        assertNull(BackgroundCommands.setBackgroundDataBlocked(-5, true))
        assertNull(BackgroundCommands.setBackgroundDataBlocked(99_999, true))
        assertEquals(
            listOf("cmd netpolicy add restrict-background-blacklist $uid"),
            BackgroundCommands.commandsFor(BackgroundChange.SetBackgroundData(true), 0, pkg, uid),
        )
        assertNull(BackgroundCommands.commandsFor(BackgroundChange.SetBackgroundData(true), 0, pkg, null))

        assertEquals(setOf(10321, 10322), BackgroundCommands.parseUidList("Restrict background blacklisted UIDs: 10321 10322 \n"))
        assertEquals(emptySet<Int>(), BackgroundCommands.parseUidList("Restrict background blacklisted UIDs: none\n"))
        assertNull(BackgroundCommands.parseUidList("Error: UID 10321 not blacklisted"))
        assertNull(BackgroundCommands.parseUidList("Unknown command: netpolicy"))
        assertNull(BackgroundCommands.parseUidList(""))

        assertTrue(BackgroundCommands.isConfirmed(BackgroundChange.SetBackgroundData(true), snapshot(data = true)))
        assertFalse(BackgroundCommands.isConfirmed(BackgroundChange.SetBackgroundData(true), snapshot(data = null)))
    }

    @Test
    fun overviewCarriesModeAndData() {
        val overview = BackgroundOverview(
            runAnyRestricted = setOf("a.restricted"),
            runRestricted = setOf("a.restricted"),
            buckets = emptyMap(),
            allowlist = mapOf("c.unrestricted" to PowerAllowlistKind.USER),
            backgroundDataBlockedUids = setOf(10500),
        )
        assertEquals(BackgroundMode.RESTRICTED, overview.snapshotOf("a.restricted").mode)
        assertEquals(BackgroundMode.UNRESTRICTED, overview.snapshotOf("c.unrestricted").mode)
        assertEquals(BackgroundMode.OPTIMIZED, overview.snapshotOf("d.default").mode)
        assertEquals(true, overview.snapshotOf("a.restricted", 10500).backgroundDataBlocked)
        assertEquals(false, overview.snapshotOf("a.restricted", 10501).backgroundDataBlocked)
        assertNull(overview.snapshotOf("a.restricted").backgroundDataBlocked)
        assertNull(overview.copy(backgroundDataBlockedUids = null).snapshotOf("a.restricted", 10500).backgroundDataBlocked)
    }

    @Test
    fun reviewDiffAndRestore() {
        val reviewedState = snapshot(any = AppOpMode.IGNORE, run = AppOpMode.IGNORE, data = true)
        val review = reviewOf(reviewedState, 1_000L)!!
        assertEquals(ReviewedSettings(AppOpMode.IGNORE, AppOpMode.IGNORE, false, true, 1_000L), review)
        assertTrue(reviewDiff(review, reviewedState).isEmpty())
        assertTrue(isReviewMatched(review, reviewedState))
        assertNull(reviewOf(snapshot(allowlistKnown = false), 0L))

        // An app update reset the ops to allow and Android dropped the data restriction.
        val reset = snapshot(any = AppOpMode.ALLOW, run = AppOpMode.DEFAULT, data = false)
        assertEquals(
            setOf(ReviewField.RUN_ANY_IN_BACKGROUND, ReviewField.RUN_IN_BACKGROUND, ReviewField.BACKGROUND_DATA),
            reviewDiff(review, reset),
        )
        assertEquals(ReviewState.CHANGED, reviewStateOf(review, reset))
        assertEquals(ReviewState.REVIEWED, reviewStateOf(review, reviewedState))
        assertEquals(ReviewState.REVIEWED, reviewStateOf(review, null))
        assertEquals(ReviewState.NONE, reviewStateOf(null, reset))
        assertEquals(
            listOf(
                "cmd appops set --user 0 $pkg RUN_ANY_IN_BACKGROUND ignore",
                "cmd appops set --user 0 $pkg RUN_IN_BACKGROUND ignore",
                "cmd netpolicy add restrict-background-blacklist $uid",
            ),
            BackgroundCommands.commandsFor(BackgroundChange.RestoreReview(review), 0, pkg, uid, reset),
        )
        // Without the state read just before, a restore cannot be planned.
        assertNull(BackgroundCommands.commandsFor(BackgroundChange.RestoreReview(review), 0, pkg, uid, null))
        // Data differs but the UID is unknown: refuse rather than half-restore.
        assertNull(BackgroundCommands.commandsFor(BackgroundChange.RestoreReview(review), 0, pkg, null, reset))

        // Equivalent op modes and unknown live values are not "changed".
        val equivalent = snapshot(any = AppOpMode.DENY, run = AppOpMode.IGNORE, data = null)
        assertTrue(reviewDiff(review, equivalent).isEmpty())
        assertFalse(isReviewMatched(review, equivalent))
        assertFalse(BackgroundCommands.isConfirmed(BackgroundChange.RestoreReview(review), equivalent))
        assertTrue(BackgroundCommands.isConfirmed(BackgroundChange.RestoreReview(review), reviewedState))
    }

    @Test
    fun restoreHandlesAllowlistOrder() {
        val unrestricted = reviewOf(snapshot(kinds = setOf(PowerAllowlistKind.USER), data = null), 0L)!!
        assertEquals(
            listOf("dumpsys deviceidle whitelist +$pkg"),
            BackgroundCommands.restoreCommands(unrestricted, snapshot(), 0, pkg, null),
        )
        val restricted = reviewOf(snapshot(any = AppOpMode.IGNORE, run = AppOpMode.IGNORE, data = null), 0L)!!
        assertEquals(
            listOf(
                "dumpsys deviceidle whitelist -$pkg",
                "cmd appops set --user 0 $pkg RUN_ANY_IN_BACKGROUND ignore",
                "cmd appops set --user 0 $pkg RUN_IN_BACKGROUND ignore",
            ),
            BackgroundCommands.restoreCommands(restricted, snapshot(kinds = setOf(PowerAllowlistKind.USER)), 0, pkg, null),
        )
        assertEquals(emptyList<String>(), BackgroundCommands.restoreCommands(restricted, snapshot(any = AppOpMode.IGNORE, run = AppOpMode.IGNORE), 0, pkg, null))
    }

    @Test
    fun reviewStoreRoundTrip() {
        val reviews = mapOf(
            BackgroundReviewStore.key(0, pkg) to ReviewedSettings(AppOpMode.IGNORE, AppOpMode.ALLOW, true, null, 42L),
        )
        val text = BackgroundReviewStore.encode(reviews)
        assertEquals(reviews, BackgroundReviewStore.decode(text))
        assertEquals(emptyMap<String, ReviewedSettings>(), BackgroundReviewStore.decode(""))
        assertEquals("10:$pkg", BackgroundReviewStore.key(10, pkg))
    }

    @Test
    fun filtersDataBlockedAndReviewed() {
        fun row(name: String, data: Boolean?, review: ReviewState) = BackgroundRow(
            packageName = name, label = name, isSystem = false, status = BackgroundStatus.ALLOWED,
            bucket = null, batteryOptimizationExempt = null, isProtected = false, removal = null, blockReason = null, risky = false,
            backgroundDataBlocked = data, review = review,
        )
        val rows = listOf(
            row("com.a.blocked", true, ReviewState.NONE),
            row("com.b.reviewed", false, ReviewState.REVIEWED),
            row("com.c.changed", null, ReviewState.CHANGED),
        )
        assertEquals(listOf("com.a.blocked"), filterBackgroundRows(rows, BackgroundFilters(backgroundDataBlockedOnly = true)).map { it.packageName })
        assertEquals(
            listOf("com.a.blocked", "com.c.changed"),
            filterBackgroundRows(rows, BackgroundFilters(hideReviewed = true)).map { it.packageName },
        )
        assertFalse(BackgroundFilters(hideReviewed = true).isDefault)
        assertTrue(BackgroundFilters(type = AppTypeFilter.ALL).isDefault)
    }
}
