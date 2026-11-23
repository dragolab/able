package com.dragolabs.able.feature.tools.applock

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** How long an app stays unlocked after the user leaves it (APPLOCK-002). */
enum class AppLockGrace(val minutes: Int) {
    IMMEDIATELY(0),
    ONE_MINUTE(1),
    FIVE_MINUTES(5),
    FIFTEEN_MINUTES(15),
    ;

    val millis: Long get() = minutes * 60_000L

    companion object {
        fun fromMinutes(value: Int): AppLockGrace = entries.firstOrNull { it.minutes == value } ?: IMMEDIATELY
    }
}

@Immutable
data class AppLockConfig(
    val enabled: Boolean = false,
    val lockedPackages: Set<String> = emptySet(),
    val grace: AppLockGrace = AppLockGrace.IMMEDIATELY,
    val lockAble: Boolean = false,
) {
    /** The service only watches for locked apps when this is true. */
    val active: Boolean get() = enabled && (lockedPackages.isNotEmpty() || lockAble)
}

/**
 * App lock settings (APPLOCK-001..APPLOCK-003), stored locally in the SharedPreferences file
 * [FILE] ("able_app_lock"), read by this tool and by the `:app` Accessibility service
 * (`com.dragolabs.able.applock.*`). Nothing is synced or sent; no PIN is stored by Able, because
 * authentication is done by Android's own `BiometricPrompt` with the device screen lock.
 *
 * Keys (keep stable):
 * - [KEY_ENABLED] `enabled` Boolean, default false: master switch.
 * - [KEY_PACKAGES] `locked_packages` String set: package names to lock.
 * - [KEY_GRACE_MINUTES] `grace_minutes` Int, one of 0, 1, 5, 15, default 0: how long an app stays
 *   unlocked after leaving it. Every unlock ends when the screen turns off.
 * - [KEY_LOCK_ABLE] `lock_able` Boolean, default false: also lock Able itself.
 */
class AppLockSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): AppLockConfig = AppLockConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        // Copy: the returned set must not be modified (SharedPreferences contract).
        lockedPackages = prefs.getStringSet(KEY_PACKAGES, null)?.toSet().orEmpty(),
        grace = AppLockGrace.fromMinutes(prefs.getInt(KEY_GRACE_MINUTES, 0)),
        lockAble = prefs.getBoolean(KEY_LOCK_ABLE, false),
    )

    fun setEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun setLocked(packageName: String, locked: Boolean) {
        val current = read().lockedPackages.toMutableSet()
        if (locked) current += packageName else current -= packageName
        prefs.edit().putStringSet(KEY_PACKAGES, current).apply()
    }

    fun setGrace(grace: AppLockGrace) = prefs.edit().putInt(KEY_GRACE_MINUTES, grace.minutes).apply()

    fun setLockAble(enabled: Boolean) = prefs.edit().putBoolean(KEY_LOCK_ABLE, enabled).apply()

    fun changes(): Flow<AppLockConfig> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        const val FILE = "able_app_lock"
        const val KEY_ENABLED = "enabled"
        const val KEY_PACKAGES = "locked_packages"
        const val KEY_GRACE_MINUTES = "grace_minutes"
        const val KEY_LOCK_ABLE = "lock_able"
    }
}
