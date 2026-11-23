package com.dragolabs.able.lock

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.dragolabs.able.applock.ForegroundPackages
import com.dragolabs.able.feature.tools.lock.HomeDoubleTapStatus
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

    private var foreground: String? = null

    /** The foreground app changed: a pair never spans two apps. */
    fun onForeground(pkg: String) {
        if (pkg == foreground) return
        foreground = pkg
        classifier.reset()
    }

    /** Launcher click, long click or scroll: the touches were not on empty space. */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled || view == null) return
        if (event.packageName?.toString() != packages.launcherPackage) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            -> classifier.onLauncherInteraction(event.eventTime)
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
        val decideAt = classifier.onTouchDown(eventTimeMs, timeoutMs) ?: return
        handler.removeCallbacks(decideRunnable)
        handler.postAtTime(decideRunnable, decideAt.coerceAtLeast(SystemClock.uptimeMillis()))
    }

    private fun decide() {
        if (!enabled || !screenUsable()) return
        if (!classifier.decide()) return
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        if (!launcherHomeVisible(windows)) return
        val scene = runCatching { SystemUiLockState.read(windows, keyguardLocked = false) }
            .getOrDefault(SystemUiLockState.Scene.UNKNOWN)
        if (scene == SystemUiLockState.Scene.PANEL_OR_BOUNCER) return
        onDoubleTap(view)
    }

    /**
     * True when the default launcher is the active application window and shows its home pages.
     * Launchers without the Launcher3 view IDs are accepted on the package check alone.
     */
    private fun launcherHomeVisible(windows: List<AccessibilityWindowInfo>): Boolean {
        val launcher = packages.launcherPackage ?: return false
        val app = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) }
        val root = app?.root ?: runCatching { service.rootInActiveWindow }.getOrNull() ?: return false
        if (root.packageName?.toString() != launcher) return false
        if (anyVisible(root, "$launcher:id/apps_view")) return false
        val workspace = root.findAccessibilityNodeInfosByViewId("$launcher:id/workspace").orEmpty()
        return workspace.isEmpty() || workspace.any(AccessibilityNodeInfo::isVisibleToUser)
    }

    private fun anyVisible(root: AccessibilityNodeInfo, id: String): Boolean =
        root.findAccessibilityNodeInfosByViewId(id).orEmpty().any(AccessibilityNodeInfo::isVisibleToUser)
}
