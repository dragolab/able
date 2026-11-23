package com.dragolabs.able.feature.tools.fountains

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.Accessible
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleButtonFlow
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.AbleFilledTonalButton
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.InlineLoading
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.LocalAbleBottomBarPadding
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import java.util.Locale
import kotlin.math.roundToInt
import com.dragolabs.able.core.designsystem.R as DsR

internal class FountainsEvents(
    val onViewport: (BoundingBox, Double) -> Unit,
    val onSelect: (Fountain) -> Unit,
    val onClearSelection: () -> Unit,
    val onLocateMe: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onRefresh: () -> Unit,
    val onBottleOnly: (Boolean) -> Unit,
    val onWheelchairOnly: (Boolean) -> Unit,
    val onNavigate: (Fountain) -> Unit,
    val onShare: (Fountain) -> Unit,
    val onOpenOsm: (Fountain) -> Unit,
)

private val SheetPeekHeight = 196.dp

/** Full-bleed OSM map with water-drop markers, a floating toolbar and a "Nearest fountains" sheet. */
@Composable
internal fun FountainsScreen(
    state: FountainsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    events: FountainsEvents,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBottomSheetScaffoldState()
    val mapHandle = remember { FountainsMapHandle() }
    val darkTiles = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val haptics = rememberAbleHaptics()
    val visible = state.visible

    BottomSheetScaffold(
        // The persistent floating bar stays on screen (SHELL-001): the sheet rests above it.
        modifier = modifier.padding(bottom = LocalAbleBottomBarPadding.current),
        scaffoldState = sheetState,
        sheetPeekHeight = SheetPeekHeight,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_fountains_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    AbleIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(DsR.string.ds_navigate_back))
                    }
                },
                actions = {
                    HelpButton(
                        title = stringResource(R.string.fountains_help_title),
                        text = stringResource(R.string.fountains_help_text),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        sheetContent = { FountainsSheet(state, events) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            val mapDescription = stringResource(R.string.fountains_map_cd, visible.size)
            FountainsMap(
                startCenter = state.startCenter,
                startZoom = state.startZoom,
                fountains = visible,
                selectedKey = state.selectedKey,
                user = state.user,
                camera = state.camera,
                darkTiles = darkTiles,
                handle = mapHandle,
                onViewport = events.onViewport,
                onSelect = { haptics.tick(); events.onSelect(it) },
                onTapEmpty = events.onClearSelection,
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
                    stringResource(R.string.fountains_osm_attribution),
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
                AbleIconButton(
                    onClick = if (state.locationPermission) events.onLocateMe else events.onRequestPermission,
                    enabled = !state.locating,
                    modifier = Modifier.focusRing(),
                ) {
                    if (state.locating) {
                        InlineLoading(Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Rounded.MyLocation, contentDescription = stringResource(R.string.fountains_locate_me))
                    }
                }
                AbleIconButton(
                    onClick = events.onRefresh,
                    enabled = state.status != AreaStatus.LOADING && state.status != AreaStatus.ZOOM_IN,
                    modifier = Modifier.focusRing(),
                ) {
                    if (state.status == AreaStatus.LOADING) {
                        InlineLoading(Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.fountains_refresh))
                    }
                }
                AbleIconButton(onClick = mapHandle::zoomIn, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.fountains_zoom_in))
                }
                AbleIconButton(onClick = mapHandle::zoomOut, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.fountains_zoom_out))
                }
            }
        }
    }
}

// region Sheet

@Composable
private fun FountainsSheet(state: FountainsUiState, events: FountainsEvents) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbleSpacing.screenGutter)
            .padding(bottom = AbleSpacing.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
    ) {
        val selected = state.selected
        if (selected != null) {
            FountainDetail(selected, state.originIsUser, events)
        } else {
            SheetHeader(state)
            Filters(state.filters, events)
            AreaBanner(state, events)
            if (!state.locationPermission) {
                StatusBanner(
                    level = StatusLevel.INFO,
                    title = stringResource(R.string.fountains_permission_title),
                    message = stringResource(R.string.fountains_permission_text),
                    actionLabel = stringResource(R.string.fountains_permission_action),
                    onAction = events.onRequestPermission,
                )
            }
            NearestList(state, events)
        }
    }
}

@Composable
private fun SheetHeader(state: FountainsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.fountains_nearest_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (state.status == AreaStatus.LOADING) InlineLoading(Modifier.size(24.dp))
        }
        val line = when (state.status) {
            AreaStatus.LOADING -> stringResource(R.string.fountains_status_loading)
            AreaStatus.WAITING -> stringResource(R.string.fountains_status_waiting)
            else -> if (state.fetchedAtMs != null && state.status == AreaStatus.LOADED) {
                pluralStringResource(R.plurals.fountains_count, state.fountains.size, state.fountains.size) +
                    " · " + stringResource(if (state.originIsUser) R.string.fountains_from_you else R.string.fountains_from_center)
            } else {
                null
            }
        }
        if (line != null) {
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.fetchedAtMs?.takeIf { state.status == AreaStatus.LOADED }?.let { at ->
            val relative = DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
            Text(
                stringResource(if (state.fromCache) R.string.fountains_fetched_cached else R.string.fountains_fetched_at, relative),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Filters(filters: FountainFilters, events: FountainsEvents) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        AbleFilterChip(
            selected = filters.bottleOnly,
            onClick = { events.onBottleOnly(!filters.bottleOnly) },
            label = { Text(stringResource(R.string.fountains_filter_bottle)) },
            leadingIcon = { Icon(Icons.Rounded.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        AbleFilterChip(
            selected = filters.wheelchairOnly,
            onClick = { events.onWheelchairOnly(!filters.wheelchairOnly) },
            label = { Text(stringResource(R.string.fountains_filter_wheelchair)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Accessible, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun AreaBanner(state: FountainsUiState, events: FountainsEvents) {
    when (state.status) {
        AreaStatus.ZOOM_IN -> StatusBanner(
            level = StatusLevel.INFO,
            title = stringResource(R.string.fountains_zoom_title),
            message = stringResource(R.string.fountains_zoom_text),
        )
        AreaStatus.OFFLINE -> StatusBanner(
            level = StatusLevel.WARNING,
            title = stringResource(R.string.fountains_offline_title),
            message = stringResource(R.string.fountains_offline_text),
            actionLabel = stringResource(R.string.fountains_retry),
            onAction = events.onRefresh,
        )
        AreaStatus.BUSY -> StatusBanner(
            level = StatusLevel.WARNING,
            title = stringResource(R.string.fountains_busy_title),
            message = stringResource(R.string.fountains_busy_text),
            actionLabel = stringResource(R.string.fountains_retry),
            onAction = events.onRefresh,
        )
        AreaStatus.FAILED -> StatusBanner(
            level = StatusLevel.CRITICAL,
            title = stringResource(R.string.fountains_failed_title),
            message = stringResource(R.string.fountains_failed_text),
            actionLabel = stringResource(R.string.fountains_retry),
            onAction = events.onRefresh,
        )
        AreaStatus.LOADED -> when {
            state.fountains.isEmpty() -> StatusBanner(
                level = StatusLevel.INFO,
                title = stringResource(R.string.fountains_empty_title),
                message = stringResource(R.string.fountains_empty_text),
            )
            state.nearest.isEmpty() && state.filters.active -> StatusBanner(
                level = StatusLevel.INFO,
                title = stringResource(R.string.fountains_empty_filtered_title),
                message = stringResource(R.string.fountains_empty_filtered_text),
                actionLabel = stringResource(R.string.fountains_filters_clear),
                onAction = { events.onBottleOnly(false); events.onWheelchairOnly(false) },
            )
            else -> Unit
        }
        AreaStatus.WAITING, AreaStatus.LOADING -> Unit
    }
}

@Composable
private fun NearestList(state: FountainsUiState, events: FountainsEvents) {
    if (state.nearest.isEmpty()) return
    ListGroup {
        state.nearest.forEach { nearby ->
            item { shape ->
                val title = nearby.fountain.displayName()
                val distance = distanceLabel(nearby.distanceMeters)
                val supporting = stringResource(R.string.fountains_distance_direction, distance, nearby.compass.shortLabel())
                val description = stringResource(R.string.fountains_row_cd, title, distance, nearby.compass.longLabel())
                ActionRow(
                    title = title,
                    supportingText = supporting,
                    icon = Icons.Rounded.WaterDrop,
                    shape = shape,
                    modifier = Modifier.semantics { contentDescription = description },
                    onClick = { events.onSelect(nearby.fountain) },
                )
            }
        }
    }
}

// endregion

// region Detail

@Composable
private fun FountainDetail(nearby: NearbyFountain, originIsUser: Boolean, events: FountainsEvents) {
    val f = nearby.fountain
    Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(AbleSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(f.displayName(), style = MaterialTheme.typography.titleLarge)
                if (f.name != null) {
                    Text(stringResource(f.kind.labelRes()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AbleIconButton(onClick = events.onClearSelection) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.fountains_close_detail))
            }
        }
        if (!nearby.distanceMeters.isNaN()) {
            val distance = distanceLabel(nearby.distanceMeters)
            Text(
                stringResource(R.string.fountains_distance_direction, distance, nearby.compass.shortLabel()) +
                    " · " + stringResource(if (originIsUser) R.string.fountains_from_you else R.string.fountains_from_center),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        f.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        val tags = fountainTagLabels(f)
        Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
            tags.forEach { label ->
                Text("• $label", style = MaterialTheme.typography.bodyMedium)
            }
        }

        AbleButtonFlow(Modifier.padding(top = AbleSpacing.sm)) {
            AbleButton(onClick = { events.onNavigate(f) }, haptic = AbleHaptic.Confirm, modifier = Modifier.focusRing()) {
                Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, contentDescription = null)
                Spacer(Modifier.width(AbleSpacing.sm))
                Text(stringResource(R.string.fountains_navigate), maxLines = 1)
            }
            AbleFilledTonalButton(onClick = { events.onShare(f) }, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.Share, contentDescription = null)
                Spacer(Modifier.width(AbleSpacing.sm))
                Text(stringResource(R.string.fountains_share), maxLines = 1)
            }
            AbleOutlinedButton(onClick = { events.onOpenOsm(f) }, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.Map, contentDescription = null)
                Spacer(Modifier.width(AbleSpacing.sm))
                Text(stringResource(R.string.fountains_open_osm), maxLines = 1)
            }
        }
        Text(
            stringResource(R.string.fountains_detail_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionHeader(stringResource(R.string.fountains_osm_attribution))
    }
}

@Composable
private fun fountainTagLabels(f: Fountain): List<String> = buildList {
    add(stringResource(R.string.fountains_tag_drinkable))
    when (f.bottle) {
        TriState.YES -> add(stringResource(R.string.fountains_tag_bottle_yes))
        TriState.NO -> add(stringResource(R.string.fountains_tag_bottle_no))
        else -> Unit
    }
    when (f.wheelchair) {
        TriState.YES -> add(stringResource(R.string.fountains_tag_wheelchair_yes))
        TriState.LIMITED -> add(stringResource(R.string.fountains_tag_wheelchair_limited))
        TriState.NO -> add(stringResource(R.string.fountains_tag_wheelchair_no))
        TriState.UNKNOWN -> Unit
    }
    when (f.fee) {
        TriState.YES -> add(stringResource(R.string.fountains_tag_fee_yes))
        TriState.NO -> add(stringResource(R.string.fountains_tag_fee_no))
        else -> Unit
    }
    f.seasonal?.let { seasonal ->
        when (seasonal.lowercase(Locale.ROOT)) {
            "yes" -> add(stringResource(R.string.fountains_tag_seasonal_yes))
            "no" -> add(stringResource(R.string.fountains_tag_seasonal_no))
            else -> add(stringResource(R.string.fountains_tag_seasonal_other, seasonal))
        }
    }
    f.openingHours?.let { add(stringResource(R.string.fountains_tag_hours, it)) }
    f.operator?.let { add(stringResource(R.string.fountains_tag_operator, it)) }
    add(f.lastChecked?.let { stringResource(R.string.fountains_tag_checked, it) } ?: stringResource(R.string.fountains_tag_not_checked))
}

// endregion

// region Labels

@Composable
internal fun Fountain.displayName(): String = name ?: stringResource(kind.labelRes())

internal fun FountainKind.labelRes(): Int = when (this) {
    FountainKind.DRINKING_WATER -> R.string.fountains_kind_drinking_water
    FountainKind.WATER_TAP -> R.string.fountains_kind_water_tap
    FountainKind.SPRING -> R.string.fountains_kind_spring
    FountainKind.FOUNTAIN -> R.string.fountains_kind_fountain
}

@Composable
private fun distanceLabel(meters: Double): String = if (meters < 1000) {
    stringResource(R.string.fountains_distance_m, ((meters / 10).roundToInt() * 10).coerceAtLeast(0))
} else {
    stringResource(R.string.fountains_distance_km, String.format(Locale.getDefault(), "%.1f", meters / 1000))
}

@Composable
private fun Compass.shortLabel(): String = stringResource(
    when (this) {
        Compass.N -> R.string.fountains_dir_n
        Compass.NE -> R.string.fountains_dir_ne
        Compass.E -> R.string.fountains_dir_e
        Compass.SE -> R.string.fountains_dir_se
        Compass.S -> R.string.fountains_dir_s
        Compass.SW -> R.string.fountains_dir_sw
        Compass.W -> R.string.fountains_dir_w
        Compass.NW -> R.string.fountains_dir_nw
    },
)

@Composable
private fun Compass.longLabel(): String = stringResource(
    when (this) {
        Compass.N -> R.string.fountains_dir_long_n
        Compass.NE -> R.string.fountains_dir_long_ne
        Compass.E -> R.string.fountains_dir_long_e
        Compass.SE -> R.string.fountains_dir_long_se
        Compass.S -> R.string.fountains_dir_long_s
        Compass.SW -> R.string.fountains_dir_long_sw
        Compass.W -> R.string.fountains_dir_long_w
        Compass.NW -> R.string.fountains_dir_long_nw
    },
)

// endregion
