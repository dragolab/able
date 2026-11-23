package com.dragolabs.able.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dragolabs.able.R
import com.dragolabs.able.actions.ActionCatalog
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.model.CustomWidgetConfig
import kotlinx.coroutines.launch

/**
 * Customizable multi-action widget (CUST-002, ADR-024). Layout and actions come from
 * `custom_widget_configs`, written by [CustomWidgetConfigActivity].
 */
class CustomActionsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = loadConfig(context, appWidgetId)
        val setup = actionStartActivity(CustomWidgetConfigActivity.intent(context, appWidgetId))
        val slots = config?.let { cfg ->
            (0 until cfg.gridFormat.maxSlots).map { index ->
                ActionCatalog.find(cfg.actionIds.getOrNull(index))?.let { buildWidgetSlot(context, it) }
            }
        }
        provideContent {
            GlanceTheme {
                if (config == null || slots.isNullOrEmpty() || slots.all { it == null }) {
                    SetupPrompt(context, setup)
                } else {
                    ActionSlotGrid(context, slots, columns = config.gridFormat.columns, emptyClick = setup)
                }
            }
        }
    }

    companion object {
        suspend fun loadConfig(context: Context, appWidgetId: Int): CustomWidgetConfig? =
            context.ableContainer.database.customWidgetDao().getWidgetById(appWidgetId)
                ?.let { runCatching { it.toDomain() }.getOrNull() }
    }
}

@androidx.compose.runtime.Composable
private fun SetupPrompt(context: Context, setup: Action) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(28.dp)
            .padding(12.dp)
            .clickable(setup),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_tile_able),
            contentDescription = null,
            modifier = GlanceModifier.size(28.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = context.getString(R.string.tiles_widget_setup),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface),
            maxLines = 2,
        )
    }
}

class AbleCustomActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CustomActionsWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val container = context.ableContainer
        container.applicationScope.launch {
            val dao = container.database.customWidgetDao()
            appWidgetIds.filter { it != AppWidgetManager.INVALID_APPWIDGET_ID }.forEach { dao.deleteWidgetById(it) }
        }
    }
}
