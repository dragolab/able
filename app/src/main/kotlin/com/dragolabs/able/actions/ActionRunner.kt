package com.dragolabs.able.actions

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.glance.appwidget.updateAll
import com.dragolabs.able.R
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.core.model.ActionExecutionType
import com.dragolabs.able.tile.AutoBrightnessTileService
import com.dragolabs.able.tile.NfcTileService
import com.dragolabs.able.tile.PrivateDnsTileService
import com.dragolabs.able.tile.ScreenLockTileService
import com.dragolabs.able.tile.TorchTileService
import com.dragolabs.able.tile.UsbAdbTileService
import com.dragolabs.able.tile.WirelessAdbTileService
import com.dragolabs.able.widget.CustomActionsWidget
import com.dragolabs.able.widget.QuickActionsWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** When a surface shows a toast for an action result. */
enum class ReportMode {
    /** Always (widgets: the widget itself may not show the new state immediately). */
    ALWAYS,

    /** Only when the action did not apply (tiles: the tile already shows the new state). */
    FAILURES_ONLY,
}

/**
 * Runs catalog actions for tiles, widgets and the in-app screen: executes, reads the state back,
 * reports the real outcome to the user and records it in the action log (BAT-005).
 */
object ActionRunner {

    suspend fun execute(context: Context, action: CatalogAction, report: ReportMode): ActionOutcome {
        val outcome = runCatching { action.execute(context) }.getOrDefault(ActionOutcome.REJECTED)
        val after = runCatching { action.readState(context) }.getOrNull()
        val title = context.getString(action.titleRes)
        val status = after?.status(context)

        context.ableContainer.actionLog.log(
            actionId = "action:${action.id}",
            status = if (outcome == ActionOutcome.APPLIED) ActionExecutionStatus.SUCCESS else ActionExecutionStatus.FAILED,
            detail = "$outcome${status?.let { " ($it)" } ?: ""}",
            reversible = action.type == ActionExecutionType.IMMEDIATE_TOGGLE,
        )

        val showToast = when (report) {
            ReportMode.ALWAYS -> outcome != ActionOutcome.APPLIED || !action.silentOnSuccess
            ReportMode.FAILURES_ONLY -> outcome != ActionOutcome.APPLIED
        }
        if (showToast) toast(context, message(context, outcome, title, status))
        refreshSurfaces(context)
        return outcome
    }

    fun message(context: Context, outcome: ActionOutcome, title: String, status: String?): String = when (outcome) {
        ActionOutcome.APPLIED ->
            if (status != null) context.getString(R.string.action_result_applied, title, status)
            else context.getString(R.string.action_result_done, title)
        ActionOutcome.REJECTED -> context.getString(R.string.action_result_rejected, title)
        ActionOutcome.UNVERIFIED -> context.getString(R.string.action_result_unverified, title)
        ActionOutcome.UNSUPPORTED -> context.getString(R.string.action_result_unsupported, title)
    }

    suspend fun toast(context: Context, text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    /** Asks every Able tile to re-read its state and re-renders action widgets. */
    fun refreshSurfaces(context: Context) {
        val app = context.applicationContext
        val tiles = CustomTileSlots.services + listOf(
            TorchTileService::class.java,
            NfcTileService::class.java,
            AutoBrightnessTileService::class.java,
            UsbAdbTileService::class.java,
            WirelessAdbTileService::class.java,
            PrivateDnsTileService::class.java,
            ScreenLockTileService::class.java,
        )
        tiles.forEach { cls -> runCatching { TileService.requestListeningState(app, ComponentName(app, cls)) } }
        app.ableContainer.applicationScope.launch {
            runCatching { CustomActionsWidget().updateAll(app) }
            runCatching { QuickActionsWidget().updateAll(app) }
        }
    }
}
