package com.dragolabs.able.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.dragolabs.able.actions.ActionCatalog
import com.dragolabs.able.actions.ActionIds

/**
 * Fixed quick actions widget (torch, NFC, GPS save, screen lock), rendered with the same
 * catalog slots as the custom widget. Actions that need access Able does not have open the
 * relevant screen instead of failing silently (ADR-023). NFC is hidden without NFC hardware.
 */
class QuickActionsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val slots = listOf(ActionIds.TORCH, ActionIds.NFC, ActionIds.GPS_SAVE, ActionIds.SCREEN_LOCK)
            .mapNotNull(ActionCatalog::find)
            .filter { it.id != ActionIds.NFC || it.isSupported(context) }
            .map { buildWidgetSlot(context, it) }
        provideContent {
            GlanceTheme {
                ActionSlotGrid(context, slots, columns = slots.size, emptyClick = null)
            }
        }
    }
}

class AbleQuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}
