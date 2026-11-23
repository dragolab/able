package com.dragolabs.able.applock

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.lang.ref.WeakReference
import com.dragolabs.able.feature.tools.applock.AppLockConfig
import com.dragolabs.able.feature.tools.applock.AppLockSettings

/**
 * App lock (APPLOCK-001..APPLOCK-003) without overlays: when a locked app's window comes to the
 * foreground, Able starts its own transparent [AppLockActivity], which shows Android's
 * `BiometricPrompt` (fingerprint, face or the device PIN/pattern/password). Success returns to
 * the app; cancel sends the user to the home screen with `GLOBAL_ACTION_HOME`.
 *
 * Starting the activity from the service is allowed: Android binds Accessibility services with
 * `Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS` (AOSP `AccessibilityServiceConnection.bindLocked`,
 * main branch, checked 2026-11-22), which matches the "bound by a service that has been granted
 * permission to start background activities" exception in "Background activity launch
 * restrictions" (developer.android.com, checked 2026-11-22).
 *
 * Limits stated in the tool: the app is covered right after its window appears, not before;
 * its recents thumbnail and notifications stay visible (Able cannot set `FLAG_SECURE` on other
 * apps); nothing is locked while the device has no secure screen lock, during calls, or while
 * the keyguard is showing (checked again after unlocking).
 *
 * All methods run on the main thread (service callbacks and the activity).
 */
internal class AppLockController(
    private val service: AccessibilityService,
    private val packages: ForegroundPackages,
) {
    private val settings = AppLockSettings(service)
    private val handler = Handler(Looper.getMainLooper())
    private val keyguard = service.getSystemService(KeyguardManager::class.java)
    private val audio = service.getSystemService(AudioManager::class.java)
    private val policy = AppLockPolicy()
    private var config = AppLockConfig()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> reload() }

    val active: Boolean get() = config.active

    fun start() {
        settings.registerListener(prefsListener)
        AppLockBridge.controller = this
        reload()
    }

    fun stop() {
        settings.unregisterListener(prefsListener)
        if (AppLockBridge.controller === this) AppLockBridge.controller = null
        handler.removeCallbacksAndMessages(null)
        policy.clear()
    }

    private fun reload() {
        config = settings.read()
        policy.lockedPackages = buildSet {
            addAll(config.lockedPackages)
            if (config.lockAble) add(service.packageName)
        }
        policy.graceMs = config.grace.millis
        if (!config.active) policy.clear()
    }

    /** Called for every `TYPE_WINDOW_STATE_CHANGED` with the classified package. */
    fun onForeground(pkg: String) {
        if (!config.active) return
        val decision = policy.onForeground(pkg, packages.leavesPrevious(pkg), SystemClock.elapsedRealtime())
        when (decision) {
            AppLockDecision.NONE -> Unit
            AppLockDecision.GO_HOME -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            AppLockDecision.PROMPT -> if (canPromptNow()) prompt(pkg) else policy.onPromptFailed(pkg)
        }
    }

    fun onScreenOff() = policy.onScreenOff()

    /** After the keyguard goes away the locked app may already be in front without a new event. */
    fun onUserPresent() {
        if (!config.active) return
        // At ACTION_USER_PRESENT the keyguard window can still be the active one (seen on the API 36
        // emulator, 2026-11-22), so use the last app window reported by events, and wait briefly
        // until the keyguard has gone before starting the prompt.
        handler.removeCallbacks(userPresentCheck)
        handler.postDelayed(userPresentCheck, USER_PRESENT_DELAY_MS)
    }

    private val userPresentCheck = Runnable {
        val pkg = policy.current
            ?: runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull()
            ?: return@Runnable
        if (policy.needsPrompt(pkg, SystemClock.elapsedRealtime())) {
            // Feed it through the normal path so the loop guards apply.
            onForeground(pkg)
        }
    }

    private fun canPromptNow(): Boolean {
        // Without a secure lock there is nothing to authenticate with: never trap the user.
        if (keyguard?.isDeviceSecure != true) return false
        // The prompt cannot be shown over the keyguard; onUserPresent re-checks.
        if (keyguard.isKeyguardLocked) return false
        // Never cover the in-call screen or a ringing phone.
        val mode = audio?.mode ?: AudioManager.MODE_NORMAL
        return mode == AudioManager.MODE_NORMAL
    }

    private fun prompt(pkg: String) {
        val intent = Intent(service, AppLockActivity::class.java)
            .putExtra(AppLockActivity.EXTRA_PACKAGE, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        val started = runCatching { service.startActivity(intent) }.isSuccess
        if (!started) policy.onPromptFailed(pkg)
    }

    // region Results from AppLockActivity

    fun onAuthenticated(pkg: String) = policy.onAuthenticated(pkg)

    fun onCancelled(pkg: String, goHome: Boolean) {
        policy.onPromptCancelled(pkg, SystemClock.elapsedRealtime())
        if (goHome) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun isUnlocked(pkg: String): Boolean = policy.isUnlocked(pkg, SystemClock.elapsedRealtime())

    // endregion
}

private const val USER_PRESENT_DELAY_MS = 300L

/**
 * In-process link between [AppLockActivity] and the running controller (main thread only).
 * Weak, and cleared in [AppLockController.stop], so it never keeps the service alive.
 */
internal object AppLockBridge {
    private var ref: WeakReference<AppLockController>? = null

    var controller: AppLockController?
        get() = ref?.get()
        set(value) {
            ref = value?.let(::WeakReference)
        }
}
