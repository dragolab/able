package com.dragolabs.able.feature.tools.lock

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** What the Accessibility service last observed when the keyguard was shown (LOCK-001). */
enum class LockScreenRecognition { UNKNOWN, RECOGNIZED, NOT_RECOGNIZED }

/** What the Accessibility service reported for the home-screen double tap (LOCK-001). */
enum class HomeDoubleTapStatus {
    UNKNOWN,

    /** Watching taps while the launcher is in the foreground. */
    WATCHING,

    /** No default launcher could be resolved (or the resolver is the default). */
    NO_LAUNCHER,

    /** Android refused the invisible watcher window. */
    FAILED,
}

@Immutable
data class LockGestureConfig(
    val lockScreenEnabled: Boolean = false,
    val statusBarEnabled: Boolean = false,
    val homeEnabled: Boolean = false,
    val timeoutMs: Int = LockGestureSettings.DEFAULT_TIMEOUT_MS,
    val hapticOnLock: Boolean = true,
    val lockScreenRecognition: LockScreenRecognition = LockScreenRecognition.UNKNOWN,
    val homeStatus: HomeDoubleTapStatus = HomeDoubleTapStatus.UNKNOWN,
)

/**
 * Double-tap-to-lock settings (LOCK-001). Stored locally in the SharedPreferences file
 * [FILE] ("able_lock_gesture"); nothing is synced or sent anywhere.
 *
 * Both this tool and the `:app` Accessibility service / home-screen widget read the same file.
 * Keys (keep stable, they are read by `com.dragolabs.able.lock.*` in `:app`):
 * - [KEY_LOCK_SCREEN] `lock_screen_enabled` Boolean, default false: double tap on the lock screen.
 * - [KEY_STATUS_BAR] `status_bar_enabled` Boolean, default false: double tap on the status bar.
 * - [KEY_HOME] `home_double_tap_enabled` Boolean, default false: experimental double tap on empty
 *   home-screen space.
 *   Detected by a timing-only watcher that never holds a touch (`HomeDoubleTapController`).
 *   The former `home_mode` / `home_area` keys (Precise touch overlay, removed in 0.8) are ignored.
 * - [KEY_TIMEOUT_MS] `double_tap_timeout_ms` Int, default 300, range 200..600: max gap between taps.
 * - [KEY_HAPTIC] `haptic_on_lock` Boolean, default true: short vibration before locking.
 * - [KEY_RECOGNITION] `lock_screen_recognition` String ([LockScreenRecognition] name), written by
 *   the service only: whether the SystemUI lock-screen layout was recognised the last time the
 *   keyguard was shown.
 * The home-screen widget has no setting: placing or removing the widget enables or disables it.
 * [KEY_HOME_STATUS] `home_double_tap_status` String ([HomeDoubleTapStatus] name), written by the
 * service only: whether it could start watching home-screen taps.
 */
class LockGestureSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): LockGestureConfig = LockGestureConfig(
        lockScreenEnabled = prefs.getBoolean(KEY_LOCK_SCREEN, false),
        statusBarEnabled = prefs.getBoolean(KEY_STATUS_BAR, false),
        homeEnabled = prefs.getBoolean(KEY_HOME, false),
        timeoutMs = prefs.getInt(KEY_TIMEOUT_MS, DEFAULT_TIMEOUT_MS).coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS),
        hapticOnLock = prefs.getBoolean(KEY_HAPTIC, true),
        lockScreenRecognition = prefs.getString(KEY_RECOGNITION, null)
            ?.let { stored -> LockScreenRecognition.entries.firstOrNull { it.name == stored } }
            ?: LockScreenRecognition.UNKNOWN,
        homeStatus = prefs.getString(KEY_HOME_STATUS, null)
            ?.let { stored -> HomeDoubleTapStatus.entries.firstOrNull { it.name == stored } }
            ?: HomeDoubleTapStatus.UNKNOWN,
    )

    fun setLockScreenEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_LOCK_SCREEN, enabled).apply()

    fun setStatusBarEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_STATUS_BAR, enabled).apply()

    fun setHomeEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_HOME, enabled).apply()

    /** Written by the Accessibility service; only stored when it changes. */
    fun reportHomeStatus(value: HomeDoubleTapStatus) {
        if (prefs.getString(KEY_HOME_STATUS, null) != value.name) {
            prefs.edit().putString(KEY_HOME_STATUS, value.name).apply()
        }
    }

    fun setTimeoutMs(value: Int) =
        prefs.edit().putInt(KEY_TIMEOUT_MS, value.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)).apply()

    fun setHapticOnLock(enabled: Boolean) = prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()

    /** Written by the Accessibility service; only stored when it changes. */
    fun reportRecognition(value: LockScreenRecognition) {
        if (prefs.getString(KEY_RECOGNITION, null) != value.name) {
            prefs.edit().putString(KEY_RECOGNITION, value.name).apply()
        }
    }

    /** Emits the current configuration and every later change. */
    fun changes(): Flow<LockGestureConfig> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    /** Registers a raw listener (used by the service); keep a strong reference to [listener]. */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val FILE = "able_lock_gesture"
        const val KEY_LOCK_SCREEN = "lock_screen_enabled"
        const val KEY_STATUS_BAR = "status_bar_enabled"
        const val KEY_HOME = "home_double_tap_enabled"
        const val KEY_HOME_STATUS = "home_double_tap_status"
        const val KEY_TIMEOUT_MS = "double_tap_timeout_ms"
        const val KEY_HAPTIC = "haptic_on_lock"
        const val KEY_RECOGNITION = "lock_screen_recognition"

        const val DEFAULT_TIMEOUT_MS = 300
        const val MIN_TIMEOUT_MS = 200
        const val MAX_TIMEOUT_MS = 600
    }
}
