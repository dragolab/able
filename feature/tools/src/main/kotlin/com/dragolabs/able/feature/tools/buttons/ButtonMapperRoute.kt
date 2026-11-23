package com.dragolabs.able.feature.tools.buttons

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ScreenLockController
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.ExpressiveIcon
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.activities.ActivityCatalog
import com.dragolabs.able.feature.tools.activities.LaunchableActivity
import com.dragolabs.able.feature.tools.applock.LauncherApp
import com.dragolabs.able.feature.tools.applock.LauncherAppsSource
import com.dragolabs.able.feature.tools.common.MessageEffect
import com.dragolabs.able.feature.tools.common.UiMessage
import com.dragolabs.able.feature.tools.common.startFirstAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.dragolabs.able.core.designsystem.R as DsR

@Immutable
data class ButtonMapperUiState(
    val serviceEnabled: Boolean = false,
    val config: ButtonMapperConfig = ButtonMapperConfig(),
    val apps: List<LauncherApp>? = null,
    val activities: List<LaunchableActivity>? = null,
    val message: UiMessage? = null,
)

/** Button shortcuts tool (BTN-001..BTN-003). Keys are handled by the `:app` Accessibility service. */
class ButtonMapperViewModel(
    private val controller: ScreenLockController,
    private val settings: ButtonMapperSettings,
    private val appsSource: LauncherAppsSource,
    private val activityCatalog: ActivityCatalog,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ButtonMapperUiState(serviceEnabled = readService(), config = settings.read()))
    val uiState: StateFlow<ButtonMapperUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { settings.changes().collect { config -> _uiState.update { it.copy(config = config) } } }
    }

    private fun readService() = runCatching { controller.isServiceEnabled() }.getOrDefault(false)

    fun refresh() = _uiState.update { it.copy(serviceEnabled = readService()) }

    /** Lists are loaded only when the picker needs them. */
    fun loadApps() {
        if (_uiState.value.apps != null) return
        viewModelScope.launch {
            val apps = runCatching { appsSource.load() }.getOrDefault(emptyList())
            _uiState.update { it.copy(apps = apps) }
        }
    }

    fun loadActivities() {
        if (_uiState.value.activities != null) return
        viewModelScope.launch {
            val list = runCatching { activityCatalog.load().flatMap { it.activities } }.getOrDefault(emptyList())
            _uiState.update { it.copy(activities = list) }
        }
    }

    fun setEnabled(enabled: Boolean) = settings.setEnabled(enabled)

    fun assign(gesture: ButtonGesture, target: ButtonTarget?) {
        settings.setMapping(gesture, target)
        if (target != null && gesture.isLongPress() && !_uiState.value.config.mappings.keys.any { it.isLongPress() }) {
            _uiState.update { it.copy(message = UiMessage(R.string.buttons_long_press_note)) }
        }
    }

    fun setDoublePressMs(value: Int) = settings.setDoublePressMs(value)

    fun setLongPressMs(value: Int) = settings.setLongPressMs(value)

    fun setUndo(enabled: Boolean) = settings.setUndoVolumeStep(enabled)

    fun setHaptic(enabled: Boolean) = settings.setHaptic(enabled)

    fun setTestMode(enabled: Boolean) = settings.setTestMode(enabled)

    fun reportSettingsUnavailable() = _uiState.update { it.copy(message = UiMessage(DsR.string.ds_no_handler)) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    override fun onCleared() {
        settings.setTestMode(false)
    }
}

private fun ButtonGesture.isLongPress() = this == ButtonGesture.LONG_UP || this == ButtonGesture.LONG_DOWN

@Composable
fun ButtonMapperRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ButtonMapperViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            ButtonMapperViewModel(
                controller = container.screenLock,
                settings = ButtonMapperSettings(container.appContext),
                appsSource = LauncherAppsSource(container.appContext),
                activityCatalog = ActivityCatalog(container.appContext),
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        // Test mode never outlives the visible screen, so real shortcuts are not silenced.
        onPauseOrDispose { viewModel.setTestMode(false) }
    }
    ButtonMapperScreen(
        state = state,
        onBack = onBack,
        actions = ButtonMapperActions(
            onOpenAccessibility = {
                if (!context.startFirstAvailable(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))) {
                    viewModel.reportSettingsUnavailable()
                }
            },
            onEnabledChange = viewModel::setEnabled,
            onAssign = viewModel::assign,
            onLoadApps = viewModel::loadApps,
            onLoadActivities = viewModel::loadActivities,
            onDoublePressChange = viewModel::setDoublePressMs,
            onLongPressChange = viewModel::setLongPressMs,
            onUndoChange = viewModel::setUndo,
            onHapticChange = viewModel::setHaptic,
            onTestModeChange = viewModel::setTestMode,
            onMessageConsumed = viewModel::consumeMessage,
        ),
        modifier = modifier,
    )
}

@Immutable
class ButtonMapperActions(
    val onOpenAccessibility: () -> Unit,
    val onEnabledChange: (Boolean) -> Unit,
    val onAssign: (ButtonGesture, ButtonTarget?) -> Unit,
    val onLoadApps: () -> Unit,
    val onLoadActivities: () -> Unit,
    val onDoublePressChange: (Int) -> Unit,
    val onLongPressChange: (Int) -> Unit,
    val onUndoChange: (Boolean) -> Unit,
    val onHapticChange: (Boolean) -> Unit,
    val onTestModeChange: (Boolean) -> Unit,
    val onMessageConsumed: () -> Unit,
)

@Composable
fun ButtonMapperScreen(
    state: ButtonMapperUiState,
    onBack: () -> Unit,
    actions: ButtonMapperActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, actions.onMessageConsumed)
    var picking by rememberSaveable { mutableStateOf<ButtonGesture?>(null) }
    AbleDetailScaffold(
        title = stringResource(R.string.tools_buttons_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) { padding ->
        ReadableWidth {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(screenContentPadding(padding)),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
            ) {
                HeroCard(state, actions)
                GesturesCard(state, onPick = { picking = it }, onClear = { actions.onAssign(it, null) })
                TimingCard(state.config, actions)
                TestCard(state, actions)
                StatusBanner(
                    level = StatusLevel.INFO,
                    title = stringResource(R.string.buttons_limits_title),
                    message = stringResource(R.string.buttons_limits_body),
                )
            }
        }
    }
    picking?.let { gesture ->
        TargetPickerSheet(
            gesture = gesture,
            state = state,
            onLoadApps = actions.onLoadApps,
            onLoadActivities = actions.onLoadActivities,
            onPick = { target ->
                actions.onAssign(gesture, target)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun HeroCard(state: ButtonMapperUiState, actions: ButtonMapperActions) {
    val haptics = rememberAbleHaptics()
    val working = state.config.active && state.serviceEnabled
    AbleCard(style = AbleCardStyle.Hero) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg)) {
            ExpressiveIcon(icon = Icons.Rounded.SmartButton, shape = MaterialShapes.Pill, size = 64.dp, iconSize = 32.dp)
            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.buttons_hero_title), style = MaterialTheme.typography.titleMedium)
                StatusPill(
                    text = stringResource(
                        when {
                            !state.serviceEnabled -> R.string.buttons_status_service_off
                            !state.config.enabled -> R.string.buttons_status_off
                            state.config.mappings.isEmpty() -> R.string.buttons_status_no_mapping
                            else -> R.string.buttons_status_on
                        },
                    ),
                    level = when {
                        working -> StatusLevel.SUCCESS
                        !state.serviceEnabled -> StatusLevel.WARNING
                        else -> StatusLevel.INFO
                    },
                )
            }
            HelpButton(
                title = stringResource(R.string.buttons_help_service_title),
                text = stringResource(R.string.buttons_help_service_text),
            )
        }
        if (!state.serviceEnabled) {
            Button(onClick = { haptics.click(); actions.onOpenAccessibility() }, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.Accessibility, contentDescription = null)
                Text(stringResource(R.string.buttons_open_accessibility), modifier = Modifier.padding(start = AbleSpacing.sm))
            }
        }
        ListGroup {
            item { shape ->
                SwitchRow(
                    title = stringResource(R.string.buttons_enable_title),
                    supportingText = stringResource(R.string.buttons_enable_summary),
                    checked = state.config.enabled,
                    onCheckedChange = actions.onEnabledChange,
                    icon = Icons.Rounded.Keyboard,
                    shape = shape,
                )
            }
        }
    }
}

@Composable
private fun GesturesCard(state: ButtonMapperUiState, onPick: (ButtonGesture) -> Unit, onClear: (ButtonGesture) -> Unit) {
    val haptics = rememberAbleHaptics()
    AbleCard(
        title = stringResource(R.string.buttons_gestures_title),
        icon = Icons.Rounded.SmartButton,
        style = AbleCardStyle.Outlined,
        trailing = {
            HelpButton(
                title = stringResource(R.string.buttons_help_gestures_title),
                text = stringResource(R.string.buttons_help_gestures_text),
            )
        },
    ) {
        ListGroup {
            ButtonGesture.entries.forEach { gesture ->
                item { shape ->
                    val target = state.config.mappings[gesture]
                    ActionRow(
                        title = stringResource(gesture.titleRes()),
                        supportingText = if (target != null) {
                            targetLabel(target)
                        } else {
                            stringResource(R.string.buttons_unassigned) + " · " + stringResource(gesture.hintRes())
                        },
                        icon = target?.let(ButtonTargets::icon) ?: Icons.Rounded.AddCircleOutline,
                        enabled = state.config.enabled,
                        shape = shape,
                        trailing = if (target != null) {
                            {
                                IconButton(onClick = { haptics.reject(); onClear(gesture) }, enabled = state.config.enabled) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.buttons_clear_cd, stringResource(gesture.titleRes())))
                                }
                            }
                        } else {
                            null
                        },
                        onClick = { onPick(gesture) },
                    )
                }
            }
        }
        if (!state.config.enabled) {
            Text(
                stringResource(R.string.buttons_enable_first),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimingCard(config: ButtonMapperConfig, actions: ButtonMapperActions) {
    AbleCard(title = stringResource(R.string.buttons_timing_title), icon = Icons.Rounded.Timer, style = AbleCardStyle.Outlined) {
        TimingSlider(
            labelRes = R.string.buttons_double_press_label,
            value = config.doublePressMs,
            range = ButtonMapperSettings.MIN_DOUBLE_PRESS_MS..ButtonMapperSettings.MAX_DOUBLE_PRESS_MS,
            onChange = actions.onDoublePressChange,
            helpTitle = stringResource(R.string.buttons_help_double_title),
            helpText = stringResource(R.string.buttons_help_double_text),
        )
        TimingSlider(
            labelRes = R.string.buttons_long_press_label,
            value = config.longPressMs,
            range = ButtonMapperSettings.MIN_LONG_PRESS_MS..ButtonMapperSettings.MAX_LONG_PRESS_MS,
            onChange = actions.onLongPressChange,
            helpTitle = stringResource(R.string.buttons_help_long_title),
            helpText = stringResource(R.string.buttons_help_long_text),
        )
        ListGroup {
            item { shape ->
                SwitchRow(
                    title = stringResource(R.string.buttons_undo_title),
                    supportingText = stringResource(R.string.buttons_undo_summary),
                    checked = config.undoVolumeStep,
                    onCheckedChange = actions.onUndoChange,
                    icon = Icons.Rounded.Undo,
                    shape = shape,
                )
            }
            item { shape ->
                SwitchRow(
                    title = stringResource(R.string.buttons_haptic_title),
                    supportingText = stringResource(R.string.buttons_haptic_summary),
                    checked = config.haptic,
                    onCheckedChange = actions.onHapticChange,
                    icon = Icons.Rounded.Vibration,
                    shape = shape,
                )
            }
        }
    }
}

@Composable
private fun TimingSlider(
    labelRes: Int,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    helpTitle: String,
    helpText: String,
) {
    val haptics = rememberAbleHaptics()
    var slider by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shown = (slider / STEP_MS).roundToInt() * STEP_MS
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes, shown), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        HelpButton(title = helpTitle, text = helpText)
    }
    Slider(
        value = slider,
        onValueChange = {
            val stepped = (it / STEP_MS).roundToInt() * STEP_MS
            if (stepped != shown) haptics.tick()
            slider = it
        },
        onValueChangeFinished = { onChange(shown) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first) / STEP_MS - 1,
        modifier = Modifier.focusRing(),
    )
}

@Composable
private fun TestCard(state: ButtonMapperUiState, actions: ButtonMapperActions) {
    val haptics = rememberAbleHaptics()
    val testing = state.config.testMode
    val canTest = state.serviceEnabled && state.config.enabled
    // Leaving the card's composition (navigation) ends the test as well.
    DisposableEffect(Unit) { onDispose { actions.onTestModeChange(false) } }
    AbleCard(
        title = stringResource(R.string.buttons_test_title),
        icon = Icons.Rounded.Science,
        style = AbleCardStyle.Tonal,
        trailing = {
            HelpButton(
                title = stringResource(R.string.buttons_help_test_title),
                text = stringResource(R.string.buttons_help_test_text),
            )
        },
    ) {
        Text(stringResource(R.string.buttons_test_body), style = MaterialTheme.typography.bodyMedium)
        val detected = state.config.lastDetected?.takeIf { it.testOnly && testing }
        Text(
            text = if (detected != null) {
                stringResource(
                    R.string.buttons_test_detected,
                    stringResource(detected.gesture.titleRes()),
                    DateFormat.getTimeFormat(LocalContext.current).format(detected.wallTimeMs),
                )
            } else if (testing) {
                stringResource(R.string.buttons_test_waiting)
            } else {
                stringResource(R.string.buttons_test_idle)
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (testing) {
            OutlinedButton(onClick = { haptics.toggle(false); actions.onTestModeChange(false) }, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.buttons_test_stop))
            }
        } else {
            Button(
                onClick = { haptics.toggle(true); actions.onTestModeChange(true) },
                enabled = canTest,
                modifier = Modifier.focusRing(),
            ) {
                Text(stringResource(R.string.buttons_test_start))
            }
        }
        if (!canTest) {
            Text(
                stringResource(R.string.buttons_test_needs_setup),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private const val STEP_MS = 50
