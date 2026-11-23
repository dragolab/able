package com.dragolabs.able.feature.tools.fakegps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.WhereToVote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleButtonFlow
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleSingleChoiceToggleGroup
import com.dragolabs.able.core.designsystem.component.AbleSlider
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.InlineLoading
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.listGroupItemShape
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.gps.GpsTags
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
import com.dragolabs.able.core.designsystem.R as DsR

internal class FakeGpsEvents(
    val onMapTap: (LatLon) -> Unit,
    val onMapLongPress: (LatLon) -> Unit,
    val onModeChange: (FakeMode) -> Unit,
    val onSpeedChange: (SpeedPreset) -> Unit,
    val onRepeatChange: (RouteRepeat) -> Unit,
    val onJitterChange: (Boolean) -> Unit,
    val onAltitudeChange: (Double) -> Unit,
    val onAccuracyChange: (Float) -> Unit,
    val onUndoWaypoint: () -> Unit,
    val onClearWaypoints: () -> Unit,
    val onRestartRoute: () -> Unit,
    val onJoystick: (Float, Float) -> Unit,
    val onNudge: (Double) -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onDismissFailure: () -> Unit,
    val onLocateMe: () -> Unit,
    val onCenterFake: () -> Unit,
    val onOpenDeveloperOptions: () -> Unit,
    val onAllowWithShizuku: () -> Unit,
    val onOpenLocationSettings: () -> Unit,
    val onOpenSearch: () -> Unit,
    val onCloseSearch: () -> Unit,
    val onSearchText: (String) -> Unit,
    val onConfirmSearch: () -> Unit,
    val onApplyPoint: (LatLon) -> Unit,
    val onOpenSavePoint: () -> Unit,
    val onDismissSavePoint: () -> Unit,
    val onSaveLabel: (String) -> Unit,
    val onSaveCustomTag: (String) -> Unit,
    val onSaveToggleTag: (String) -> Unit,
    val onSaveAddCustomTag: () -> Unit,
    val onConfirmSavePoint: () -> Unit,
    val onClearHistory: () -> Unit,
)

private val SheetPeekHeight = 236.dp

/**
 * Full-bleed OpenStreetMap with a floating toolbar and a bottom sheet holding the engine
 * status, the big start / stop button and every simulation option (Material 3 Expressive).
 */
@Composable
internal fun FakeGpsScreen(
    state: FakeGpsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    events: FakeGpsEvents,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBottomSheetScaffoldState()
    val mapHandle = remember { MapHandle() }
    val running = state.engine.status == EngineStatus.Running
    val darkTiles = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = sheetState,
        sheetPeekHeight = SheetPeekHeight,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_fakegps_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    AbleIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(DsR.string.ds_navigate_back))
                    }
                },
                actions = {
                    AbleIconButton(onClick = events.onOpenSearch) {
                        Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.fakegps_search))
                    }
                    HelpButton(
                        title = stringResource(R.string.fakegps_help_mock_title),
                        text = stringResource(R.string.fakegps_help_mock_text),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        sheetContent = { ControlsSheet(state, events) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            val mapDescription = state.config.target?.let { stringResource(R.string.fakegps_map_cd_position, it.format()) }
                ?: stringResource(R.string.fakegps_map_cd_empty)
            FakeGpsMap(
                target = state.config.target,
                emitted = if (running) state.engine.emitted else null,
                waypoints = state.config.waypoints,
                closedRoute = state.config.repeat == RouteRepeat.LOOP,
                showRoute = state.config.mode == FakeMode.ROUTE,
                camera = state.camera,
                darkTiles = darkTiles,
                handle = mapHandle,
                onTap = events.onMapTap,
                onLongPress = events.onMapLongPress,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = mapDescription },
            )

            // OSM attribution, always visible above the map (tile usage policy, ODbL).
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(AbleSpacing.sm),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            ) {
                Text(
                    stringResource(R.string.fakegps_osm_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = AbleSpacing.sm, vertical = 2.dp),
                )
            }

            VerticalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = AbleSpacing.xl, end = AbleSpacing.md),
            ) {
                AbleIconButton(onClick = events.onLocateMe, enabled = !state.locating, modifier = Modifier.focusRing()) {
                    if (state.locating) {
                        InlineLoading(Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Rounded.MyLocation, contentDescription = stringResource(R.string.fakegps_locate_me))
                    }
                }
                AbleIconButton(
                    onClick = events.onCenterFake,
                    enabled = state.config.target != null,
                    modifier = Modifier.focusRing(),
                ) {
                    Icon(Icons.Rounded.WhereToVote, contentDescription = stringResource(R.string.fakegps_center_fake))
                }
                AbleIconButton(onClick = mapHandle::zoomIn, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.fakegps_zoom_in))
                }
                AbleIconButton(onClick = mapHandle::zoomOut, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.fakegps_zoom_out))
                }
            }

            if (state.config.mode == FakeMode.JOYSTICK && running) {
                FakeGpsJoystick(
                    onMove = events.onJoystick,
                    onNudge = events.onNudge,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = AbleSpacing.lg, bottom = SheetPeekHeight + AbleSpacing.lg),
                )
            }
        }
    }

    if (state.searchOpen) SearchDialog(state, events)
    state.savePoint?.let { SavePointDialog(it, state.availableTags, events) }
}

// region Sheet

@Composable
private fun ControlsSheet(state: FakeGpsUiState, events: FakeGpsEvents) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbleSpacing.screenGutter)
            .padding(bottom = AbleSpacing.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
    ) {
        StatusHeader(state)
        EngineButton(state, events)
        SetupBanners(state, events)
        ModeSection(state, events)
        RealismSection(state.config, events)
        SavedPointsSection(state, events)
        HistorySection(state, events)
        SafetySection()
    }
}

@Composable
private fun StatusHeader(state: FakeGpsUiState) {
    val engine = state.engine
    val (level, text) = when (engine.status) {
        EngineStatus.Running -> StatusLevel.SUCCESS to stringResource(R.string.fakegps_status_running)
        EngineStatus.Starting -> StatusLevel.INFO to stringResource(R.string.fakegps_status_starting)
        is EngineStatus.Failed -> StatusLevel.CRITICAL to stringResource(R.string.fakegps_status_failed)
        EngineStatus.Idle -> if (state.setup.mockAppSelected) {
            StatusLevel.INFO to stringResource(R.string.fakegps_status_off)
        } else {
            StatusLevel.WARNING to stringResource(R.string.fakegps_status_setup)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            StatusPill(text = text, level = level)
            Spacer(Modifier.weight(1f))
            HelpButton(
                title = stringResource(R.string.fakegps_help_detect_title),
                text = stringResource(R.string.fakegps_help_detect_text),
            )
        }
        val position = if (engine.status == EngineStatus.Running) engine.emitted ?: state.config.target else state.config.target
        Text(
            text = position?.format() ?: stringResource(R.string.fakegps_no_position),
            style = MaterialTheme.typography.titleMedium,
        )
        if (engine.status == EngineStatus.Running) {
            Text(
                text = stringResource(
                    R.string.fakegps_status_details,
                    engine.providers.joinToString(", "),
                    stringResource(
                        when (engine.fusedMockMode) {
                            true -> R.string.fakegps_fused_on
                            false -> R.string.fakegps_fused_off
                            null -> R.string.fakegps_fused_pending
                        },
                    ),
                    speedKmh(engine.speedMetersPerSecond.toDouble()),
                    engine.accuracyMeters.roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EngineButton(state: FakeGpsUiState, events: FakeGpsEvents) {
    val status = state.engine.status
    if (status == EngineStatus.Running || status == EngineStatus.Starting) {
        AbleButton(
            onClick = events.onStop,
            enabled = status == EngineStatus.Running,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .focusRing(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            haptic = AbleHaptic.Confirm,
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = null)
            Spacer(Modifier.width(AbleSpacing.sm))
            Text(stringResource(R.string.fakegps_stop), style = MaterialTheme.typography.titleMedium)
        }
    } else {
        AbleButton(
            onClick = events.onStart,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .focusRing(),
            haptic = AbleHaptic.Confirm,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(AbleSpacing.sm))
            Text(stringResource(R.string.fakegps_start), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SetupBanners(state: FakeGpsUiState, events: FakeGpsEvents) {
    val setup = state.setup
    (state.engine.status as? EngineStatus.Failed)?.let { failed ->
        StatusBanner(
            level = StatusLevel.CRITICAL,
            title = stringResource(R.string.fakegps_failed_title),
            message = stringResource(failed.failure.messageRes()),
            actionLabel = stringResource(R.string.fakegps_dismiss),
            onAction = events.onDismissFailure,
        )
    }
    if (!setup.mockAppSelected) {
        val steps = if (setup.developerOptionsEnabled) {
            stringResource(R.string.fakegps_setup_steps)
        } else {
            stringResource(R.string.fakegps_setup_steps_devoff)
        }
        val shizukuHint = if (setup.shizukuReady) "" else "\n\n" + stringResource(R.string.fakegps_setup_shizuku_hint)
        StatusBanner(
            level = StatusLevel.WARNING,
            title = stringResource(R.string.fakegps_setup_title),
            message = steps + shizukuHint,
            actionLabel = stringResource(
                if (setup.developerOptionsEnabled) R.string.fakegps_open_dev_options else R.string.fakegps_open_about_phone,
            ),
            onAction = events.onOpenDeveloperOptions,
            secondaryActionLabel = if (setup.shizukuReady && !state.settingUpShizuku) {
                stringResource(R.string.fakegps_set_with_shizuku)
            } else {
                null
            },
            onSecondaryAction = if (setup.shizukuReady && !state.settingUpShizuku) events.onAllowWithShizuku else null,
        )
    }
    if (!setup.locationServicesOn) {
        StatusBanner(
            level = StatusLevel.WARNING,
            title = stringResource(R.string.fakegps_location_off_title),
            message = stringResource(R.string.fakegps_location_off_text),
            actionLabel = stringResource(R.string.fakegps_open_location_settings),
            onAction = events.onOpenLocationSettings,
        )
    }
    if (!setup.fineLocationGranted) {
        StatusBanner(
            level = StatusLevel.INFO,
            title = stringResource(R.string.fakegps_permission_title),
            message = stringResource(R.string.fakegps_permission_text),
        )
    }
}

@Composable
private fun ModeSection(state: FakeGpsUiState, events: FakeGpsEvents) {
    val config = state.config
    SectionHeader(stringResource(R.string.fakegps_section_mode))
    AbleSingleChoiceToggleGroup(
        options = FakeMode.entries,
        selected = config.mode,
        onSelect = events.onModeChange,
    ) { mode ->
        Icon(
            when (mode) {
                FakeMode.POINT -> Icons.Rounded.Place
                FakeMode.JOYSTICK -> Icons.Rounded.SportsEsports
                FakeMode.ROUTE -> Icons.Rounded.Route
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AbleSpacing.xs))
        Text(stringResource(mode.labelRes()), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Text(
        stringResource(
            when (config.mode) {
                FakeMode.POINT -> R.string.fakegps_mode_point_hint
                FakeMode.JOYSTICK -> R.string.fakegps_mode_joystick_hint
                FakeMode.ROUTE -> R.string.fakegps_mode_route_hint
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (config.mode != FakeMode.POINT) {
        AbleSingleChoiceToggleGroup(
            options = SpeedPreset.entries,
            selected = config.speed,
            onSelect = events.onSpeedChange,
        ) { speed ->
            Icon(
                when (speed) {
                    SpeedPreset.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
                    SpeedPreset.BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike
                    SpeedPreset.CAR -> Icons.Rounded.DirectionsCar
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(AbleSpacing.xs))
            Text(
                stringResource(speed.labelRes(), speedKmh(speed.metersPerSecond)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (config.mode == FakeMode.ROUTE) {
        AbleSingleChoiceToggleGroup(
            options = RouteRepeat.entries,
            selected = config.repeat,
            onSelect = events.onRepeatChange,
        ) { repeat ->
            Text(stringResource(repeat.labelRes()), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val path = remember(config.waypoints, config.repeat) {
            RoutePath(config.waypoints, closed = config.repeat == RouteRepeat.LOOP).totalLength
        }
        Text(
            stringResource(R.string.fakegps_route_summary, config.waypoints.size, distanceText(path)),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.engine.status == EngineStatus.Running && config.waypoints.size >= 2) {
            Text(
                if (state.engine.routeFinished) {
                    stringResource(R.string.fakegps_route_finished)
                } else {
                    stringResource(R.string.fakegps_route_progress, (state.engine.routeProgress * 100).roundToInt())
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AbleButtonFlow {
            AbleOutlinedButton(onClick = events.onUndoWaypoint, enabled = config.waypoints.isNotEmpty(), modifier = Modifier.focusRing()) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AbleSpacing.xs))
                Text(stringResource(R.string.fakegps_route_undo), maxLines = 1)
            }
            AbleOutlinedButton(
                onClick = events.onClearWaypoints,
                enabled = config.waypoints.isNotEmpty(),
                haptic = AbleHaptic.Reject,
                modifier = Modifier.focusRing(),
            ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AbleSpacing.xs))
                Text(stringResource(R.string.fakegps_route_clear), maxLines = 1)
            }
            AbleOutlinedButton(
                onClick = events.onRestartRoute,
                enabled = state.engine.status == EngineStatus.Running && config.waypoints.size >= 2,
                modifier = Modifier.focusRing(),
            ) {
                Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AbleSpacing.xs))
                Text(stringResource(R.string.fakegps_route_restart), maxLines = 1)
            }
        }
    }
}

@Composable
private fun RealismSection(config: FakeGpsConfig, events: FakeGpsEvents) {
    SectionHeader(stringResource(R.string.fakegps_section_realism))
    ListGroup {
        item { shape ->
            SwitchRow(
                title = stringResource(R.string.fakegps_jitter),
                supportingText = stringResource(R.string.fakegps_jitter_desc),
                checked = config.jitter,
                onCheckedChange = events.onJitterChange,
                shape = shape,
            )
        }
        item { shape ->
            SliderRow(
                title = stringResource(R.string.fakegps_altitude),
                valueText = stringResource(R.string.fakegps_meters_value, config.altitudeMeters.roundToInt()),
                value = config.altitudeMeters.toFloat(),
                range = FakeGpsConfig.MIN_ALTITUDE_M.toFloat()..FakeGpsConfig.MAX_ALTITUDE_M.toFloat(),
                onChange = { events.onAltitudeChange((it / 10f).roundToInt() * 10.0) },
                shape = shape,
            )
        }
        item { shape ->
            SliderRow(
                title = stringResource(R.string.fakegps_accuracy),
                valueText = stringResource(R.string.fakegps_meters_value, config.accuracyMeters.roundToInt()),
                value = config.accuracyMeters,
                range = FakeGpsConfig.MIN_ACCURACY_M..FakeGpsConfig.MAX_ACCURACY_M,
                onChange = { events.onAccuracyChange(it.roundToInt().toFloat()) },
                shape = shape,
            )
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = AbleSpacing.lg, vertical = AbleSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            AbleSlider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = title },
            )
        }
    }
}

@Composable
private fun SavedPointsSection(state: FakeGpsUiState, events: FakeGpsEvents) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(stringResource(R.string.fakegps_section_saved), modifier = Modifier.weight(1f))
        AbleTextButton(onClick = events.onOpenSavePoint, enabled = state.config.target != null) {
            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AbleSpacing.xs))
            Text(stringResource(R.string.fakegps_save_point), maxLines = 1)
        }
    }
    when {
        !state.savedLoaded -> InlineLoading()
        state.savedPoints.isEmpty() -> Text(
            stringResource(R.string.fakegps_saved_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> {
            val points = state.savedPoints.take(MAX_SAVED_SHOWN)
            val routeMode = state.config.mode == FakeMode.ROUTE
            ListGroup {
                points.forEach { point ->
                    item { shape ->
                        val supporting = buildString {
                            append(LatLon(point.latitude, point.longitude).format())
                            if (point.tags.isNotEmpty()) append(" · ").append(point.tags.joinToString(", "))
                        }
                        ActionRow(
                            title = point.label,
                            supportingText = supporting,
                            icon = Icons.Rounded.Place,
                            shape = shape,
                            trailing = {
                                Text(
                                    stringResource(if (routeMode) R.string.fakegps_add_to_route else R.string.fakegps_apply),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = { events.onApplyPoint(LatLon(point.latitude, point.longitude)) },
                        )
                    }
                }
            }
            if (state.savedPoints.size > MAX_SAVED_SHOWN) {
                Text(
                    stringResource(R.string.fakegps_saved_more, state.savedPoints.size - MAX_SAVED_SHOWN),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistorySection(state: FakeGpsUiState, events: FakeGpsEvents) {
    if (state.history.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(stringResource(R.string.fakegps_section_history), modifier = Modifier.weight(1f))
        AbleTextButton(onClick = events.onClearHistory, haptic = AbleHaptic.Reject) {
            Text(stringResource(R.string.fakegps_history_clear), maxLines = 1)
        }
    }
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    }
    ListGroup {
        state.history.forEach { entry ->
            item { shape ->
                ActionRow(
                    title = entry.position.format(),
                    supportingText = formatter.format(Instant.ofEpochMilli(entry.epochMs)),
                    icon = Icons.Rounded.History,
                    shape = shape,
                    showChevron = false,
                    onClick = { events.onApplyPoint(entry.position) },
                )
            }
        }
    }
}

@Composable
private fun SafetySection() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = AbleSpacing.lg, end = AbleSpacing.xs, top = AbleSpacing.sm, bottom = AbleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.fakegps_safety_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            HelpButton(
                title = stringResource(R.string.fakegps_help_safety_title),
                text = stringResource(R.string.fakegps_help_safety_text),
            )
        }
    }
}

// endregion

// region Dialogs

@Composable
private fun SearchDialog(state: FakeGpsUiState, events: FakeGpsEvents) {
    val haptics = rememberAbleHaptics()
    val parsed = state.searchResult
    val showError = state.searchText.isNotBlank() && parsed == null
    AlertDialog(
        onDismissRequest = events.onCloseSearch,
        icon = { Icon(Icons.Rounded.LocationSearching, contentDescription = null) },
        title = { Text(stringResource(R.string.fakegps_search_title)) },
        text = {
            OutlinedTextField(
                value = state.searchText,
                onValueChange = events.onSearchText,
                label = { Text(stringResource(R.string.fakegps_search_label)) },
                placeholder = { Text("45.464203, 9.189982") },
                singleLine = true,
                isError = showError,
                supportingText = {
                    Text(
                        when {
                            parsed != null -> stringResource(R.string.fakegps_search_found, parsed.format())
                            showError -> stringResource(R.string.fakegps_search_invalid)
                            else -> stringResource(R.string.fakegps_search_hint)
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (parsed != null) { haptics.confirm(); events.onConfirmSearch() } }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            AbleButton(onClick = events.onConfirmSearch, enabled = parsed != null, haptic = AbleHaptic.Confirm) {
                Text(stringResource(R.string.fakegps_search_go))
            }
        },
        dismissButton = {
            AbleTextButton(onClick = events.onCloseSearch) { Text(stringResource(DsR.string.ds_cancel)) }
        },
    )
}

@Composable
private fun SavePointDialog(draft: SavePointDraft, availableTags: List<String>, events: FakeGpsEvents) {
    AlertDialog(
        onDismissRequest = events.onDismissSavePoint,
        icon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null) },
        title = { Text(stringResource(R.string.fakegps_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                Text(draft.position.format(), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = events.onSaveLabel,
                    label = { Text(stringResource(R.string.fakegps_save_label)) },
                    singleLine = true,
                    isError = draft.labelError,
                    supportingText = if (draft.labelError) {
                        { Text(stringResource(R.string.fakegps_save_label_required)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                    (availableTags + draft.tags).distinct().forEach { tag ->
                        val selected = tag in draft.tags
                        AbleFilterChip(
                            selected = selected,
                            onClick = { events.onSaveToggleTag(tag) },
                            label = { Text(tagLabel(tag)) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = draft.customTag,
                    onValueChange = events.onSaveCustomTag,
                    label = { Text(stringResource(R.string.fakegps_save_custom_tag)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { events.onSaveAddCustomTag() }),
                    trailingIcon = {
                        AbleIconButton(onClick = events.onSaveAddCustomTag, enabled = draft.customTag.isNotBlank()) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.fakegps_save_add_tag))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            AbleButton(onClick = events.onConfirmSavePoint, haptic = AbleHaptic.Confirm) {
                Text(stringResource(R.string.fakegps_save_confirm))
            }
        },
        dismissButton = {
            AbleTextButton(onClick = events.onDismissSavePoint) { Text(stringResource(DsR.string.ds_cancel)) }
        },
    )
}

// endregion

// region Labels

@Composable
private fun tagLabel(tag: String): String = when (tag) {
    GpsTags.PARKING -> stringResource(R.string.gps_tag_parking)
    GpsTags.TREKKING -> stringResource(R.string.gps_tag_trekking)
    GpsTags.WORK -> stringResource(R.string.gps_tag_work)
    GpsTags.PHOTOGRAPHY -> stringResource(R.string.gps_tag_photography)
    else -> tag
}

private fun FakeMode.labelRes(): Int = when (this) {
    FakeMode.POINT -> R.string.fakegps_mode_point
    FakeMode.JOYSTICK -> R.string.fakegps_mode_joystick
    FakeMode.ROUTE -> R.string.fakegps_mode_route
}

private fun SpeedPreset.labelRes(): Int = when (this) {
    SpeedPreset.WALK -> R.string.fakegps_speed_walk
    SpeedPreset.BIKE -> R.string.fakegps_speed_bike
    SpeedPreset.CAR -> R.string.fakegps_speed_car
}

private fun RouteRepeat.labelRes(): Int = when (this) {
    RouteRepeat.ONCE -> R.string.fakegps_repeat_once
    RouteRepeat.LOOP -> R.string.fakegps_repeat_loop
    RouteRepeat.BACK_AND_FORTH -> R.string.fakegps_repeat_back_forth
}

private fun EngineFailure.messageRes(): Int = when (this) {
    EngineFailure.NOT_MOCK_APP -> R.string.fakegps_fail_not_mock_app
    EngineFailure.LOCATION_PERMISSION -> R.string.fakegps_fail_permission
    EngineFailure.FOREGROUND_NOT_ALLOWED -> R.string.fakegps_fail_foreground
    EngineFailure.NO_PROVIDER -> R.string.fakegps_fail_no_provider
    EngineFailure.REVOKED -> R.string.fakegps_fail_revoked
    EngineFailure.GENERIC -> R.string.fakegps_fail_generic
}

private fun speedKmh(metersPerSecond: Double): Int = (metersPerSecond * 3.6).roundToInt()

@Composable
private fun distanceText(meters: Double): String =
    if (meters >= 1000) {
        stringResource(R.string.fakegps_km_value, String.format(Locale.getDefault(), "%.1f", meters / 1000))
    } else {
        stringResource(R.string.fakegps_meters_value, meters.roundToInt())
    }

private const val MAX_SAVED_SHOWN = 20

// endregion
