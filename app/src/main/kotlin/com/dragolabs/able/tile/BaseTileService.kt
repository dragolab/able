package com.dragolabs.able.tile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dragolabs.able.actions.ActionRoute
import com.dragolabs.able.actions.ActionRunner
import com.dragolabs.able.actions.ActionState
import com.dragolabs.able.actions.ReportMode
import com.dragolabs.able.actions.TileBinding
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shared Quick Settings tile behavior (CUST-001, ADR-024). A tile is bound to one catalog
 * action: it renders the action's live state and icon, runs it in place when possible, and
 * otherwise opens the screen the action needs (ADR-023 two-tier model).
 */
abstract class BaseTileService : TileService() {

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running = false

    /** The bound action, or `null` for an unassigned slot. */
    protected abstract suspend fun binding(): TileBinding?

    /** Whether the tile label follows the bound action (custom slots) or keeps the manifest label. */
    protected open val labelFollowsAction: Boolean = false

    protected open fun renderUnbound(tile: Tile) {
        tile.state = Tile.STATE_UNAVAILABLE
        tile.updateTile()
    }

    protected open fun onUnboundClick() = Unit

    /** Re-reads the bound action's state when Android broadcasts a change (e.g. NFC adapter on/off). */
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch { refresh() }
        }
    }
    private var receiverRegistered = false

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            registerStateReceiver(binding()?.action?.stateChangedActions.orEmpty())
            refresh()
        }
    }

    override fun onStopListening() {
        unregisterStateReceiver()
        super.onStopListening()
    }

    private fun registerStateReceiver(actions: List<String>) {
        unregisterStateReceiver()
        if (actions.isEmpty()) return
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        // System broadcasts (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) need an exported receiver.
        runCatching { ContextCompat.registerReceiver(this, stateReceiver, filter, ContextCompat.RECEIVER_EXPORTED) }
            .onSuccess { receiverRegistered = true }
    }

    private fun unregisterStateReceiver() {
        if (!receiverRegistered) return
        // May already be gone when the tile is removed while listening.
        runCatching { unregisterReceiver(stateReceiver) }
        receiverRegistered = false
    }

    protected suspend fun refresh() {
        val bound = binding()
        val tile = qsTile ?: return
        if (bound == null) {
            renderUnbound(tile)
            return
        }
        val action = bound.action
        val state = runCatching { action.readState(this) }
            .getOrElse { ActionState(active = null, available = false, iconRes = action.iconRes) }
        if (labelFollowsAction) tile.label = bound.customLabel ?: getString(action.titleRes)
        tile.icon = Icon.createWithResource(this, state.iconRes)
        tile.state = when {
            !state.available -> Tile.STATE_UNAVAILABLE
            state.active == true -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.subtitle = state.status(this)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val bound = binding()
            if (bound == null) {
                onUnboundClick()
                return@launch
            }
            when (val route = bound.action.route(this@BaseTileService)) {
                is ActionRoute.OpenScreen -> startActivityAndCollapseCompat(route.intent)
                is ActionRoute.Unsupported -> route.infoIntent?.let { startActivityAndCollapseCompat(it) } ?: refresh()
                ActionRoute.RunInPlace ->
                    if (bound.action.requiresUnlock && isLocked) {
                        unlockAndRun { scope.launch { run(bound) } }
                    } else {
                        run(bound)
                    }
            }
        }
    }

    private suspend fun run(bound: TileBinding) {
        if (running) return
        running = true
        try {
            ActionRunner.execute(this, bound.action, ReportMode.FAILURES_ONLY)
        } finally {
            running = false
        }
        refresh()
    }

    override fun onDestroy() {
        unregisterStateReceiver()
        scope.cancel()
        super.onDestroy()
    }
}
