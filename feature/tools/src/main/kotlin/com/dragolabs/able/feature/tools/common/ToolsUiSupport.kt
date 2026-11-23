package com.dragolabs.able.feature.tools.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

/**
 * A user-facing message produced by a ViewModel without holding a Context. Resolved to text in
 * the UI so every message stays a string resource (EN/IT).
 */
@Immutable
data class UiMessage(@StringRes val res: Int, val args: List<Any>) {
    constructor(@StringRes res: Int, vararg args: Any) : this(res, args.toList())

    fun resolve(context: Context): String = context.getString(res, *args.toTypedArray())
}

@Composable
fun UiMessage.asString(): String = stringResource(res, *args.toTypedArray())

/** Shows [message] once in [snackbarHostState] and reports it as consumed. */
@Composable
fun MessageEffect(message: UiMessage?, snackbarHostState: SnackbarHostState, onConsumed: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        if (message != null) {
            val text = message.resolve(context)
            onConsumed()
            snackbarHostState.showSnackbar(text)
        }
    }
}

/**
 * Starts the first intent Android can resolve. Settings destinations differ between OEMs, so
 * callers pass the most specific screen first and broader fallbacks after it.
 * Returns false when no destination could be opened, so the caller can report it honestly.
 */
fun Context.startFirstAvailable(vararg intents: Intent): Boolean {
    for (intent in intents) {
        try {
            if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // Try the next, broader destination.
        } catch (_: SecurityException) {
            // Destination exists but is not exported to Able; try the next one.
        }
    }
    return false
}

/**
 * Polls [read] until it returns [expected] or [timeoutMs] elapses. Used to read back state after
 * a privileged change, because several Android services apply changes asynchronously.
 */
suspend fun <T> awaitReadBack(expected: T, timeoutMs: Long = 3_000, intervalMs: Long = 250, read: () -> T): Boolean {
    var waited = 0L
    while (true) {
        if (runCatching(read).getOrNull() == expected) return true
        if (waited >= timeoutMs) return false
        delay(intervalMs)
        waited += intervalMs
    }
}
