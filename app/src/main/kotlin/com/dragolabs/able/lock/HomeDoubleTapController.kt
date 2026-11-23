package com.dragolabs.able.lock

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.dragolabs.able.applock.ForegroundPackages
import com.dragolabs.able.feature.tools.lock.HomeDoubleTapStatus
import com.dragolabs.able.feature.tools.lock.HomeTapAttempt
import com.dragolabs.able.feature.tools.lock.LockGestureSettings

/**
 * Home-screen double tap (LOCK-001, ADR-053): the same logic as the reference app the owner
 * provided, "Double Tap Screen Off" (jamworks, decompiled build v1.22: `PixelService`, `d30`,
 * `c30` case 8), which works reliably on the owner's Pixel 8 Pro. Re-implemented, not copied.
 *
 * - While the feature is on, a 1×1 px transparent `TYPE_ACCESSIBILITY_OVERLAY` at the top-left
 *   corner with `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH |
 *   FLAG_HARDWARE_ACCELERATED` stays attached for the whole life of the service, as in the
 *   reference app (it is not removed and re-added on screen or lock changes). Android sends it one
 *   `ACTION_OUTSIDE` per touch-down anywhere else; the touch itself still goes to the app below.
 * - [HomeTapClassifier] applies the reference timing rule and decides at the second touch-down.
 * - Then, as the reference app does at that moment: the phone must be unlocked, no keyboard
 *   window may be on screen, and the *focused* application window must be the default launcher
 *   showing its home pages (its `id/workspace` visible, checked on a refreshed node tree;
 *   launchers without that ID are accepted on the package check alone).
 * - Then the screen is locked (`GLOBAL_ACTION_LOCK_SCREEN`), after the optional haptic.
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
    private val inputMethods = service.getSystemService(InputMethodManager::class.java)
    private val classifier = HomeTapClassifier()

    private var enabled = false
    private var timeoutMs = LockGestureSettings.DEFAULT_TIMEOUT_MS
    private var view: View? = null

    /** The reference app needs no click/scroll events for this; neither does Able any more. */
    val wantsInteractionEvents: Boolean get() = false

    val attachedView: View? get() = view

    fun reload() {
        val config = settings.read()
        val wasEnabled = enabled
        enabled = config.homeEnabled
        if (enabled && !wasEnabled) settings.reportHomeStatus(HomeDoubleTapStatus.UNKNOWN)
        timeoutMs = config.timeoutMs
        if (enabled) packages.refresh()
        if (enabled && packages.launcherPackage == null) settings.reportHomeStatus(HomeDoubleTapStatus.NO_LAUNCHER)
        update()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onForeground(pkg: String) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    fun onScreenStateChanged() {
        classifier.reset()
        // The default launcher can change while the screen is off.
        if (enabled && power?.isInteractive == true) packages.refresh()
        update()
    }

    fun stop() {
        enabled = false
        update()
        handler.removeCallbacksAndMessages(null)
    }

    private fun update() {
        if (enabled) {
            if (view == null) {
                val added = add()
                settings.reportHomeStatus(if (added) HomeDoubleTapStatus.WATCHING else HomeDoubleTapStatus.FAILED)
            }
        } else {
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
        }.onFailure { Log.w(TAG, "watcher not added", it) }.getOrDefault(false)
    }

    private fun onOutsideTouch(eventTimeMs: Long) {
        if (!enabled || power?.isInteractive != true || keyguard?.isKeyguardLocked == true) {
            classifier.reset()
            return
        }
        when (val result = classifier.onTouchDown(eventTimeMs, timeoutMs)) {
            HomeTapClassifier.Result.None -> Unit
            is HomeTapClassifier.Result.TooSlow -> {
                if (focusedLauncherRoot() != null) report(HomeTapAttempt.TOO_SLOW, result.gapMs)
            }
            is HomeTapClassifier.Result.DoubleTap -> onDoubleTapCandidate(result.gapMs)
        }
    }

    private fun onDoubleTapCandidate(gapMs: Long) {
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        if (keyboardShown(windows)) {
            classifier.cancelTrigger()
            return
        }
        val root = focusedLauncherRoot(windows)
        if (root == null) {
            // Another app (or SystemUI) is focused: not Able's business, nothing reported.
            classifier.cancelTrigger()
            return
        }
        if (!homePagesVisible(root)) {
            classifier.cancelTrigger()
            report(HomeTapAttempt.NOT_HOME_PAGES, gapMs)
            return
        }
        report(HomeTapAttempt.LOCKED, gapMs)
        onDoubleTap(view)
    }

    /** Root of the focused application window when it belongs to the default launcher. */
    private fun focusedLauncherRoot(
        windows: List<AccessibilityWindowInfo> = runCatching { service.windows }.getOrNull().orEmpty(),
    ): AccessibilityNodeInfo? {
        val launcher = packages.launcherPackage ?: return null
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION && window.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
            if (!window.isFocused) continue
            val root = runCatching { window.root }.getOrNull() ?: continue
            if (root.packageName?.toString() == launcher) return root
        }
        // Fallback when the window list is not available yet.
        val active = runCatching { service.rootInActiveWindow }.getOrNull() ?: return null
        return active.takeIf { it.packageName?.toString() == launcher }
    }

    /** The reference app's check: the launcher's `id/workspace` is visible to the user. */
    private fun homePagesVisible(root: AccessibilityNodeInfo): Boolean {
        val launcher = packages.launcherPackage ?: return false
        runCatching { root.refresh() }
        val workspace = runCatching { root.findAccessibilityNodeInfosByViewId("$launcher:id/workspace") }
            .getOrNull().orEmpty()
        return workspace.isEmpty() || workspace.any(AccessibilityNodeInfo::isVisibleToUser)
    }

    /** As in the reference app: no double tap while a keyboard window is on screen. */
    private fun keyboardShown(windows: List<AccessibilityWindowInfo>): Boolean {
        if (windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) return true
        val imePackages = runCatching { inputMethods?.enabledInputMethodList?.map { it.packageName }?.toSet() }
            .getOrNull().orEmpty()
        if (imePackages.isEmpty()) return false
        return windows.any { window ->
            runCatching { window.root?.packageName?.toString() }.getOrNull() in imePackages
        }
    }

    private fun report(attempt: HomeTapAttempt, gapMs: Long) {
        Log.i(TAG, "home double tap: $attempt gap=${gapMs}ms timeout=${timeoutMs}ms")
        settings.reportHomeAttempt(attempt, gapMs.toInt())
    }

    private companion object {
        const val TAG = "AbleHomeTap"
    }
}
