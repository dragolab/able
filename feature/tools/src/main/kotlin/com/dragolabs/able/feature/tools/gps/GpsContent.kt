package com.dragolabs.able.feature.tools.gps

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.component.AbleButtonFlow
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleFilledTonalButton
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.ConfirmationDialog
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.LoadingState
import com.dragolabs.able.core.designsystem.component.MetricGrid
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.GpsPoint
import com.dragolabs.able.feature.tools.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.dragolabs.able.core.designsystem.R as DsR

/** Android-side actions on a point, implemented by the route. */
internal class GpsPointActions(
    val onCopy: (String) -> Unit,
    val onShare: (String) -> Unit,
    val onOpenMap: (point: GpsPoint, label: String) -> Unit,
)

internal class GpsEditorEvents(
    val onLabelChange: (String) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onCustomTagChange: (String) -> Unit,
    val onToggleTag: (String) -> Unit,
    val onAddCustomTag: () -> Unit,
    val onSave: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
internal fun GpsContent(
    state: GpsUiState,
    actions: GpsPointActions,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onCapture: () -> Unit,
    onCancelCapture: () -> Unit,
    onDismissCaptureError: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onQueryChange: (String) -> Unit,
    onTagFilterChange: (String?) -> Unit,
    onOpenSheet: (GpsSheetTarget) -> Unit,
    onCloseSheet: () -> Unit,
    onSaveCaptured: () -> Unit,
    onEdit: (GpsPoint) -> Unit,
    onRequestDelete: (GpsPoint) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    editorEvents: GpsEditorEvents,
    modifier: Modifier = Modifier,
) {
    AbleDetailScaffold(
        title = stringResource(R.string.gps_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        val capturePanel: @Composable () -> Unit = {
            CapturePanel(
                state = state,
                actions = actions,
                onCapture = onCapture,
                onCancelCapture = onCancelCapture,
                onDismissCaptureError = onDismissCaptureError,
                onOpenAppSettings = onOpenAppSettings,
                onOpenLocationSettings = onOpenLocationSettings,
                onSaveCaptured = onSaveCaptured,
                onMore = { onOpenSheet(GpsSheetTarget.Captured) },
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 840.dp) {
                // Expanded: capture on the left, catalog on the right (list-detail style).
                val contentPadding = screenContentPadding(padding)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(AbleSpacing.xl)) {
                    Column(
                        Modifier
                            .weight(0.42f)
                            .verticalScroll(rememberScrollState())
                            .padding(contentPadding.withoutEnd()),
                        verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
                    ) { capturePanel() }
                    LazyColumn(
                        modifier = Modifier.weight(0.58f),
                        contentPadding = contentPadding.withoutStart(),
                        verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                    ) {
                        catalogItems(state, onQueryChange, onTagFilterChange) { onOpenSheet(GpsSheetTarget.Saved(it.id)) }
                    }
                }
            } else {
                ReadableWidth {
                    LazyColumn(
                        contentPadding = screenContentPadding(padding),
                        verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                    ) {
                        item(key = "capture") {
                            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg)) { capturePanel() }
                        }
                        item(key = "spacer") { Spacer(Modifier.size(AbleSpacing.sm)) }
                        catalogItems(state, onQueryChange, onTagFilterChange) { onOpenSheet(GpsSheetTarget.Saved(it.id)) }
                    }
                }
            }
        }
    }

    val sheetPoint = state.sheetPoint
    val sheetTarget = state.sheetTarget
    if (sheetPoint != null && sheetTarget != null) {
        PointSheet(
            point = sheetPoint,
            saved = sheetTarget is GpsSheetTarget.Saved,
            actions = actions,
            onDismiss = onCloseSheet,
            onSaveCaptured = onSaveCaptured,
            onEdit = { onEdit(sheetPoint) },
            onDelete = { onRequestDelete(sheetPoint) },
        )
    }

    state.editor?.let { editor -> PointEditorDialog(editor, state.availableTags, editorEvents) }

    state.pendingDelete?.let { point ->
        ConfirmationDialog(
            title = stringResource(R.string.gps_delete_title),
            message = stringResource(R.string.gps_delete_msg, point.label),
            confirmLabel = stringResource(DsR.string.ds_delete),
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
            destructive = true,
            icon = Icons.Rounded.Delete,
        )
    }
}

/** Two-pane padding: the outer gutter only on the outside edge of each pane. */
private fun PaddingValues.withoutEnd(): PaddingValues = PaddingValues(
    start = AbleSpacing.screenGutter,
    top = calculateTopPadding(),
    bottom = calculateBottomPadding(),
)

private fun PaddingValues.withoutStart(): PaddingValues = PaddingValues(
    end = AbleSpacing.screenGutter,
    top = calculateTopPadding(),
    bottom = calculateBottomPadding(),
)

// region Capture

@Composable
private fun CapturePanel(
    state: GpsUiState,
    actions: GpsPointActions,
    onCapture: () -> Unit,
    onCancelCapture: () -> Unit,
    onDismissCaptureError: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onSaveCaptured: () -> Unit,
    onMore: () -> Unit,
) {
    when (state.permission) {
        LocationPermissionStatus.Denied -> StatusBanner(
            level = StatusLevel.WARNING,
            title = stringResource(R.string.gps_permission_denied_title),
            message = stringResource(R.string.gps_permission_denied_msg),
            actionLabel = stringResource(DsR.string.ds_open_android_settings),
            onAction = onOpenAppSettings,
            secondaryActionLabel = stringResource(R.string.gps_permission_grant),
            onSecondaryAction = onCapture,
        )

        LocationPermissionStatus.ApproximateOnly -> StatusBanner(
            level = StatusLevel.WARNING,
            title = stringResource(R.string.gps_permission_approximate_title),
            message = stringResource(R.string.gps_permission_approximate_msg),
            actionLabel = stringResource(DsR.string.ds_open_android_settings),
            onAction = onOpenAppSettings,
            secondaryActionLabel = stringResource(R.string.gps_permission_grant),
            onSecondaryAction = onCapture,
        )

        else -> Unit
    }
    if (!state.locationEnabled && state.permission == LocationPermissionStatus.Granted) {
        StatusBanner(
            level = StatusLevel.WARNING,
            title = stringResource(R.string.gps_location_off_title),
            message = stringResource(R.string.gps_location_off_msg),
            actionLabel = stringResource(R.string.gps_location_settings),
            onAction = onOpenLocationSettings,
        )
    }

    when (val capture = state.capture) {
        CaptureState.Acquiring -> AcquiringCard(onCancelCapture)
        is CaptureState.Captured -> CapturedCard(capture.point, actions, onSaveCaptured, onCapture, onMore)
        is CaptureState.Failed -> {
            if (capture.error != CaptureError.Permission && capture.error != CaptureError.LocationOff) {
                StatusBanner(
                    level = StatusLevel.CRITICAL,
                    title = stringResource(capture.error.messageRes()),
                    actionLabel = stringResource(DsR.string.ds_retry),
                    onAction = onCapture,
                    secondaryActionLabel = stringResource(DsR.string.ds_close),
                    onSecondaryAction = onDismissCaptureError,
                )
            }
            IdleCaptureCard(state.permission, onCapture)
        }

        CaptureState.Idle -> IdleCaptureCard(state.permission, onCapture)
    }
}

@Composable
private fun IdleCaptureCard(permission: LocationPermissionStatus, onCapture: () -> Unit) {
    AbleCard(
        title = stringResource(R.string.gps_capture_title),
        subtitle = stringResource(R.string.gps_capture_hint),
        icon = Icons.Rounded.MyLocation,
        iconShape = MaterialShapes.Cookie9Sided,
        style = AbleCardStyle.Hero,
    ) {
        if (permission == LocationPermissionStatus.NotRequested) {
            Text(
                stringResource(R.string.gps_permission_rationale),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        AbleButton(onClick = onCapture, modifier = Modifier.fillMaxWidth().focusRing()) {
            Icon(Icons.Rounded.MyLocation, contentDescription = null)
            Text(stringResource(R.string.gps_capture_action), modifier = Modifier.padding(start = AbleSpacing.sm))
        }
    }
}

@Composable
private fun AcquiringCard(onCancel: () -> Unit) {
    AbleCard(style = AbleCardStyle.Tonal, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg)) {
            LoadingIndicator(Modifier.size(56.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.gps_acquiring), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.gps_acquiring_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        AbleOutlinedButton(onClick = onCancel, modifier = Modifier.align(Alignment.End).focusRing()) {
            Text(stringResource(DsR.string.ds_cancel))
        }
    }
}

@Composable
private fun CapturedCard(
    point: GpsPoint,
    actions: GpsPointActions,
    onSave: () -> Unit,
    onCaptureAgain: () -> Unit,
    onMore: () -> Unit,
) {
    AbleCard(
        title = stringResource(R.string.gps_capture_title),
        subtitle = GpsFormat.decimal(point),
        icon = Icons.Rounded.Place,
        iconShape = MaterialShapes.Sunny,
        style = AbleCardStyle.Hero,
        trailing = {
            AbleIconButton(onClick = onMore, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.gps_more_actions))
            }
        },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        PointMetrics(point)
        point.reverseGeocodedAddress?.let { address ->
            Text(stringResource(R.string.gps_field_address), style = MaterialTheme.typography.labelLarge)
            Text(address, style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
        ) {
            AbleButton(onClick = onSave, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Text(stringResource(R.string.gps_save_point), modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
            }
            AbleFilledTonalButton(onClick = { actions.onOpenMap(point, "") }, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.Map, contentDescription = null)
                Text(stringResource(R.string.gps_open_map), modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
            }
            AbleTextButton(onClick = onCaptureAgain, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.gps_capture_again))
            }
        }
    }
}

@Composable
private fun PointMetrics(point: GpsPoint) {
    val unavailable = stringResource(DsR.string.ds_unavailable)
    MetricGrid(
        metrics = listOf(
            stringResource(R.string.gps_field_latitude) to GpsFormat.coordinate(point.latitude),
            stringResource(R.string.gps_field_longitude) to GpsFormat.coordinate(point.longitude),
            stringResource(R.string.gps_field_altitude) to (
                point.altitudeMeters?.let { stringResource(R.string.gps_value_meters, formatMeters(it)) } ?: unavailable
                ),
            stringResource(R.string.gps_field_accuracy) to
                stringResource(R.string.gps_value_accuracy, formatMeters(point.accuracyMeters.toDouble())),
            stringResource(R.string.gps_field_time) to formatTimestamp(point.timestampEpochMs),
        ),
    )
}

// endregion

// region Catalog

private fun LazyListScope.catalogItems(
    state: GpsUiState,
    onQueryChange: (String) -> Unit,
    onTagFilterChange: (String?) -> Unit,
    onOpen: (GpsPoint) -> Unit,
) {
    item(key = "catalog_header") {
        Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
            SectionHeader(stringResource(R.string.gps_catalog_title))
            Text(
                if (state.totalCount > 0) {
                    pluralStringResource(R.plurals.gps_catalog_count, state.totalCount, state.totalCount)
                } else {
                    stringResource(R.string.gps_offline_note)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (!state.catalogLoaded) {
        item(key = "catalog_loading") { LoadingState() }
        return
    }
    if (state.totalCount == 0) {
        item(key = "catalog_empty") {
            EmptyState(
                title = stringResource(R.string.gps_catalog_empty_title),
                message = stringResource(R.string.gps_catalog_empty_msg),
                icon = Icons.Rounded.TravelExplore,
            )
        }
        return
    }
    item(key = "catalog_search") {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    AbleIconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(DsR.string.ds_clear_search))
                    }
                }
            },
            label = { Text(stringResource(DsR.string.ds_search)) },
            placeholder = { Text(stringResource(R.string.gps_search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
        )
    }
    item(key = "catalog_filters") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
        ) {
            AbleFilterChip(
                selected = state.tagFilter == null,
                onClick = { onTagFilterChange(null) },
                label = { Text(stringResource(R.string.gps_filter_all)) },
                modifier = Modifier.focusRing(),
            )
            state.availableTags.forEach { tag ->
                AbleFilterChip(
                    selected = state.tagFilter == tag,
                    onClick = { onTagFilterChange(if (state.tagFilter == tag) null else tag) },
                    label = { Text(tagLabel(tag)) },
                    leadingIcon = if (state.tagFilter == tag) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    modifier = Modifier.focusRing(),
                )
            }
        }
    }
    if (state.points.isEmpty()) {
        item(key = "catalog_no_results") {
            EmptyState(title = stringResource(R.string.gps_catalog_no_results), icon = Icons.Rounded.Search)
        }
    } else {
        items(state.points, key = { it.id }) { point -> PointRow(point, onClick = { onOpen(point) }) }
    }
}

@Composable
private fun PointRow(point: GpsPoint, onClick: () -> Unit) {
    AbleCard(
        title = point.label,
        subtitle = GpsFormat.decimal(point),
        icon = Icons.Rounded.Place,
        iconShape = MaterialShapes.Cookie7Sided,
        onClick = onClick,
    ) {
        Text(
            "${formatTimestamp(point.timestampEpochMs)} · " +
                stringResource(R.string.gps_value_accuracy, formatMeters(point.accuracyMeters.toDouble())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (point.tags.isNotEmpty()) TagChips(point.tags)
        if (point.notes.isNotBlank()) {
            Text(point.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TagChips(tags: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
        tags.forEach { tag -> SuggestionChip(onClick = {}, enabled = false, label = { Text(tagLabel(tag)) }) }
    }
}

// endregion

// region Sheet and dialogs

@Composable
private fun PointSheet(
    point: GpsPoint,
    saved: Boolean,
    actions: GpsPointActions,
    onDismiss: () -> Unit,
    onSaveCaptured: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val title = if (saved) point.label else stringResource(R.string.gps_unsaved_point)
    // Unsaved positions have no name yet: share and open them as bare coordinates.
    val shareLabel = if (saved) point.label else ""
    val decimal = GpsFormat.decimal(point)
    val dms = GpsFormat.dms(point)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbleSpacing.lg)
                .navigationBarsPadding()
                .padding(bottom = AbleSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(dms, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PointMetrics(point)
            point.reverseGeocodedAddress?.let {
                Text(stringResource(R.string.gps_field_address), style = MaterialTheme.typography.labelLarge)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            if (point.tags.isNotEmpty()) TagChips(point.tags)
            if (point.notes.isNotBlank()) {
                Text(stringResource(R.string.gps_field_notes), style = MaterialTheme.typography.labelLarge)
                Text(point.notes, style = MaterialTheme.typography.bodyMedium)
            }
            ListGroup {
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.gps_open_map),
                        icon = Icons.Rounded.Map,
                        shape = shape,
                        onClick = { actions.onOpenMap(point, shareLabel) },
                    )
                }
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.gps_copy_decimal),
                        supportingText = decimal,
                        icon = Icons.Rounded.ContentCopy,
                        shape = shape,
                        showChevron = false,
                        onClick = { actions.onCopy(decimal) },
                    )
                }
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.gps_copy_dms),
                        supportingText = dms,
                        icon = Icons.Rounded.ContentCopy,
                        shape = shape,
                        showChevron = false,
                        onClick = { actions.onCopy(dms) },
                    )
                }
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.gps_share_text),
                        icon = Icons.Rounded.Share,
                        shape = shape,
                        onClick = { actions.onShare(GpsFormat.shareText(point, shareLabel)) },
                    )
                }
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.gps_share_geo),
                        icon = Icons.AutoMirrored.Rounded.TextSnippet,
                        shape = shape,
                        onClick = { actions.onShare(GpsFormat.geoUri(point, shareLabel)) },
                    )
                }
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.gps_share_web),
                        icon = Icons.Rounded.Link,
                        shape = shape,
                        onClick = { actions.onShare(GpsFormat.webMapLink(point)) },
                    )
                }
            }
            AbleButtonFlow {
                if (saved) {
                    AbleFilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f).focusRing()) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        Text(stringResource(DsR.string.ds_edit), modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
                    }
                    AbleOutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f).focusRing()) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Text(stringResource(DsR.string.ds_delete), modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
                    }
                } else {
                    AbleButton(onClick = onSaveCaptured, modifier = Modifier.weight(1f).focusRing()) {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Text(stringResource(R.string.gps_save_point), modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun PointEditorDialog(
    editor: PointEditorState,
    availableTags: List<String>,
    events: GpsEditorEvents,
) {
    val tagOptions = (availableTags + editor.tags).distinct()
    AlertDialog(
        onDismissRequest = events.onDismiss,
        title = {
            Text(stringResource(if (editor.isNew) R.string.gps_editor_new_title else R.string.gps_editor_edit_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            ) {
                Text(GpsFormat.decimal(editor.base), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = editor.label,
                    onValueChange = events.onLabelChange,
                    label = { Text(stringResource(R.string.gps_editor_label)) },
                    singleLine = true,
                    isError = editor.labelError,
                    supportingText = if (editor.labelError) {
                        { Text(stringResource(R.string.gps_editor_label_required)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.gps_editor_tags), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
                ) {
                    tagOptions.forEach { tag ->
                        val selected = tag in editor.tags
                        AbleFilterChip(
                            selected = selected,
                            onClick = { events.onToggleTag(tag) },
                            label = { Text(tagLabel(tag)) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                            modifier = Modifier.focusRing(),
                        )
                    }
                }
                OutlinedTextField(
                    value = editor.customTag,
                    onValueChange = events.onCustomTagChange,
                    label = { Text(stringResource(R.string.gps_editor_custom_tag)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { events.onAddCustomTag() }),
                    trailingIcon = {
                        AbleIconButton(onClick = events.onAddCustomTag, enabled = editor.customTag.isNotBlank()) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.gps_editor_add_tag))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.notes,
                    onValueChange = events.onNotesChange,
                    label = { Text(stringResource(R.string.gps_editor_notes)) },
                    minLines = 2,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            AbleButton(onClick = events.onSave, modifier = Modifier.focusRing()) { Text(stringResource(DsR.string.ds_save)) }
        },
        dismissButton = {
            AbleTextButton(onClick = events.onDismiss, modifier = Modifier.focusRing()) {
                Text(stringResource(DsR.string.ds_cancel))
            }
        },
    )
}

// endregion

// region Formatting helpers

@Composable
private fun tagLabel(tag: String): String = when (tag) {
    GpsTags.PARKING -> stringResource(R.string.gps_tag_parking)
    GpsTags.TREKKING -> stringResource(R.string.gps_tag_trekking)
    GpsTags.WORK -> stringResource(R.string.gps_tag_work)
    GpsTags.PHOTOGRAPHY -> stringResource(R.string.gps_tag_photography)
    else -> tag
}

private fun CaptureError.messageRes(): Int = when (this) {
    CaptureError.NoFix -> R.string.gps_error_no_fix
    CaptureError.Timeout -> R.string.gps_error_timeout
    CaptureError.Permission -> R.string.gps_permission_title
    CaptureError.LocationOff -> R.string.gps_location_off_title
    CaptureError.Generic -> R.string.gps_error_generic
}

private fun formatMeters(value: Double): String =
    if (value < 10) String.format(Locale.getDefault(), "%.1f", value) else String.format(Locale.getDefault(), "%.0f", value)

private fun formatTimestamp(epochMs: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))

// endregion
