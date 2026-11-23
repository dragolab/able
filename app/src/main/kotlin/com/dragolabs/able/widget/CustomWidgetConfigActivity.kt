package com.dragolabs.able.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.dragolabs.able.R
import com.dragolabs.able.actions.ActionCatalog
import com.dragolabs.able.actions.CatalogAction
import com.dragolabs.able.actions.ui.AbleActivityTheme
import com.dragolabs.able.actions.ui.WidgetConfigScreen
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.database.entity.CustomWidgetConfigEntity
import com.dragolabs.able.core.model.CustomWidgetConfig
import com.dragolabs.able.core.model.WidgetGridFormat
import kotlinx.coroutines.launch

/**
 * Widget builder for [CustomActionsWidget] (CUST-002), launched by the launcher through
 * `APPWIDGET_CONFIGURE`, by the pin callback, by an unconfigured widget, and from "Tiles &
 * widgets". The result stays `RESULT_CANCELED` until the user saves.
 */
class CustomWidgetConfigActivity : ComponentActivity() {

    private val format = mutableStateOf(WidgetGridFormat.GRID_4X1)
    private val slots = mutableStateListOf<CatalogAction?>()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        resize(format.value)

        lifecycleScope.launch {
            CustomActionsWidget.loadConfig(this@CustomWidgetConfigActivity, appWidgetId)?.let { config ->
                format.value = config.gridFormat
                resize(config.gridFormat)
                config.actionIds.take(config.gridFormat.maxSlots).forEachIndexed { index, id ->
                    slots[index] = ActionCatalog.find(id)
                }
            }
        }

        setContent {
            AbleActivityTheme {
                WidgetConfigScreen(
                    format = format.value,
                    slots = slots.toList(),
                    onFormatChange = { newFormat ->
                        format.value = newFormat
                        resize(newFormat)
                    },
                    onSlotChange = { index, action -> slots[index] = action },
                    onSave = ::save,
                    onBack = ::finish,
                )
            }
        }
    }

    /** Keeps assigned actions when the grid changes; extra slots are dropped from the end. */
    private fun resize(newFormat: WidgetGridFormat) {
        while (slots.size < newFormat.maxSlots) slots.add(null)
        while (slots.size > newFormat.maxSlots) slots.removeAt(slots.lastIndex)
    }

    private fun save() {
        val context = applicationContext
        val config = CustomWidgetConfig(
            widgetId = appWidgetId,
            gridFormat = format.value,
            actionIds = slots.map { it?.id.orEmpty() },
        )
        lifecycleScope.launch {
            val saved = runCatching {
                context.ableContainer.database.customWidgetDao()
                    .insertOrUpdateWidget(CustomWidgetConfigEntity.fromDomain(config))
            }.isSuccess
            if (!saved) {
                Toast.makeText(context, R.string.tiles_save_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            runCatching {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                CustomActionsWidget().update(context, glanceId)
            }
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    companion object {
        fun intent(context: Context, appWidgetId: Int): Intent =
            Intent(context, CustomWidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
}
