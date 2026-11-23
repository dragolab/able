package com.dragolabs.able.feature.tools.fountains

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.feature.tools.R
import kotlinx.coroutines.launch
import java.util.Locale
import com.dragolabs.able.core.designsystem.R as DsR

/**
 * Drinking fountains entry point (MAP-001): public drinking-water points from OpenStreetMap
 * (Overpass API) on an osmdroid map. Owns the ViewModel, the in-context location permission
 * request and the outgoing intents (navigate, share, open in OpenStreetMap).
 */
@Composable
fun FountainsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: FountainsViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            val ctx = container.appContext
            FountainsViewModel(
                appContext = ctx,
                repository = FountainsRepository.get(ctx),
                location = container.location,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermission() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        viewModel.onPermissionResult(result[Manifest.permission.ACCESS_FINE_LOCATION] == true)
    }

    val messageText = state.message?.let { stringResource(it.labelRes()) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    val noHandler = stringResource(DsR.string.ds_no_handler)
    val navigateChooser = stringResource(R.string.fountains_navigate_chooser)
    val shareChooser = stringResource(R.string.fountains_share_chooser)
    val kindLabels = FountainKind.entries.associateWith { stringResource(it.labelRes()) }
    val shareTemplate = stringResource(R.string.fountains_share_text)

    fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    fun failed() {
        scope.launch { snackbarHostState.showSnackbar(noHandler) }
    }

    FountainsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        events = FountainsEvents(
            onViewport = viewModel::onViewport,
            onSelect = viewModel::select,
            onClearSelection = viewModel::clearSelection,
            onLocateMe = viewModel::locateMe,
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            },
            onRefresh = viewModel::refresh,
            onBottleOnly = viewModel::setBottleOnly,
            onWheelchairOnly = viewModel::setWheelchairOnly,
            onNavigate = { f ->
                // Walking directions in Google Maps (app or web); otherwise any map app via geo:.
                val directions = Intent(Intent.ACTION_VIEW, Uri.parse(FountainLinks.walkingDirections(f.position)))
                if (!start(directions)) {
                    val geo = Intent(Intent.ACTION_VIEW, Uri.parse(FountainLinks.geoUri(f.position, f.name ?: kindLabels.getValue(f.kind))))
                    if (!start(Intent.createChooser(geo, navigateChooser))) failed()
                }
            },
            onShare = { f ->
                val text = String.format(
                    shareTemplate,
                    f.name ?: kindLabels.getValue(f.kind),
                    FountainLinks.coordinates(f.position),
                    "${f.osmType.path}/${f.osmId}",
                )
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                if (!start(Intent.createChooser(send, shareChooser))) failed()
            },
            onOpenOsm = { f ->
                if (!start(Intent(Intent.ACTION_VIEW, Uri.parse(FountainLinks.osmElement(f))))) failed()
            },
        ),
        modifier = modifier,
    )
}

/** Outgoing links (pure, unit-tested). */
internal object FountainLinks {
    private fun Double.fmt() = String.format(Locale.ROOT, "%.6f", this)

    fun coordinates(p: GeoPos): String = "${p.latitude.fmt()}, ${p.longitude.fmt()}"

    fun walkingDirections(p: GeoPos): String =
        "https://www.google.com/maps/dir/?api=1&destination=${p.latitude.fmt()},${p.longitude.fmt()}&travelmode=walking"

    fun geoUri(p: GeoPos, label: String): String {
        val safe = java.net.URLEncoder.encode(label, Charsets.UTF_8.name()).replace("+", "%20")
        return "geo:${p.latitude.fmt()},${p.longitude.fmt()}?q=${p.latitude.fmt()},${p.longitude.fmt()}($safe)"
    }

    fun osmElement(f: Fountain): String = "https://www.openstreetmap.org/${f.osmType.path}/${f.osmId}"
}

private fun FountainsMessage.labelRes(): Int = when (this) {
    FountainsMessage.PERMISSION_DENIED -> R.string.fountains_msg_permission_denied
    FountainsMessage.LOCATION_OFF -> R.string.fountains_msg_location_off
    FountainsMessage.LOCATION_FAILED -> R.string.fountains_msg_location_failed
    FountainsMessage.RETRY_LATER -> R.string.fountains_msg_retry_later
}
