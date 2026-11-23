package com.dragolabs.able.tile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dragolabs.able.actions.ActionRoute
import com.dragolabs.able.actions.ActionRunner
import com.dragolabs.able.actions.ActionState
import com.dragolabs.able.actions.ReportMode
import com.dragolabs.able.actions.TileBinding
import com.dragolabs.able.actions.transitioningTo
import com.dragolabs.able.core.model.ActionExecutionType
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    /**
     * Re-reads the bound action's state when Android broadcasts a change (e.g. NFC adapter on/off).
     * A broadcast that reports a transition (NFC STATE_TURNING_ON) is shown as "Turning on…".
     */
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch {
                val action = binding()?.action
                val transitional = action?.let { runCatching { it.transitionalState(this@BaseTileService, intent) }.getOrNull() }
                if (transitional != null) render(transitional) else refresh()
            }
        }
    }
    private var receiverRegistered = false

    /** Re-reads the state when a watched setting changes (adb_enabled, adb_wifi_enabled, Private DNS...). */
    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!running) scope.launch { refresh() }
        }
    }
    private var observerRegistered = false

    private var pollJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        lastRendered = null
        scope.launch {
            val action = binding()?.action
            registerStateReceiver(action?.stateChangedActions.orEmpty())
            registerSettingsObserver(action?.let { runCatching { it.stateChangedUris(this@BaseTileService) }.getOrNull() }.orEmpty())
            refresh()
            startPolling(action?.listeningPollMs)
        }
    }

    /** Re-reads the state while the tile is visible (only between onStartListening/onStopListening). */
    private fun startPolling(intervalMs: Long?) {
        pollJob?.cancel()
        if (intervalMs == null) return
        pollJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                if (!running) refresh()
            }
        }
    }

    override fun onStopListening() {
        pollJob?.cancel()
        pollJob = null
        unregisterStateReceiver()
        unregisterSettingsObserver()
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

    private fun registerSettingsObserver(uris: List<Uri>) {
        unregisterSettingsObserver()
        if (uris.isEmpty()) return
        runCatching { uris.forEach { contentResolver.registerContentObserver(it, false, settingsObserver) } }
            .onSuccess { observerRegistered = true }
    }

    private fun unregisterSettingsObserver() {
        if (!observerRegistered) return
        runCatching { contentResolver.unregisterContentObserver(settingsObserver) }
        observerRegistered = false
    }

    protected suspend fun refresh() {
        val bound = binding()
        val tile = qsTile ?: return
        if (bound == null) {
            lastRendered = null
            renderUnbound(tile)
            return
        }
        val action = bound.action
        val state = runCatching { action.readState(this) }
            .getOrElse { ActionState(active = null, available = false, iconRes = action.iconRes) }
        render(state, bound)
    }

    private var lastRendered: Pair<String, ActionState>? = null

    private suspend fun render(state: ActionState, bound: TileBinding? = null) {
        val tile = qsTile ?: return
        val binding = bound ?: binding() ?: return
        // Polling re-reads often: only push a tile update when something changed.
        val key = binding.action.id + "|" + binding.customLabel
        if (lastRendered == key to state) return
        lastRendered = key to state
        if (labelFollowsAction) tile.label = binding.customLabel ?: getString(binding.action.titleRes)
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
            // Show "Turning on…/off…" until the read-back confirms (enabling NFC is asynchronous).
            val before = runCatching { bound.action.readState(this) }.getOrNull()
            val current = before?.active
            if (before != null && current != null && bound.action.type == ActionExecutionType.IMMEDIATE_TOGGLE) {
                render(before.transitioningTo(!current), bound)
            }
            ActionRunner.execute(this, bound.action, ReportMode.FAILURES_ONLY)
        } finally {
            running = false
        }
        refresh()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        unregisterStateReceiver()
        unregisterSettingsObserver()
        scope.cancel()
        super.onDestroy()
    }
}
