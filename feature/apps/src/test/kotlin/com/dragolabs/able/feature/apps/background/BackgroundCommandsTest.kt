package com.dragolabs.able.feature.apps.background

import com.dragolabs.able.feature.apps.AppTypeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Outputs below were captured with `adb shell` on the API 36.1 emulator (2026-11-22). */
class BackgroundCommandsTest {

    private val pkg = "dragolabs.calciolive"

    @Test
    fun buildsAppopsAndStandbyCommands() {
        assertEquals(
            "cmd appops get --user 0 $pkg RUN_ANY_IN_BACKGROUND",
            BackgroundCommands.getOp(0, pkg, BackgroundCommands.OP_RUN_ANY_IN_BACKGROUND),
        )
        assertEquals(
            "cmd appops set --user 10 $pkg RUN_IN_BACKGROUND ignore",
            BackgroundCommands.setOp(10, pkg, BackgroundCommands.OP_RUN_IN_BACKGROUND, AppOpMode.IGNORE),
        )
        assertEquals(
            "cmd appops query-op --user 0 RUN_ANY_IN_BACKGROUND ignore",
            BackgroundCommands.queryOp(0, BackgroundCommands.OP_RUN_ANY_IN_BACKGROUND, AppOpMode.IGNORE),
        )
        assertEquals("am get-standby-bucket --user 0", BackgroundCommands.getStandbyBucket(0))
        assertEquals("am get-standby-bucket --user 0 $pkg", BackgroundCommands.getStandbyBucket(0, pkg))
        assertEquals(
            "am set-standby-bucket --user 0 $pkg restricted",
            BackgroundCommands.setStandbyBucket(0, pkg, StandbyBucket.RESTRICTED),
        )
        assertEquals(
            "am set-standby-bucket --user 0 $pkg working_set",
            BackgroundCommands.setStandbyBucket(0, pkg, StandbyBucket.WORKING_SET),
        )
    }

    @Test
    fun rejectsInvalidInputs() {
        assertNull(BackgroundCommands.getOp(0, "com.evil; reboot", BackgroundCommands.OP_RUN_ANY_IN_BACKGROUND))
        assertNull(BackgroundCommands.setOp(0, "\$(id).x", BackgroundCommands.OP_RUN_ANY_IN_BACKGROUND, AppOpMode.IGNORE))
        assertNull(BackgroundCommands.setOp(-1, pkg, BackgroundCommands.OP_RUN_ANY_IN_BACKGROUND, AppOpMode.IGNORE))
        assertNull(BackgroundCommands.getStandbyBucket(0, "nodots"))
        // Exempted and never are reported by Android but cannot be set.
        assertNull(BackgroundCommands.setStandbyBucket(0, pkg, StandbyBucket.EXEMPTED))
        assertNull(BackgroundCommands.setStandbyBucket(0, pkg, StandbyBucket.NEVER))
        assertNull(BackgroundCommands.commandsFor(BackgroundChange.SetBackground(false), 0, "bad pkg"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedOps() {
        BackgroundCommands.setOp(0, pkg, "CAMERA", AppOpMode.IGNORE)
    }

    @Test
    fun restrictAndAllowCommandOrder() {
        assertEquals(
            listOf(
                "cmd appops set --user 0 $pkg RUN_ANY_IN_BACKGROUND ignore",
                "cmd appops set --user 0 $pkg RUN_IN_BACKGROUND ignore",
            ),
            BackgroundCommands.commandsFor(BackgroundChange.SetBackground(allowed = false), 0, pkg),
        )
        assertEquals(
            listOf(
                "cmd appops set --user 0 $pkg RUN_IN_BACKGROUND allow",
                "cmd appops set --user 0 $pkg RUN_ANY_IN_BACKGROUND allow",
            ),
            BackgroundCommands.commandsFor(BackgroundChange.SetBackground(allowed = true), 0, pkg),
        )
    }

    @Test
    fun parsesOpModes() {
        val op = BackgroundCommands.OP_RUN_ANY_IN_BACKGROUND
        assertEquals(AppOpMode.ALLOW, BackgroundCommands.parseOpMode("No operations.\nDefault mode: allow", op))
        assertEquals(AppOpMode.IGNORE, BackgroundCommands.parseOpMode("RUN_ANY_IN_BACKGROUND: ignore", op))
        assertEquals(AppOpMode.IGNORE, BackgroundCommands.parseOpMode("RUN_ANY_IN_BACKGROUND: ignore; time=+2s123ms ago", op))
        assertEquals(AppOpMode.ALLOW, BackgroundCommands.parseOpMode("RUN_ANY_IN_BACKGROUND: allow", op))
        assertEquals(AppOpMode.DEFAULT, BackgroundCommands.parseOpMode("RUN_ANY_IN_BACKGROUND: default", op))
        // A non-default uid mode wins over the package mode.
        assertEquals(
            AppOpMode.IGNORE,
            BackgroundCommands.parseOpMode("Uid mode: RUN_IN_BACKGROUND: ignore\nRUN_IN_BACKGROUND: allow", BackgroundCommands.OP_RUN_IN_BACKGROUND),
        )
        // Must not match the other op whose name is a suffix.
        assertNull(BackgroundCommands.parseOpMode("RUN_ANY_IN_BACKGROUND: ignore", BackgroundCommands.OP_RUN_IN_BACKGROUND))
        assertNull(BackgroundCommands.parseOpMode("Error: No UID for not.a.pkg in user 0", op))
        assertTrue(AppOpMode.IGNORE.restricts)
        assertTrue(AppOpMode.DENY.restricts)
        assertFalse(AppOpMode.DEFAULT.restricts)
    }

    @Test
    fun parsesQueryOpAndBuckets() {
        assertEquals(setOf(pkg), BackgroundCommands.parsePackageList("dragolabs.calciolive\n"))
        assertEquals(emptySet<String>(), BackgroundCommands.parsePackageList(""))
        assertNull(BackgroundCommands.parsePackageList("Error: Unknown operation string: FOO"))

        assertEquals(45, BackgroundCommands.parseStandbyBucket("45"))
        assertNull(
            BackgroundCommands.parseStandbyBucket(
                "\nException occurred while executing 'get-standby-bucket':\n" +
                    "java.lang.IllegalArgumentException: Cannot get standby bucket for non existent package (not.a.pkg)",
            ),
        )
        assertNull(BackgroundCommands.parseStandbyBucket("Error: Unknown bucket: bogus"))
        assertEquals(
            mapOf("com.google.android.networkstack.tethering" to 5, "com.google.android.calendar" to 50, pkg to 10),
            BackgroundCommands.parseStandbyBuckets(
                "com.google.android.networkstack.tethering: 5\ncom.google.android.calendar: 50\n$pkg: 10\n",
            ),
        )
        assertEquals(StandbyBucket.RESTRICTED, StandbyBucket.fromValue(45))
        assertNull(StandbyBucket.fromValue(99))
    }

    @Test
    fun parsesPowerAllowlist() {
        val map = BackgroundCommands.parsePowerAllowlist(
            """
            system-excidle,com.android.providers.calendar,10106
            system,com.android.providers.calendar,10106
            system-excidle,com.android.vending,10153
            user,$pkg,10230
            garbage line
            """.trimIndent(),
        )!!
        assertEquals(PowerAllowlistKind.SYSTEM, map["com.android.providers.calendar"])
        assertEquals(PowerAllowlistKind.SYSTEM_EXCEPT_IDLE, map["com.android.vending"])
        assertEquals(PowerAllowlistKind.USER, map[pkg])
        assertEquals(3, map.size)
    }

    @Test
    fun derivesStatusAndConfirmation() {
        assertEquals(BackgroundStatus.UNKNOWN, backgroundStatus(null, AppOpMode.ALLOW))
        assertEquals(BackgroundStatus.RESTRICTED, backgroundStatus(AppOpMode.IGNORE, AppOpMode.ALLOW))
        assertEquals(BackgroundStatus.PARTIAL, backgroundStatus(AppOpMode.ALLOW, AppOpMode.IGNORE))
        assertEquals(BackgroundStatus.ALLOWED, backgroundStatus(AppOpMode.DEFAULT, null))

        val restricted = BackgroundSnapshot(AppOpMode.IGNORE, AppOpMode.IGNORE, StandbyBucket.RARE, allowlist = null)
        val partial = restricted.copy(runInBackground = AppOpMode.ALLOW)
        val allowed = restricted.copy(runAnyInBackground = AppOpMode.ALLOW, runInBackground = AppOpMode.DEFAULT)
        assertTrue(BackgroundCommands.isConfirmed(BackgroundChange.SetBackground(false), restricted))
        assertFalse(BackgroundCommands.isConfirmed(BackgroundChange.SetBackground(false), partial))
        assertTrue(BackgroundCommands.isConfirmed(BackgroundChange.SetBackground(true), allowed))
        assertFalse(BackgroundCommands.isConfirmed(BackgroundChange.SetBackground(true), null))
        assertTrue(BackgroundCommands.isConfirmed(BackgroundChange.SetBucket(StandbyBucket.RARE), restricted))
        assertFalse(BackgroundCommands.isConfirmed(BackgroundChange.SetBucket(StandbyBucket.RESTRICTED), restricted))
        assertEquals(false, restricted.batteryOptimizationExempt)
        assertEquals(true, restricted.copy(allowlist = PowerAllowlistKind.USER).batteryOptimizationExempt)
        assertEquals(false, restricted.copy(allowlist = PowerAllowlistKind.SYSTEM_EXCEPT_IDLE).batteryOptimizationExempt)
        assertNull(restricted.copy(allowlistKnown = false).batteryOptimizationExempt)
    }

    @Test
    fun overviewSnapshots() {
        val overview = BackgroundOverview(
            runAnyRestricted = setOf("a.restricted"),
            runRestricted = setOf("a.restricted", "b.partial"),
            buckets = mapOf("a.restricted" to 45),
            allowlist = mapOf("c.allowed" to PowerAllowlistKind.USER),
        )
        assertEquals(BackgroundStatus.RESTRICTED, overview.statusOf("a.restricted"))
        assertEquals(BackgroundStatus.PARTIAL, overview.statusOf("b.partial"))
        assertEquals(BackgroundStatus.ALLOWED, overview.statusOf("c.allowed"))
        assertEquals(StandbyBucket.RESTRICTED, overview.snapshotOf("a.restricted").bucket)
        assertEquals(true, overview.snapshotOf("c.allowed").batteryOptimizationExempt)
        assertEquals(BackgroundStatus.UNKNOWN, overview.copy(runAnyRestricted = null).statusOf("a.restricted"))
    }

    @Test
    fun filtersRows() {
        fun row(name: String, system: Boolean, status: BackgroundStatus) = BackgroundRow(
            packageName = name, label = name.substringAfterLast('.'), isSystem = system, status = status,
            bucket = null, batteryOptimizationExempt = null, isProtected = false, removal = null, blockReason = null, risky = false,
        )
        val rows = listOf(
            row("com.user.mail", false, BackgroundStatus.RESTRICTED),
            row("com.user.chat", false, BackgroundStatus.ALLOWED),
            row("com.sys.clock", true, BackgroundStatus.PARTIAL),
        )
        assertEquals(2, filterBackgroundRows(rows, BackgroundFilters(restrictedOnly = true)).size)
        assertEquals(listOf("com.sys.clock"), filterBackgroundRows(rows, BackgroundFilters(type = AppTypeFilter.SYSTEM)).map { it.packageName })
        assertEquals(listOf("com.user.chat"), filterBackgroundRows(rows, BackgroundFilters(query = "CHAT")).map { it.packageName })
    }
}
