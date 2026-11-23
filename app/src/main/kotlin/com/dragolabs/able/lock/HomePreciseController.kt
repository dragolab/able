package com.dragolabs.able.lock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.pm.ApplicationInfo
import android.graphics.Path
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
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.dragolabs.able.applock.ForegroundPackages
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
 *   replayed with `dispatchGesture` (long presses are replayed with a hold just past the
 *   long-press timeout, swipes at most [MAX_SWIPE_REPLAY_MS] long to keep latency low).
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
    private val touchExplorationListener = AccessibilityManager.TouchExplorationStateChangeListener { requestEvaluate(0) }
    private var listening = false

    val attachedView: View? get() = view

    fun reload() {
        val config = settings.read()
        val wasEnabled = enabled
        enabled = config.homeEnabled && config.homeMode == HomeDoubleTapMode.PRECISE
        timeoutMs = config.timeoutMs
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

    fun onScreenStateChanged() {
        if (!enabled) return
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

    private fun isTouchable(): Boolean = scene == HomeScene.WORKSPACE && !replaying && a11y?.isTouchExplorationEnabled != true

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
        for (i in 0 until dragLayer.childCount) {
            val child = dragLayer.getChild(i) ?: continue
            if (child.isVisibleToUser) ids += child.viewIdResourceName
        }
        return HomeHitTest.scene(dragLayerFound = true, dragLayerChildIds = ids)
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
        return Frame(left, top, max(1, bounds.width() - left - right), max(1, bounds.height() - top - bottom))
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
            overlay
        }.getOrElse {
            settings.reportHomeStatus(HomeDoubleTapStatus.FAILED)
            null
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
        launcherWindow = null
        scene = HomeScene.UNKNOWN
        view?.let { runCatching { windowManager?.removeView(it) } }
        view = null
    }

    private fun applyLayout(overlay: View, touchable: Boolean) {
        val params = overlay.layoutParams as? WindowManager.LayoutParams ?: return
        val f = frame()
        val flags = flags(touchable)
        if (params.flags == flags && params.x == f.x && params.y == f.y && params.width == f.width && params.height == f.height) return
        params.flags = flags
        params.x = f.x
        params.y = f.y
        params.width = f.width
        params.height = f.height
        if (debug) Log.d(TAG, "layout touchable=$touchable frame=$f")
        runCatching { windowManager?.updateViewLayout(overlay, params) }
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
                passThroughGesture = false
                if (SystemClock.uptimeMillis() - lastEvaluatedMs > STALE_MS) {
                    scene = runCatching { readScene() }.getOrDefault(HomeScene.UNKNOWN)
                    lastEvaluatedMs = SystemClock.uptimeMillis()
                }
                if (scene != HomeScene.WORKSPACE || !launcherInFront) passThroughGesture = true
                process(classifier.onDown(id, event.getX(index) + dx, event.getY(index) + dy, event.eventTime, timeoutMs))
            }
            MotionEvent.ACTION_POINTER_DOWN ->
                process(classifier.onDown(id, event.getX(index) + dx, event.getY(index) + dy, event.eventTime, timeoutMs))
            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until event.historySize) {
                    for (p in 0 until event.pointerCount) {
                        classifier.onMove(event.getPointerId(p), event.getHistoricalX(p, h) + dx, event.getHistoricalY(p, h) + dy, event.getHistoricalEventTime(h))
                    }
                }
                for (p in 0 until event.pointerCount) {
                    classifier.onMove(event.getPointerId(p), event.getX(p) + dx, event.getY(p) + dy, event.eventTime)
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val decisions = classifier.onUp(id, event.getX(index) + dx, event.getY(index) + dy, event.eventTime, timeoutMs, ::targetAt)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    v.performClick()
                    process(decisions)
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

    private fun process(decisions: List<HomeDecision>) {
        for (decision in decisions) {
            when (decision) {
                is HomeDecision.Replay -> enqueue(decision.gesture)
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
        onDoubleTap(view)
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
        handler.postDelayed(armRunnable, armMs)
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
        if (debug) Log.d(TAG, "replay ${gesture.kind} try ${retries + 1} after arm ${armMs} ms (lag ${inFlightAtMs - gesture.upMs} ms)")
        val accepted = dispatch(gesture) {
            if (generation != replayGeneration) return@dispatch // superseded by a retry
            inFlight = null
            if (retries == 0) armMs = (armMs - ARM_DECAY_MS).coerceAtLeast(ARM_MS)
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
        const val MAX_DRAG_REPLAY_MS = 2_000L
        const val MAX_STROKES = 10
        const val TAP_RESTORE_MS = 60L
        const val GESTURE_RESTORE_MS = 350L
        const val FALLBACK_EDGE_DP = 24

        /** A SystemUI window taller than 1/3 of the screen is a panel or dialog, not a bar. */
        const val SYSTEM_WINDOW_MAX_FRACTION = 3
    }
}
