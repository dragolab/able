package com.dragolabs.able.lock

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.dragolabs.able.applock.ForegroundPackages
import com.dragolabs.able.feature.tools.lock.HomeDoubleTapStatus
import com.dragolabs.able.feature.tools.lock.HomeTapAttempt
import com.dragolabs.able.feature.tools.lock.LockGestureSettings

/**
 * Home-screen double tap (LOCK-001), modelled on how "Double Tap Screen Off" (jamworks) does it
 * (decompiled reference build v1.22 analysed 2026-11-23): Able never holds, replays or delays a
 * touch, so the launcher's page scrolling is untouched.
 *
 * - While the feature is on and the screen is on and unlocked, a 1×1 px transparent
 *   `TYPE_ACCESSIBILITY_OVERLAY` with `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL |
 *   FLAG_WATCH_OUTSIDE_TOUCH` stays attached. Android delivers one `ACTION_OUTSIDE` per touch that
 *   starts outside it ("only the location of the first down as an ACTION_OUTSIDE",
 *   WindowManager.LayoutParams#FLAG_WATCH_OUTSIDE_TOUCH) and the touch itself still goes to the
 *   app below. Android withholds the position of touches on other apps' windows, so only the
 *   timing is used. The watcher no longer depends on window-change events to know whether the
 *   launcher is in front (those were missed when returning home with a gesture, which is why
 *   the gesture used to work only sometimes).
 * - [HomeTapClassifier] pairs the touch-downs and rejects pairs where the launcher reported a
 *   click, long click or scroll.
 * - At decision time Able checks live, from the window list, that the default launcher is the
 *   active app window, that its home pages (`id/workspace` on Launcher3-based launchers such as
 *   Pixel Launcher) are visible and the app list is not, and that SystemUI shows no shade.
 *   Outside the launcher nothing happens.
 */
internal class HomeDoubleTapController(
    private val service: AccessibilityService,
    private val settings: LockGestureSettings,
    private val packages: ForegroundPackages,
    private val onDoubleTap: (View?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val keyguard = service.getSystemService(KeyguardManager::class.java)
    private val power = service.getSystemService(PowerManager::class.java)
    private val classifier = HomeTapClassifier()

    private var enabled = false
    private var timeoutMs = LockGestureSettings.DEFAULT_TIMEOUT_MS
    private var view: View? = null

    private val decideRunnable = Runnable { decide() }

    /** True while the feature wants the launcher's click/scroll events. */
    val wantsInteractionEvents: Boolean get() = enabled

    val attachedView: View? get() = view

    fun reload() {
        val config = settings.read()
        val wasEnabled = enabled
        enabled = config.homeEnabled
        if (enabled && !wasEnabled) settings.reportHomeStatus(HomeDoubleTapStatus.UNKNOWN)
        timeoutMs = config.timeoutMs
        if (enabled && packages.launcherPackage == null) packages.refresh()
        if (enabled && packages.launcherPackage == null) settings.reportHomeStatus(HomeDoubleTapStatus.NO_LAUNCHER)
        update()
    }

    /**
     * The foreground app changed. Nothing to do: the launcher is checked live at decision time,
     * and resetting here cancelled real double taps when SystemUI briefly reported a window.
     */
    @Suppress("UNUSED_PARAMETER")
    fun onForeground(pkg: String) = Unit

    /** Launcher click, long click or scroll: the touches were not on empty space. */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled || view == null) return
        if (event.packageName?.toString() != packages.launcherPackage) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            -> classifier.onLauncherClick(event.eventTime)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> classifier.onLauncherScroll(event.eventTime)
        }
    }

    fun onScreenStateChanged() {
        classifier.reset()
        handler.removeCallbacks(decideRunnable)
        update()
    }

    fun stop() {
        enabled = false
        update()
        handler.removeCallbacksAndMessages(null)
    }

    private fun screenUsable(): Boolean = power?.isInteractive == true && keyguard?.isKeyguardLocked != true

    private fun update() {
        if (enabled && screenUsable()) {
            if (view == null) {
                val added = add()
                settings.reportHomeStatus(if (added) HomeDoubleTapStatus.WATCHING else HomeDoubleTapStatus.FAILED)
            }
        } else {
            handler.removeCallbacks(decideRunnable)
            view?.let { runCatching { windowManager?.removeView(it) } }
            view = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun add(): Boolean {
        val wm = windowManager ?: return false
        val watcher = View(service).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) onOutsideTouch(event.eventTime)
                false
            }
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        return runCatching {
            wm.addView(watcher, params)
            view = watcher
            true
        }.getOrDefault(false)
    }

    private fun onOutsideTouch(eventTimeMs: Long) {
        val decideAt = classifier.onTouchDown(eventTimeMs, timeoutMs)
        if (decideAt == null) {
            val slow = classifier.lastSlowGapMs
            if (slow > 0 && launcherInFrontQuick()) report(HomeTapAttempt.TOO_SLOW, slow)
            return
        }
        handler.removeCallbacks(decideRunnable)
        handler.postAtTime(decideRunnable, decideAt.coerceAtLeast(SystemClock.uptimeMillis()))
    }

    private fun decide() {
        if (!enabled || !screenUsable()) return
        val gap = classifier.lastPairGapMs
        val decision = classifier.decideDetailed()
        if (decision == HomeTapClassifier.Decision.NONE) return
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        val home = launcherHomeVisible(windows)
        // Pairs outside the launcher are none of Able's business: not reported.
        if (home == HomeCheck.OTHER_APP) return
        when {
            decision == HomeTapClassifier.Decision.LAUNCHER_INTERACTION -> report(HomeTapAttempt.ICON_OR_SWIPE, gap)
            home == HomeCheck.NOT_HOME_PAGES -> report(HomeTapAttempt.NOT_HOME_PAGES, gap)
            runCatching { SystemUiLockState.read(windows, keyguardLocked = false) }
                .getOrDefault(SystemUiLockState.Scene.UNKNOWN) == SystemUiLockState.Scene.PANEL_OR_BOUNCER ->
                report(HomeTapAttempt.SHADE_OPEN, gap)
            else -> {
                report(HomeTapAttempt.LOCKED, gap)
                onDoubleTap(view)
            }
        }
    }

    private fun report(attempt: HomeTapAttempt, gapMs: Long) {
        Log.i(TAG, "home double tap: $attempt gap=${gapMs}ms timeout=${timeoutMs}ms")
        settings.reportHomeAttempt(attempt, gapMs.toInt())
    }

    /** Cheap check used only to decide whether a slow pair is worth reporting. */
    private fun launcherInFrontQuick(): Boolean {
        val launcher = packages.launcherPackage ?: return false
        return runCatching { service.rootInActiveWindow?.packageName?.toString() == launcher }.getOrDefault(false)
    }

    private enum class HomeCheck { HOME_PAGES, NOT_HOME_PAGES, OTHER_APP }

    /**
     * True when the default launcher is the active application window and shows its home pages.
     * Launchers without the Launcher3 view IDs are accepted on the package check alone.
     */
    private fun launcherHomeVisible(windows: List<AccessibilityWindowInfo>): HomeCheck {
        val launcher = packages.launcherPackage ?: return HomeCheck.OTHER_APP
        val candidates = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val app = candidates.firstOrNull { it.isActive || it.isFocused } ?: candidates.firstOrNull()
        val root = runCatching { app?.root }.getOrNull()
            ?: runCatching { service.rootInActiveWindow }.getOrNull()
            ?: return HomeCheck.OTHER_APP
        if (root.packageName?.toString() != launcher) return HomeCheck.OTHER_APP
        if (anyVisible(root, "$launcher:id/apps_view")) return HomeCheck.NOT_HOME_PAGES
        val workspace = root.findAccessibilityNodeInfosByViewId("$launcher:id/workspace").orEmpty()
        return if (workspace.isEmpty() || workspace.any(AccessibilityNodeInfo::isVisibleToUser)) {
            HomeCheck.HOME_PAGES
        } else {
            HomeCheck.NOT_HOME_PAGES
        }
    }

    private fun anyVisible(root: AccessibilityNodeInfo, id: String): Boolean =
        root.findAccessibilityNodeInfosByViewId(id).orEmpty().any(AccessibilityNodeInfo::isVisibleToUser)

    private companion object {
        const val TAG = "AbleHomeTap"
    }
}
