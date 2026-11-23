package com.dragolabs.able.feature.tools.measure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleSlider
import com.dragolabs.able.core.designsystem.component.AbleFilledTonalIconButton
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.UnsupportedState
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import java.util.Locale
import kotlin.math.roundToInt
import com.dragolabs.able.core.designsystem.R as DsR

private const val CARD_WIDTH_MM = 85.60f
private const val CARD_HEIGHT_MM = 53.98f
private const val CARD_CORNER_MM = 3.18f

/** Screen ruler entry point (SENS-002). */
@Composable
fun ScreenRulerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ScreenRulerViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            ScreenRulerViewModel(
                hasTouchscreen = container.capabilities.evaluateCapabilities().hasTouchscreen,
                prefs = MeasurePreferences(container.appContext),
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Use the hosting window's metrics; re-read on configuration changes (rotation, display move).
    val configuration = LocalConfiguration.current
    val metrics = LocalContext.current.resources.displayMetrics
    LaunchedEffect(configuration, metrics.xdpi, metrics.ydpi, metrics.densityDpi) {
        viewModel.onDisplayMetrics(metrics.xdpi, metrics.ydpi, metrics.densityDpi)
    }

    ScreenRulerContent(
        state = state,
        onBack = onBack,
        onImperialChange = viewModel::setImperial,
        onScaleChange = viewModel::setScale,
        onScaleChangeFinished = viewModel::persistScale,
        onNudgeScale = viewModel::nudgeScale,
        onResetScale = viewModel::resetScale,
        onCardGuideChange = viewModel::setCardGuide,
        onTap = viewModel::onTap,
        onDrag = viewModel::onDrag,
        onClearMarkers = viewModel::clearMarkers,
        onCanvasResized = viewModel::onCanvasResized,
        modifier = modifier,
    )
}

@Composable
internal fun ScreenRulerContent(
    state: ScreenRulerUiState,
    onBack: () -> Unit,
    onImperialChange: (Boolean) -> Unit,
    onScaleChange: (Float) -> Unit,
    onScaleChangeFinished: () -> Unit,
    onNudgeScale: (Int) -> Unit,
    onResetScale: () -> Unit,
    onCardGuideChange: (Boolean) -> Unit,
    onTap: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onClearMarkers: () -> Unit,
    onCanvasResized: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AbleDetailScaffold(title = stringResource(R.string.ruler_title), onBack = onBack, modifier = modifier) { padding ->
        if (!state.supported) {
            UnsupportedState(reason = stringResource(R.string.ruler_unsupported), modifier = Modifier.padding(padding))
            return@AbleDetailScaffold
        }
        val distanceText = formatDistance(state)
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            val wide = maxWidth >= 600.dp
            val compactControlsMaxHeight = maxHeight * 0.5f
            val controls: @Composable (Modifier) -> Unit = { m ->
                RulerControls(
                    state = state,
                    distanceText = distanceText,
                    onImperialChange = onImperialChange,
                    onScaleChange = onScaleChange,
                    onScaleChangeFinished = onScaleChangeFinished,
                    onNudgeScale = onNudgeScale,
                    onResetScale = onResetScale,
                    onCardGuideChange = onCardGuideChange,
                    onClearMarkers = onClearMarkers,
                    startExpanded = wide,
                    modifier = m,
                )
            }
            if (wide) {
                // Wide windows: the ruler keeps the full height; controls move to a side panel.
                Row(Modifier.fillMaxSize()) {
                    RulerCanvas(state, distanceText, onTap, onDrag, onCanvasResized, Modifier.weight(1f).fillMaxHeight())
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(360.dp).fillMaxHeight()) {
                        controls(Modifier.verticalScroll(rememberScrollState()).padding(AbleSpacing.lg))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    RulerCanvas(state, distanceText, onTap, onDrag, onCanvasResized, Modifier.weight(1f).fillMaxWidth())
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        controls(
                            Modifier
                                .heightIn(max = compactControlsMaxHeight)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = AbleSpacing.lg, vertical = AbleSpacing.md),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun formatDistance(state: ScreenRulerUiState): String? {
    val mm = state.distanceMm ?: return null
    return if (state.imperial) {
        stringResource(R.string.ruler_value_imperial, String.format(Locale.getDefault(), "%.2f", mm / ScreenRulerUiState.MM_PER_INCH))
    } else {
        stringResource(
            R.string.ruler_value_metric,
            String.format(Locale.getDefault(), "%.1f", mm),
            String.format(Locale.getDefault(), "%.2f", mm / 10f),
        )
    }
}

@Composable
private fun RulerCanvas(
    state: ScreenRulerUiState,
    distanceText: String?,
    onTap: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onCanvasResized: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentTap by rememberUpdatedState(onTap)
    val currentDrag by rememberUpdatedState(onDrag)
    val textMeasurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val colors = RulerColors(
        background = scheme.surface,
        tick = scheme.onSurface,
        label = scheme.onSurfaceVariant,
        marker = scheme.primary,
        markerLine = scheme.tertiary,
        guide = scheme.secondary,
    )
    val labelStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = colors.label)
    val description = stringResource(R.string.ruler_area_desc, distanceText ?: stringResource(R.string.ruler_markers_hint))
    var lastSize by remember { mutableStateOf<IntSize?>(null) }

    Canvas(
        modifier = modifier
            .onSizeChanged { size ->
                if (lastSize != null && lastSize != size) onCanvasResized()
                lastSize = size
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { currentTap(it) }) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { currentDrag(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        currentDrag(change.position)
                    },
                )
            }
            .semantics { contentDescription = description },
    ) {
        drawRect(colors.background)
        if (state.pxPerMmX <= 0f || state.pxPerMmY <= 0f) return@Canvas
        if (state.showCardGuide) drawCardGuide(state, colors.guide)
        drawScale(state, horizontal = true, textMeasurer = textMeasurer, labelStyle = labelStyle, colors = colors)
        drawScale(state, horizontal = false, textMeasurer = textMeasurer, labelStyle = labelStyle, colors = colors)
        drawMarkers(state, colors)
    }
}

private data class RulerColors(
    val background: Color,
    val tick: Color,
    val label: Color,
    val marker: Color,
    val markerLine: Color,
    val guide: Color,
)

private fun DrawScope.drawScale(
    state: ScreenRulerUiState,
    horizontal: Boolean,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    colors: RulerColors,
) {
    val pxPerMm = if (horizontal) state.pxPerMmX else state.pxPerMmY
    val extent = if (horizontal) size.width else size.height
    val stroke = 1.dp.toPx()
    val long = 24.dp.toPx()
    // Each step is 1 mm (metric) or 1/16 inch (imperial).
    val stepPx = if (state.imperial) pxPerMm * ScreenRulerUiState.MM_PER_INCH / 16f else pxPerMm
    val majorEvery = if (state.imperial) 16 else 10
    val count = (extent / stepPx).toInt()
    for (i in 0..count) {
        val pos = i * stepPx
        val len = if (state.imperial) {
            when {
                i % 16 == 0 -> long
                i % 8 == 0 -> 18.dp.toPx()
                i % 4 == 0 -> 14.dp.toPx()
                i % 2 == 0 -> 10.dp.toPx()
                else -> 6.dp.toPx()
            }
        } else {
            when {
                i % 10 == 0 -> long
                i % 5 == 0 -> 16.dp.toPx()
                else -> 10.dp.toPx()
            }
        }
        val width = if (i % majorEvery == 0) stroke * 2f else stroke
        if (horizontal) {
            drawLine(colors.tick, Offset(pos, 0f), Offset(pos, len), width)
        } else {
            drawLine(colors.tick, Offset(0f, pos), Offset(len, pos), width)
        }
        if (i % majorEvery == 0 && i > 0) {
            val label = (i / majorEvery).toString()
            val layout = textMeasurer.measure(label, labelStyle)
            val topLeft = if (horizontal) {
                Offset(pos - layout.size.width / 2f, long + 2.dp.toPx())
            } else {
                Offset(long + 4.dp.toPx(), pos - layout.size.height / 2f)
            }
            drawText(layout, topLeft = topLeft)
        }
    }
}

private fun DrawScope.drawCardGuide(state: ScreenRulerUiState, color: Color) {
    // Orient the card so it fits the canvas: landscape when wide enough, otherwise portrait.
    val landscapeWidth = CARD_WIDTH_MM * state.pxPerMmX
    val landscape = landscapeWidth <= size.width
    val w = if (landscape) landscapeWidth else CARD_HEIGHT_MM * state.pxPerMmX
    val h = if (landscape) CARD_HEIGHT_MM * state.pxPerMmY else CARD_WIDTH_MM * state.pxPerMmY
    drawRoundRect(
        color = color.copy(alpha = 0.12f),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = CornerRadius(CARD_CORNER_MM * state.pxPerMmX),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = CornerRadius(CARD_CORNER_MM * state.pxPerMmX),
        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
    )
}

private fun DrawScope.drawMarkers(state: ScreenRulerUiState, colors: RulerColors) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
    val markers = listOfNotNull(state.markerA, state.markerB)
    markers.forEach { m ->
        drawLine(colors.marker.copy(alpha = 0.5f), Offset(0f, m.y), Offset(size.width, m.y), 1.dp.toPx(), pathEffect = dash)
        drawLine(colors.marker.copy(alpha = 0.5f), Offset(m.x, 0f), Offset(m.x, size.height), 1.dp.toPx(), pathEffect = dash)
    }
    val a = state.markerA
    val b = state.markerB
    if (a != null && b != null) drawLine(colors.markerLine, a, b, 3.dp.toPx())
    markers.forEach { m ->
        drawCircle(colors.marker, radius = 10.dp.toPx(), center = m, style = Stroke(3.dp.toPx()))
        drawCircle(colors.marker, radius = 3.dp.toPx(), center = m)
    }
}

@Composable
private fun RulerControls(
    state: ScreenRulerUiState,
    distanceText: String?,
    onImperialChange: (Boolean) -> Unit,
    onScaleChange: (Float) -> Unit,
    onScaleChangeFinished: () -> Unit,
    onNudgeScale: (Int) -> Unit,
    onResetScale: () -> Unit,
    onCardGuideChange: (Boolean) -> Unit,
    onClearMarkers: () -> Unit,
    startExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    var calibrationExpanded by rememberSaveable { mutableStateOf(startExpanded) }
    val sliderState = remember {
        SliderState(state.scale, 0, ScreenRulerViewModel.MIN_SCALE..ScreenRulerViewModel.MAX_SCALE)
    }
    // Keep the slider in sync with fine-step buttons and reset.
    LaunchedEffect(state.scale) { if (sliderState.value != state.scale) sliderState.value = state.scale }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        ConnectedChoiceGroup(
            options = listOf(stringResource(R.string.ruler_unit_metric), stringResource(R.string.ruler_unit_imperial)),
            selectedIndex = if (state.imperial) 1 else 0,
            onSelect = { onImperialChange(it == 1) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ruler_distance),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    distanceText ?: stringResource(R.string.ruler_markers_hint),
                    style = if (distanceText != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
                )
            }
            AbleTextButton(
                onClick = onClearMarkers,
                enabled = state.markerA != null,
                modifier = Modifier.focusRing(),
            ) { Text(stringResource(R.string.ruler_clear_markers)) }
        }
        AbleTextButton(
            onClick = { calibrationExpanded = !calibrationExpanded },
            modifier = Modifier.focusRing(),
        ) {
            Text(stringResource(R.string.ruler_calibration))
            Icon(if (calibrationExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
        }
        if (calibrationExpanded) {
            if (state.dpiSuspicious) {
                StatusBanner(level = StatusLevel.WARNING, title = stringResource(R.string.ruler_dpi_suspicious))
            }
            Text(
                stringResource(R.string.ruler_calibration_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                AbleFilledTonalIconButton(onClick = { onNudgeScale(-1) }, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.ruler_scale_decrease))
                }
                AbleSlider(
                    state = sliderState,
                    onValueChange = {
                        sliderState.value = it
                        onScaleChange(it)
                    },
                    onValueChangeFinished = onScaleChangeFinished,
                    modifier = Modifier.weight(1f).focusRing(),
                )
                AbleFilledTonalIconButton(onClick = { onNudgeScale(1) }, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.ruler_scale_increase))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        R.string.ruler_calibration_value,
                        String.format(Locale.getDefault(), "%.1f", state.scale * 100f),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                AbleTextButton(
                    onClick = onResetScale,
                    enabled = (state.scale * 1000f).roundToInt() != 1000,
                    modifier = Modifier.focusRing(),
                ) { Text(stringResource(DsR.string.ds_reset)) }
            }
            ListGroup {
                item { shape ->
                    SwitchRow(
                        title = stringResource(R.string.ruler_card_guide),
                        checked = state.showCardGuide,
                        onCheckedChange = onCardGuideChange,
                        shape = shape,
                        icon = Icons.Rounded.CreditCard,
                    )
                }
            }
            Text(
                stringResource(
                    R.string.ruler_dpi_info,
                    state.reportedXdpi.roundToInt().toString(),
                    state.reportedYdpi.roundToInt().toString(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
