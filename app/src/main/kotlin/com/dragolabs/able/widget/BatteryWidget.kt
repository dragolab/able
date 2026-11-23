package com.dragolabs.able.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dragolabs.able.R
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.designsystem.component.labelRes
import com.dragolabs.able.core.model.BatteryStatus
import com.dragolabs.able.core.model.ProfileType
import com.dragolabs.able.system.DeviceActions
import kotlinx.coroutines.flow.first
import java.util.Date

/**
 * Battery summary widget (MON-004). The widget is a convenience summary, not a live display:
 * it shows when it was last refreshed so staleness is visible.
 */
class BatteryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.ableContainer
        val status = container.battery.current()
        val profile = container.preferences.userPreferencesFlow.first().selectedBatteryProfile
        val updatedAt = DateFormat.getTimeFormat(context).format(Date())
        provideContent {
            GlanceTheme {
                BatteryWidgetContent(context, status, profile, updatedAt)
            }
        }
    }
}

@Composable
private fun BatteryWidgetContent(context: Context, status: BatteryStatus, profile: ProfileType, updatedAt: String) {
    val percent = status.levelPercent
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(28.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(DeviceActions.openAbleIntent(context, "battery"))),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = percent?.let { "$it%" } ?: "—",
                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onPrimaryContainer),
                modifier = GlanceModifier.defaultWeight(),
            )
            // Able mark (BRAND-001), tinted with the dynamic palette.
            Image(
                provider = ImageProvider(R.drawable.ic_tile_able),
                contentDescription = null,
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            )
        }
        Text(
            text = context.getString(
                when {
                    percent == null -> R.string.widget_battery_unavailable
                    status.isCharging -> R.string.widget_battery_charging
                    else -> R.string.widget_battery_discharging
                },
            ),
            style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onPrimaryContainer),
        )
        if (percent != null) {
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = percent / 100f,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surface,
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = context.getString(R.string.widget_battery_profile, context.getString(profile.labelRes())),
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onPrimaryContainer),
        )
        Text(
            text = context.getString(R.string.widget_updated_at, updatedAt),
            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onPrimaryContainer),
        )
    }
}

class AbleBatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()
}
