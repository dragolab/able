package com.dragolabs.able.feature.battery.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dragolabs.able.core.database.MonitorNotificationLayout
import com.dragolabs.able.core.database.UserPreferences
import com.dragolabs.able.feature.battery.ui.displayName
import com.dragolabs.able.core.model.BatteryState
import com.dragolabs.able.core.model.BatteryStatus
import com.dragolabs.able.feature.battery.R
import com.dragolabs.able.feature.battery.domain.BatteryFormat
import kotlin.math.abs

/** Notification channels and builders for the live monitor (MON-004, MON-007, BAT-CHG-001). */
internal object MonitorNotifications {
    const val CHANNEL_MONITOR = "battery_monitor"
    const val CHANNEL_ALERTS = "battery_alerts"
    const val ID_MONITOR = 7201
    const val ID_THERMAL = 7202
    const val ID_IDLE_DRAIN = 7203

    /** Creates or renames (after a language change) the channels. Safe to call repeatedly. */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR,
                context.getString(R.string.battery_channel_monitor),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.battery_channel_monitor_desc) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.battery_channel_alerts),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.battery_channel_alerts_desc) },
        )
    }

    private fun openBatteryIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra("route", "battery")
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return null
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun serviceAction(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, BatteryMonitorService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun stateLabel(context: Context, status: BatteryStatus?): String = context.getString(
        when (status?.state) {
            BatteryState.CHARGING -> R.string.battery_state_charging
            BatteryState.FULL -> R.string.battery_state_full
            BatteryState.DISCHARGING -> R.string.battery_state_discharging
            BatteryState.NOT_CHARGING -> R.string.battery_state_not_charging
            else -> R.string.battery_state_unknown
        },
    )

    /** Text lines of the ongoing notification; also used to skip redundant updates. */
    fun ongoingContent(
        context: Context,
        status: BatteryStatus?,
        live: LiveStats?,
        prefs: UserPreferences,
        activeProfilesLabel: String?,
    ): Pair<String, List<String>> {
        val res = context.resources
        val level = status?.levelPercent?.let { context.getString(R.string.battery_percent_value, it) }
            ?: context.getString(R.string.battery_value_unavailable)
        val title = context.getString(R.string.battery_notif_title, level, stateLabel(context, status))
        val lines = buildList {
            if (live != null) {
                add(
                    context.getString(
                        R.string.battery_notif_screen_on,
                        BatteryFormat.duration(res, live.screenOnMs),
                        live.screenOnDropPercent,
                    ),
                )
                add(
                    context.getString(
                        R.string.battery_notif_screen_off,
                        BatteryFormat.duration(res, live.screenOffMs),
                        live.screenOffDropPercent,
                    ),
                )
            }
            status?.temperatureCelsius?.let {
                add(context.getString(R.string.battery_notif_temperature, BatteryFormat.temperature(res, it, prefs.temperatureUnit)))
            }
            if (prefs.monitorNotificationShowPower) {
                val current = status?.currentMicroAmperes?.let { abs(it) / 1000 }
                val power = status?.powerMilliWatts
                if (current != null) {
                    val text = if (power != null) {
                        context.getString(R.string.battery_notif_current_power, current, BatteryFormat.decimal(power / 1000f))
                    } else {
                        context.getString(R.string.battery_notif_current, current)
                    }
                    add(text)
                }
            }
            if (status?.isCharging == true) {
                status.chargeTimeRemainingMs?.let {
                    add(context.getString(R.string.battery_notif_time_to_full, BatteryFormat.duration(res, it)))
                }
            }
            activeProfilesLabel?.let { add(context.getString(R.string.battery_notif_profile, it)) }
        }
        return title to lines
    }

    fun buildOngoing(context: Context, title: String, lines: List<String>, layout: MonitorNotificationLayout): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.battery_ic_monitor)
            .setContentTitle(title)
            .setContentIntent(openBatteryIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.battery_monitor_stop), serviceAction(context, BatteryMonitorService.ACTION_STOP, 1))
        when (layout) {
            MonitorNotificationLayout.COMPACT -> builder.setContentText(lines.take(2).joinToString(" · "))
            MonitorNotificationLayout.DETAILED -> {
                builder.setContentText(lines.firstOrNull().orEmpty())
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
                builder.addAction(
                    0,
                    context.getString(R.string.battery_monitor_reset_now),
                    serviceAction(context, BatteryMonitorService.ACTION_RESET, 2),
                )
            }
        }
        return builder.build()
    }

    private fun canNotify(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun postThermalAlert(context: Context, temperatureText: String) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.battery_ic_monitor)
            .setContentTitle(context.getString(R.string.battery_alert_thermal_title))
            .setContentText(context.getString(R.string.battery_alert_thermal_body, temperatureText))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.battery_alert_thermal_body, temperatureText)))
            .setContentIntent(openBatteryIntent(context))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_THERMAL, notification) }
    }

    fun postIdleDrainAlert(context: Context, body: String) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.battery_ic_monitor)
            .setContentTitle(context.getString(R.string.battery_alert_idle_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openBatteryIntent(context))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_IDLE_DRAIN, notification) }
    }

    /** Names of the active profiles, or "none" (several profiles can be active together). */
    fun activeProfilesLabel(context: Context, active: List<com.dragolabs.able.core.model.BatteryProfile>): String =
        if (active.isEmpty()) {
            context.getString(R.string.battery_notif_profile_none)
        } else {
            active.joinToString(", ") { it.displayName(context.resources) }
        }
}
