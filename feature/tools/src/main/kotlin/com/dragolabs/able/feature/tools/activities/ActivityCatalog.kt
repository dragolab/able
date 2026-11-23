package com.dragolabs.able.feature.tools.activities

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class LaunchableActivity(
    val packageName: String,
    val className: String,
    val label: String,
    val appLabel: String,
    /** True for the activity the launcher shows for this app. */
    val isLauncherEntry: Boolean,
) {
    val key: String get() = "$packageName/$className"
    val shortClassName: String get() = className.removePrefix(packageName).ifEmpty { className }
    val component: ComponentName get() = ComponentName(packageName, className)
}

@Immutable
data class AppActivities(
    val packageName: String,
    val appLabel: String,
    val activities: List<LaunchableActivity>,
)

enum class LaunchOutcome { STARTED, NOT_FOUND, DENIED, FAILED }

/**
 * Feature-local catalog of normally launchable activities (ACT-001).
 *
 * Package visibility is limited to apps with a launcher entry (`<queries>` in the app manifest).
 * Only activities that are exported, enabled, and either unprotected or protected by a
 * permission Able holds are listed. Nothing bypasses platform rules.
 */
class ActivityCatalog(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    suspend fun load(): List<AppActivities> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherEntries = pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        val launcherClasses = launcherEntries.map { it.activityInfo.packageName to it.activityInfo.name }.toSet()
        val packages = launcherEntries.map { it.activityInfo.packageName }.distinct().filter { it != context.packageName }

        packages.mapNotNull { packageName ->
            val info = runCatching {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
            }.getOrNull() ?: return@mapNotNull null
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            if (!appInfo.enabled) return@mapNotNull null
            val appLabel = appInfo.loadLabel(pm).toString()
            val activities = info.activities.orEmpty()
                .filter { it.isNormallyLaunchable() }
                .map { activity ->
                    LaunchableActivity(
                        packageName = packageName,
                        className = activity.name,
                        label = activity.loadLabel(pm).toString().ifBlank { activity.name.substringAfterLast('.') },
                        appLabel = appLabel,
                        isLauncherEntry = (packageName to activity.name) in launcherClasses,
                    )
                }
                .sortedWith(compareByDescending<LaunchableActivity> { it.isLauncherEntry }.thenBy { it.label.lowercase() })
            if (activities.isEmpty()) null else AppActivities(packageName, appLabel, activities)
        }.sortedBy { it.appLabel.lowercase() }
    }

    private fun ActivityInfo.isNormallyLaunchable(): Boolean {
        if (!exported || !enabled) return false
        val setting = runCatching { pm.getComponentEnabledSetting(ComponentName(packageName, name)) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        if (setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        ) {
            return false
        }
        val required = permission ?: return true
        return context.checkSelfPermission(required) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun loadIcon(activity: LaunchableActivity, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { pm.getActivityIcon(activity.component).toBitmap(sizePx, sizePx) }.getOrNull()
    }

    fun launchIntent(activity: LaunchableActivity): Intent =
        Intent(Intent.ACTION_MAIN).setComponent(activity.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the activity if Android permits (ACT-002). */
    fun open(activity: LaunchableActivity): LaunchOutcome = try {
        context.startActivity(launchIntent(activity))
        LaunchOutcome.STARTED
    } catch (_: ActivityNotFoundException) {
        LaunchOutcome.NOT_FOUND
    } catch (_: SecurityException) {
        LaunchOutcome.DENIED
    } catch (_: RuntimeException) {
        LaunchOutcome.FAILED
    }

}
