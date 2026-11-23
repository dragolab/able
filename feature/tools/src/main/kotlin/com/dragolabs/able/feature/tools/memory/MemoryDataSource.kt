package com.dragolabs.able.feature.tools.memory

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.core.common.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Device memory as reported by Android (ActivityManager.MemoryInfo) and `/proc/meminfo`. */
data class RamSummary(
    val totalBytes: Long,
    val availableBytes: Long,
    /** Android starts killing background processes when available memory drops below this. */
    val thresholdBytes: Long,
    val lowMemory: Boolean,
    /** Swap (zram on most devices); `null` when `/proc/meminfo` is not readable. */
    val swapTotalBytes: Long?,
    val swapFreeBytes: Long?,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    val usedPercent: Int get() = (usedFraction * 100).toInt().coerceIn(0, 100)
    val swapUsedBytes: Long? get() = if (swapTotalBytes != null && swapFreeBytes != null) (swapTotalBytes - swapFreeBytes).coerceAtLeast(0) else null
}

/** Why Able never stops an app. */
enum class ProtectReason { SELF, SHIZUKU, CORE_SYSTEM, LAUNCHER, PHONE, KEYBOARD, PERSISTENT }

/** A running package (all of its processes for one Android user). */
data class RunningApp(
    val packageName: String,
    val label: String,
    val userId: Int,
    val isSystem: Boolean,
    /** Sum of resident memory of the app's processes. Shared pages are counted in each process. */
    val rssBytes: Long,
    val processCount: Int,
    val importance: ProcessImportance,
    val protectReason: ProtectReason?,
) {
    val key: String get() = "$userId:$packageName"
    val stoppable: Boolean get() = protectReason == null
}

/** Outcome of a stop request after reading the process list back. */
enum class StopOutcome {
    /** No process of the app is left. */
    STOPPED,

    /** Some processes were stopped; Android kept others (e.g. a foreground service). */
    PARTIAL,

    /** Android accepted the command but every process is still running (or restarted). */
    STILL_RUNNING,

    /** The shell command failed. */
    FAILED,
}

enum class StopKind { FORCE_STOP, KILL_BACKGROUND }

/**
 * Reads device memory and, through Shizuku, the running processes; stops apps with `am`.
 * Never stops packages in the block list ([protectReasonFor]).
 */
class MemoryDataSource(
    private val context: Context,
    private val shizuku: ShizukuAdapter,
) {
    private val pm: PackageManager = context.packageManager
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    fun ramSummary(): RamSummary? {
        val am = activityManager ?: return null
        val info = ActivityManager.MemoryInfo()
        runCatching { am.getMemoryInfo(info) }.getOrElse { return null }
        val meminfo = runCatching { parseMeminfo(File("/proc/meminfo").readText()) }.getOrDefault(emptyMap())
        return RamSummary(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            thresholdBytes = info.threshold,
            lowMemory = info.lowMemory,
            swapTotalBytes = meminfo["SwapTotal"]?.times(1024),
            swapFreeBytes = meminfo["SwapFree"]?.times(1024),
        )
    }

    /** Fills swap values through the shell when the app sandbox cannot read `/proc/meminfo`. */
    suspend fun withShellSwap(summary: RamSummary): RamSummary {
        if (summary.swapTotalBytes != null || !shizuku.isReady()) return summary
        val meminfo = shizuku.executeCommand("cat /proc/meminfo").getOrNull()?.let(::parseMeminfo) ?: return summary
        return summary.copy(
            swapTotalBytes = meminfo["SwapTotal"]?.times(1024),
            swapFreeBytes = meminfo["SwapFree"]?.times(1024),
        )
    }

    /** Running apps, or `null` when the shell is unavailable or the command failed. */
    suspend fun runningApps(): List<RunningApp>? = withContext(Dispatchers.IO) {
        val ps = shizuku.executeCommand(PS_COMMAND, timeoutMs = 10_000L).getOrNull() ?: return@withContext null
        val lru = shizuku.executeCommand("dumpsys activity lru", timeoutMs = 10_000L).getOrNull()?.let(::parseLru).orEmpty()
        val blocks = protectedPackages()
        val labels = HashMap<String, Pair<String, ApplicationInfo?>>()

        class Acc(var rss: Long = 0, var count: Int = 0, var importance: ProcessImportance = ProcessImportance.OTHER)
        val grouped = LinkedHashMap<Pair<Int, String>, Acc>()
        parsePs(ps).forEach { row ->
            val pkg = packageFor(row) ?: return@forEach
            val acc = grouped.getOrPut(row.uid / PER_USER_RANGE to pkg) { Acc() }
            acc.rss += row.rssKb
            acc.count++
            val imp = lru[row.pid] ?: ProcessImportance.OTHER
            if (imp.ordinal < acc.importance.ordinal) acc.importance = imp
        }
        grouped.map { (key, acc) ->
            val (userId, pkg) = key
            val (label, info) = labels.getOrPut(pkg) {
                val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                (ai?.loadLabel(pm)?.toString() ?: pkg) to ai
            }
            val system = info != null && (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            val persistent = info != null && (info.flags and ApplicationInfo.FLAG_PERSISTENT) != 0
            RunningApp(
                packageName = pkg,
                label = label,
                userId = userId,
                isSystem = system,
                rssBytes = acc.rss * 1024,
                processCount = acc.count,
                importance = acc.importance,
                protectReason = blocks[pkg] ?: if (persistent || acc.importance == ProcessImportance.PERSISTENT) ProtectReason.PERSISTENT else null,
            )
        }.sortedByDescending { it.rssBytes }
    }

    /** Process names are `package` or `package:suffix`; shared/isolated names fall back to the UID. */
    private fun packageFor(row: PsRow): String? {
        val appId = row.uid % PER_USER_RANGE
        val base = basePackageOf(row.name)
        if (isSafePackageName(base) && isInstalled(base)) return base
        if (appId !in FIRST_APP_ID..LAST_APP_ID) return null
        return runCatching { pm.getPackagesForUid(row.uid)?.firstOrNull() }.getOrNull()
            ?.takeIf { isSafePackageName(it) }
    }

    private val installed = HashMap<String, Boolean>()

    private fun isInstalled(pkg: String): Boolean = installed.getOrPut(pkg) {
        runCatching { pm.getApplicationInfo(pkg, 0); true }.getOrDefault(false)
    }

    /**
     * Block list: Able, Shizuku, the framework and System UI, the current launcher, phone and
     * telephony, the current keyboard, and Google Play services (Shappky protects the same set).
     */
    fun protectedPackages(): Map<String, ProtectReason> = buildMap {
        CORE_PACKAGES.forEach { put(it, ProtectReason.CORE_SYSTEM) }
        PHONE_PACKAGES.forEach { put(it, ProtectReason.PHONE) }
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
            ?.let { put(it, ProtectReason.PHONE) }
        runCatching {
            pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull()?.takeIf { it != "android" }?.let { put(it, ProtectReason.LAUNCHER) }
        runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) }.getOrNull()
            ?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let { put(it, ProtectReason.KEYBOARD) }
        put(SHIZUKU_PACKAGE, ProtectReason.SHIZUKU)
        put(context.packageName, ProtectReason.SELF)
    }

    /**
     * Runs `am force-stop` or `am kill` for one user, then reads the process list back. A
     * force-stopped app can still be restarted by Android (alarms, jobs, push messages).
     */
    suspend fun stop(app: RunningApp, kind: StopKind): StopOutcome = withContext(Dispatchers.IO) {
        if (!app.stoppable || !isSafePackageName(app.packageName)) return@withContext StopOutcome.FAILED
        val before = processCount(app) ?: app.processCount
        val verb = if (kind == StopKind.FORCE_STOP) "force-stop" else "kill"
        val result = shizuku.executeCommand("am $verb --user ${app.userId} ${app.packageName}")
        if (result !is AbleResult.Success) return@withContext StopOutcome.FAILED
        var after = before
        repeat(READ_BACK_STEPS) {
            delay(READ_BACK_INTERVAL_MS)
            after = processCount(app) ?: return@withContext StopOutcome.STILL_RUNNING
            if (after == 0) return@withContext StopOutcome.STOPPED
        }
        if (after < before) StopOutcome.PARTIAL else StopOutcome.STILL_RUNNING
    }

    /** Number of processes the app still has for its user, or `null` if `ps` failed. */
    private suspend fun processCount(app: RunningApp): Int? {
        val ps = shizuku.executeCommand(PS_COMMAND, timeoutMs = 10_000L).getOrNull() ?: return null
        return parsePs(ps).count { it.uid / PER_USER_RANGE == app.userId && packageFor(it) == app.packageName }
    }

    fun appInfoIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null))

    /** Android's own "Running services" screen (Developer options), with broader fallbacks. */
    fun runningServicesIntents(): Array<Intent> = arrayOf(
        Intent(Intent.ACTION_MAIN).setClassName(SETTINGS_PACKAGE, "$SETTINGS_PACKAGE.Settings\$RunningServicesActivity"),
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).putExtra(":settings:fragment_args_key", "running_apps"),
        Intent(Settings.ACTION_APPLICATION_SETTINGS),
    )

    private companion object {
        const val PS_COMMAND = "ps -A -o PID,UID,RSS,NAME"
        const val PER_USER_RANGE = 100_000
        const val FIRST_APP_ID = 10_000
        const val LAST_APP_ID = 19_999
        const val READ_BACK_STEPS = 6
        const val READ_BACK_INTERVAL_MS = 400L
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        val CORE_PACKAGES = listOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.shell",
            "com.google.android.gms",
            "com.google.android.gsf",
        )
        val PHONE_PACKAGES = listOf("com.android.phone", "com.android.server.telecom", "com.android.dialer", "com.google.android.dialer")
    }
}
