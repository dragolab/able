package com.dragolabs.able

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.dragolabs.able.applock.AppLockController
import com.dragolabs.able.applock.ForegroundPackages
import com.dragolabs.able.buttons.ButtonShortcutController
import com.dragolabs.able.core.data.ScreenLockController
import com.dragolabs.able.feature.tools.lock.LockGestureSettings
import com.dragolabs.able.lock.HomeDoubleTapController
import com.dragolabs.able.lock.HomePreciseController
import com.dragolabs.able.lock.LockGestureOverlay
import com.dragolabs.able.lock.LockTapWidgetProvider
import com.dragolabs.able.lock.SystemUiLockState

/**
 * Able's optional Accessibility service. It stays small and delegates to feature controllers;
 * every feature is off by default and only does work while the user has turned it on:
 *
 * - screen lock (LOCK-001, LOCK-002): `GLOBAL_ACTION_LOCK_SCREEN` for the tool, tile and widgets;
 *   double tap on the lock screen / status bar ([LockGestureOverlay]) and on empty home-screen
 *   space: Precise mode ([HomePreciseController], a touch overlay over the launcher's home pages
 *   that hands every other touch back) or Light mode ([HomeDoubleTapController]);
 * - app lock (APPLOCK-001): foreground app changes from window events ([AppLockController]);
 * - button shortcuts (BTN-001): volume key gestures from `onKeyEvent` ([ButtonShortcutController]).
 *
 * Event types and flags beyond the XML baseline (key filtering, launcher click/scroll events) are
 * requested at runtime only while a feature needs them ([updateServiceInfo]); the launcher's
 * click/scroll events are used by both home-screen double tap modes (in Precise mode to keep
 * swipes native while the user is browsing pages). Window content is
 * read only to classify SystemUI scenes and launcher package names; nothing typed is read, and
 * nothing is stored or sent apart from the feature status flags in the settings files.
 */
class AbleAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: LockGestureSettings
    private lateinit var packages: ForegroundPackages
    private var overlay: LockGestureOverlay? = null
    private var home: HomeDoubleTapController? = null
    private var homePrecise: HomePreciseController? = null
    private var appLock: AppLockController? = null
    private var buttons: ButtonShortcutController? = null
    private var screenReceiverRegistered = false
    private var baseInfo: Pair<Int, Int>? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == LockGestureSettings.KEY_RECOGNITION || key == LockGestureSettings.KEY_HOME_STATUS) return@OnSharedPreferenceChangeListener
        overlay?.refresh()
        home?.reload()
        homePrecise?.reload()
        updateServiceInfo()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            overlay?.refresh()
            overlay?.scheduleRefresh(SCREEN_SETTLE_MS)
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    appLock?.onScreenOff()
                    buttons?.onScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> packages.refresh()
                Intent.ACTION_USER_PRESENT -> appLock?.onUserPresent()
            }
            home?.onScreenStateChanged()
            homePrecise?.onScreenStateChanged()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = LockGestureSettings(this)
        packages = ForegroundPackages(this).also { it.refresh() }
        overlay = LockGestureOverlay(this, settings, ::onGestureDoubleTap)
        home = HomeDoubleTapController(this, settings, packages, ::onGestureDoubleTap)
        homePrecise = HomePreciseController(this, settings, packages, ::onGestureDoubleTap)
        appLock = AppLockController(this, packages).also { it.start() }
        buttons = ButtonShortcutController(this, onActiveChanged = ::updateServiceInfo, haptic = { performHaptic(null) })
            .also { it.start() }
        activeService = this
        settings.registerListener(prefsListener)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiverRegistered = true
        overlay?.refresh()
        home?.reload()
        homePrecise?.reload()
        updateServiceInfo()
        LockTapWidgetProvider.refreshAll(this)
    }

    /**
     * Adds key filtering and launcher click/scroll events only while a feature needs them, so
     * with those features off no key or click event passes through Able.
     */
    private fun updateServiceInfo() {
        val info = serviceInfo ?: return
        val base = baseInfo ?: (info.flags to info.eventTypes).also { baseInfo = it }
        var flags = base.first
        var types = base.second
        if (buttons?.wantsKeyEvents == true) flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        if (home?.wantsInteractionEvents == true || homePrecise?.wantsInteractionEvents == true) {
            types = types or AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or AccessibilityEvent.TYPE_VIEW_SCROLLED
        }
        if (info.flags == flags && info.eventTypes == types) return
        info.flags = flags
        info.eventTypes = types
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Scene animations produce many events; inspect the settled tree only.
                val pkg = event.packageName?.toString()
                if (pkg == SystemUiLockState.SYSTEM_UI_PACKAGE) {
                    overlay?.scheduleRefresh()
                    homePrecise?.onWindowsMaybeChanged()
                } else if (pkg != null && pkg == packages.launcherPackage) {
                    homePrecise?.onWindowsMaybeChanged()
                    homePrecise?.onLauncherEvent(event)
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                overlay?.refresh()
                overlay?.scheduleRefresh()
                homePrecise?.onWindowListChanged()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                overlay?.refresh()
                overlay?.scheduleRefresh()
                home?.onAccessibilityEvent(event)
                homePrecise?.onLauncherEvent(event)
                packages.foregroundPackage(event)?.let { pkg ->
                    home?.onForeground(pkg)
                    homePrecise?.onForeground(pkg)
                    appLock?.onForeground(pkg)
                }
                homePrecise?.onWindowListChanged()
            }
            else -> {
                home?.onAccessibilityEvent(event)
                homePrecise?.onLauncherEvent(event)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        homePrecise?.onConfigurationChanged()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = buttons?.onKeyEvent(event) ?: false

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (activeService === this) activeService = null
        handler.removeCallbacksAndMessages(null)
        overlay?.remove()
        overlay = null
        home?.stop()
        home = null
        homePrecise?.stop()
        homePrecise = null
        appLock?.stop()
        appLock = null
        buttons?.stop()
        buttons = null
        if (::settings.isInitialized) settings.unregisterListener(prefsListener)
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        // Widgets fall back to opening the setup screen while the service is off.
        LockTapWidgetProvider.refreshAll(applicationContext)
    }

    private fun onGestureDoubleTap(source: View?) = lockWithFeedback(source)

    /** Lock after an optional short haptic so the user feels the gesture was recognised. */
    private fun lockWithFeedback(source: View?) {
        val haptic = ::settings.isInitialized && settings.read().hapticOnLock
        if (haptic && performHaptic(source ?: overlay?.attachedView ?: homePrecise?.attachedView ?: home?.attachedView)) {
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }, HAPTIC_LEAD_MS)
        } else {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    /**
     * Uses the vibrator when the VIBRATE permission is declared, otherwise the overlay view's
     * haptic feedback (no permission needed). Returns false when no haptic could be played.
     */
    private fun performHaptic(view: View?): Boolean {
        if (checkSelfPermission(Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED) {
            val vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator
            if (vibrator?.hasVibrator() == true) {
                return runCatching {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    true
                }.getOrDefault(false)
            }
        }
        return view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM) == true
    }

    companion object {
        private const val HAPTIC_LEAD_MS = 80L
        private const val SCREEN_SETTLE_MS = 400L

        @Volatile
        private var activeService: AbleAccessibilityService? = null

        /** True while Android has the service bound (enabled and running). */
        val isRunning: Boolean get() = activeService != null

        fun lockScreen(): Boolean = activeService?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true

        /** Lock from a recognised gesture (home-screen widget), with the configured haptic. */
        fun lockFromGesture(): Boolean {
            val service = activeService ?: return false
            service.handler.post { service.lockWithFeedback(null) }
            return true
        }
    }
}

class AccessibilityScreenLockController(private val context: Context) : ScreenLockController {
    override fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val component = ComponentName(context, AbleAccessibilityService::class.java)
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    override fun lockNow(): Boolean = AbleAccessibilityService.lockScreen()
}
