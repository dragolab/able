package com.dragolabs.able.feature.apps.background

import com.dragolabs.able.feature.apps.data.PackageCommands

/*
 * Background-activity controls (APP-007 proposal). Verified 2026-11-22:
 *
 * - AOSP `AppOpsManager` (frameworks/base, main): `OP_RUN_IN_BACKGROUND` "Control whether an
 *   application is allowed to run in the background" (`android:run_in_background`) and
 *   `OP_RUN_ANY_IN_BACKGROUND` "Run jobs when in background" (`android:run_any_in_background`).
 *   Mode names: "allow", "ignore", "deny", "default", "foreground".
 *   https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/AppOpsManager.java
 * - RUN_ANY_IN_BACKGROUND = ignore is the "Restricted" app battery usage setting; Android then
 *   restricts jobs and alarms ("User manually restricts app battery").
 *   https://developer.android.com/topic/performance/power/power-details
 * - Standby buckets (active 10, working_set 20, frequent 30, rare 40, restricted 45 since API 31,
 *   never 50, exempted 5) and `adb shell am get-standby-bucket PACKAGE_NAME`.
 *   https://developer.android.com/topic/performance/appstandby
 * - Reference app NoMoreBackground (adil192/no_more_background, lib/compute/adb.dart) uses
 *   `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore|allow`, `cmd appops query-op
 *   RUN_ANY_IN_BACKGROUND ignore` and `dumpsys deviceidle whitelist`.
 *
 * Output formats below were checked with `adb shell` on the API 36.1 emulator (2026-11-22).
 *
 * Added 2026-11-24 (NoMoreBackground parity, AOSP frameworks/base main):
 * - `DeviceIdleController` shell `whitelist +<pkg>` prints "Added: <pkg>" (or "Unknown package:
 *   <pkg>"), `whitelist -<pkg>` prints "Removed: <pkg>" or nothing when the package was not in the
 *   user allowlist. Entries of kind `system` cannot be removed this way.
 * - `NetworkPolicyManagerShellCommand`: `list restrict-background-blacklist` prints
 *   "Restrict background blacklisted UIDs: " followed by space-separated UIDs or "none";
 *   `add|remove restrict-background-blacklist <uid>` print nothing on success and
 *   "Error: UID <uid> not blacklisted" when removing an absent UID. The policy is keyed by UID
 *   (POLICY_REJECT_METERED_BACKGROUND: metered networks only, background only).
 *   https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/net/NetworkPolicyManagerShellCommand.java
 */

/** AppOps mode as printed by `cmd appops`. */
enum class AppOpMode(val shellName: String) {
    ALLOW("allow"),
    IGNORE("ignore"),
    DENY("deny"),
    DEFAULT("default"),
    FOREGROUND("foreground"),
    ;

    /** Whether this mode blocks the background op. `default` resolves to the op default (allow). */
    val restricts: Boolean get() = this == IGNORE || this == DENY

    companion object {
        fun parse(value: String): AppOpMode? = entries.firstOrNull { it.shellName == value.trim().lowercase() }
    }
}

/** App standby bucket (UsageStatsManager.STANDBY_BUCKET_*). */
enum class StandbyBucket(val value: Int, val shellName: String?, val settable: Boolean) {
    EXEMPTED(5, null, false),
    ACTIVE(10, "active", true),
    WORKING_SET(20, "working_set", true),
    FREQUENT(30, "frequent", true),
    RARE(40, "rare", true),
    RESTRICTED(45, "restricted", true),
    NEVER(50, null, false),
    ;

    companion object {
        fun fromValue(value: Int): StandbyBucket? = entries.firstOrNull { it.value == value }
        val SETTABLE: List<StandbyBucket> = entries.filter { it.settable }
    }
}

/** Kind of entry in `dumpsys deviceidle whitelist`. */
enum class PowerAllowlistKind {
    /** `system-excidle`: exempt from app standby only, still subject to Doze. */
    SYSTEM_EXCEPT_IDLE,
    /** `system`: exempt from battery optimization by the system image. */
    SYSTEM,
    /** `user`: the user turned battery optimization off ("Unrestricted"). */
    USER,
}

/** Summary shown as a pill (icon + text). */
enum class BackgroundStatus { ALLOWED, RESTRICTED, PARTIAL, UNKNOWN }

/**
 * Android's three battery-usage options (Settings > App battery usage), as NoMoreBackground shows
 * them: [RESTRICTED] = RUN_ANY_IN_BACKGROUND ignore and not allowlisted, [OPTIMIZED] = allowed and
 * not allowlisted (Android default), [UNRESTRICTED] = allowed and on the battery-optimization
 * allowlist. [CUSTOM] is the inconsistent combination (allowlisted but restricted) that Settings
 * reports as unknown; Able shows it honestly instead of guessing.
 */
enum class BackgroundMode {
    RESTRICTED,
    OPTIMIZED,
    UNRESTRICTED,
    CUSTOM,
    ;

    companion object {
        /** Modes the user can choose. */
        val SELECTABLE: List<BackgroundMode> = listOf(RESTRICTED, OPTIMIZED, UNRESTRICTED)
    }
}

/** Mode from the RUN_ANY_IN_BACKGROUND op and the doze allowlist; null when either is unknown. */
fun backgroundModeOf(runAny: AppOpMode?, allowlisted: Boolean?): BackgroundMode? {
    if (runAny == null || allowlisted == null) return null
    return when {
        runAny.restricts && !allowlisted -> BackgroundMode.RESTRICTED
        runAny.restricts -> BackgroundMode.CUSTOM
        allowlisted -> BackgroundMode.UNRESTRICTED
        else -> BackgroundMode.OPTIMIZED
    }
}

/** Background state of one package, as read back from the system. Null fields are unknown. */
data class BackgroundSnapshot(
    val runAnyInBackground: AppOpMode?,
    val runInBackground: AppOpMode?,
    val bucket: StandbyBucket?,
    val bucketValue: Int? = bucket?.value,
    /** Strongest allowlist kind of the package (user > system > system-excidle). */
    val allowlist: PowerAllowlistKind?,
    /** False when the allowlist could not be read; then [allowlist] is meaningless. */
    val allowlistKnown: Boolean = true,
    /** Every allowlist kind the package appears with (a package can be both `system` and `user`). */
    val allowlistKinds: Set<PowerAllowlistKind> = setOfNotNull(allowlist),
    /** UID on the restrict-background (mobile data) denylist; null when unknown or no UID. */
    val backgroundDataBlocked: Boolean? = null,
) {
    val status: BackgroundStatus get() = backgroundStatus(runAnyInBackground, runInBackground)

    /** [allowlistKinds] plus [allowlist] (so a `copy(allowlist = …)` is still honoured). */
    private val kinds: Set<PowerAllowlistKind> get() = if (allowlist == null) allowlistKinds else allowlistKinds + allowlist

    /** Exempt from battery optimization (Android "Unrestricted"), null if unknown. */
    val batteryOptimizationExempt: Boolean?
        get() = if (!allowlistKnown) null else PowerAllowlistKind.SYSTEM in kinds || PowerAllowlistKind.USER in kinds

    /** In the user allowlist (removable with `whitelist -<pkg>`), null if unknown. */
    val userAllowlisted: Boolean? get() = if (!allowlistKnown) null else PowerAllowlistKind.USER in kinds

    /** Allowlisted by the system image: Able cannot make it Optimised or Restricted. */
    val systemAllowlisted: Boolean get() = allowlistKnown && PowerAllowlistKind.SYSTEM in kinds

    val mode: BackgroundMode? get() = backgroundModeOf(runAnyInBackground, batteryOptimizationExempt)
}

/** A change the user can request. */
sealed interface BackgroundChange {
    /** `allowed = false` restricts both background ops; `true` restores them to `allow`. */
    data class SetBackground(val allowed: Boolean) : BackgroundChange
    data class SetBucket(val bucket: StandbyBucket) : BackgroundChange
    /** One of [BackgroundMode.SELECTABLE]: both ops plus the user battery-optimization allowlist. */
    data class SetMode(val mode: BackgroundMode) : BackgroundChange {
        init { require(mode in BackgroundMode.SELECTABLE) { "Mode $mode is not selectable" } }
    }
    /** Adds (true) or removes the app UID from the restrict-background (mobile data) denylist. */
    data class SetBackgroundData(val blocked: Boolean) : BackgroundChange
    /** Re-applies the settings the user reviewed (only the fields that differ now). */
    data class RestoreReview(val review: ReviewedSettings) : BackgroundChange
}

fun backgroundStatus(runAny: AppOpMode?, runInBackground: AppOpMode?): BackgroundStatus = when {
    runAny == null -> BackgroundStatus.UNKNOWN
    runAny.restricts -> BackgroundStatus.RESTRICTED
    runInBackground?.restricts == true -> BackgroundStatus.PARTIAL
    else -> BackgroundStatus.ALLOWED
}

/**
 * Builds the `cmd appops` / `am` / `dumpsys` commands for background controls and parses their
 * output. Package names are validated with [PackageCommands.isValidPackageName] before being
 * interpolated, and every other argument comes from a closed enum, so nothing user-controlled
 * reaches the shell unchecked.
 */
object BackgroundCommands {

    const val OP_RUN_ANY_IN_BACKGROUND = "RUN_ANY_IN_BACKGROUND"
    const val OP_RUN_IN_BACKGROUND = "RUN_IN_BACKGROUND"
    val BACKGROUND_OPS = listOf(OP_RUN_ANY_IN_BACKGROUND, OP_RUN_IN_BACKGROUND)

    private fun valid(userId: Int, packageName: String) = userId >= 0 && PackageCommands.isValidPackageName(packageName)

    private fun checkOp(op: String) = require(op in BACKGROUND_OPS) { "Unsupported op $op" }

    fun getOp(userId: Int, packageName: String, op: String): String? {
        checkOp(op)
        return if (valid(userId, packageName)) "cmd appops get --user $userId $packageName $op" else null
    }

    fun setOp(userId: Int, packageName: String, op: String, mode: AppOpMode): String? {
        checkOp(op)
        return if (valid(userId, packageName)) "cmd appops set --user $userId $packageName $op ${mode.shellName}" else null
    }

    /** Every package of [userId] whose [op] is in [mode] (one package name per line). */
    fun queryOp(userId: Int, op: String, mode: AppOpMode): String? {
        checkOp(op)
        return if (userId >= 0) "cmd appops query-op --user $userId $op ${mode.shellName}" else null
    }

    /** Bucket of one package (prints a number) or, with [packageName] null, of all (`pkg: n`). */
    fun getStandbyBucket(userId: Int, packageName: String? = null): String? = when {
        userId < 0 -> null
        packageName == null -> "am get-standby-bucket --user $userId"
        PackageCommands.isValidPackageName(packageName) -> "am get-standby-bucket --user $userId $packageName"
        else -> null
    }

    fun setStandbyBucket(userId: Int, packageName: String, bucket: StandbyBucket): String? {
        val name = bucket.shellName?.takeIf { bucket.settable } ?: return null
        return if (valid(userId, packageName)) "am set-standby-bucket --user $userId $packageName $name" else null
    }

    const val POWER_ALLOWLIST = "dumpsys deviceidle whitelist"

    /** Adds [packageName] to (or removes it from) the user battery-optimization allowlist. */
    fun setUserAllowlisted(packageName: String, allowlisted: Boolean): String? =
        if (PackageCommands.isValidPackageName(packageName)) "dumpsys deviceidle whitelist ${if (allowlisted) '+' else '-'}$packageName" else null

    const val BACKGROUND_DATA_DENYLIST = "cmd netpolicy list restrict-background-blacklist"

    private const val PER_USER_RANGE = 100_000
    private const val FIRST_APPLICATION_UID = 10_000
    private const val LAST_APPLICATION_UID = 19_999

    /** Application UID (`UserHandle.isApp`): app id 10000..19999 in any user. */
    fun isAppUid(uid: Int): Boolean = uid >= 0 && uid % PER_USER_RANGE in FIRST_APPLICATION_UID..LAST_APPLICATION_UID

    fun setBackgroundDataBlocked(uid: Int, blocked: Boolean): String? =
        if (isAppUid(uid)) "cmd netpolicy ${if (blocked) "add" else "remove"} restrict-background-blacklist $uid" else null

    private fun setBothOps(userId: Int, packageName: String, allowed: Boolean): List<String>? {
        val mode = if (allowed) AppOpMode.ALLOW else AppOpMode.IGNORE
        // Restrict the broad op first; restore it last so a partial failure errs on "restricted".
        val ops = if (allowed) BACKGROUND_OPS.reversed() else BACKGROUND_OPS
        return ops.map { setOp(userId, packageName, it, mode) ?: return null }
    }

    /**
     * Commands for [change], in order; null if any input is invalid. [uid] is needed for
     * background-data changes, [current] (the state read just before) for [BackgroundChange.RestoreReview].
     */
    fun commandsFor(
        change: BackgroundChange,
        userId: Int,
        packageName: String,
        uid: Int? = null,
        current: BackgroundSnapshot? = null,
    ): List<String>? = when (change) {
        is BackgroundChange.SetBackground -> setBothOps(userId, packageName, change.allowed)
        is BackgroundChange.SetBucket -> listOf(setStandbyBucket(userId, packageName, change.bucket) ?: return null)
        is BackgroundChange.SetMode -> when (change.mode) {
            // Leave the allowlist first, then restrict: "Restricted" is never left allowlisted.
            BackgroundMode.RESTRICTED -> listOf(setUserAllowlisted(packageName, false) ?: return null) +
                (setBothOps(userId, packageName, allowed = false) ?: return null)
            BackgroundMode.OPTIMIZED -> listOf(setUserAllowlisted(packageName, false) ?: return null) +
                (setBothOps(userId, packageName, allowed = true) ?: return null)
            // Allow first, then allowlist (NoMoreBackground order: appops allow + whitelist +pkg).
            BackgroundMode.UNRESTRICTED -> (setBothOps(userId, packageName, allowed = true) ?: return null) +
                listOf(setUserAllowlisted(packageName, true) ?: return null)
            BackgroundMode.CUSTOM -> null
        }
        is BackgroundChange.SetBackgroundData -> listOf(uid?.let { setBackgroundDataBlocked(it, change.blocked) } ?: return null)
        is BackgroundChange.RestoreReview -> restoreCommands(change.review, current ?: return null, userId, packageName, uid)
    }

    /** Only the fields that differ from [review]; an empty list when nothing differs. Null if invalid. */
    fun restoreCommands(review: ReviewedSettings, current: BackgroundSnapshot, userId: Int, packageName: String, uid: Int?): List<String>? {
        val diff = reviewDiff(review, current)
        val commands = ArrayList<String>()
        // Allowlist first when leaving it, last when joining it (as in SetMode).
        if (ReviewField.ALLOWLIST in diff && !review.userAllowlisted) commands += setUserAllowlisted(packageName, false) ?: return null
        if (ReviewField.RUN_ANY_IN_BACKGROUND in diff) {
            commands += setOp(userId, packageName, OP_RUN_ANY_IN_BACKGROUND, review.runAnyInBackground) ?: return null
        }
        if (ReviewField.RUN_IN_BACKGROUND in diff) {
            commands += setOp(userId, packageName, OP_RUN_IN_BACKGROUND, review.runInBackground) ?: return null
        }
        if (ReviewField.ALLOWLIST in diff && review.userAllowlisted) commands += setUserAllowlisted(packageName, true) ?: return null
        if (ReviewField.BACKGROUND_DATA in diff) {
            val blocked = review.backgroundDataBlocked ?: return null
            commands += uid?.let { setBackgroundDataBlocked(it, blocked) } ?: return null
        }
        return commands
    }

    /** Whether the read-back [after] confirms [change]. */
    fun isConfirmed(change: BackgroundChange, after: BackgroundSnapshot?): Boolean {
        if (after == null) return false
        return when (change) {
            is BackgroundChange.SetBackground -> {
                val any = after.runAnyInBackground ?: return false
                val run = after.runInBackground ?: return false
                if (change.allowed) !any.restricts && !run.restricts else any.restricts && run.restricts
            }
            is BackgroundChange.SetBucket -> after.bucketValue == change.bucket.value
            is BackgroundChange.SetMode -> {
                val run = after.runInBackground ?: return false
                after.mode == change.mode && run.restricts == (change.mode == BackgroundMode.RESTRICTED)
            }
            is BackgroundChange.SetBackgroundData -> after.backgroundDataBlocked == change.blocked
            is BackgroundChange.RestoreReview -> isReviewMatched(change.review, after)
        }
    }

    // region Parsing

    private val ERROR_LINE = Regex("""^(Error|Exception occurred|java\.lang\.)""")

    /** True when `cmd`/`am` printed an error instead of a result (they sometimes exit 0). */
    fun isErrorOutput(output: String): Boolean =
        output.lineSequence().map(String::trim).any { ERROR_LINE.containsMatchIn(it) || it.startsWith("Unknown ") }

    /**
     * Effective mode of [op] from `cmd appops get <pkg> <op>`. Handles:
     * `OP: ignore`, `OP: ignore; time=+1s`, `Uid mode: OP: ignore`, and
     * `No operations.` followed by `Default mode: allow`. A non-default uid mode wins over the
     * package mode (as AppOpsService evaluates it); `default` falls back to the op default.
     * Returns null for errors or unparseable output.
     */
    fun parseOpMode(output: String, op: String): AppOpMode? {
        if (isErrorOutput(output)) return null
        var uidMode: AppOpMode? = null
        var packageMode: AppOpMode? = null
        var defaultMode: AppOpMode? = null
        val opLine = Regex("""^${Regex.escape(op)}:\s*([a-z]+)""")
        for (raw in output.lineSequence()) {
            var line = raw.trim()
            if (line.isEmpty()) continue
            val isUid = line.startsWith("Uid mode:")
            if (isUid) line = line.removePrefix("Uid mode:").trim()
            if (line.startsWith("Default mode:")) {
                defaultMode = AppOpMode.parse(line.removePrefix("Default mode:"))
                continue
            }
            val match = opLine.find(line) ?: continue
            val mode = AppOpMode.parse(match.groupValues[1]) ?: continue
            if (isUid) uidMode = mode else packageMode = mode
        }
        val explicit = listOfNotNull(uidMode, packageMode).firstOrNull { it != AppOpMode.DEFAULT }
        return explicit
            ?: defaultMode
            ?: if (uidMode == AppOpMode.DEFAULT || packageMode == AppOpMode.DEFAULT) AppOpMode.DEFAULT else null
    }

    /** Package names printed by `cmd appops query-op`; null on error output. */
    fun parsePackageList(output: String): Set<String>? {
        if (isErrorOutput(output)) return null
        return output.lineSequence().map(String::trim).filter(PackageCommands::isValidPackageName).toSet()
    }

    /** Single bucket number from `am get-standby-bucket <pkg>`; null on error. */
    fun parseStandbyBucket(output: String): Int? {
        if (isErrorOutput(output)) return null
        return output.trim().toIntOrNull()
    }

    /** `pkg: n` lines from `am get-standby-bucket` without a package; null on error. */
    fun parseStandbyBuckets(output: String): Map<String, Int>? {
        if (isErrorOutput(output)) return null
        val result = HashMap<String, Int>()
        for (line in output.lineSequence()) {
            val parts = line.trim().split(':')
            if (parts.size != 2) continue
            val name = parts[0].trim()
            val value = parts[1].trim().toIntOrNull() ?: continue
            if (PackageCommands.isValidPackageName(name)) result[name] = value
        }
        return result
    }

    /**
     * `dumpsys deviceidle whitelist` lines `kind,package,uid`. A package can appear more than
     * once; the strongest kind wins (user > system > system-excidle). Null on error output.
     */
    fun parsePowerAllowlist(output: String): Map<String, PowerAllowlistKind>? =
        parsePowerAllowlistKinds(output)?.mapValues { (_, kinds) -> kinds.maxBy { it.ordinal } }

    /** Every kind each package of `dumpsys deviceidle whitelist` appears with. Null on error output. */
    fun parsePowerAllowlistKinds(output: String): Map<String, Set<PowerAllowlistKind>>? {
        if (isErrorOutput(output)) return null
        val result = HashMap<String, MutableSet<PowerAllowlistKind>>()
        for (line in output.lineSequence()) {
            val parts = line.trim().split(',')
            if (parts.size < 2) continue
            val kind = when (parts[0].trim()) {
                "system-excidle" -> PowerAllowlistKind.SYSTEM_EXCEPT_IDLE
                "system" -> PowerAllowlistKind.SYSTEM
                "user" -> PowerAllowlistKind.USER
                else -> continue
            }
            val name = parts[1].trim()
            if (!PackageCommands.isValidPackageName(name)) continue
            result.getOrPut(name) { LinkedHashSet() } += kind
        }
        return result
    }

    private val WHITESPACE = Regex("\\s+")

    /**
     * UIDs from `cmd netpolicy list restrict-background-blacklist`
     * ("Restrict background blacklisted UIDs: 10321 10322 " or "...: none"). Null on error or
     * when the expected line is missing.
     */
    fun parseUidList(output: String): Set<Int>? {
        if (isErrorOutput(output)) return null
        val line = output.lineSequence().map(String::trim).lastOrNull { it.contains(':') } ?: return null
        val values = line.substringAfter(':').trim()
        if (values.isEmpty() || values.equals("none", ignoreCase = true)) return emptySet()
        val uids = HashSet<Int>()
        for (token in values.split(WHITESPACE)) {
            uids += token.toIntOrNull() ?: return null
        }
        return uids
    }

    // endregion
}

/** Bulk background state of all packages of a user, from four commands. */
data class BackgroundOverview(
    /** Packages with RUN_ANY_IN_BACKGROUND = ignore; null when the query failed. */
    val runAnyRestricted: Set<String>?,
    /** Packages with RUN_IN_BACKGROUND = ignore; null when the query failed. */
    val runRestricted: Set<String>?,
    val buckets: Map<String, Int>,
    val allowlist: Map<String, PowerAllowlistKind>?,
    /** Every allowlist kind per package; defaults to the strongest kind only. */
    val allowlistKinds: Map<String, Set<PowerAllowlistKind>>? = allowlist?.mapValues { setOf(it.value) },
    /** UIDs on the restrict-background (mobile data) denylist; null when the query failed. */
    val backgroundDataBlockedUids: Set<Int>? = null,
) {
    fun statusOf(packageName: String): BackgroundStatus {
        val any = runAnyRestricted ?: return BackgroundStatus.UNKNOWN
        return when {
            packageName in any -> BackgroundStatus.RESTRICTED
            runRestricted?.contains(packageName) == true -> BackgroundStatus.PARTIAL
            else -> BackgroundStatus.ALLOWED
        }
    }

    /** [uid] is the app UID for the current user (PackageManager `ApplicationInfo.uid`), if known. */
    fun snapshotOf(packageName: String, uid: Int? = null): BackgroundSnapshot {
        val status = statusOf(packageName)
        val bucketValue = buckets[packageName]
        return BackgroundSnapshot(
            runAnyInBackground = when (status) {
                BackgroundStatus.UNKNOWN -> null
                BackgroundStatus.RESTRICTED -> AppOpMode.IGNORE
                else -> AppOpMode.ALLOW
            },
            runInBackground = when {
                runRestricted == null -> null
                packageName in runRestricted -> AppOpMode.IGNORE
                else -> AppOpMode.ALLOW
            },
            bucket = bucketValue?.let(StandbyBucket::fromValue),
            bucketValue = bucketValue,
            allowlist = allowlist?.get(packageName),
            allowlistKnown = allowlist != null,
            allowlistKinds = allowlistKinds?.get(packageName).orEmpty(),
            backgroundDataBlocked = if (uid == null || !BackgroundCommands.isAppUid(uid)) null else backgroundDataBlockedUids?.contains(uid),
        )
    }
}
