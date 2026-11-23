package com.dragolabs.able.feature.tools.measure

import android.Manifest
import android.content.Context
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.LoadingState
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.UnsupportedState
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import kotlin.math.abs
import kotlin.math.roundToInt
import com.dragolabs.able.core.designsystem.R as DsR

/** Digital compass entry point (SENS-003). */
@Composable
fun CompassScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: CompassViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            CompassViewModel(
                appContext = container.appContext,
                sensorManager = container.appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager,
                prefs = MeasurePreferences(container.appContext),
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val rotation by rememberDisplayRotation()
    LaunchedEffect(rotation) { viewModel.onDisplayRotationChanged(rotation) }
    // Permission may change in Android settings while the screen is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDeclination() }

    val context = LocalContext.current
    // Approximate location is enough for magnetic declination; requested only from this screen.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onLocationPermissionResult(granted)
    }

    CompassContent(
        state = state,
        onBack = onBack,
        onReferenceChange = viewModel::setTrueNorthPreferred,
        onRequestLocation = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
        onOpenAppSettings = {
            runCatching { context.startActivity(context.ableContainer.permissions.getAppSettingsIntent()) }
        },
        onCheckLocationAgain = viewModel::refreshDeclination,
        modifier = modifier,
    )
}

@Composable
internal fun CompassContent(
    state: CompassUiState,
    onBack: () -> Unit,
    onReferenceChange: (Boolean) -> Unit,
    onRequestLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onCheckLocationAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AbleDetailScaffold(title = stringResource(R.string.compass_title), onBack = onBack, modifier = modifier) { padding ->
        when {
            !state.supported -> UnsupportedState(
                reason = stringResource(R.string.compass_unsupported),
                modifier = Modifier.padding(padding),
            )

            !state.hasReading -> LoadingState(
                message = stringResource(R.string.compass_waiting),
                modifier = Modifier.padding(padding),
            )

            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > maxHeight && maxWidth >= 600.dp
                val scroll = rememberScrollState()
                val details: @Composable (Modifier) -> Unit = { m ->
                    CompassDetails(state, onReferenceChange, onRequestLocation, onOpenAppSettings, onCheckLocationAgain, m)
                }
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(screenContentPadding(padding)),
                        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.xl),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompassDial(state, Modifier.weight(1f).widthIn(max = 440.dp))
                        details(Modifier.weight(1f))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(screenContentPadding(padding)),
                        verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CompassDial(state, Modifier.widthIn(max = 440.dp).fillMaxWidth())
                        details(Modifier.widthIn(max = 600.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun cardinalLabels(): List<String> = listOf(
    stringResource(R.string.compass_cardinal_n),
    stringResource(R.string.compass_cardinal_ne),
    stringResource(R.string.compass_cardinal_e),
    stringResource(R.string.compass_cardinal_se),
    stringResource(R.string.compass_cardinal_s),
    stringResource(R.string.compass_cardinal_sw),
    stringResource(R.string.compass_cardinal_w),
    stringResource(R.string.compass_cardinal_nw),
)

@Composable
private fun CompassDial(state: CompassUiState, modifier: Modifier = Modifier) {
    val cardinals = cardinalLabels()
    val headingText = state.headingDegrees.roundToInt().mod(360).toString()
    val cardinal = cardinals[state.cardinalIndex]
    val reference = stringResource(
        if (state.usingTrueNorth) R.string.compass_reference_true else R.string.compass_reference_magnetic,
    )
    val description = stringResource(R.string.compass_heading_desc, headingText, cardinal, reference)
    val scheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val colors = DialColors(
        face = scheme.surfaceContainerHigh,
        tick = scheme.onSurfaceVariant,
        majorTick = scheme.onSurface,
        north = scheme.error,
        lubber = scheme.primary,
        label = scheme.onSurface,
    )
    val cardinalStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.label)
    val northStyle = cardinalStyle.copy(color = colors.north)
    val degreeStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = colors.tick)
    // Only the four main points are drawn on the card; intercardinals appear in the readout.
    val mainCardinals = listOf(cardinals[0], cardinals[2], cardinals[4], cardinals[6])

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawDial(state.headingDegrees, colors, textMeasurer, mainCardinals, cardinalStyle, northStyle, degreeStyle)
        }
        Surface(
            shape = MaterialShapes.Cookie12Sided.toShape(),
            color = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            modifier = Modifier.size(132.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.compass_heading_value, headingText),
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = FontFamily.Monospace,
                )
                Text(cardinal, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private data class DialColors(
    val face: Color,
    val tick: Color,
    val majorTick: Color,
    val north: Color,
    val lubber: Color,
    val label: Color,
)

private fun DrawScope.drawDial(
    heading: Float,
    colors: DialColors,
    textMeasurer: TextMeasurer,
    cardinals: List<String>,
    cardinalStyle: TextStyle,
    northStyle: TextStyle,
    degreeStyle: TextStyle,
) {
    val radius = size.minDimension / 2f - 8.dp.toPx()
    val c = center
    drawCircle(colors.face, radius = radius, center = c)
    drawCircle(colors.tick.copy(alpha = 0.4f), radius = radius, center = c, style = Stroke(1.dp.toPx()))

    // The card rotates so that the current heading sits under the fixed lubber line.
    rotate(degrees = -heading, pivot = c) {
        for (deg in 0 until 360 step 5) {
            val major = deg % 30 == 0
            val len = when {
                deg % 90 == 0 -> 18.dp.toPx()
                major -> 14.dp.toPx()
                else -> 7.dp.toPx()
            }
            rotate(degrees = deg.toFloat(), pivot = c) {
                drawLine(
                    color = if (deg == 0) colors.north else if (major) colors.majorTick else colors.tick,
                    start = Offset(c.x, c.y - radius + 2.dp.toPx()),
                    end = Offset(c.x, c.y - radius + 2.dp.toPx() + len),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                )
                if (deg % 90 == 0) {
                    val label = cardinals[deg / 90]
                    val layout = textMeasurer.measure(label, if (deg == 0) northStyle else cardinalStyle)
                    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - radius + 24.dp.toPx()))
                } else if (major) {
                    val layout = textMeasurer.measure(deg.toString(), degreeStyle)
                    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - radius + 20.dp.toPx()))
                }
            }
        }
        // North pointer triangle on the card.
        val tip = Offset(c.x, c.y - radius + 52.dp.toPx())
        val half = 9.dp.toPx()
        val north = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - half, tip.y + 16.dp.toPx())
            lineTo(tip.x + half, tip.y + 16.dp.toPx())
            close()
        }
        drawPath(north, colors.north)
    }

    // Fixed lubber line marking the direction the device points to.
    val top = c.y - radius - 6.dp.toPx()
    val lubber = Path().apply {
        moveTo(c.x, top + 18.dp.toPx())
        lineTo(c.x - 10.dp.toPx(), top)
        lineTo(c.x + 10.dp.toPx(), top)
        close()
    }
    drawPath(lubber, colors.lubber)
}

@Composable
private fun CompassDetails(
    state: CompassUiState,
    onReferenceChange: (Boolean) -> Unit,
    onRequestLocation: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onCheckLocationAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            Text(
                stringResource(R.string.compass_accuracy),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            StatusPill(text = stringResource(state.accuracy.labelRes()), level = state.accuracy.statusLevel())
        }

        ConnectedChoiceGroup(
            options = listOf(
                stringResource(R.string.compass_reference_magnetic),
                stringResource(R.string.compass_reference_true),
            ),
            selectedIndex = if (state.usingTrueNorth) 1 else 0,
            onSelect = { onReferenceChange(it == 1) },
            enabled = { index -> index == 0 || state.trueNorthStatus == TrueNorthStatus.Available },
        )

        when (state.trueNorthStatus) {
            TrueNorthStatus.Available -> state.declinationDegrees?.let { decl ->
                Text(
                    stringResource(R.string.compass_declination, formatSignedTenths(decl)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TrueNorthStatus.PermissionNeeded -> StatusBanner(
                level = StatusLevel.INFO,
                title = stringResource(R.string.compass_true_permission_title),
                message = stringResource(R.string.compass_true_permission_msg),
                actionLabel = stringResource(R.string.compass_true_grant),
                onAction = onRequestLocation,
            )

            TrueNorthStatus.PermissionDenied -> StatusBanner(
                level = StatusLevel.WARNING,
                title = stringResource(R.string.compass_true_denied_title),
                message = stringResource(R.string.compass_true_denied_msg),
                actionLabel = stringResource(DsR.string.ds_open_android_settings),
                onAction = onOpenAppSettings,
            )

            TrueNorthStatus.NoLocation -> StatusBanner(
                level = StatusLevel.INFO,
                title = stringResource(R.string.compass_true_no_location_title),
                message = stringResource(R.string.compass_true_no_location_msg),
                actionLabel = stringResource(R.string.compass_check_again),
                onAction = onCheckLocationAgain,
            )
        }

        StatusBanner(
            level = if (state.needsCalibration) StatusLevel.WARNING else StatusLevel.INFO,
            title = stringResource(R.string.compass_calibrate_title),
            message = stringResource(R.string.compass_calibrate_msg),
        )
        Text(
            stringResource(R.string.compass_interference),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatSignedTenths(value: Float): String {
    val text = formatTenths(abs(value))
    return when {
        text == formatTenths(0f) -> text
        value > 0f -> "+$text"
        else -> "−$text"
    }
}

private fun CompassAccuracy.labelRes(): Int = when (this) {
    CompassAccuracy.High -> R.string.compass_accuracy_high
    CompassAccuracy.Medium -> R.string.compass_accuracy_medium
    CompassAccuracy.Low -> R.string.compass_accuracy_low
    CompassAccuracy.Unreliable -> R.string.compass_accuracy_unreliable
    CompassAccuracy.Unknown -> R.string.compass_accuracy_unknown
}

private fun CompassAccuracy.statusLevel(): StatusLevel = when (this) {
    CompassAccuracy.High -> StatusLevel.SUCCESS
    CompassAccuracy.Medium -> StatusLevel.INFO
    CompassAccuracy.Low -> StatusLevel.WARNING
    CompassAccuracy.Unreliable -> StatusLevel.CRITICAL
    CompassAccuracy.Unknown -> StatusLevel.INFO
}
