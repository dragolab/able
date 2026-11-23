package com.dragolabs.able.feature.tools.storage

import android.os.Process
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.common.AbleResult

/** Outcome of "Clear all caches". [freedBytes] is measured from free space, never estimated. */
data class TrimCachesResult(val accepted: Boolean, val freedBytes: Long, val measured: Boolean)

/** Outcome of clearing selected apps' caches. Bytes are measured; see [measuredBy]. */
data class AppCacheResult(
    val requested: List<String>,
    val cleared: List<String>,
    val failed: List<String>,
    val freedBytes: Long,
    val measuredBy: FreedMeasure,
)

enum class FreedMeasure { APP_CACHE_STATS, FREE_SPACE, NONE }

/**
 * System-wide cache cleanup through Shizuku (STO-002). Uses only Android's own package-manager
 * shell commands; Able never deletes other apps' files directly.
 *
 * - `pm trim-caches <size>` asks Android to free cached files until the requested free space is
 *   reached, so a very large target frees as much cache as Android allows. Its exit code is 0
 *   even when nothing was freed, so the outcome is measured from free space before/after.
 * - `pm clear --cache-only --user <id> <package>` deletes one package's cache (AOSP
 *   PackageManagerShellCommand.runClear, present in android13-release and main; checked 2026-11-22).
 */
class SystemCacheCleaner(
    private val shizuku: ShizukuAdapter,
    private val stats: StorageStatsSource,
) {
    fun isReady(): Boolean = shizuku.isReady()

    suspend fun trimAllCaches(): TrimCachesResult {
        val before = stats.freeBytes()
        val result = shizuku.executeCommand("pm trim-caches $TRIM_TARGET", timeoutMs = TRIM_TIMEOUT_MS)
        val accepted = result is AbleResult.Success
        val after = stats.freeBytes()
        val measured = before != null && after != null
        return TrimCachesResult(
            accepted = accepted,
            freedBytes = if (measured) measuredFreedBytes(before, after) else 0L,
            measured = measured,
        )
    }

    /** Clears each package's cache in turn; [onProgress] receives the number processed so far. */
    suspend fun clearAppCaches(packages: List<String>, onProgress: (Int) -> Unit = {}): AppCacheResult {
        val valid = packages.distinct().filter(::isValidPackageName)
        val rejected = packages.distinct() - valid.toSet()
        val cacheBefore = stats.cacheBytes(valid)
        val freeBefore = stats.freeBytes()
        val user = Process.myUid() / PER_USER_RANGE
        val cleared = mutableListOf<String>()
        val failed = rejected.toMutableList()
        onProgress(rejected.size)
        valid.forEachIndexed { index, pkg ->
            val result = shizuku.executeCommand("pm clear --cache-only --user $user $pkg", timeoutMs = APP_TIMEOUT_MS)
            if (result is AbleResult.Success && result.data.contains("Success", ignoreCase = true)) cleared += pkg else failed += pkg
            onProgress(rejected.size + index + 1)
        }
        val freeAfter = stats.freeBytes()
        val (freed, measure) = when {
            cacheBefore.isNotEmpty() -> measuredCacheDecrease(cacheBefore, stats.cacheBytes(cacheBefore.keys)) to FreedMeasure.APP_CACHE_STATS
            freeBefore != null && freeAfter != null -> measuredFreedBytes(freeBefore, freeAfter) to FreedMeasure.FREE_SPACE
            else -> 0L to FreedMeasure.NONE
        }
        return AppCacheResult(packages.distinct(), cleared, failed, freed, measure)
    }

    private companion object {
        /** Larger than any device, so Android frees every cache it is willing to free. */
        const val TRIM_TARGET = "999G"
        const val TRIM_TIMEOUT_MS = 120_000L
        const val APP_TIMEOUT_MS = 15_000L

        /** android.os.UserHandle.PER_USER_RANGE (stable since API 17). */
        const val PER_USER_RANGE = 100_000
    }
}
