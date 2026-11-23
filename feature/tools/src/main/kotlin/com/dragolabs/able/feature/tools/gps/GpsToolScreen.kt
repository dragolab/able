package com.dragolabs.able.feature.tools.gps

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.model.GpsPoint
import com.dragolabs.able.feature.tools.R
import kotlinx.coroutines.launch
import com.dragolabs.able.core.designsystem.R as DsR

/**
 * GPS coordinate saver and catalog entry point (GPS-001, GPS-002). Stateful route: owns the
 * ViewModel, the contextual location permission request and the Android intents for copy,
 * share and map launch.
 */
@Composable
fun GpsToolScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    com.dragolabs.able.core.designsystem.component.ClearRestoredFocusOnTouch()
    val viewModel: GpsViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            val ctx = container.appContext
            GpsViewModel(
                appContext = ctx,
                location = container.location,
                dao = container.database.gpsDao(),
                presetLabels = mapOf(
                    GpsTags.PARKING to ctx.getString(R.string.gps_tag_parking),
                    GpsTags.TREKKING to ctx.getString(R.string.gps_tag_trekking),
                    GpsTags.WORK to ctx.getString(R.string.gps_tag_work),
                    GpsTags.PHOTOGRAPHY to ctx.getString(R.string.gps_tag_photography),
                ),
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshEnvironment() }

    // Fine + coarse together, as Android 12+ requires; the user may still grant approximate only.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        viewModel.onPermissionResult(
            fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true,
            coarseGranted = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true,
        )
    }

    val messageText = state.message?.let { stringResource(it.labelRes()) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    val noHandler = stringResource(DsR.string.ds_no_handler)
    val chooserTitle = stringResource(R.string.gps_share_chooser)
    fun launch(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar(noHandler) }
        } catch (_: SecurityException) {
            scope.launch { snackbarHostState.showSnackbar(noHandler) }
        }
    }

    val actions = GpsPointActions(
        onCopy = { text -> copyToClipboard(context, chooserTitle, text) },
        onShare = { text ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            launch(Intent.createChooser(send, chooserTitle))
        },
        onOpenMap = { point: GpsPoint, label: String ->
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(GpsFormat.geoUri(point, label))))
        },
    )

    GpsContent(
        state = state,
        actions = actions,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onCapture = {
            if (state.permission == LocationPermissionStatus.Granted) {
                viewModel.capture()
            } else {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            }
        },
        onCancelCapture = viewModel::cancelCapture,
        onDismissCaptureError = viewModel::dismissCaptureError,
        onOpenAppSettings = { launch(context.ableContainer.permissions.getAppSettingsIntent()) },
        onOpenLocationSettings = {
            launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        onQueryChange = viewModel::onQueryChange,
        onTagFilterChange = viewModel::onTagFilterChange,
        onOpenSheet = viewModel::openSheet,
        onCloseSheet = viewModel::closeSheet,
        onSaveCaptured = viewModel::openEditorForCaptured,
        onEdit = viewModel::openEditor,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
        editorEvents = GpsEditorEvents(
            onLabelChange = viewModel::onEditorLabelChange,
            onNotesChange = viewModel::onEditorNotesChange,
            onCustomTagChange = viewModel::onEditorCustomTagChange,
            onToggleTag = viewModel::onEditorToggleTag,
            onAddCustomTag = viewModel::onEditorAddCustomTag,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::dismissEditor,
        ),
        modifier = modifier,
    )
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    // Android 13+ shows its own clipboard confirmation, so no extra snackbar is needed.
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun GpsMessage.labelRes(): Int = when (this) {
    GpsMessage.Saved -> R.string.gps_saved
    GpsMessage.Updated -> R.string.gps_updated
    GpsMessage.Deleted -> R.string.gps_deleted
    GpsMessage.SaveFailed -> R.string.gps_save_failed
}
