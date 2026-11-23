package com.dragolabs.able.actions.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.R
import com.dragolabs.able.actions.ActionCatalog
import com.dragolabs.able.actions.CatalogAction
import com.dragolabs.able.actions.CustomTileSlots
import com.dragolabs.able.core.model.WidgetGridFormat
import com.dragolabs.able.widget.AbleCustomActionsWidgetReceiver
import com.dragolabs.able.widget.CustomActionsWidget
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TileSlotUi(val slot: Int, val action: CatalogAction?)

data class PlacedWidgetUi(val appWidgetId: Int, val format: WidgetGridFormat?, val actions: List<CatalogAction>)

data class TilesWidgetsUiState(
    val loading: Boolean = true,
    val slots: List<TileSlotUi> = CustomTileSlots.slots.map { TileSlotUi(it, null) },
    val widgets: List<PlacedWidgetUi> = emptyList(),
    val pinWidgetSupported: Boolean = false,
)

/** State for "Tiles & widgets" (CUST-001, CUST-002). */
class TilesWidgetsViewModel(private val appContext: Context) : ViewModel() {

    private val widgetRefresh = MutableStateFlow(0)
    private val messageChannel = Channel<String>(Channel.BUFFERED)

    /** One-off snackbar messages. */
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<TilesWidgetsUiState> = combine(
        CustomTileSlots.observe(appContext),
        widgetRefresh.map { loadWidgets() },
    ) { mappings, widgets ->
        TilesWidgetsUiState(
            loading = false,
            slots = CustomTileSlots.slots.map { slot -> TileSlotUi(slot, ActionCatalog.find(mappings[slot]?.actionId)) },
            widgets = widgets,
            pinWidgetSupported = AppWidgetManager.getInstance(appContext).isRequestPinAppWidgetSupported,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TilesWidgetsUiState())

    fun assign(slot: Int, action: CatalogAction?) {
        viewModelScope.launch {
            runCatching { CustomTileSlots.assign(appContext, slot, action?.id) }
                .onSuccess {
                    post(
                        if (action == null) appContext.getString(R.string.tiles_slot_cleared, slot)
                        else appContext.getString(R.string.tiles_slot_assigned, appContext.getString(action.titleRes), slot),
                    )
                }
                .onFailure { post(appContext.getString(R.string.tiles_save_failed)) }
        }
    }

    /** Placed widgets are not observable through Room; re-read when the screen resumes. */
    fun refreshWidgets() {
        widgetRefresh.value++
    }

    fun post(message: String) {
        messageChannel.trySend(message)
    }

    private suspend fun loadWidgets(): List<PlacedWidgetUi> {
        val ids = runCatching {
            AppWidgetManager.getInstance(appContext)
                .getAppWidgetIds(ComponentName(appContext, AbleCustomActionsWidgetReceiver::class.java))
        }.getOrDefault(IntArray(0))
        return ids.map { id ->
            val config = CustomActionsWidget.loadConfig(appContext, id)
            PlacedWidgetUi(
                appWidgetId = id,
                format = config?.gridFormat,
                actions = config?.actionIds.orEmpty().mapNotNull(ActionCatalog::find),
            )
        }
    }
}
