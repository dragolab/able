package com.dragolabs.able.lock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.dragolabs.able.feature.tools.lock.LockGestureConfig
import com.dragolabs.able.feature.tools.lock.LockGestureSettings
import com.dragolabs.able.feature.tools.lock.LockScreenRecognition
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One transparent `TYPE_ACCESSIBILITY_OVERLAY` window used for double tap to lock (LOCK-001):
 *
 * - **Lock screen:** while the keyguard is locked and its content is recognised, the overlay
 *   covers the upper part of the screen (clock and notifications, not the fingerprint area or
 *   bottom shortcuts). Taps on clickable SystemUI nodes are forwarded with `ACTION_CLICK`;
 *   swipes and long presses are replayed with `dispatchGesture` after the overlay is made
 *   untouchable, so unlocking and the shade keep working.
 * - **Status bar (optional):** while unlocked, a strip of status-bar height; a swipe down opens
 *   the notification shade. Untouchable in fullscreen apps.
 * - In every other state (shade, bouncer/PIN, unrecognised tree, screen off) the overlay is
 *   `FLAG_NOT_TOUCHABLE`, so it never blocks the system.
 *
 * Approach adapted from TapLock's TapLockAccessibilityService (Apache-2.0,
 * github.com/modelorona/TapLock). Window content is inspected only to classify the SystemUI
 * scene; nothing is stored or sent apart from the "lock screen recognised" flag in
 * [LockGestureSettings].
 */
internal class LockGestureOverlay(
    private val service: AccessibilityService,
    private val settings: LockGestureSettings,
    private val onDoubleTap: (View) -> Unit,
) {
    private enum class Mode { HIDDEN, LOCK_SCREEN, STATUS_BAR }

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val keyguard = service.getSystemService(KeyguardManager::class.java)
    private val power = service.getSystemService(PowerManager::class.java)
    private val detector = DoubleTapDetector()
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
    private val longPressMs = ViewConfiguration.getLongPressTimeout()

    private var view: View? = null
    private var mode = Mode.HIDDEN
    private var config = LockGestureConfig()
    private var gestureInProgress = false
    private var forwarding = false
    private var lockSessionRecognised = false
    private var wasShowingKeyguard = false

    private val refreshRunnable = Runnable { refresh() }
    private val recognitionCheck = Runnable { checkRecognition() }

    /** Debounced refresh for bursts of SystemUI content changes. */
    fun scheduleRefresh(delayMs: Long = REFRESH_DEBOUNCE_MS) {
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, delayMs)
    }

    fun refresh() {
        config = settings.read()
        if (!config.lockScreenEnabled && !config.statusBarEnabled) {
            remove()
            return
        }
        val overlay = view ?: add() ?: return

        val locked = keyguard?.isKeyguardLocked == true
        val interactive = power?.isInteractive == true
        val showingKeyguard = locked && interactive
        if (showingKeyguard != wasShowingKeyguard) {
            // A new lock-screen "session" starts each time the keyguard becomes visible.
            wasShowingKeyguard = showingKeyguard
            lockSessionRecognised = false
            detector.reset()
            handler.removeCallbacks(recognitionCheck)
            if (showingKeyguard && config.lockScreenEnabled) handler.postDelayed(recognitionCheck, RECOGNITION_GRACE_MS)
        }

        val newMode = when {
            locked && config.lockScreenEnabled -> Mode.LOCK_SCREEN
            !locked && config.statusBarEnabled -> Mode.STATUS_BAR
            else -> Mode.HIDDEN
        }
        val scene = if (newMode == Mode.HIDDEN || !interactive) {
            SystemUiLockState.Scene.UNKNOWN
        } else {
            runCatching { SystemUiLockState.read(service.windows, locked) }.getOrDefault(SystemUiLockState.Scene.UNKNOWN)
        }
        if (newMode == Mode.LOCK_SCREEN && scene == SystemUiLockState.Scene.LOCK_SCREEN) {
            lockSessionRecognised = true
            settings.reportRecognition(LockScreenRecognition.RECOGNIZED)
        }

        val touchable = interactive && !forwarding && when (newMode) {
            Mode.LOCK_SCREEN -> scene == SystemUiLockState.Scene.LOCK_SCREEN
            Mode.STATUS_BAR -> scene == SystemUiLockState.Scene.NONE && isStatusBarVisible()
            Mode.HIDDEN -> false
        }
        if (newMode != mode) {
            mode = newMode
            detector.reset()
        }
        // Revoking touchability mid-drag would cancel the user's gesture (e.g. opening the shade).
        if (gestureInProgress) return
        applyLayout(overlay, frameFor(newMode), touchable)
    }

    fun remove() {
        handler.removeCallbacks(refreshRunnable)
        handler.removeCallbacks(recognitionCheck)
        gestureInProgress = false
        forwarding = false
        mode = Mode.HIDDEN
        view?.let { runCatching { windowManager?.removeView(it) } }
        view = null
    }

    /** The attached overlay view, used for haptic feedback without the VIBRATE permission. */
    val attachedView: View? get() = view

    private fun checkRecognition() {
        val stillLocked = keyguard?.isKeyguardLocked == true
        if (stillLocked && power?.isInteractive == true && !lockSessionRecognised && config.lockScreenEnabled) {
            settings.reportRecognition(LockScreenRecognition.NOT_RECOGNIZED)
        }
    }

    private data class Frame(val width: Int, val height: Int, val y: Int)

    private fun frameFor(mode: Mode): Frame {
        val statusBar = statusBarHeight()
        return when (mode) {
            Mode.LOCK_SCREEN -> {
                val screenHeight = windowManager?.currentWindowMetrics?.bounds?.height() ?: 0
                Frame(WindowManager.LayoutParams.MATCH_PARENT, (screenHeight * LOCK_ZONE_FRACTION).roundToInt().coerceAtLeast(statusBar), 0)
            }
            Mode.STATUS_BAR, Mode.HIDDEN -> Frame(WindowManager.LayoutParams.MATCH_PARENT, statusBar, 0)
        }
    }

    private fun baseFlags() = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun applyLayout(overlay: View, frame: Frame, touchable: Boolean) {
        val params = overlay.layoutParams as? WindowManager.LayoutParams ?: return
        val flags = if (touchable) baseFlags() else baseFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (params.width == frame.width && params.height == frame.height && params.y == frame.y && params.flags == flags) return
        params.width = frame.width
        params.height = frame.height
        params.y = frame.y
        params.flags = flags
        if (!touchable) detector.reset()
        runCatching { windowManager?.updateViewLayout(overlay, params) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun add(): View? {
        val wm = windowManager ?: return null
        val overlay = View(service).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnTouchListener(TouchHandler())
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            statusBarHeight(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            baseFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        return runCatching {
            wm.addView(overlay, params)
            view = overlay
            overlay
        }.getOrNull()
    }

    private inner class TouchHandler : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var moved = false
        private var pulledShade = false
        private var startMode = Mode.HIDDEN
        private val path = Path()

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureInProgress = true
                    downX = event.rawX
                    downY = event.rawY
                    moved = false
                    pulledShade = false
                    startMode = mode
                    path.reset()
                    path.moveTo(downX, downY)
                }
                MotionEvent.ACTION_MOVE -> {
                    path.lineTo(event.rawX, event.rawY)
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        moved = true
                        detector.reset()
                    }
                    if (startMode == Mode.STATUS_BAR && moved && dy > touchSlop && !pulledShade) {
                        pulledShade = true
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    gestureInProgress = false
                    val duration = event.eventTime - event.downTime
                    val isTap = !moved && duration < longPressMs
                    when (startMode) {
                        Mode.LOCK_SCREEN -> if (isTap) {
                            onLockScreenTap(v, downX, downY, event.eventTime)
                        } else {
                            path.lineTo(event.rawX, event.rawY)
                            forwardGesture(Path(path), duration)
                        }
                        Mode.STATUS_BAR -> if (isTap && detector.onTap(config.timeoutMs, event.eventTime)) onDoubleTap(v)
                        Mode.HIDDEN -> detector.reset()
                    }
                    v.performClick()
                    refresh()
                }
                MotionEvent.ACTION_CANCEL -> {
                    gestureInProgress = false
                    detector.reset()
                    refresh()
                }
            }
            return true
        }
    }

    private fun onLockScreenTap(v: View, x: Float, y: Float, timeMs: Long) {
        val node = runCatching { findClickableNodeAt(x.toInt(), y.toInt()) }.getOrNull()
        if (node != null) {
            detector.reset()
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        if (detector.onTap(config.timeoutMs, timeMs)) onDoubleTap(v)
    }

    private fun findClickableNodeAt(x: Int, y: Int): AccessibilityNodeInfo? {
        val bounds = Rect()
        for (window in service.windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            window.getBoundsInScreen(bounds)
            if (!bounds.contains(x, y)) continue
            val root = window.root ?: continue
            findDeepestClickable(root, x, y, bounds, 0)?.let { return it }
        }
        return null
    }

    private fun findDeepestClickable(node: AccessibilityNodeInfo, x: Int, y: Int, rect: Rect, depth: Int): AccessibilityNodeInfo? {
        if (depth > 40) return null
        node.getBoundsInScreen(rect)
        if (!rect.contains(x, y) || !node.isVisibleToUser) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findDeepestClickable(child, x, y, rect, depth + 1)?.let { return it }
        }
        // Structural nodes are only reported for scene detection; never treat them as controls.
        return node.takeIf { it.isClickable && it.isImportantForAccessibility }
    }

    /** Replays a swipe or long press to SystemUI after removing our touch target. */
    private fun forwardGesture(path: Path, durationMs: Long) {
        val overlay = view ?: return
        forwarding = true
        refresh()
        overlay.postDelayed({
            fun restore() {
                forwarding = false
                if (view === overlay) refresh()
            }
            if (view !== overlay || keyguard?.isKeyguardLocked != true) {
                restore()
                return@postDelayed
            }
            val gesture = runCatching {
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(1, MAX_REPLAY_MS)))
                    .build()
            }.getOrNull()
            val dispatched = gesture != null && service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) = restore()
                    override fun onCancelled(gestureDescription: GestureDescription) = restore()
                },
                null,
            )
            if (!dispatched) restore()
        }, FORWARD_DELAY_MS)
    }

    private fun isStatusBarVisible(): Boolean {
        val windows = runCatching { service.windows }.getOrDefault(emptyList())
        if (windows.isEmpty()) return true
        val rect = Rect()
        return windows.any { window ->
            window.type == AccessibilityWindowInfo.TYPE_SYSTEM &&
                rect.also(window::getBoundsInScreen).let { it.top == 0 && it.height() > 0 }
        }
    }

    private fun statusBarHeight(): Int {
        val top = runCatching {
            windowManager?.currentWindowMetrics?.windowInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
        }.getOrDefault(0)
        return if (top > 0) top else (24 * service.resources.displayMetrics.density).roundToInt()
    }

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 100L
        const val RECOGNITION_GRACE_MS = 2_000L
        const val FORWARD_DELAY_MS = 50L
        const val MAX_REPLAY_MS = 5_000L

        /** Upper two thirds: clock and notifications, away from the fingerprint sensor and shortcuts. */
        const val LOCK_ZONE_FRACTION = 0.66f
    }
}
