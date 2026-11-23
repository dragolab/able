package com.dragolabs.able.feature.apps.background

import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.feature.apps.data.PackageActionOutcome
import com.dragolabs.able.feature.apps.data.PackageCommands
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

data class BackgroundChangeResult(
    val outcome: PackageActionOutcome,
    val before: BackgroundSnapshot?,
    val after: BackgroundSnapshot?,
    val detail: String? = null,
)

/**
 * Reads and changes background controls through Shizuku. Every change is verified by reading the
 * state back; the outcome is never assumed from the command exit code alone.
 */
class BackgroundRepository(
    private val context: Context,
    private val shizuku: ShizukuAdapter,
) {

    val isReady: Boolean get() = shizuku.isReady()

    private suspend fun run(command: String): Pair<String?, String?> =
        when (val result = shizuku.executeCommand(command, COMMAND_TIMEOUT_MS)) {
            is AbleResult.Success -> result.data to null
            is AbleResult.Failure -> null to result.error.message
            AbleResult.Loading -> null to null
        }

    /** Reads all packages of [userId] at once; null when Shizuku is not ready. */
    suspend fun readOverview(userId: Int): BackgroundOverview? = coroutineScope {
        if (!shizuku.isReady()) return@coroutineScope null
        val any = async {
            BackgroundCommands.queryOp(userId, BackgroundCommands.OP_RUN_ANY_IN_BACKGROUND, AppOpMode.IGNORE)
                ?.let { run(it).first }?.let(BackgroundCommands::parsePackageList)
        }
        val runBg = async {
            BackgroundCommands.queryOp(userId, BackgroundCommands.OP_RUN_IN_BACKGROUND, AppOpMode.IGNORE)
                ?.let { run(it).first }?.let(BackgroundCommands::parsePackageList)
        }
        val buckets = async {
            BackgroundCommands.getStandbyBucket(userId)?.let { run(it).first }?.let(BackgroundCommands::parseStandbyBuckets)
        }
        val allowlist = async { run(BackgroundCommands.POWER_ALLOWLIST).first?.let(BackgroundCommands::parsePowerAllowlistKinds) }
        val data = async { run(BackgroundCommands.BACKGROUND_DATA_DENYLIST).first?.let(BackgroundCommands::parseUidList) }
        val kinds = allowlist.await()
        BackgroundOverview(
            runAnyRestricted = any.await(),
            runRestricted = runBg.await(),
            buckets = buckets.await().orEmpty(),
            allowlist = kinds?.mapValues { (_, set) -> set.maxBy { it.ordinal } },
            allowlistKinds = kinds,
            backgroundDataBlockedUids = data.await(),
        )
    }

    /** App UID for the user Able runs in (PackageManager `ApplicationInfo.uid`); null if unknown. */
    fun uidOf(packageName: String): Int? = runCatching {
        context.packageManager.getApplicationInfo(
            packageName,
            PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()),
        ).uid
    }.getOrNull()?.takeIf(BackgroundCommands::isAppUid)

    /** Reads one package through Shizuku; null when Shizuku is not ready or the name is invalid. */
    suspend fun read(userId: Int, packageName: String): BackgroundSnapshot? = coroutineScope {
        if (!shizuku.isReady() || !PackageCommands.isValidPackageName(packageName)) return@coroutineScope null
        val any = async {
            BackgroundCommands.getOp(userId, packageName, BackgroundCommands.OP_RUN_ANY_IN_BACKGROUND)
                ?.let { run(it).first }?.let { BackgroundCommands.parseOpMode(it, BackgroundCommands.OP_RUN_ANY_IN_BACKGROUND) }
        }
        val runBg = async {
            BackgroundCommands.getOp(userId, packageName, BackgroundCommands.OP_RUN_IN_BACKGROUND)
                ?.let { run(it).first }?.let { BackgroundCommands.parseOpMode(it, BackgroundCommands.OP_RUN_IN_BACKGROUND) }
        }
        val bucket = async {
            BackgroundCommands.getStandbyBucket(userId, packageName)?.let { run(it).first }?.let(BackgroundCommands::parseStandbyBucket)
        }
        val allowlist = async { run(BackgroundCommands.POWER_ALLOWLIST).first?.let(BackgroundCommands::parsePowerAllowlistKinds) }
        val uid = uidOf(packageName)
        val data = async {
            if (uid == null) null else run(BackgroundCommands.BACKGROUND_DATA_DENYLIST).first?.let(BackgroundCommands::parseUidList)
        }
        val list = allowlist.await()
        val bucketValue = bucket.await()
        val kinds = list?.get(packageName).orEmpty()
        BackgroundSnapshot(
            runAnyInBackground = any.await(),
            runInBackground = runBg.await(),
            bucket = bucketValue?.let(StandbyBucket::fromValue),
            bucketValue = bucketValue,
            allowlist = kinds.maxByOrNull { it.ordinal },
            allowlistKnown = list != null,
            allowlistKinds = kinds,
            backgroundDataBlocked = uid?.let { u -> data.await()?.contains(u) },
        )
    }

    /**
     * Battery-optimization exemption without Shizuku (public `PowerManager` API, read-only).
     * Null if Android does not answer.
     */
    fun isBatteryOptimizationExempt(packageName: String): Boolean? = runCatching {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName)
    }.getOrNull()

    /** Applies [change] and reads the state back. */
    suspend fun apply(userId: Int, packageName: String, change: BackgroundChange): BackgroundChangeResult {
        if (!PackageCommands.isValidPackageName(packageName)) {
            return BackgroundChangeResult(PackageActionOutcome.REJECTED, null, null, "Invalid package name")
        }
        if (!shizuku.isReady()) return BackgroundChangeResult(PackageActionOutcome.REJECTED, null, null, "Shizuku unavailable")
        val before = read(userId, packageName)
        val uid = uidOf(packageName)
        val commands = BackgroundCommands.commandsFor(change, userId, packageName, uid, before)
            ?: return BackgroundChangeResult(
                PackageActionOutcome.REJECTED,
                before,
                before,
                when {
                    change is BackgroundChange.SetBackgroundData || change is BackgroundChange.RestoreReview && uid == null -> "App UID unavailable"
                    before == null -> "Current state unavailable"
                    else -> "Invalid request"
                },
            )
        var failure: String? = null
        for (command in commands) {
            val (output, error) = run(command)
            val text = output.orEmpty()
            if (error != null || BackgroundCommands.isErrorOutput(text)) {
                failure = error ?: text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                break
            }
        }
        delay(VERIFY_DELAY_MS)
        val after = read(userId, packageName)
        val confirmed = BackgroundCommands.isConfirmed(change, after)
        val outcome = when {
            confirmed -> PackageActionOutcome.APPLIED
            failure != null && after == before -> PackageActionOutcome.REJECTED
            else -> PackageActionOutcome.NOT_CONFIRMED
        }
        return BackgroundChangeResult(outcome, before, after, if (confirmed) null else failure)
    }

    private companion object {
        const val COMMAND_TIMEOUT_MS = 10_000L
        const val VERIFY_DELAY_MS = 300L
    }
}
