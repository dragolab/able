package com.dragolabs.able.applock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Classifies `TYPE_WINDOW_STATE_CHANGED` events into "which app is in the foreground", shared by
 * app lock and the home-screen double tap. Only the package and class names carried by the
 * event are used; window content is not read.
 *
 * Package lookups are cached and refreshed with [refresh] (service connect, screen on), so a
 * window event costs a few set lookups. Package visibility: Able declares `QUERY_ALL_PACKAGES`
 * (from `:feature:apps`) and a launcher `<queries>` entry, so the default home app resolves.
 */
class ForegroundPackages(private val context: Context) {
    private val pm: PackageManager = context.packageManager
    private val launchableCache = HashMap<String, Boolean>()

    var launcherPackage: String? = null
        private set
    private var imePackages: Set<String> = emptySet()

    fun refresh() {
        launchableCache.clear()
        launcherPackage = runCatching {
            pm.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )?.activityInfo?.packageName
            // "android" means the chooser (no default home app): treat as unknown.
        }.getOrNull()?.takeIf { it != "android" }
        imePackages = runCatching {
            context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }?.toSet()
        }.getOrNull().orEmpty()
    }

    /**
     * The package of a foreground window change, or null for windows that never count as an app
     * (SystemUI, keyboards, the framework, Able's own lock prompt).
     */
    fun foregroundPackage(event: AccessibilityEvent): String? {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return null
        val pkg = event.packageName?.toString() ?: return null
        if (pkg == SYSTEM_UI || pkg == FRAMEWORK || pkg in imePackages) return null
        if (pkg == context.packageName) {
            // Able's own overlays (lock gestures, home watcher) also emit window-state events with
            // a framework View class name (seen on API 36 emulator, 2026-11-22). Only Able's own
            // activities count as Able in the foreground, and never the lock prompt itself.
            val cls = event.className?.toString() ?: return null
            if (!cls.startsWith(context.packageName) || cls == AppLockActivity::class.java.name) return null
        }
        return pkg
    }

    fun isLauncher(pkg: String): Boolean = pkg == launcherPackage

    /** Launcher and launchable apps end the current app's session; helper dialogs do not. */
    fun leavesPrevious(pkg: String): Boolean = isLauncher(pkg) || launchableCache.getOrPut(pkg) {
        runCatching { pm.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)
    }

    companion object {
        const val SYSTEM_UI = "com.android.systemui"
        const val FRAMEWORK = "android"
    }
}
