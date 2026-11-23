package com.dragolabs.able.feature.tools.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import androidx.compose.runtime.Immutable
import com.dragolabs.able.core.capability.PermissionCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Storage used by one installed package, as reported by [StorageStatsManager]. */
@Immutable
data class AppStorage(
    val packageName: String,
    val label: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
) {
    /** `dataBytes` already includes the cache (StorageStats contract), so it is not added twice. */
    val totalBytes: Long get() = appBytes + dataBytes
}

/** Total/free bytes of the internal shared volume. */
data class VolumeSpace(val totalBytes: Long, val freeBytes: Long)

/** Shared media sizes reported by Android for the current user (requires Usage access). */
data class ExternalMediaSizes(val imageBytes: Long, val videoBytes: Long, val audioBytes: Long)

/**
 * Feature-local reader of storage statistics (STO-003). Uses [StorageStatsManager] on
 * [StorageManager.UUID_DEFAULT]. Per-app and per-user statistics require Usage access
 * (PACKAGE_USAGE_STATS, granted by the user in Android settings); without it the reader returns
 * null instead of guessing.
 *
 * Package visibility: only packages visible to Able (manifest `<queries>`) can be listed.
 */
class StorageStatsSource(
    context: Context,
    private val permissions: PermissionCoordinator,
) {
    private val appContext = context.applicationContext
    private val statsManager = appContext.getSystemService(StorageStatsManager::class.java)
    private val packageManager = appContext.packageManager

    fun hasUsageAccess(): Boolean = runCatching { permissions.hasUsageStatsPermission() }.getOrDefault(false)

    suspend fun readVolume(): VolumeSpace? = withContext(Dispatchers.IO) {
        runCatching {
            VolumeSpace(
                totalBytes = statsManager.getTotalBytes(StorageManager.UUID_DEFAULT),
                freeBytes = statsManager.getFreeBytes(StorageManager.UUID_DEFAULT),
            )
        }.getOrNull()?.takeIf { it.totalBytes > 0 }
    }

    /** Free bytes right now, or null when Android does not report it. */
    suspend fun freeBytes(): Long? = withContext(Dispatchers.IO) {
        runCatching { statsManager.getFreeBytes(StorageManager.UUID_DEFAULT) }.getOrNull()
    }

    /** Every visible package's storage, largest first. Null without Usage access. */
    suspend fun readApps(): List<AppStorage>? = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext null
        val apps = runCatching {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        }.getOrNull() ?: return@withContext null
        val user = Process.myUserHandle()
        val result = apps.mapNotNull { info ->
            runCatching {
                val stats = statsManager.queryStatsForPackage(info.storageUuid ?: StorageManager.UUID_DEFAULT, info.packageName, user)
                AppStorage(
                    packageName = info.packageName,
                    label = label(info),
                    appBytes = stats.appBytes,
                    dataBytes = stats.dataBytes,
                    cacheBytes = stats.cacheBytes,
                )
            }.getOrNull()
        }
        // A SecurityException on every package means access was revoked in the meantime.
        if (result.isEmpty() && apps.isNotEmpty()) null else result.sortedByDescending { it.totalBytes }
    }

    /** Current cache bytes for [packages]; packages Android does not report are omitted. */
    suspend fun cacheBytes(packages: Collection<String>): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext emptyMap()
        val user = Process.myUserHandle()
        packages.mapNotNull { pkg ->
            runCatching {
                pkg to statsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, user).cacheBytes
            }.getOrNull()
        }.toMap()
    }

    /** Image/video/audio totals for the current user, complete even with partial media access. */
    suspend fun readExternalMedia(): ExternalMediaSizes? = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext null
        runCatching {
            val stats = statsManager.queryExternalStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle())
            ExternalMediaSizes(stats.imageBytes, stats.videoBytes, stats.audioBytes)
        }.getOrNull()
    }

    private fun label(info: ApplicationInfo): String =
        runCatching { packageManager.getApplicationLabel(info).toString() }.getOrNull()?.takeIf { it.isNotBlank() } ?: info.packageName
}
