package com.dragolabs.able.feature.battery.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app that can receive a profile rule. */
data class RuleAppEntry(val packageName: String, val label: String)

/**
 * Apps offered in the profile editor: launcher apps (Able declares `QUERY_ALL_PACKAGES`) plus any
 * package that already has a rule, so an existing rule is never hidden. The `:feature:apps`
 * catalog is not reachable from this module (features do not depend on each other).
 */
class LauncherAppsSource(private val context: Context) {
    suspend fun apps(extraPackages: Collection<String> = emptyList()): List<RuleAppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val labels = linkedMapOf<String, String>()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        runCatching { pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0)) }
            .getOrDefault(emptyList())
            .forEach { info ->
                val pkg = info.activityInfo?.packageName ?: return@forEach
                if (pkg != context.packageName && pkg !in labels) {
                    labels[pkg] = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
                }
            }
        extraPackages.forEach { pkg ->
            if (pkg !in labels) {
                labels[pkg] = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
                }.getOrDefault(pkg)
            }
        }
        labels.map { (pkg, label) -> RuleAppEntry(pkg, label) }.sortedBy { it.label.lowercase() }
    }
}
