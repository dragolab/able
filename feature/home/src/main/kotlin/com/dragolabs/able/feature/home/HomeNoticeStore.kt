package com.dragolabs.able.feature.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Feature-local Home preferences: remembers up to which failure timestamp the user hid the
 * failed-actions notice, so it stays hidden until a newer failure is logged.
 */
class HomeNoticeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _dismissedThrough = MutableStateFlow(prefs.getLong(KEY_FAILURES_DISMISSED_THROUGH, 0L))

    /** Newest failure timestamp (epoch ms) covered by the last "Hide"; 0 when never hidden. */
    val failuresDismissedThrough: StateFlow<Long> = _dismissedThrough.asStateFlow()

    fun dismissFailuresThrough(epochMs: Long) {
        val value = maxOf(epochMs, _dismissedThrough.value)
        prefs.edit().putLong(KEY_FAILURES_DISMISSED_THROUGH, value).apply()
        _dismissedThrough.value = value
    }

    private companion object {
        const val FILE = "home_notices"
        const val KEY_FAILURES_DISMISSED_THROUGH = "failures_dismissed_through"
    }
}
