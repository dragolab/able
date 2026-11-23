package com.dragolabs.able.feature.apps.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap

/** Install state of a package for the current user (APP-001). */
enum class AppInstallState {
    /** Installed and enabled. */
    INSTALLED,
    /** Installed but disabled (by the user, Able, or the system). */
    DISABLED,
    /** Uninstalled for this user while the APK is kept on the device (restorable). */
    UNINSTALLED_FOR_USER,
    /** Archived by Android 15+ (APK removed, data and icon kept). */
    ARCHIVED_BY_SYSTEM,
    /** Not reported by PackageManager any more; known only from the Able archive. */
    NOT_FOUND,
}

/** Package as resolved from Android (APP-001). */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val isInstalledForUser: Boolean,
    val isArchivedBySystem: Boolean,
    val hasLauncherEntry: Boolean,
    val firstInstallTimeMs: Long,
    val lastUpdateTimeMs: Long,
    /** Linux UID for the current user (`ApplicationInfo.uid`); -1 when unknown. */
    val uid: Int = -1,
) {
    val state: AppInstallState
        get() = when {
            isArchivedBySystem -> AppInstallState.ARCHIVED_BY_SYSTEM
            !isInstalledForUser -> AppInstallState.UNINSTALLED_FOR_USER
            !isEnabled -> AppInstallState.DISABLED
            else -> AppInstallState.INSTALLED
        }
}

/** Foreground usage reported by UsageStatsManager. These are Android estimates, not drain figures. */
data class AppUsageSummary(
    val foregroundTimeMs: Long,
    val lastTimeUsedMs: Long?,
    val windowDays: Int,
)

/**
 * Feature-local catalog of every package visible to Able (QUERY_ALL_PACKAGES, owner decision),
 * including disabled packages, packages uninstalled for the current user with the APK kept and,
 * on Android 15+, archived packages. All PackageManager work runs off the main thread; labels and
 * icons are cached.
 */
class InstalledAppCatalog(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val iconCache = LruCache<String, ImageBitmap>(ICON_CACHE_SIZE)
    private val labelCache = ConcurrentHashMap<String, Pair<Long, String>>()

    /** Android user id of the current process (`uid / PER_USER_RANGE`, see `UserHandle.getUserId`). */
    val currentUserId: Int get() = Process.myUid() / PER_USER_RANGE

    val ownPackageName: String get() = context.packageName

    private val queryFlags: Long
        get() {
            var flags = PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong() or
                PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                flags = flags or PackageManager.MATCH_ARCHIVED_PACKAGES
            }
            return flags
        }

    suspend fun loadApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packages = packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(queryFlags))
        val launcherPackages = runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            packageManager
                .queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
                )
                .mapNotNullTo(HashSet()) { it.activityInfo?.packageName }
        }.getOrDefault(emptySet())

        // Labels are the slow part (resources are loaded per package): resolve in parallel chunks.
        val resolved = coroutineScope {
            packages.chunked(RESOLVE_CHUNK)
                .map { chunk -> async { chunk.mapNotNull { runCatching { resolve(it, launcherPackages) }.getOrNull() } } }
                .awaitAll()
                .flatten()
        }
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        resolved.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun resolve(info: PackageInfo, launcherPackages: Set<String>): InstalledApp? {
        val appInfo = info.applicationInfo ?: return null
        val packageName = info.packageName
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        val archived = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && appInfo.isArchived
        return InstalledApp(
            packageName = packageName,
            label = label(packageName, info.lastUpdateTime, appInfo),
            versionName = info.versionName,
            versionCode = info.longVersionCode,
            isSystem = appInfo.flags and systemFlags != 0,
            isEnabled = appInfo.enabled,
            isInstalledForUser = appInfo.flags and ApplicationInfo.FLAG_INSTALLED != 0,
            isArchivedBySystem = archived,
            hasLauncherEntry = packageName in launcherPackages,
            firstInstallTimeMs = info.firstInstallTime,
            lastUpdateTimeMs = info.lastUpdateTime,
            uid = appInfo.uid,
        )
    }

    private fun label(packageName: String, lastUpdate: Long, appInfo: ApplicationInfo): String {
        labelCache[packageName]?.let { (stamp, label) -> if (stamp == lastUpdate) return label }
        val label = runCatching { appInfo.loadLabel(packageManager).toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: packageName
        labelCache[packageName] = lastUpdate to label
        return label
    }

    /**
     * Reads the current state of [packageName] for the current user. Returns null when
     * PackageManager does not know the package at all (fully removed).
     */
    suspend fun readState(packageName: String): PackageStateSnapshot? = withContext(Dispatchers.IO) {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(queryFlags))
            PackageStateSnapshot(
                installedForUser = appInfo.flags and ApplicationInfo.FLAG_INSTALLED != 0,
                enabled = appInfo.enabled,
                archivedBySystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && appInfo.isArchived,
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** Returns the app icon, or null when Android cannot provide it (the UI shows a placeholder). */
    suspend fun loadIcon(packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(queryFlags))
                packageManager.getApplicationIcon(appInfo)
                    .toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
                    .asImageBitmap()
            }.getOrNull()?.also { iconCache.put(packageName, it) }
        }
    }

    /**
     * Aggregated foreground time over the last [windowDays] days. Returns null when Android
     * reports nothing for the package (for example without Usage access).
     */
    suspend fun usageSummary(packageName: String, windowDays: Int = USAGE_WINDOW_DAYS): AppUsageSummary? =
        withContext(Dispatchers.IO) {
            runCatching {
                val manager = context.getSystemService(UsageStatsManager::class.java) ?: return@runCatching null
                val end = System.currentTimeMillis()
                val begin = end - windowDays * 24L * 60 * 60 * 1000
                val stats = manager.queryAndAggregateUsageStats(begin, end)[packageName] ?: return@runCatching null
                AppUsageSummary(
                    foregroundTimeMs = stats.totalTimeInForeground,
                    lastTimeUsedMs = stats.lastTimeUsed.takeIf { it > 0 },
                    windowDays = windowDays,
                )
            }.getOrNull()
        }

    private companion object {
        const val ICON_CACHE_SIZE = 256
        const val ICON_SIZE_PX = 144
        const val USAGE_WINDOW_DAYS = 7
        const val RESOLVE_CHUNK = 48
        const val PER_USER_RANGE = 100_000
    }
}
