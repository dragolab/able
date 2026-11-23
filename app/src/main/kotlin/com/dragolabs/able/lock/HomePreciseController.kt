package com.dragolabs.able.lock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.pm.ApplicationInfo
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.dragolabs.able.applock.ForegroundPackages
import com.dragolabs.able.feature.tools.lock.HomeDoubleTapArea
import com.dragolabs.able.feature.tools.lock.HomeDoubleTapMode
import com.dragolabs.able.feature.tools.lock.HomeDoubleTapStatus
import com.dragolabs.able.feature.tools.lock.LockGestureSettings
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Precise home-screen double tap (LOCK-001, default home mode).
 *
 * While the default launcher shows its plain home pages ([HomeScene.WORKSPACE]: no app drawer,
 * recents, folder, menu, widget sheet, keyboard or notification shade), a transparent
 * `TYPE_ACCESSIBILITY_OVERLAY` covers the screen *minus* the status bar, the gesture-navigation
 * edges and the navigation bar, so system navigation never passes through Able. Accessibility
 * overlays need no `SYSTEM_ALERT_WINDOW`.
 *
 * The overlay receives the touches and [HomeGestureClassifier] decides:
 * - double tap on empty space (checked with real coordinates against the launcher's node tree,
 *   [HomeHitTest.target]) → lock;
 * - tap on an icon, widget or other control → `ACTION_CLICK` on that node right away (falls back
 *   to a replayed tap);
 * - single tap on empty space → replayed tap once the double-tap window has closed;
 * - swipe, long press, drag → the overlay becomes `FLAG_NOT_TOUCHABLE` and the recorded path is
 *   replayed with `dispatchGesture` when the finger lifts (long presses with a hold just past
 *   the long-press timeout, swipes as a fast head plus the real last [SwipeReplayPlan.TAIL_MS]
 *   so the launcher sees the real final velocity).
 *
 * Swipe responsiveness (owner feedback 2026-11-23: page scrolling felt slow):
 * - the overlay becomes untouchable as soon as a touch moves past the slop, so the wait for
 *   Android to apply the flag overlaps the rest of the finger's motion and the replay usually
 *   starts right at finger up;
 * - after a swipe, a [HomeBrowseWindow] keeps the overlay untouchable while the user keeps
 *   touching the home screen (1×1 `FLAG_WATCH_OUTSIDE_TOUCH` watcher: time of each touch down
 *   only) or the launcher reports page scrolls, so the following swipes, taps and long presses
 *   reach the launcher natively; it closes after a short quiet period. A double tap made while
 *   it is open is recognised with the timing-only rule of [HomeTapClassifier] (no position);
 * - with [HomeDoubleTapArea.ABOVE_DOCK] (default) the dock and the search bar are not covered.
 *
 * Robustness: the scene is re-evaluated (throttled) on window changes, launcher/SystemUI content
 * changes, screen on/off and configuration changes; it is re-checked live on a touch down when
 * the last check is older than [STALE_MS]; a watchdog re-checks every [WATCHDOG_MS] while the
 * overlay is touchable. With the feature off, or the launcher not in front, the overlay is
 * removed and nothing runs. With TalkBack (touch exploration) on, nothing is intercepted.
 * Nothing touched or read is stored or sent.
 */
internal class HomePreciseController(
    private val service: AccessibilityService,
    private val settings: LockGestureSettings,
    private val packages: ForegroundPackages,
    private val onDoubleTap: (View?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val keyguard = service.getSystemService(KeyguardManager::class.java)
    private val power = service.getSystemService(PowerManager::class.java)
    private val a11y = service.getSystemService(AccessibilityManager::class.java)
    private val viewConfig = ViewConfiguration.get(service)
    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
    private val classifier = HomeGestureClassifier(
        touchSlopPx = viewConfig.scaledTouchSlop.toFloat(),
        doubleTapSlopPx = viewConfig.scaledDoubleTapSlop.toFloat(),
        longPressMs = longPressMs,
    )
    private val debug = (service.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private var enabled = false
    private var timeoutMs = LockGestureSettings.DEFAULT_TIMEOUT_MS
    private var launcherInFront = false
    private var view: View? = null
    private var scene = HomeScene.UNKNOWN
    private var launcherWindow: AccessibilityWindowInfo? = null
    private var lastEvaluatedMs = 0L
    private var unknownSinceMs = 0L
    private var passThroughGesture = false
    private var ignoringGesture = false
    private var area = HomeDoubleTapArea.ABOVE_DOCK

    /** Top of the launcher dock (hotseat) in screen pixels, or -1 when unknown. */
    private var dockTopPx = -1

    /** The current gesture already made the overlay untouchable (it moved past the slop). */
    private var earlyUntouchable = false

    /** Uptime at which the overlay was last made untouchable / touchable again. */
    private var untouchableSinceMs = 0L
    private var touchableSinceMs = 0L

    /** A touch seen by the watcher just after the overlay was made touchable (see [onOutsideTouch]). */
    private var transitionalDownMs = 0L

    private val browse = HomeBrowseWindow()
    private val browseTaps = HomeTapClassifier(settleMs = BROWSE_TAP_SETTLE_MS)
    private var watcher: View? = null

    private val replayQueue = ArrayDeque<TouchGesture>()
    private var replaying = false
    private var restoreDelayMs = 0L
    private var replayGeneration = 0
    private var inFlight: TouchGesture? = null
    private var inFlightAtMs = 0L
    private var retries = 0

    /** Learned wait between FLAG_NOT_TOUCHABLE and injecting (see [onReplayHitOverlay]). */
    private var armMs = INITIAL_ARM_MS

    private val evaluateRunnable = Runnable { evaluate() }
    private val watchdogRunnable = Runnable { evaluate() }
    private val timeoutRunnable = Runnable { process(classifier.onTimeout(SystemClock.uptimeMillis())) }
    private val browseEndRunnable = Runnable {
        if (debug) Log.d(TAG, "browse window closed")
        evaluate()
    }
    private val browseTapRunnable = Runnable { decideBrowseTap() }
    private val transitionalRunnable = Runnable {
        val downMs = transitionalDownMs
        transitionalDownMs = 0L
        if (downMs != 0L) onNativeTouch(downMs)
    }
    private val touchExplorationListener = AccessibilityManager.TouchExplorationStateChangeListener { requestEvaluate(0) }
    private var listening = false

    val attachedView: View? get() = view

    fun reload() {
        val config = settings.read()
        val wasEnabled = enabled
        enabled = config.homeEnabled && config.homeMode == HomeDoubleTapMode.PRECISE
        timeoutMs = config.timeoutMs
        area = config.homeArea
        if (enabled && !wasEnabled) {
            settings.reportHomeStatus(HomeDoubleTapStatus.UNKNOWN)
            packages.refresh()
            val pkg = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull()
            launcherInFront = pkg != null && packages.isLauncher(pkg)
            if (!launcherInFront) handler.postDelayed(frontCheckRunnable, FRONT_CHECK_THROTTLE_MS)
            if (!listening) listening = a11y?.addTouchExplorationStateChangeListener(touchExplorationListener) == true
        }
        if (enabled && packages.launcherPackage == null) settings.reportHomeStatus(HomeDoubleTapStatus.NO_LAUNCHER)
        if (!enabled && listening) {
            a11y?.removeTouchExplorationStateChangeListener(touchExplorationListener)
            listening = false
        }
        evaluate()
    }

    fun onForeground(pkg: String) {
        if (!enabled) return
        val inFront = packages.isLauncher(pkg)
        if (inFront == launcherInFront) return
        launcherInFront = inFront
        // Leaving the launcher must never leave a touchable overlay over the next app.
        evaluate()
    }

    /** Launcher or SystemUI content changed: re-check soon (throttled), only while home is in front. */
    fun onWindowsMaybeChanged() {
        if (!enabled || !launcherInFront) return
        requestEvaluate(EVALUATE_THROTTLE_MS)
    }

    /**
     * The window list changed (window added, removed or focused). Besides re-checking the scene,
     * this catches the launcher coming back without a window-state event (for example when an
     * app or dialog closes): one active-window lookup, throttled, only while the feature is on.
     */
    fun onWindowListChanged() {
        if (!enabled) return
        if (launcherInFront) {
            requestEvaluate(EVALUATE_THROTTLE_MS)
        } else if (!handler.hasCallbacks(frontCheckRunnable)) {
            handler.postDelayed(frontCheckRunnable, FRONT_CHECK_THROTTLE_MS)
        }
    }

    private val frontCheckRunnable = Runnable { checkLauncherInFront() }

    private fun checkLauncherInFront() {
        if (!enabled || launcherInFront || power?.isInteractive != true) return
        val pkg = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: return
        if (packages.isLauncher(pkg)) {
            launcherInFront = true
            evaluate()
        }
    }

    /** True while the feature wants the launcher's click/scroll events (browse window, fallback). */
    val wantsInteractionEvents: Boolean get() = enabled

    /**
     * Launcher click, long click, scroll, window or content change. Scrolls keep the browse
     * window open; any of them means a touch pair seen while browsing was not a double tap on
     * empty space.
     */
    fun onLauncherEvent(event: AccessibilityEvent) {
        if (!enabled || view == null) return
        if (event.packageName?.toString() != packages.launcherPackage) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                browseTaps.onLauncherInteraction(event.eventTime)
                // Only while still open: a page settling just after the window closed must not
                // make the overlay flip back to untouchable.
                if (browse.isOpen(SystemClock.uptimeMillis())) {
                    browse.onLauncherScroll(event.eventTime)
                    scheduleBrowseEnd()
                }
            }
            // Content changes too: Pixel Launcher reports them every ~0.2 s while a page moves,
            // also when a swipe at the first/last page bounces without scrolling (API 36
            // emulator, 2026-11-23), and not for a tap on empty space.
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> browseTaps.onLauncherInteraction(event.eventTime)
        }
    }

    fun onScreenStateChanged() {
        if (!enabled) return
        closeBrowse()
        classifier.onCancel() // a pending tap on empty space needs no replay across screen off/on
        if (!launcherInFront) handler.postDelayed(frontCheckRunnable, FRONT_CHECK_THROTTLE_MS)
        evaluate()
    }

    fun onConfigurationChanged() {
        if (enabled) requestEvaluate(0)
    }

    fun stop() {
        enabled = false
        replayQueue.clear()
        replaying = false
        evaluate()
        handler.removeCallbacksAndMessages(null)
        if (listening) {
            a11y?.removeTouchExplorationStateChangeListener(touchExplorationListener)
            listening = false
        }
    }

    private fun requestEvaluate(delayMs: Long) {
        if (delayMs == 0L) {
            handler.removeCallbacks(evaluateRunnable)
            handler.post(evaluateRunnable)
        } else if (!handler.hasCallbacks(evaluateRunnable)) {
            handler.postDelayed(evaluateRunnable, delayMs)
        }
    }

    private fun shouldAttach(): Boolean = enabled && launcherInFront &&
        power?.isInteractive == true && keyguard?.isKeyguardLocked != true

    private fun evaluate() {
        handler.removeCallbacks(evaluateRunnable)
        handler.removeCallbacks(watchdogRunnable)
        if (!shouldAttach()) {
            detach()
            return
        }
        val overlay = view ?: attach() ?: return
        if (replaying || browse.isOpen(SystemClock.uptimeMillis())) {
            // The overlay is untouchable anyway: do not query the launcher's tree (an IPC served
            // on its UI thread) while it animates a page; the scene is read again afterwards.
            if (!classifier.inGesture) applyLayout(overlay, touchable = false)
            return
        }
        val started = SystemClock.uptimeMillis()
        scene = runCatching { readScene() }.getOrDefault(HomeScene.UNKNOWN)
        lastEvaluatedMs = SystemClock.uptimeMillis()
        if (debug) Log.d(TAG, "evaluate scene=$scene in ${lastEvaluatedMs - started} ms")
        reportScene()
        if (!launcherInFront) {
            detach()
            return
        }
        if (classifier.inGesture) return // never change touchability mid-gesture
        applyLayout(overlay, touchable = isTouchable())
        if (isTouchable()) handler.postDelayed(watchdogRunnable, WATCHDOG_MS)
    }

    private fun isTouchable(): Boolean = scene == HomeScene.WORKSPACE && !replaying &&
        !browse.isOpen(SystemClock.uptimeMillis()) && a11y?.isTouchExplorationEnabled != true

    private fun reportScene() {
        when (scene) {
            HomeScene.WORKSPACE -> {
                unknownSinceMs = 0L
                settings.reportHomeStatus(HomeDoubleTapStatus.PRECISE_READY)
            }
            HomeScene.UNKNOWN -> {
                val now = SystemClock.uptimeMillis()
                if (unknownSinceMs == 0L) {
                    unknownSinceMs = now
                    requestEvaluate(UNRECOGNIZED_AFTER_MS)
                } else if (now - unknownSinceMs >= UNRECOGNIZED_AFTER_MS) {
                    settings.reportHomeStatus(HomeDoubleTapStatus.LAUNCHER_NOT_RECOGNIZED)
                }
            }
            HomeScene.OTHER -> unknownSinceMs = 0L
        }
    }

    /**
     * Reads which launcher surface is showing. Anything other than the launcher's full-screen
     * window on top (another app, a keyboard, a split-screen divider, an expanded SystemUI
     * window such as the shade) is [HomeScene.OTHER].
     */
    private fun readScene(): HomeScene {
        val launcher = packages.launcherPackage ?: return HomeScene.UNKNOWN
        val windows = service.windows.sortedByDescending { it.layer }
        val screen = windowManager?.currentWindowMetrics?.bounds ?: return HomeScene.UNKNOWN
        val rect = Rect()
        var topApp: AccessibilityWindowInfo? = null
        for (window in windows) {
            when (window.type) {
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> Unit
                AccessibilityWindowInfo.TYPE_INPUT_METHOD,
                AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER,
                -> return HomeScene.OTHER
                AccessibilityWindowInfo.TYPE_SYSTEM -> {
                    // Status and navigation bars are thin; the shade, volume panel or a system
                    // dialog cover far more of the screen.
                    window.getBoundsInScreen(rect)
                    if (rect.height() > screen.height() / SYSTEM_WINDOW_MAX_FRACTION && rect.width() > 0) return HomeScene.OTHER
                }
                AccessibilityWindowInfo.TYPE_APPLICATION -> if (topApp == null) topApp = window
            }
        }
        val app = topApp ?: return HomeScene.UNKNOWN
        val root = app.root ?: return HomeScene.UNKNOWN
        if (root.packageName?.toString() != launcher) {
            launcherInFront = false
            return HomeScene.OTHER
        }
        launcherWindow = app
        val dragLayer = root.findAccessibilityNodeInfosByViewId("$launcher:id/drag_layer").firstOrNull()
            ?: return HomeScene.UNKNOWN
        val ids = ArrayList<String?>(dragLayer.childCount)
        var dockTop = -1
        for (i in 0 until dragLayer.childCount) {
            val child = dragLayer.getChild(i) ?: continue
            if (!child.isVisibleToUser) continue
            val id = child.viewIdResourceName
            ids += id
            if (id != null && id.endsWith(":id/hotseat")) {
                child.getBoundsInScreen(rect)
                if (rect.height() > 0) dockTop = rect.top
            }
        }
        val result = HomeHitTest.scene(dragLayerFound = true, dragLayerChildIds = ids)
        if (result == HomeScene.WORKSPACE) dockTopPx = dockTop
        return result
    }

    private fun targetAt(x: Float, y: Float): HomeTarget {
        if (passThroughGesture) return HomeTarget.UNKNOWN
        val started = SystemClock.uptimeMillis()
        val root = runCatching { launcherWindow?.root }.getOrNull() ?: return HomeTarget.UNKNOWN
        val target = runCatching { HomeHitTest.target(NodeAdapter(root), x.toInt(), y.toInt()) }.getOrDefault(HomeTarget.UNKNOWN)
        if (debug) Log.d(TAG, "target($x,$y)=$target in ${SystemClock.uptimeMillis() - started} ms")
        return target
    }

    // ---- Overlay window ----

    private data class Frame(val x: Int, val y: Int, val width: Int, val height: Int)

    /** Full screen minus status bar, display cutout, gesture-navigation edges and nav bar. */
    private fun frame(): Frame {
        val metrics = windowManager?.currentWindowMetrics
        val bounds = metrics?.bounds ?: Rect(0, 0, service.resources.displayMetrics.widthPixels, service.resources.displayMetrics.heightPixels)
        val insets = metrics?.windowInsets
        fun of(type: Int) = insets?.getInsets(type) ?: android.graphics.Insets.NONE
        val gestures = of(WindowInsets.Type.systemGestures())
        val mandatory = of(WindowInsets.Type.mandatorySystemGestures())
        val bars = of(WindowInsets.Type.systemBars())
        val cutout = of(WindowInsets.Type.displayCutout())
        val fallback = (FALLBACK_EDGE_DP * service.resources.displayMetrics.density).toInt()
        val top = maxOf(bars.top, mandatory.top, cutout.top, fallback)
        val bottom = maxOf(bars.bottom, mandatory.bottom, gestures.bottom, cutout.bottom, fallback)
        val left = maxOf(gestures.left, bars.left, cutout.left)
        val right = maxOf(gestures.right, bars.right, cutout.right)
        val bottomEdge = HomeHitTest.interceptBottom(
            screenBottom = bounds.height() - bottom,
            dockTop = dockTopPx,
            aboveDock = area == HomeDoubleTapArea.ABOVE_DOCK,
        )
        return Frame(left, top, max(1, bounds.width() - left - right), max(1, bottomEdge - top))
    }

    private fun flags(touchable: Boolean): Int {
        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return if (touchable) base else base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach(): View? {
        val wm = windowManager ?: return null
        val overlay = View(service).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnTouchListener { v, event -> onTouch(v, event) }
        }
        val f = frame()
        val params = WindowManager.LayoutParams(
            f.width,
            f.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags(touchable = false),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = f.x
            y = f.y
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "AbleHomeDoubleTap"
        }
        return runCatching {
            wm.addView(overlay, params)
            view = overlay
            attachWatcher(wm)
            overlay
        }.getOrElse {
            settings.reportHomeStatus(HomeDoubleTapStatus.FAILED)
            null
        }
    }

    /**
     * 1×1 px watcher, as in Light mode ([HomeDoubleTapController]): Android sends it one
     * `ACTION_OUTSIDE` per touch that starts elsewhere (time only; the position of touches on
     * the launcher is withheld), while the touch itself still reaches the launcher. Used only
     * while the overlay is untouchable, to keep the browse window open.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachWatcher(wm: WindowManager) {
        if (watcher != null) return
        val w = View(service).apply {
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
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "AbleHomeDoubleTapWatcher"
        }
        runCatching {
            wm.addView(w, params)
            watcher = w
        }
    }

    private fun detach() {
        handler.removeCallbacks(watchdogRunnable)
        handler.removeCallbacks(timeoutRunnable)
        classifier.onCancel() // a pending first tap on empty space needs no replay once home is gone
        replayQueue.clear()
        replaying = false
        inFlight = null
        armed = null
        handler.removeCallbacks(armRunnable)
        replayGeneration++
        closeBrowse()
        launcherWindow = null
        scene = HomeScene.UNKNOWN
        view?.let { runCatching { windowManager?.removeView(it) } }
        view = null
        watcher?.let { runCatching { windowManager?.removeView(it) } }
        watcher = null
    }

    private fun applyLayout(overlay: View, touchable: Boolean) {
        val params = overlay.layoutParams as? WindowManager.LayoutParams ?: return
        val f = frame()
        val flags = flags(touchable)
        if (params.flags == flags && params.x == f.x && params.y == f.y && params.width == f.width && params.height == f.height) return
        val wasTouchable = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0
        if (!touchable && wasTouchable) untouchableSinceMs = SystemClock.uptimeMillis()
        if (touchable && !wasTouchable) touchableSinceMs = SystemClock.uptimeMillis()
        params.flags = flags
        params.x = f.x
        params.y = f.y
        params.width = f.width
        params.height = f.height
        if (debug) Log.d(TAG, "layout touchable=$touchable frame=$f")
        runCatching { windowManager?.updateViewLayout(overlay, params) }
    }

    private fun overlayTouchable(): Boolean {
        val params = view?.layoutParams as? WindowManager.LayoutParams ?: return false
        return params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0
    }

    // ---- Touches ----

    private fun onTouch(v: View, event: MotionEvent): Boolean {
        val index = event.actionIndex
        val id = event.getPointerId(index)
        // Raw (screen) coordinates of the pointer that changed: the overlay is offset by its frame.
        val dx = event.rawX - event.x
        val dy = event.rawY - event.y
        if (event.actionMasked == MotionEvent.ACTION_DOWN && replaying && inFlight == null) deferArmedReplay()
        if (event.actionMasked == MotionEvent.ACTION_DOWN && replaying) {
            // FLAG_NOT_TOUCHABLE was not applied yet, so this is (almost always) Able's own replay
            // landing on the overlay: never classify it (a gesture could loop); dispatch it again.
            ignoringGesture = true
            onReplayHitOverlay()
        }
        if (ignoringGesture) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) ignoringGesture = false
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(timeoutRunnable)
                if (event.eventTime == transitionalDownMs) {
                    // The watcher's copy of this touch: it did reach the overlay.
                    transitionalDownMs = 0L
                    handler.removeCallbacks(transitionalRunnable)
                }
                passThroughGesture = false
                earlyUntouchable = false
                if (SystemClock.uptimeMillis() - lastEvaluatedMs > STALE_MS) {
                    scene = runCatching { readScene() }.getOrDefault(HomeScene.UNKNOWN)
                    lastEvaluatedMs = SystemClock.uptimeMillis()
                }
                if (scene != HomeScene.WORKSPACE || !launcherInFront) passThroughGesture = true
                process(classifier.onDown(id, event.getX(index) + dx, event.getY(index) + dy, event.eventTime, timeoutMs))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                process(classifier.onDown(id, event.getX(index) + dx, event.getY(index) + dy, event.eventTime, timeoutMs))
                untouchableIfReplaying(v)
            }
            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until event.historySize) {
                    for (p in 0 until event.pointerCount) {
                        classifier.onMove(event.getPointerId(p), event.getHistoricalX(p, h) + dx, event.getHistoricalY(p, h) + dy, event.getHistoricalEventTime(h))
                    }
                }
                for (p in 0 until event.pointerCount) {
                    classifier.onMove(event.getPointerId(p), event.getX(p) + dx, event.getY(p) + dy, event.eventTime)
                }
                untouchableIfReplaying(v)
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val decisions = classifier.onUp(id, event.getX(index) + dx, event.getY(index) + dy, event.eventTime, timeoutMs, ::targetAt)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    v.performClick()
                    process(decisions)
                    completeMixedDoubleTap(decisions)
                    if (!replaying && replayQueue.isNotEmpty()) pump()
                    if (!replaying) evaluate()
                } else {
                    process(decisions)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                process(classifier.onCancel())
                if (!replaying && replayQueue.isNotEmpty()) pump()
                if (!replaying) evaluate()
            }
        }
        return true
    }

    /**
     * The gesture moved past the slop (or became multi-touch): it will be replayed, so stop
     * intercepting already. Android keeps delivering the rest of this gesture to the overlay
     * (touch targets are chosen on the down), and the time until the finger lifts covers the
     * wait for the flag to apply, so the replay can usually start right at finger up.
     */
    private fun untouchableIfReplaying(v: View) {
        if (earlyUntouchable || !classifier.willReplay) return
        earlyUntouchable = true
        applyLayout(v, touchable = false)
    }

    private fun process(decisions: List<HomeDecision>) {
        for (decision in decisions) {
            when (decision) {
                is HomeDecision.Replay -> {
                    if (decision.gesture.kind == TouchGesture.Kind.SWIPE) openBrowse(decision.gesture.upMs)
                    enqueue(decision.gesture)
                }
                HomeDecision.Lock -> lockIfStillHome()
            }
        }
        handler.removeCallbacks(timeoutRunnable)
        classifier.deadlineMs?.let { handler.postAtTime(timeoutRunnable, it) }
    }

    private fun lockIfStillHome() {
        val fresh = runCatching { readScene() }.getOrDefault(HomeScene.UNKNOWN)
        if (debug) Log.d(TAG, "double tap, scene=$fresh")
        if (fresh != HomeScene.WORKSPACE || !shouldAttach()) return
        val systemUi = runCatching { SystemUiLockState.read(service.windows, keyguardLocked = false) }
            .getOrDefault(SystemUiLockState.Scene.UNKNOWN)
        if (systemUi == SystemUiLockState.Scene.PANEL_OR_BOUNCER) return
        closeBrowse()
        onDoubleTap(view)
    }

    // ---- Browse window ----

    private fun openBrowse(upMs: Long) {
        browse.onSwipe(upMs)
        browseTaps.reset()
        scheduleBrowseEnd()
        if (debug) Log.d(TAG, "browse window open until ${browse.untilMs}")
    }

    private fun scheduleBrowseEnd() {
        handler.removeCallbacks(browseEndRunnable)
        if (browse.untilMs > 0L) handler.postAtTime(browseEndRunnable, browse.untilMs)
    }

    private fun closeBrowse() {
        browse.close()
        browseTaps.reset()
        handler.removeCallbacks(browseEndRunnable)
        handler.removeCallbacks(browseTapRunnable)
        handler.removeCallbacks(transitionalRunnable)
        transitionalDownMs = 0L
    }

    /** A touch started somewhere other than the 1×1 watcher (time only). */
    private fun onOutsideTouch(timeMs: Long) {
        if (!enabled || view == null) return
        // Able's own injected replay also starts with a down: ignore the one at dispatch time.
        if (inFlight != null && timeMs - inFlightAtMs in -INJECTED_DOWN_SLACK_MS..INJECTED_DOWN_SLACK_MS) return
        if (overlayTouchable()) {
            // Android applies window changes asynchronously (up to ~0.1 s on the API 36
            // emulator, 2026-11-23): just after the browse window closed a touch may still go to
            // the launcher. If the overlay does not receive it shortly, count it as native.
            if (SystemClock.uptimeMillis() - touchableSinceMs > TOUCHABLE_APPLY_MS) return
            handler.removeCallbacks(transitionalRunnable)
            // An earlier one still waiting never reached the overlay either (a quick second tap).
            if (transitionalDownMs != 0L) onNativeTouch(transitionalDownMs)
            transitionalDownMs = timeMs
            handler.postDelayed(transitionalRunnable, OVERLAY_DOWN_WAIT_MS)
            return
        }
        if (!browse.onTouchDown(timeMs)) return
        scheduleBrowseEnd()
        if (debug) Log.d(TAG, "touch while browsing: window until ${browse.untilMs}")
        onNativeTouch(timeMs)
    }

    /** A touch reached the launcher without Able seeing where: feed the timing-only double tap. */
    private fun onNativeTouch(timeMs: Long) {
        if (debug) Log.d(TAG, "native touch at $timeMs")
        val decideAt = browseTaps.onTouchDown(timeMs, timeoutMs)
        handler.removeCallbacks(browseTapRunnable)
        if (decideAt != null) handler.postAtTime(browseTapRunnable, decideAt.coerceAtLeast(SystemClock.uptimeMillis()))
    }

    /**
     * The first tap of a double tap reached the launcher natively (browse window closing) and
     * the second one was seen by the overlay on empty space: complete the double tap.
     */
    private fun completeMixedDoubleTap(decisions: List<HomeDecision>) {
        if (decisions.any { it == HomeDecision.Lock }) return
        val downMs = classifier.pendingTapDownMs ?: return
        if (!browseTaps.pairsWith(downMs, timeoutMs) || !classifier.consumePendingTap()) return
        handler.removeCallbacks(timeoutRunnable)
        if (debug) Log.d(TAG, "double tap: first tap native, second on the overlay")
        lockIfStillHome()
    }

    /**
     * Two quick touches reached the launcher while browsing and it reported no click, long
     * click, scroll or window change around them: treat them as a double tap on empty space.
     */
    private fun decideBrowseTap() {
        if (!browseTaps.decide()) return
        if (debug) Log.d(TAG, "double tap while browsing (timing only)")
        lockIfStillHome()
    }

    // ---- Replay ----

    private fun enqueue(gesture: TouchGesture) {
        replayQueue.addLast(gesture)
        // Never inject while a finger is down: the next real event would cancel the replay.
        if (!replaying && !classifier.inGesture) pump()
    }

    private fun pump() {
        val gesture = replayQueue.removeFirstOrNull()
        if (gesture == null) {
            replaying = false
            // Let the launcher react (drawer opening, page settling) before taking touches again.
            handler.postDelayed({ if (!replaying) evaluate() }, restoreDelayMs)
            return
        }
        replaying = true
        val started = SystemClock.uptimeMillis()
        if (gesture.kind == TouchGesture.Kind.TAP && gesture.target == HomeTarget.CONTROL && clickAt(gesture.downX, gesture.downY)) {
            if (debug) Log.d(TAG, "replay click in ${SystemClock.uptimeMillis() - started} ms (lag ${started - gesture.upMs} ms)")
            restoreDelayMs = TAP_RESTORE_MS
            pump()
            return
        }
        val overlay = view
        if (overlay == null) {
            replayQueue.clear()
            replaying = false
            return
        }
        applyLayout(overlay, touchable = false)
        restoreDelayMs = if (gesture.kind == TouchGesture.Kind.TAP) TAP_RESTORE_MS else GESTURE_RESTORE_MS
        retries = 0
        armed = gesture
        // The flag may have been set while the finger was still moving: wait only for the rest.
        val wait = armMs - (SystemClock.uptimeMillis() - untouchableSinceMs)
        if (wait <= 0L) armRunnable.run() else handler.postDelayed(armRunnable, wait)
    }

    /** Gesture waiting for FLAG_NOT_TOUCHABLE to apply (not dispatched yet). */
    private var armed: TouchGesture? = null
    private val armRunnable = Runnable {
        val gesture = armed ?: return@Runnable
        armed = null
        view?.let { startDispatch(it, gesture) }
    }

    /**
     * A real touch arrived while a replay was still waiting to be injected (the overlay is not
     * untouchable yet). Put the replay back at the head of the queue and handle the touch
     * normally; the queue resumes when that touch ends, so neither is lost.
     */
    private fun deferArmedReplay(): Boolean {
        val gesture = armed ?: return false
        handler.removeCallbacks(armRunnable)
        armed = null
        replayQueue.addFirst(gesture)
        replaying = false
        if (debug) Log.d(TAG, "real touch during arm: ${gesture.kind} replay deferred")
        return true
    }

    private fun startDispatch(overlay: View, gesture: TouchGesture) {
        if (view !== overlay || !replaying) {
            replaying = false
            return
        }
        val generation = ++replayGeneration
        inFlight = gesture
        inFlightAtMs = SystemClock.uptimeMillis()
        val untouchableFor = inFlightAtMs - untouchableSinceMs
        if (debug) Log.d(TAG, "replay ${gesture.kind} try ${retries + 1} untouchable for $untouchableFor ms, arm $armMs ms (lag ${inFlightAtMs - gesture.upMs} ms)")
        val accepted = dispatch(gesture) {
            if (generation != replayGeneration) return@dispatch // superseded by a retry
            inFlight = null
            // Learn only from replays whose wait was the arm delay itself.
            if (retries == 0 && untouchableFor <= armMs + ARM_DECAY_MS) armMs = (armMs - ARM_DECAY_MS).coerceAtLeast(ARM_MS)
            pump()
        }
        if (!accepted) {
            inFlight = null
            pump()
        }
    }

    /**
     * The injected gesture reached the overlay because Android had not applied
     * `FLAG_NOT_TOUCHABLE` yet (window updates are asynchronous; on the API 36 emulator a 48 ms
     * wait was often too short, 2026-11-22). Re-dispatch it a little later (this cancels the
     * stray copy) and remember a longer arm delay for the next replays.
     */
    private fun onReplayHitOverlay() {
        val gesture = inFlight
        val overlay = view
        if (gesture == null || overlay == null || SystemClock.uptimeMillis() - inFlightAtMs > SELF_HIT_WINDOW_MS) {
            if (debug) Log.d(TAG, "touch while replaying: ignored")
            return
        }
        replayGeneration++ // the stray dispatch's callback must not advance the queue
        armMs = (armMs + ARM_STEP_MS).coerceAtMost(MAX_ARM_MS)
        if (retries >= MAX_RETRIES) {
            if (debug) Log.d(TAG, "replay ${gesture.kind} gave up after $retries retries")
            inFlight = null
            pump()
            return
        }
        retries++
        if (debug) Log.d(TAG, "replay ${gesture.kind} hit the overlay, retry in $armMs ms")
        handler.postDelayed({ startDispatch(overlay, gesture) }, armMs)
    }

    private fun clickAt(x: Float, y: Float): Boolean {
        val root = runCatching { launcherWindow?.root }.getOrNull() ?: return false
        val node = runCatching { HomeHitTest.clickableAt(NodeAdapter(root), x.toInt(), y.toInt()) }.getOrNull() ?: return false
        return runCatching { node.node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
    }

    /** Dispatches [gesture]; [done] runs once on completion or cancellation. False if refused. */
    private fun dispatch(gesture: TouchGesture, done: () -> Unit): Boolean {
        val strokes = when (gesture.kind) {
            TouchGesture.Kind.TAP -> listOf(point(gesture.downX, gesture.downY, 0, TAP_MS, willContinue = false))
            TouchGesture.Kind.LONG_PRESS -> listOf(point(gesture.downX, gesture.downY, 0, longPressMs + LONG_PRESS_EXTRA_MS, willContinue = false))
            TouchGesture.Kind.LONG_PRESS_DRAG -> {
                val hold = point(gesture.downX, gesture.downY, 0, longPressMs + LONG_PRESS_EXTRA_MS, willContinue = true)
                val samples = gesture.strokes.first().samples
                val dragMs = (samples.last().timeMs - samples.first().timeMs - longPressMs).coerceIn(MIN_STROKE_MS, MAX_DRAG_REPLAY_MS)
                return dispatchStrokes(listOf(hold)) { completed ->
                    if (!completed) {
                        done()
                    } else {
                        val drag = runCatching { hold.continueStroke(path(samples), 0, dragMs, false) }.getOrNull()
                        if (drag == null || !dispatchStrokes(listOf(drag)) { done() }) done()
                    }
                }
            }
            TouchGesture.Kind.SWIPE -> {
                if (gesture.strokes.size == 1) return dispatchSwipe(gesture.strokes.single()) { done() }
                val start = gesture.downMs
                val total = (gesture.upMs - start).coerceAtLeast(1)
                val scale = if (total > MAX_SWIPE_REPLAY_MS) MAX_SWIPE_REPLAY_MS.toDouble() / total else 1.0
                gesture.strokes.take(MAX_STROKES).mapNotNull { stroke ->
                    val offset = ((stroke.startMs - start) * scale).roundToLong()
                    val duration = ((stroke.endMs - stroke.startMs) * scale).roundToLong().coerceAtLeast(MIN_STROKE_MS)
                    runCatching { GestureDescription.StrokeDescription(path(stroke.samples), offset, duration) }.getOrNull()
                }
            }
        }
        return dispatchStrokes(strokes) { done() }
    }

    /**
     * One-finger swipe: a fast head stroke continued by the real tail ([SwipeReplayPlan]), so the
     * launcher sees the real final velocity (fling to the next page or settle back).
     */
    private fun dispatchSwipe(stroke: TouchStroke, done: () -> Unit): Boolean {
        val plan = SwipeReplayPlan.of(stroke.samples)
        val head = plan.head
        if (head == null) {
            val single = runCatching { GestureDescription.StrokeDescription(path(plan.tail), 0, plan.tailMs) }.getOrNull() ?: return false
            return dispatchStrokes(listOf(single)) { done() }
        }
        val headPath = path(head)
        val first = runCatching { GestureDescription.StrokeDescription(headPath, 0, plan.headMs, true) }.getOrNull() ?: return false
        // Start the tail exactly where Android's sampling ends the head, or the continuation is refused.
        val end = FloatArray(2)
        val measure = PathMeasure(headPath, false)
        if (!measure.getPosTan(measure.length, end, null)) return false
        val tailPath = Path().apply {
            moveTo(end[0], end[1])
            for (i in 1 until plan.tail.size) lineTo(plan.tail[i].x.coerceAtLeast(0f), plan.tail[i].y.coerceAtLeast(0f))
        }
        return dispatchStrokes(listOf(first)) { completed ->
            if (!completed) {
                done()
            } else {
                val rest = runCatching { first.continueStroke(tailPath, 0, plan.tailMs, false) }.getOrNull()
                if (rest == null || !dispatchStrokes(listOf(rest)) { done() }) done()
            }
        }
    }

    private fun dispatchStrokes(strokes: List<GestureDescription.StrokeDescription>, done: (Boolean) -> Unit): Boolean {
        if (strokes.isEmpty()) return false
        val description = runCatching {
            GestureDescription.Builder().apply { strokes.forEach(::addStroke) }.build()
        }.getOrNull() ?: return false
        return service.dispatchGesture(
            description,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) = done(true)
                override fun onCancelled(gestureDescription: GestureDescription) {
                    if (debug) Log.d(TAG, "replay cancelled")
                    done(false)
                }
            },
            handler,
        )
    }

    private fun point(x: Float, y: Float, start: Long, duration: Long, willContinue: Boolean) =
        GestureDescription.StrokeDescription(Path().apply { moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f)) }, start, duration, willContinue)

    private fun path(samples: List<TouchSample>): Path = Path().apply {
        moveTo(samples.first().x.coerceAtLeast(0f), samples.first().y.coerceAtLeast(0f))
        for (i in 1 until samples.size) lineTo(samples[i].x.coerceAtLeast(0f), samples[i].y.coerceAtLeast(0f))
    }

    /** Lazily reads the parts of an [AccessibilityNodeInfo] used by [HomeHitTest]. */
    private class NodeAdapter(val node: AccessibilityNodeInfo) : HitNode {
        override val bounds: HitBox by lazy {
            val r = Rect()
            node.getBoundsInScreen(r)
            HitBox(r.left, r.top, r.right, r.bottom)
        }
        override val visible: Boolean get() = node.isVisibleToUser
        override val clickable: Boolean get() = node.isClickable
        override val longClickable: Boolean get() = node.isLongClickable
        override val className: String? get() = node.className?.toString()
        override val viewId: String? get() = node.viewIdResourceName
        override val childCount: Int get() = node.childCount
        override fun child(index: Int): HitNode? = node.getChild(index)?.let(::NodeAdapter)
    }

    private companion object {
        const val TAG = "AbleHomePrecise"
        const val EVALUATE_THROTTLE_MS = 120L
        const val FRONT_CHECK_THROTTLE_MS = 250L
        const val WATCHDOG_MS = 2_000L
        const val STALE_MS = 1_000L
        const val UNRECOGNIZED_AFTER_MS = 2_000L

        /**
         * Floor of the wait for the window manager to apply FLAG_NOT_TOUCHABLE before injecting;
         * the controller starts at [INITIAL_ARM_MS] and adapts. An untouchable overlay stays in
         * the Accessibility window list, so there is no "applied" signal to wait for (API 36
         * emulator, 2026-11-22): 48 ms was often too short there, 120 ms always enough.
         */
        const val ARM_MS = 48L
        const val INITIAL_ARM_MS = 112L
        const val ARM_STEP_MS = 32L
        const val ARM_DECAY_MS = 4L
        const val MAX_ARM_MS = 250L
        const val MAX_RETRIES = 4
        const val SELF_HIT_WINDOW_MS = 300L
        const val TAP_MS = 40L
        const val LONG_PRESS_EXTRA_MS = 150L
        const val MIN_STROKE_MS = 16L
        const val MAX_SWIPE_REPLAY_MS = 350L

        /** Settle delay of the timing-only double tap while browsing ([HomeTapClassifier]). */
        const val BROWSE_TAP_SETTLE_MS = 700L

        /** How long after asking for a touchable overlay a touch may still reach the launcher. */
        const val TOUCHABLE_APPLY_MS = 250L

        /** Wait for the overlay's own copy of a watcher touch before counting it as native. */
        const val OVERLAY_DOWN_WAIT_MS = 40L

        /** An outside down this close to a replay's dispatch is the replay itself. */
        const val INJECTED_DOWN_SLACK_MS = 40L
        const val MAX_DRAG_REPLAY_MS = 2_000L
        const val MAX_STROKES = 10
        const val TAP_RESTORE_MS = 60L
        const val GESTURE_RESTORE_MS = 350L
        const val FALLBACK_EDGE_DP = 24

        /** A SystemUI window taller than 1/3 of the screen is a panel or dialog, not a bar. */
        const val SYSTEM_WINDOW_MAX_FRACTION = 3
    }
}
