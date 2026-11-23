package com.dragolabs.able.feature.tools.measure

import android.content.Context
import android.hardware.SensorManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleButtonFlow
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.LoadingState
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.UnsupportedState
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.designsystem.theme.AbleThemeExtras
import com.dragolabs.able.feature.tools.R

/** Spirit level / inclinometer entry point (SENS-001). */
@Composable
fun SpiritLevelScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SpiritLevelViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            SpiritLevelViewModel(
                sensorManager = container.appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager,
                prefs = MeasurePreferences(container.appContext),
            )
        },
    )
    // RESUMED: the sensor flow is cancelled (listener unregistered) as soon as the screen pauses.
    val state by viewModel.uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val rotation by rememberDisplayRotation()
    LaunchedEffect(rotation) { viewModel.onDisplayRotationChanged(rotation) }

    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.levelReached.collect { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
        }
    }

    SpiritLevelContent(
        state = state,
        onBack = onBack,
        onSetZero = viewModel::setZero,
        onResetZero = viewModel::resetZero,
        onHapticsChange = viewModel::setHapticsEnabled,
        modifier = modifier,
    )
}

@Composable
internal fun SpiritLevelContent(
    state: SpiritLevelUiState,
    onBack: () -> Unit,
    onSetZero: () -> Unit,
    onResetZero: () -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AbleDetailScaffold(title = stringResource(R.string.level_title), onBack = onBack, modifier = modifier) { padding ->
        when {
            !state.supported -> UnsupportedState(
                reason = stringResource(R.string.level_unsupported),
                modifier = Modifier.padding(padding),
            )

            !state.hasReading -> LoadingState(
                message = stringResource(R.string.level_waiting),
                modifier = Modifier.padding(padding),
            )

            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > maxHeight && maxWidth >= 600.dp
                val scroll = rememberScrollState()
                if (wide) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(screenContentPadding(padding)),
                        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.xl),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LevelGauge(state, Modifier.weight(1f).widthIn(max = 480.dp))
                        LevelControls(state, onSetZero, onResetZero, onHapticsChange, Modifier.weight(1f))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(screenContentPadding(padding)),
                        verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LevelGauge(state, Modifier.widthIn(max = 480.dp).fillMaxWidth())
                        LevelControls(state, onSetZero, onResetZero, onHapticsChange, Modifier.widthIn(max = 600.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelGauge(state: SpiritLevelUiState, modifier: Modifier = Modifier) {
    val pitchText = formatTenths(state.pitchDegrees)
    val rollText = formatTenths(state.rollDegrees)
    val description = stringResource(R.string.level_bubble_desc, pitchText, rollText)
    val scheme = MaterialTheme.colorScheme
    val success = AbleThemeExtras.semantic.success
    val bubbleColor = if (state.isLevel) success else scheme.primary
    val trackColor = scheme.surfaceContainerHighest
    val guideColor = scheme.outline
    val targetColor = if (state.isLevel) success else scheme.outlineVariant

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
    ) {
        StatusPill(
            text = stringResource(if (state.isLevel) R.string.level_status_level else R.string.level_status_tilted),
            level = if (state.isLevel) StatusLevel.SUCCESS else StatusLevel.INFO,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics { contentDescription = description },
        ) {
            drawBullseye(state.bubbleX, state.bubbleY, trackColor, guideColor, targetColor, bubbleColor)
        }
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            drawTube(position = state.bubbleX, track = trackColor, guide = guideColor, bubble = bubbleColor)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md),
        ) {
            AngleReadout(stringResource(R.string.level_pitch), pitchText, Modifier.weight(1f))
            AngleReadout(stringResource(R.string.level_roll), rollText, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AngleReadout(label: String, value: String, modifier: Modifier = Modifier) {
    AbleCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.level_degrees, value),
            style = MaterialTheme.typography.displaySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun LevelControls(
    state: SpiritLevelUiState,
    onSetZero: () -> Unit,
    onResetZero: () -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
        Text(
            text = stringResource(if (state.zeroActive) R.string.level_zero_active else R.string.level_zero_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AbleButtonFlow {
            AbleButton(onClick = onSetZero, modifier = Modifier.weight(1f).focusRing(), haptic = AbleHaptic.Confirm) {
                Text(stringResource(R.string.level_zero), maxLines = 1)
            }
            AbleOutlinedButton(
                onClick = onResetZero,
                enabled = state.zeroActive,
                modifier = Modifier.weight(1f).focusRing(),
            ) { Text(stringResource(R.string.level_zero_reset), maxLines = 1) }
        }
        ListGroup {
            item { shape ->
                SwitchRow(
                    title = stringResource(R.string.level_haptics),
                    supportingText = stringResource(R.string.level_haptics_hint),
                    checked = state.hapticsEnabled,
                    onCheckedChange = onHapticsChange,
                    shape = shape,
                    icon = Icons.Rounded.Vibration,
                )
            }
        }
    }
}

private fun DrawScope.drawBullseye(
    bubbleX: Float,
    bubbleY: Float,
    track: Color,
    guide: Color,
    target: Color,
    bubble: Color,
) {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val bubbleRadius = radius * 0.16f
    val travel = radius - bubbleRadius - 4.dp.toPx()
    drawCircle(color = track, radius = radius, center = center)
    drawCircle(color = guide, radius = radius * 0.5f, center = center, style = Stroke(1.dp.toPx()))
    drawLine(guide, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
    drawLine(guide, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
    // Target ring sized to the bubble: the bubble fits inside when level.
    drawCircle(color = target, radius = bubbleRadius + 6.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
    val bubbleCenter = Offset(center.x + bubbleX * travel, center.y + bubbleY * travel)
    drawCircle(color = bubble.copy(alpha = 0.85f), radius = bubbleRadius, center = bubbleCenter)
    drawCircle(color = Color.White.copy(alpha = 0.35f), radius = bubbleRadius * 0.35f,
        center = bubbleCenter + Offset(-bubbleRadius * 0.3f, -bubbleRadius * 0.3f))
}

/** Horizontal tube vial for the roll axis. */
private fun DrawScope.drawTube(position: Float, track: Color, guide: Color, bubble: Color) {
    val corner = CornerRadius(size.minDimension / 2f)
    drawRoundRect(color = track, cornerRadius = corner)
    val length = size.width
    val thickness = size.height
    val bubbleLength = thickness * 1.6f
    val travel = (length - bubbleLength) / 2f
    val mid = length / 2f
    val markOffset = bubbleLength / 2f + 2.dp.toPx()
    drawLine(guide, Offset(mid - markOffset, 0f), Offset(mid - markOffset, size.height), 2.dp.toPx())
    drawLine(guide, Offset(mid + markOffset, 0f), Offset(mid + markOffset, size.height), 2.dp.toPx())
    val start = mid + position * travel - bubbleLength / 2f
    drawRoundRect(
        color = bubble.copy(alpha = 0.85f),
        topLeft = Offset(start, thickness * 0.15f),
        size = Size(bubbleLength, thickness * 0.7f),
        cornerRadius = CornerRadius(thickness * 0.35f),
    )
}
