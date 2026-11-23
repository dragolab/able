package com.dragolabs.able.lock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.dragolabs.able.AbleAccessibilityService
import com.dragolabs.able.R
import com.dragolabs.able.core.model.ToolId
import com.dragolabs.able.feature.tools.lock.LockGestureSettings
import com.dragolabs.able.system.DeviceActions

/**
 * Transparent, resizable home-screen widget for double tap to lock on the launcher (LOCK-001).
 * Launchers do not let apps see touches on their own home screen, so the user places this
 * invisible widget over empty home space; each tap sends a broadcast to [LockTapReceiver] and two
 * taps within the configured timeout lock the screen through [AbleAccessibilityService].
 * Pattern adapted from TapLock's TapLockWidgetProvider (Apache-2.0, github.com/modelorona/TapLock).
 *
 * When the Accessibility service is not running the widget instead opens the Screen lock tool,
 * so a tap never silently does nothing.
 */
class LockTapWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, LockTapWidgetProvider::class.java)) }
                .getOrNull() ?: return
            ids.forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val click = if (AbleAccessibilityService.isRunning) {
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    Intent(context, LockTapReceiver::class.java).setAction(LockTapReceiver.ACTION_TAP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                openSetupPendingIntent(context, appWidgetId)
            }
            val views = RemoteViews(context.packageName, R.layout.widget_lock_tap).apply {
                setOnClickPendingIntent(R.id.lock_tap_area, click)
            }
            runCatching { manager.updateAppWidget(appWidgetId, views) }
        }

        private fun openSetupPendingIntent(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            DeviceActions.openToolIntent(context, ToolId.SCREEN_LOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Not exported: only the widget's own PendingIntent (sent with Able's identity) reaches it. */
class LockTapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAP) return
        if (!AbleAccessibilityService.isRunning) {
            // The service stopped since the widget was last drawn: point the widget at setup.
            LockTapWidgetProvider.refreshAll(context)
            Toast.makeText(context, R.string.lock_widget_service_off, Toast.LENGTH_SHORT).show()
            return
        }
        val timeout = LockGestureSettings(context).read().timeoutMs
        if (detector.onTap(timeout)) AbleAccessibilityService.lockFromGesture()
    }

    companion object {
        const val ACTION_TAP = "com.dragolabs.able.lock.action.WIDGET_TAP"

        /** Shared across all lock widgets; the service keeps the process alive between taps. */
        private val detector = DoubleTapDetector()
    }
}
