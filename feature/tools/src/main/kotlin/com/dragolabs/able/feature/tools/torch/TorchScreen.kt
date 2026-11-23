package com.dragolabs.able.feature.tools.torch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleToggleButton
import com.dragolabs.able.core.designsystem.component.AbleSlider
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ExpressiveIcon
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.UnsupportedState
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.MessageEffect
import com.dragolabs.able.core.designsystem.R as DsR

@Composable
fun TorchRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TorchViewModel = viewModel(
        factory = ableViewModelFactory { container -> TorchViewModel(TorchController(container.appContext)) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Flashing patterns never outlive the screen (TORCH-002 explicit stop behavior).
    DisposableEffect(viewModel) { onDispose { viewModel.stopPattern(turnOff = true) } }
    TorchScreen(
        state = state,
        onBack = onBack,
        onToggleSteady = viewModel::toggleSteady,
        onSelectMode = viewModel::selectMode,
        onStrengthChange = viewModel::setStrength,
        onMorseTextChange = viewModel::setMorseText,
        onIntervalChange = viewModel::setInterval,
        onStartPattern = viewModel::startPattern,
        onStopPattern = { viewModel.stopPattern(turnOff = true) },
        onMessageConsumed = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@Composable
fun TorchScreen(
    state: TorchUiState,
    onBack: () -> Unit,
    onToggleSteady: () -> Unit,
    onSelectMode: (TorchMode) -> Unit,
    onStrengthChange: (Int) -> Unit,
    onMorseTextChange: (String) -> Unit,
    onIntervalChange: (Long) -> Unit,
    onStartPattern: () -> Unit,
    onStopPattern: () -> Unit,
    onMessageConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, onMessageConsumed)
    AbleDetailScaffold(
        title = stringResource(R.string.tools_torch_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        floatingActionButton = {
            if (state.available) TorchPrimaryAction(state, onToggleSteady, onStartPattern, onStopPattern)
        },
    ) { padding ->
        if (!state.available) {
            UnsupportedState(reason = stringResource(R.string.tools_reason_no_flash), modifier = Modifier.padding(padding))
            return@AbleDetailScaffold
        }
        ReadableWidth {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(screenContentPadding(padding)),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
            ) {
                TorchStatusCard(state)
                if (state.cameraBusy) {
                    StatusBanner(
                        level = StatusLevel.WARNING,
                        title = stringResource(R.string.tools_torch_busy_title),
                        message = stringResource(R.string.tools_torch_busy_message),
                    )
                }
                TorchModeSelector(selected = state.mode, onSelect = onSelectMode)
                when (state.mode) {
                    TorchMode.STEADY -> Unit
                    TorchMode.SOS -> Text(
                        stringResource(R.string.tools_torch_sos_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TorchMode.MORSE -> OutlinedTextField(
                        value = state.morseText,
                        onValueChange = onMorseTextChange,
                        label = { Text(stringResource(R.string.tools_torch_morse_label)) },
                        supportingText = { Text(stringResource(R.string.tools_torch_morse_hint)) },
                        singleLine = true,
                        enabled = !state.patternRunning,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TorchMode.INTERVAL -> IntervalChips(state.intervalMs, onIntervalChange)
                }
                StrengthCard(state, onStrengthChange)
                Text(
                    stringResource(R.string.tools_torch_stop_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TorchStatusCard(state: TorchUiState) {
    val (label, level) = when {
        state.cameraBusy -> stringResource(R.string.tools_torch_status_busy) to StatusLevel.WARNING
        state.patternRunning -> stringResource(R.string.tools_torch_status_flashing) to StatusLevel.INFO
        state.isOn -> stringResource(DsR.string.ds_on) to StatusLevel.SUCCESS
        else -> stringResource(DsR.string.ds_off) to StatusLevel.INFO
    }
    AbleCard(style = AbleCardStyle.Hero) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg)) {
            ExpressiveIcon(
                icon = if (state.isOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                shape = if (state.isOn) MaterialShapes.Sunny else MaterialShapes.Cookie9Sided,
                containerColor = if (state.isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (state.isOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 72.dp,
                iconSize = 36.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                Text(stringResource(R.string.tools_torch_title), style = MaterialTheme.typography.titleLarge)
                StatusPill(text = label, level = level)
            }
        }
    }
}

@Composable
private fun TorchModeSelector(selected: TorchMode, onSelect: (TorchMode) -> Unit) {
    val modes = TorchMode.entries
    Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        Text(stringResource(R.string.tools_torch_mode), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            modes.forEachIndexed { index, mode ->
                AbleToggleButton(
                    checked = mode == selected,
                    onCheckedChange = { onSelect(mode) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRing()
                        .semantics { role = Role.RadioButton },
                ) {
                    Text(stringResource(mode.labelRes()), maxLines = 1)
                }
            }
        }
    }
}

private fun TorchMode.labelRes(): Int = when (this) {
    TorchMode.STEADY -> R.string.tools_torch_mode_steady
    TorchMode.SOS -> R.string.tools_torch_mode_sos
    TorchMode.MORSE -> R.string.tools_torch_mode_morse
    TorchMode.INTERVAL -> R.string.tools_torch_mode_interval
}

@Composable
private fun IntervalChips(selectedMs: Long, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        TorchPatterns.INTERVAL_OPTIONS_MS.forEach { ms ->
            AbleFilterChip(
                selected = ms == selectedMs,
                onClick = { onSelect(ms) },
                label = { Text(stringResource(R.string.tools_torch_interval_ms, ms)) },
            )
        }
    }
}

@Composable
private fun StrengthCard(state: TorchUiState, onStrengthChange: (Int) -> Unit) {
    AbleCard(style = AbleCardStyle.Tonal, title = stringResource(R.string.tools_torch_strength)) {
        if (state.strengthSupported) {
            val levelText = stringResource(R.string.tools_torch_strength_value, state.strength, state.maxStrength)
            Text(levelText, style = MaterialTheme.typography.bodyMedium)
            AbleSlider(
                value = state.strength.toFloat(),
                onValueChange = { onStrengthChange(it.toInt()) },
                valueRange = 1f..state.maxStrength.toFloat(),
                steps = (state.maxStrength - 2).coerceAtLeast(0),
                enabled = !state.patternRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = levelText },
            )
        } else {
            Text(
                stringResource(R.string.tools_torch_strength_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TorchPrimaryAction(
    state: TorchUiState,
    onToggleSteady: () -> Unit,
    onStartPattern: () -> Unit,
    onStopPattern: () -> Unit,
) {
    when {
        state.patternRunning -> ExtendedFloatingActionButton(
            onClick = onStopPattern,
            icon = { Icon(Icons.Rounded.Stop, contentDescription = null) },
            text = { Text(stringResource(R.string.tools_torch_stop)) },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.focusRing(),
        )
        state.mode == TorchMode.STEADY -> ExtendedFloatingActionButton(
            onClick = onToggleSteady,
            icon = { Icon(if (state.isOn) Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn, contentDescription = null) },
            text = { Text(stringResource(if (state.isOn) R.string.tools_torch_turn_off else R.string.tools_torch_turn_on)) },
            modifier = Modifier.focusRing(),
        )
        else -> ExtendedFloatingActionButton(
            onClick = onStartPattern,
            icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
            text = { Text(stringResource(R.string.tools_torch_start)) },
            modifier = Modifier.focusRing(),
        )
    }
}
