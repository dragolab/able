package com.dragolabs.able.feature.tools.fakegps

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.feature.tools.R
import kotlinx.coroutines.launch
import com.dragolabs.able.core.designsystem.R as DsR

/** What to resume after the in-context location permission request. */
private enum class PendingAction { START, LOCATE }

/**
 * Fake GPS entry point (FGPS). Stateful route: owns the ViewModel, the in-context location
 * (and notification) permission request and the Settings intents; the UI is [FakeGpsScreen].
 */
@Composable
fun FakeGpsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: FakeGpsViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            val ctx = container.appContext
            FakeGpsViewModel(
                appContext = ctx,
                access = MockLocationAccess(ctx, container.shizuku),
                location = container.location,
                dao = container.database.gpsDao(),
                store = FakeGpsStore.get(ctx),
                actionLog = container.actionLog,
                logScope = container.applicationScope,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingAction?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSetup() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        viewModel.refreshSetup()
        val action = pending
        pending = null
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            when (action) {
                PendingAction.START -> viewModel.start()
                PendingAction.LOCATE -> viewModel.locateMe()
                null -> Unit
            }
        } else {
            viewModel.onPermissionDenied()
        }
    }

    fun withLocationPermission(action: PendingAction, block: () -> Unit) {
        if (state.setup.fineLocationGranted) {
            block()
        } else {
            pending = action
            val permissions = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                // The ongoing notification (with its Stop action) needs this on Android 13+.
                if (action == PendingAction.START) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    val messageText = state.message?.let { stringResource(it.labelRes()) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    val noHandler = stringResource(DsR.string.ds_no_handler)
    fun launch(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar(noHandler) }
        } catch (_: SecurityException) {
            scope.launch { snackbarHostState.showSnackbar(noHandler) }
        }
    }

    FakeGpsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        events = FakeGpsEvents(
            onMapTap = viewModel::onMapTap,
            onMapLongPress = viewModel::onMapLongPress,
            onModeChange = viewModel::setMode,
            onSpeedChange = viewModel::setSpeed,
            onRepeatChange = viewModel::setRepeat,
            onJitterChange = viewModel::setJitter,
            onAltitudeChange = viewModel::setAltitude,
            onAccuracyChange = viewModel::setAccuracy,
            onUndoWaypoint = viewModel::removeLastWaypoint,
            onClearWaypoints = viewModel::clearWaypoints,
            onRestartRoute = viewModel::restartRoute,
            onJoystick = viewModel::onJoystick,
            onNudge = viewModel::nudge,
            onStart = { withLocationPermission(PendingAction.START) { viewModel.start() } },
            onStop = viewModel::stop,
            onDismissFailure = viewModel::dismissFailure,
            onLocateMe = { withLocationPermission(PendingAction.LOCATE) { viewModel.locateMe() } },
            onCenterFake = viewModel::centerOnFake,
            onOpenDeveloperOptions = { launch(MockLocationAccessIntents.developerOptions(context, state.setup.developerOptionsEnabled)) },
            onAllowWithShizuku = viewModel::allowWithShizuku,
            onOpenLocationSettings = {
                launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            onOpenSearch = viewModel::openSearch,
            onCloseSearch = viewModel::closeSearch,
            onSearchText = viewModel::onSearchText,
            onConfirmSearch = viewModel::confirmSearch,
            onApplyPoint = viewModel::applyPoint,
            onOpenSavePoint = viewModel::openSavePoint,
            onDismissSavePoint = viewModel::dismissSavePoint,
            onSaveLabel = viewModel::onSaveLabel,
            onSaveCustomTag = viewModel::onSaveCustomTag,
            onSaveToggleTag = viewModel::onSaveToggleTag,
            onSaveAddCustomTag = viewModel::onSaveAddCustomTag,
            onConfirmSavePoint = viewModel::confirmSavePoint,
            onClearHistory = viewModel::clearHistory,
        ),
        modifier = modifier,
    )
}

private object MockLocationAccessIntents {
    /** Developer options when enabled; otherwise "About phone", where tapping Build number enables them. */
    fun developerOptions(context: Context, enabled: Boolean): Intent =
        Intent(if (enabled) Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS else Settings.ACTION_DEVICE_INFO_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private fun FakeGpsMessage.labelRes(): Int = when (this) {
    FakeGpsMessage.NEED_TARGET -> R.string.fakegps_msg_need_target
    FakeGpsMessage.NEED_ROUTE -> R.string.fakegps_msg_need_route
    FakeGpsMessage.NOT_MOCK_APP -> R.string.fakegps_msg_not_mock_app
    FakeGpsMessage.STOPPED -> R.string.fakegps_msg_stopped
    FakeGpsMessage.MOCK_APP_SET -> R.string.fakegps_msg_mock_app_set
    FakeGpsMessage.MOCK_APP_REJECTED -> R.string.fakegps_msg_mock_app_rejected
    FakeGpsMessage.MOCK_APP_NOT_CONFIRMED -> R.string.fakegps_msg_mock_app_not_confirmed
    FakeGpsMessage.SHIZUKU_UNAVAILABLE -> R.string.fakegps_msg_shizuku_unavailable
    FakeGpsMessage.POINT_SAVED -> R.string.fakegps_msg_point_saved
    FakeGpsMessage.SAVE_FAILED -> R.string.fakegps_msg_save_failed
    FakeGpsMessage.ROUTE_FULL -> R.string.fakegps_msg_route_full
    FakeGpsMessage.LOCATION_FAILED -> R.string.fakegps_msg_location_failed
    FakeGpsMessage.LOCATION_WHILE_RUNNING -> R.string.fakegps_msg_location_while_running
    FakeGpsMessage.PERMISSION_DENIED -> R.string.fakegps_msg_permission_denied
    FakeGpsMessage.LOCATION_OFF -> R.string.fakegps_msg_location_off
}
