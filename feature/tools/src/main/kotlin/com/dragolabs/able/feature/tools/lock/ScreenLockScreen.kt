package com.dragolabs.able.feature.tools.lock

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.dragolabs.able.core.designsystem.component.ExpressiveIcon
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.MessageEffect
import com.dragolabs.able.feature.tools.common.UiMessage
import com.dragolabs.able.feature.tools.common.startFirstAvailable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.dragolabs.able.core.designsystem.R as DsR

@Immutable
data class ScreenLockUiState(
    val serviceEnabled: Boolean = false,
    val config: LockGestureConfig = LockGestureConfig(),
    val widgetCount: Int = 0,
    val canPinWidget: Boolean = false,
    val message: UiMessage? = null,
)

/**
 * Screen lock and double tap to turn off the screen (LOCK-001, LOCK-002). Locking goes through
 * the app's Accessibility service; gesture settings live in [LockGestureSettings].
 */
class ScreenLockViewModel(
    private val controller: ScreenLockController,
    private val tileRequester: LockTileRequester,
    private val widgetRequester: LockWidgetRequester,
    private val settings: LockGestureSettings,
    private val refreshAccess: () -> Unit,
) : ViewModel() {
    val canRequestTile: Boolean get() = tileRequester.isAvailable

    private val _uiState = MutableStateFlow(readState())
    val uiState: StateFlow<ScreenLockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.changes().collect { config -> _uiState.update { it.copy(config = config) } }
        }
    }

    private fun readEnabled() = runCatching { controller.isServiceEnabled() }.getOrDefault(false)

    private fun readState() = ScreenLockUiState(
        serviceEnabled = readEnabled(),
        config = settings.read(),
        widgetCount = widgetRequester.placedCount(),
        canPinWidget = widgetRequester.canPin,
    )

    /** Accessibility grants and widget placement are not broadcast; re-read on resume. */
    fun refresh() {
        refreshAccess()
        val fresh = readState()
        _uiState.update { fresh.copy(message = it.message) }
    }

    fun setLockScreenEnabled(enabled: Boolean) = settings.setLockScreenEnabled(enabled)

    fun setStatusBarEnabled(enabled: Boolean) = settings.setStatusBarEnabled(enabled)

    fun setHomeEnabled(enabled: Boolean) = settings.setHomeEnabled(enabled)

    fun setHomeMode(mode: HomeDoubleTapMode) = settings.setHomeMode(mode)

    fun setTimeoutMs(value: Int) = settings.setTimeoutMs(value)

    fun setHapticOnLock(enabled: Boolean) = settings.setHapticOnLock(enabled)

    fun requestWidget() {
        val message = if (widgetRequester.request()) R.string.lock_home_requested else R.string.lock_home_request_failed
        _uiState.update { it.copy(message = UiMessage(message)) }
    }

    /** Asks Android to add the Able lock tile; Android shows its own consent dialog. */
    fun requestTile(label: String) {
        val started = tileRequester.request(label) { result ->
            val message = when (result) {
                TileRequestResult.ADDED -> UiMessage(R.string.tools_lock_tile_added)
                TileRequestResult.ALREADY_ADDED -> UiMessage(R.string.tools_lock_tile_already)
                TileRequestResult.NOT_ADDED -> UiMessage(R.string.tools_lock_tile_not_added)
                TileRequestResult.FAILED -> UiMessage(R.string.tools_lock_tile_failed)
            }
            _uiState.update { it.copy(message = message) }
        }
        if (!started) _uiState.update { it.copy(message = UiMessage(R.string.tools_lock_tile_failed)) }
    }

    /** Returns true when Android accepted the lock request (the screen then turns off). */
    fun testLock(): Boolean {
        if (!readEnabled()) {
            _uiState.update { it.copy(serviceEnabled = false, message = UiMessage(R.string.tools_lock_service_off_message)) }
            return false
        }
        val accepted = runCatching { controller.lockNow() }.getOrDefault(false)
        if (!accepted) _uiState.update { it.copy(message = UiMessage(R.string.tools_lock_test_failed)) }
        return accepted
    }

    fun reportSettingsUnavailable() = _uiState.update { it.copy(message = UiMessage(DsR.string.ds_no_handler)) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}

@Composable
fun ScreenLockRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ScreenLockViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            ScreenLockViewModel(
                controller = container.screenLock,
                tileRequester = LockTileRequester(container.appContext),
                widgetRequester = LockWidgetRequester(container.appContext),
                settings = LockGestureSettings(container.appContext),
                refreshAccess = container.capabilityStatus::refresh,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val tileLabel = stringResource(R.string.tools_lock_tile_label)
    ScreenLockScreen(
        state = state,
        canRequestTile = viewModel.canRequestTile,
        onBack = onBack,
        actions = ScreenLockActions(
            onOpenAccessibilitySettings = {
                if (!context.startFirstAvailable(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))) {
                    viewModel.reportSettingsUnavailable()
                }
            },
            onLockScreenChange = viewModel::setLockScreenEnabled,
            onStatusBarChange = viewModel::setStatusBarEnabled,
            onHomeChange = viewModel::setHomeEnabled,
            onHomeModeChange = viewModel::setHomeMode,
            onTimeoutChange = viewModel::setTimeoutMs,
            onHapticChange = viewModel::setHapticOnLock,
            onAddWidget = viewModel::requestWidget,
            onRequestTile = { viewModel.requestTile(tileLabel) },
            onTestLock = viewModel::testLock,
            onMessageConsumed = viewModel::consumeMessage,
        ),
        modifier = modifier,
    )
}

@Immutable
class ScreenLockActions(
    val onOpenAccessibilitySettings: () -> Unit,
    val onLockScreenChange: (Boolean) -> Unit,
    val onStatusBarChange: (Boolean) -> Unit,
    val onHomeChange: (Boolean) -> Unit,
    val onHomeModeChange: (HomeDoubleTapMode) -> Unit,
    val onTimeoutChange: (Int) -> Unit,
    val onHapticChange: (Boolean) -> Unit,
    val onAddWidget: () -> Unit,
    val onRequestTile: () -> Unit,
    val onTestLock: () -> Boolean,
    val onMessageConsumed: () -> Unit,
)

@Composable
fun ScreenLockScreen(
    state: ScreenLockUiState,
    canRequestTile: Boolean,
    onBack: () -> Unit,
    actions: ScreenLockActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberAbleHaptics()
    MessageEffect(state.message, snackbarHostState, actions.onMessageConsumed)
    AbleDetailScaffold(
        title = stringResource(R.string.tools_lock_title),
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
                HeroCard(state)
                ServiceStepCard(state.serviceEnabled, onOpen = { haptics.click(); actions.onOpenAccessibilitySettings() })
                PlacesCard(state, actions)
                GestureSettingsCard(state.config, actions)

                AbleCard(title = stringResource(R.string.lock_test_title), icon = Icons.Rounded.Lock, style = AbleCardStyle.Tonal) {
                    Text(stringResource(R.string.lock_test_body), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { if (actions.onTestLock()) haptics.confirm() else haptics.reject() },
                        enabled = state.serviceEnabled,
                        modifier = Modifier.focusRing(),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                        Text(stringResource(R.string.tools_lock_test_action), modifier = Modifier.padding(start = AbleSpacing.sm))
                    }
                }

                AbleCard(title = stringResource(R.string.lock_alternatives_title), icon = Icons.Rounded.AppShortcut, style = AbleCardStyle.Outlined) {
                    Text(stringResource(R.string.lock_alternatives_body), style = MaterialTheme.typography.bodyMedium)
                    if (canRequestTile) {
                        OutlinedButton(onClick = { haptics.click(); actions.onRequestTile() }, modifier = Modifier.focusRing()) {
                            Text(stringResource(R.string.tools_lock_tile_add))
                        }
                    }
                }

                StatusBanner(
                    level = StatusLevel.INFO,
                    title = stringResource(R.string.lock_limits_title),
                    message = stringResource(R.string.lock_limits_body),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(state: ScreenLockUiState) {
    val places = buildList {
        if (state.serviceEnabled && state.config.lockScreenEnabled) add(stringResource(R.string.lock_place_lock_screen))
        if (state.serviceEnabled && state.widgetCount > 0) add(stringResource(R.string.lock_place_home))
        if (state.serviceEnabled && state.config.homeEnabled) add(stringResource(R.string.lock_place_home_tap))
        if (state.serviceEnabled && state.config.statusBarEnabled) add(stringResource(R.string.lock_place_status_bar))
    }
    val separator = stringResource(R.string.lock_place_separator)
    AbleCard(style = AbleCardStyle.Hero) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg)) {
            ExpressiveIcon(
                icon = if (state.serviceEnabled) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                shape = MaterialShapes.Clover4Leaf,
                size = 64.dp,
                iconSize = 32.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                Text(stringResource(R.string.lock_hero_title), style = MaterialTheme.typography.titleMedium)
                StatusPill(
                    text = stringResource(if (state.serviceEnabled) R.string.lock_hero_service_on else R.string.lock_hero_service_off),
                    level = if (state.serviceEnabled) StatusLevel.SUCCESS else StatusLevel.WARNING,
                )
            }
        }
        Text(
            text = if (places.isEmpty()) {
                stringResource(R.string.lock_hero_active_none)
            } else {
                stringResource(R.string.lock_hero_active_prefix, places.joinToString(separator))
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StepPill(done: Boolean) = StatusPill(
    text = stringResource(if (done) R.string.lock_step_done else R.string.lock_step_todo),
    level = if (done) StatusLevel.SUCCESS else StatusLevel.INFO,
)

@Composable
private fun ServiceStepCard(serviceEnabled: Boolean, onOpen: () -> Unit) {
    AbleCard(
        title = stringResource(R.string.lock_step1_title),
        icon = Icons.Rounded.Accessibility,
        style = AbleCardStyle.Outlined,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepPill(serviceEnabled)
                HelpButton(
                    title = stringResource(R.string.lock_help_service_title),
                    text = stringResource(R.string.lock_help_service_text),
                )
            }
        },
    ) {
        Text(stringResource(R.string.lock_step1_body), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.lock_step1_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (serviceEnabled) {
            OutlinedButton(onClick = onOpen, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.tools_lock_manage_service))
            }
        } else {
            Button(onClick = onOpen, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.tools_lock_open_accessibility))
            }
        }
    }
}

@Composable
private fun PlacesCard(state: ScreenLockUiState, actions: ScreenLockActions) {
    val haptics = rememberAbleHaptics()
    val anyPlace = state.config.lockScreenEnabled || state.config.statusBarEnabled || state.config.homeEnabled || state.widgetCount > 0
    AbleCard(
        title = stringResource(R.string.lock_step2_title),
        icon = Icons.Rounded.TouchApp,
        style = AbleCardStyle.Outlined,
        trailing = { StepPill(state.serviceEnabled && anyPlace) },
    ) {
        if (!state.serviceEnabled) {
            Text(
                stringResource(R.string.lock_step2_needs_service),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ListGroup {
            item { shape ->
                GestureToggleRow(
                    title = stringResource(R.string.lock_option_lock_screen_title),
                    summary = stringResource(R.string.lock_option_lock_screen_summary) + "\n" + recognitionText(state.config),
                    icon = Icons.Rounded.ScreenLockPortrait,
                    checked = state.config.lockScreenEnabled,
                    enabled = state.serviceEnabled,
                    onCheckedChange = actions.onLockScreenChange,
                    helpTitle = stringResource(R.string.lock_help_lock_screen_title),
                    helpText = stringResource(R.string.lock_help_lock_screen_text),
                    shape = shape,
                )
            }
            item { shape ->
                ListItem(
                    modifier = Modifier.fillMaxWidth().clip(shape),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    leadingContent = { Icon(Icons.Rounded.Widgets, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.lock_option_home_title)) },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                            Text(
                                if (state.widgetCount > 0) {
                                    pluralStringResource(R.plurals.lock_home_count, state.widgetCount, state.widgetCount)
                                } else {
                                    stringResource(R.string.lock_home_none)
                                },
                            )
                            if (state.canPinWidget) {
                                OutlinedButton(onClick = { haptics.click(); actions.onAddWidget() }, modifier = Modifier.focusRing()) {
                                    Icon(Icons.Rounded.Home, contentDescription = null)
                                    Text(stringResource(R.string.lock_home_add), modifier = Modifier.padding(start = AbleSpacing.sm))
                                }
                            } else {
                                Text(stringResource(R.string.lock_home_manual), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    trailingContent = {
                        HelpButton(
                            title = stringResource(R.string.lock_help_home_title),
                            text = stringResource(R.string.lock_help_home_text),
                        )
                    },
                )
            }
            item { shape ->
                GestureToggleRow(
                    title = stringResource(R.string.lock_option_home_tap_title),
                    summary = stringResource(R.string.lock_option_home_tap_summary) + "\n" + homeStatusText(state.config),
                    icon = Icons.Rounded.Home,
                    checked = state.config.homeEnabled,
                    enabled = state.serviceEnabled,
                    onCheckedChange = actions.onHomeChange,
                    helpTitle = stringResource(R.string.lock_help_home_tap_title),
                    helpText = stringResource(R.string.lock_help_home_tap_text),
                    shape = shape,
                )
            }
            if (state.config.homeEnabled) {
                item { shape ->
                    RadioRow(
                        title = stringResource(R.string.lock_home_mode_precise_title),
                        supportingText = stringResource(R.string.lock_home_mode_precise_summary),
                        selected = state.config.homeMode == HomeDoubleTapMode.PRECISE,
                        onSelect = { actions.onHomeModeChange(HomeDoubleTapMode.PRECISE) },
                        shape = shape,
                    )
                }
                item { shape ->
                    RadioRow(
                        title = stringResource(R.string.lock_home_mode_light_title),
                        supportingText = stringResource(R.string.lock_home_mode_light_summary),
                        selected = state.config.homeMode == HomeDoubleTapMode.LIGHT,
                        onSelect = { actions.onHomeModeChange(HomeDoubleTapMode.LIGHT) },
                        shape = shape,
                    )
                }
            }
            item { shape ->
                GestureToggleRow(
                    title = stringResource(R.string.lock_option_status_bar_title),
                    summary = stringResource(R.string.lock_option_status_bar_summary),
                    icon = Icons.Rounded.TouchApp,
                    checked = state.config.statusBarEnabled,
                    enabled = state.serviceEnabled,
                    onCheckedChange = actions.onStatusBarChange,
                    helpTitle = stringResource(R.string.lock_help_status_bar_title),
                    helpText = stringResource(R.string.lock_help_status_bar_text),
                    shape = shape,
                )
            }
        }
    }
}

@Composable
private fun homeStatusText(config: LockGestureConfig): String {
    val precise = config.homeMode == HomeDoubleTapMode.PRECISE
    return stringResource(
        when {
            !config.homeEnabled -> R.string.lock_home_tap_status_off
            config.homeStatus == HomeDoubleTapStatus.NO_LAUNCHER -> R.string.lock_home_tap_status_no_launcher
            config.homeStatus == HomeDoubleTapStatus.FAILED -> R.string.lock_home_tap_status_failed
            precise && config.homeStatus == HomeDoubleTapStatus.PRECISE_READY -> R.string.lock_home_tap_status_precise_ok
            precise && config.homeStatus == HomeDoubleTapStatus.LAUNCHER_NOT_RECOGNIZED -> R.string.lock_home_tap_status_not_recognized
            precise -> R.string.lock_home_tap_status_precise_unknown
            config.homeStatus == HomeDoubleTapStatus.WATCHING -> R.string.lock_home_tap_status_ok
            else -> R.string.lock_home_tap_status_unknown
        },
    )
}

@Composable
private fun recognitionText(config: LockGestureConfig): String = stringResource(
    when (config.lockScreenRecognition) {
        LockScreenRecognition.UNKNOWN -> R.string.lock_recognition_unknown
        LockScreenRecognition.RECOGNIZED -> R.string.lock_recognition_ok
        LockScreenRecognition.NOT_RECOGNIZED -> R.string.lock_recognition_failed
    },
)

@Composable
private fun GestureSettingsCard(config: LockGestureConfig, actions: ScreenLockActions) {
    val haptics = rememberAbleHaptics()
    // Local slider value so dragging is smooth; persisted when the drag ends.
    var sliderValue by remember(config.timeoutMs) { mutableFloatStateOf(config.timeoutMs.toFloat()) }
    val shownMs = (sliderValue / TIMEOUT_STEP).roundToInt() * TIMEOUT_STEP
    AbleCard(title = stringResource(R.string.lock_settings_title), icon = Icons.Rounded.Timer, style = AbleCardStyle.Outlined) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.lock_timeout_label, shownMs),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            HelpButton(
                title = stringResource(R.string.lock_help_timeout_title),
                text = stringResource(R.string.lock_help_timeout_text),
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = {
                val stepped = (it / TIMEOUT_STEP).roundToInt() * TIMEOUT_STEP
                if (stepped != shownMs) haptics.tick()
                sliderValue = it
            },
            onValueChangeFinished = { actions.onTimeoutChange(shownMs) },
            valueRange = LockGestureSettings.MIN_TIMEOUT_MS.toFloat()..LockGestureSettings.MAX_TIMEOUT_MS.toFloat(),
            steps = (LockGestureSettings.MAX_TIMEOUT_MS - LockGestureSettings.MIN_TIMEOUT_MS) / TIMEOUT_STEP - 1,
            modifier = Modifier.focusRing(),
        )
        PracticeArea(timeoutMs = shownMs)
        ListGroup {
            item { shape ->
                GestureToggleRow(
                    title = stringResource(R.string.lock_haptic_title),
                    summary = stringResource(R.string.lock_haptic_summary),
                    icon = Icons.Rounded.Vibration,
                    checked = config.hapticOnLock,
                    enabled = true,
                    onCheckedChange = actions.onHapticChange,
                    shape = shape,
                )
            }
        }
    }
}

/** Lets the user feel the timeout without locking: same rule as the service's detector. */
@Composable
private fun PracticeArea(timeoutMs: Int) {
    val haptics = rememberAbleHaptics()
    var lastTap by remember { mutableLongStateOf(0L) }
    var hits by remember { mutableIntStateOf(0) }
    var showHit by remember { mutableStateOf(false) }
    LaunchedEffect(hits) {
        if (hits > 0) {
            showHit = true
            delay(1_200)
            showHit = false
        }
    }
    val container by animateColorAsState(
        if (showHit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "practice",
    )
    val description = stringResource(R.string.lock_practice_cd)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .clickable(role = Role.Button) {
                val now = SystemClock.uptimeMillis()
                if (lastTap != 0L && now - lastTap < timeoutMs) {
                    lastTap = 0L
                    hits++
                    haptics.confirm()
                } else {
                    lastTap = now
                }
            }
            .focusRing(MaterialTheme.shapes.large)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(if (showHit) R.string.lock_practice_hit else R.string.lock_practice_idle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(AbleSpacing.lg),
        )
    }
}

/** Switch row with a "?" help button; the whole row toggles for TalkBack and D-pad. */
@Composable
private fun GestureToggleRow(
    title: String,
    summary: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
    helpTitle: String? = null,
    helpText: String? = null,
) {
    val haptics = rememberAbleHaptics()
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch) {
                haptics.toggle(it)
                onCheckedChange(it)
            }
            .focusRing(shape),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (helpTitle != null && helpText != null) HelpButton(title = helpTitle, text = helpText)
                Switch(checked = checked, onCheckedChange = null, enabled = enabled)
            }
        },
    )
}

private const val TIMEOUT_STEP = 50
