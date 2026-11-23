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
import com.dragolabs.able.applock.ForegroundPackages
import com.dragolabs.able.feature.tools.lock.HomeDoubleTapMode
import com.dragolabs.able.feature.tools.lock.HomeDoubleTapStatus
import com.dragolabs.able.feature.tools.lock.LockGestureSettings

/**
 * Light home-screen double tap (LOCK-001, `home_mode = light`): the fallback to
 * [HomePreciseController]. It never holds a touch, but it only learns when touches start.
 *
 * Why not `onMotionEvent`: `AccessibilityServiceInfo.setMotionEventSources(SOURCE_TOUCHSCREEN)`
 * (API 34) *consumes* the events ("MotionEvents from sources in getMotionEventSources() are not
 * sent to the rest of the system", AccessibilityService#onMotionEvent reference, checked
 * 2026-11-22), so the launcher would stop responding. The non-consuming variant
 * `setObservedMotionEventSources` is `@hide @TestApi` behind the `motion_event_observing` flag
 * (AOSP main, AccessibilityServiceInfo.java, checked 2026-11-22) and is not available to apps.
 *
 * What is used instead, all public API:
 * - while the default launcher is the foreground app (and the setting is on, the screen is on
 *   and unlocked), a 1×1 px transparent `TYPE_ACCESSIBILITY_OVERLAY` with
 *   `FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH`. Android delivers one `ACTION_OUTSIDE`
 *   per touch that starts outside it ("only the location of the first down as an
 *   ACTION_OUTSIDE", WindowManager.LayoutParams#FLAG_WATCH_OUTSIDE_TOUCH, checked 2026-11-22)
 *   and the touch itself still reaches the launcher (verified on the API 36 emulator with
 *   Pixel Launcher, 2026-11-22). Android withholds the position of touches on other apps'
 *   windows (AOSP InputDispatcher zeroes outside-target coordinates across UIDs), so Able uses
 *   only the timing;
 * - "empty space" is inferred by [HomeTapClassifier]: the launcher's click, long-click, scroll
 *   and window events (requested only while this feature is on) cancel the pair;
 * - before locking, SystemUI must not be showing the shade ([SystemUiLockState]).
 * The overlay is removed whenever the launcher is not in front, so nothing runs in other apps.
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
    private var launcherInFront = false
    private var view: View? = null

    private val decideRunnable = Runnable { decide() }

    /** True while the feature wants the launcher's click/scroll events. */
    val wantsInteractionEvents: Boolean get() = enabled

    val attachedView: View? get() = view

    fun reload() {
        val config = settings.read()
        val wasEnabled = enabled
        enabled = config.homeEnabled && config.homeMode == HomeDoubleTapMode.LIGHT
        if (enabled && !wasEnabled) settings.reportHomeStatus(HomeDoubleTapStatus.UNKNOWN)
        timeoutMs = config.timeoutMs
        if (enabled && packages.launcherPackage == null) packages.refresh()
        if (enabled && packages.launcherPackage == null) settings.reportHomeStatus(HomeDoubleTapStatus.NO_LAUNCHER)
        if (enabled && !launcherInFront) {
            // Initial state: the launcher may already be in front when the option is switched on.
            val pkg = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull()
            launcherInFront = pkg != null && packages.isLauncher(pkg)
        }
        update()
    }

    fun onForeground(pkg: String) {
        val inFront = packages.isLauncher(pkg)
        if (inFront != launcherInFront) {
            launcherInFront = inFront
            classifier.reset()
            update()
        }
    }

    /** Launcher click, long click, scroll or window change: the touches were not on empty space. */
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled || view == null) return
        if (event.packageName?.toString() != packages.launcherPackage) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
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

    private fun shouldWatch(): Boolean = enabled && launcherInFront &&
        power?.isInteractive == true && keyguard?.isKeyguardLocked != true

    private fun update() {
        if (shouldWatch()) {
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
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
        handler.removeCallbacks(decideRunnable)
        if (decideAt != null) handler.postAtTime(decideRunnable, decideAt.coerceAtLeast(SystemClock.uptimeMillis()))
    }

    private fun decide() {
        if (!shouldWatch()) return
        if (!classifier.decide()) return
        val scene = runCatching { SystemUiLockState.read(service.windows, keyguardLocked = false) }
            .getOrDefault(SystemUiLockState.Scene.UNKNOWN)
        if (scene == SystemUiLockState.Scene.PANEL_OR_BOUNCER) return
        onDoubleTap(view)
    }
}
