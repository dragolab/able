package com.dragolabs.able.feature.apps.background

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.designsystem.component.AbleFilledTonalButton
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleTopLevelScaffold
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.LoadingState
import com.dragolabs.able.core.designsystem.component.MessageState
import com.dragolabs.able.core.designsystem.component.PermissionRequiredState
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.listGroupItemShape
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.component.statusColors
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.apps.AppIcon
import com.dragolabs.able.feature.apps.MiniPill
import com.dragolabs.able.feature.apps.AppTypeFilter
import com.dragolabs.able.feature.apps.AppsSearchField
import com.dragolabs.able.feature.apps.ProtectedPill
import com.dragolabs.able.feature.apps.R
import com.dragolabs.able.feature.apps.RemovalPill
import com.dragolabs.able.feature.apps.labelRes

/** Callbacks of the Background list. */
internal class BackgroundListActions(
    val onOpenSettings: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onTypeChange: (AppTypeFilter) -> Unit,
    val onRestrictedOnlyChange: (Boolean) -> Unit,
    val onClearFilters: () -> Unit,
    val onRetry: () -> Unit,
    val onRequestShizukuPermission: () -> Unit,
    val onSelect: (String) -> Unit,
    val onSetSelectionMode: (Boolean) -> Unit,
    val onToggleSelected: (String) -> Unit,
    val onSelectAllShown: () -> Unit,
    val onClearSelection: () -> Unit,
    val bulkSummary: () -> BulkSummary,
    val onRunBulk: (restrict: Boolean) -> Unit,
    val onDismissBulkResult: () -> Unit,
)

/**
 * Background view of the Apps destination: every installed app with its background state,
 * filters, search and bulk Restrict / Allow through Shizuku.
 */
@Composable
internal fun BackgroundListPane(
    state: BackgroundListUiState,
    totalApps: Int,
    selectedPackage: String?,
    loadIcon: suspend (String) -> ImageBitmap?,
    actions: BackgroundListActions,
    listState: LazyListState,
    topContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAbleHaptics()
    var confirmRestrict by rememberSaveable { mutableStateOf(false) }
    var confirmAllow by rememberSaveable { mutableStateOf(false) }
    val loaded = state.load as? OverviewLoad.Loaded
    val subtitle = if (loaded != null) {
        pluralStringResource(R.plurals.apps_bg_restricted_count, state.restrictedCount, state.restrictedCount)
    } else {
        pluralStringResource(R.plurals.apps_count, totalApps, totalApps)
    }

    val bulkDone = state.bulk as? BulkUi.Done
    LaunchedEffect(bulkDone) {
        if (bulkDone != null) {
            if (bulkDone.rejected + bulkDone.notConfirmed == 0 && bulkDone.applied > 0) haptics.confirm() else haptics.reject()
        }
    }

    val canChange = loaded != null && state.bulk !is BulkUi.Running
    AbleTopLevelScaffold(
        title = stringResource(R.string.apps_title),
        subtitle = subtitle,
        onOpenSettings = actions.onOpenSettings,
        modifier = modifier,
        floatingActionButton = {
            if (state.selectionMode && state.selected.isNotEmpty() && canChange) {
                BulkActionBar(
                    count = state.selected.size,
                    onRestrict = { confirmRestrict = true },
                    onAllow = { confirmAllow = true },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = screenContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "view_switch", contentType = "view_switch") { topContent() }
            item(key = "intro", contentType = "intro") { IntroRow() }
            when (state.shizuku) {
                ShizukuConnectionState.READY -> Unit
                ShizukuConnectionState.WAITING_FOR_PERMISSION, ShizukuConnectionState.PERMISSION_DENIED -> item(key = "shizuku") {
                    PermissionRequiredState(
                        title = stringResource(R.string.apps_bg_shizuku_permission_title),
                        rationale = stringResource(R.string.apps_bg_shizuku_permission_message),
                        actionLabel = stringResource(R.string.apps_shizuku_grant),
                        onAction = actions.onRequestShizukuPermission,
                    )
                }
                else -> item(key = "shizuku") {
                    PermissionRequiredState(
                        title = stringResource(R.string.apps_bg_shizuku_missing_title),
                        rationale = stringResource(R.string.apps_bg_shizuku_missing_message),
                        actionLabel = stringResource(R.string.apps_retry),
                        onAction = actions.onRetry,
                    )
                }
            }
            item(key = "search", contentType = "search") {
                AppsSearchField(
                    query = state.filters.query,
                    onQueryChange = actions.onQueryChange,
                    placeholder = stringResource(R.string.apps_bg_search_hint),
                )
            }
            item(key = "filters", contentType = "filters") { FilterRow(state.filters, actions) }
            item(key = "count", contentType = "count") { SelectionRow(state, canChange, actions) }
            bulkBanner(state.bulk, actions)
            when (state.load) {
                OverviewLoad.Loading -> item(key = "loading") { LoadingState(message = stringResource(R.string.apps_bg_loading)) }
                OverviewLoad.Failed -> item(key = "failed") {
                    MessageState(
                        icon = Icons.Rounded.Block,
                        title = stringResource(R.string.apps_bg_load_failed_title),
                        message = stringResource(R.string.apps_bg_load_failed_message),
                        actionLabel = stringResource(R.string.apps_retry),
                        onAction = actions.onRetry,
                    )
                }
                else -> Unit
            }
            if (state.rows.isEmpty() && state.load !is OverviewLoad.Loading) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.apps_empty_title),
                        message = stringResource(R.string.apps_empty_message),
                    )
                }
            }
            val count = state.rows.size
            itemsIndexed(state.rows, key = { _, row -> row.packageName }, contentType = { _, _ -> "bg_app" }) { index, row ->
                BackgroundAppRow(
                    row = row,
                    selected = row.packageName == selectedPackage,
                    selectionMode = state.selectionMode,
                    checked = row.packageName in state.selected,
                    shape = listGroupItemShape(index, count),
                    loadIcon = loadIcon,
                    onOpen = actions.onSelect,
                    onToggle = actions.onToggleSelected,
                )
            }
        }
    }

    if (confirmRestrict) {
        RestrictConfirmDialog(
            summary = actions.bulkSummary(),
            label = null,
            onConfirm = {
                confirmRestrict = false
                actions.onRunBulk(true)
            },
            onDismiss = { confirmRestrict = false },
        )
    }
    if (confirmAllow) {
        AllowConfirmDialog(
            summary = actions.bulkSummary(),
            onConfirm = {
                confirmAllow = false
                actions.onRunBulk(false)
            },
            onDismiss = { confirmAllow = false },
        )
    }
}

@Composable
private fun IntroRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = AbleSpacing.xs, bottom = AbleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.apps_bg_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        HelpButton(title = stringResource(R.string.apps_help_background_title), text = stringResource(R.string.apps_help_background_text))
    }
}

@Composable
private fun FilterRow(filters: BackgroundFilters, actions: BackgroundListActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
    ) {
        AppTypeFilter.entries.forEach { filter ->
            AbleFilterChip(
                selected = filters.type == filter,
                onClick = { actions.onTypeChange(filter) },
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
        AbleFilterChip(
            selected = filters.restrictedOnly,
            onClick = { actions.onRestrictedOnlyChange(!filters.restrictedOnly) },
            label = { Text(stringResource(R.string.apps_bg_filter_restricted)) },
            leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun SelectionRow(state: BackgroundListUiState, canChange: Boolean, actions: BackgroundListActions) {
    val shown = state.rows.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = AbleSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = if (state.selectionMode) {
            pluralStringResource(R.plurals.apps_bg_selected_count, state.selected.size, state.selected.size)
        } else {
            stringResource(R.string.apps_results_count, shown, state.totalRows)
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (!state.filters.isDefault && !state.selectionMode) {
            AbleTextButton(onClick = actions.onClearFilters) { Text(stringResource(R.string.apps_filters_clear)) }
        }
        when {
            state.selectionMode -> {
                AbleTextButton(onClick = actions.onSelectAllShown, enabled = shown > 0) { Text(stringResource(R.string.apps_bg_select_all)) }
                AbleTextButton(onClick = { actions.onSetSelectionMode(false) }) { Text(stringResource(R.string.apps_bg_select_done)) }
            }
            canChange -> AbleTextButton(onClick = { actions.onSetSelectionMode(true) }) {
                Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.apps_bg_select), Modifier.padding(start = AbleSpacing.xs))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.bulkBanner(bulk: BulkUi, actions: BackgroundListActions) {
    when (bulk) {
        BulkUi.Idle -> Unit
        is BulkUi.Running -> item(key = "bulk") {
            StatusBanner(
                level = StatusLevel.INFO,
                title = stringResource(if (bulk.restrict) R.string.apps_bg_bulk_running_restrict else R.string.apps_bg_bulk_running_allow),
                message = stringResource(R.string.apps_bg_bulk_progress, bulk.done, bulk.total),
            )
        }
        is BulkUi.Done -> item(key = "bulk") {
            val failed = bulk.rejected + bulk.notConfirmed
            StatusBanner(
                level = when {
                    bulk.applied == 0 && failed > 0 -> StatusLevel.CRITICAL
                    failed > 0 || bulk.skipped > 0 -> StatusLevel.WARNING
                    else -> StatusLevel.SUCCESS
                },
                title = stringResource(if (bulk.restrict) R.string.apps_bg_bulk_done_restrict else R.string.apps_bg_bulk_done_allow),
                message = stringResource(R.string.apps_bg_bulk_result, bulk.applied, bulk.rejected, bulk.notConfirmed, bulk.skipped),
                actionLabel = stringResource(R.string.apps_dismiss),
                onAction = actions.onDismissBulkResult,
            )
        }
    }
}

@Composable
private fun BulkActionBar(count: Int, onRestrict: () -> Unit, onAllow: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier.padding(AbleSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AbleTextButton(onClick = onAllow) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.apps_bg_allow_selected, count), Modifier.padding(start = AbleSpacing.xs), maxLines = 1)
            }
            AbleFilledTonalButton(onClick = onRestrict, haptic = AbleHaptic.Click) {
                Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.apps_bg_restrict_selected, count), Modifier.padding(start = AbleSpacing.xs), maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackgroundAppRow(
    row: BackgroundRow,
    selected: Boolean,
    selectionMode: Boolean,
    checked: Boolean,
    shape: Shape,
    loadIcon: suspend (String) -> ImageBitmap?,
    onOpen: (String) -> Unit,
    onToggle: (String) -> Unit,
) {
    val haptics = rememberAbleHaptics()
    val scheme = MaterialTheme.colorScheme
    val blocked = row.blockReason != null
    val statusText = stringResource(row.status.labelRes())
    val openLabel = stringResource(R.string.apps_row_open_details)
    val selectLabel = stringResource(R.string.apps_bg_row_select)
    val interaction = if (selectionMode) {
        Modifier.toggleable(value = checked, enabled = !blocked, role = Role.Checkbox) {
            haptics.toggle(it)
            onToggle(row.packageName)
        }
    } else {
        Modifier.combinedClickable(
            onClickLabel = openLabel,
            onLongClickLabel = selectLabel.takeIf { !blocked },
            onLongClick = if (blocked) null else ({ haptics.longPress(); onToggle(row.packageName) }),
            onClick = { haptics.click(); onOpen(row.packageName) },
        )
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(interaction)
            .focusRing(shape)
            // In selection mode TalkBack announces the checkbox state; the status pill stays readable.
            .then(if (selectionMode) Modifier else Modifier.semantics { stateDescription = statusText }),
        colors = ListItemDefaults.colors(
            containerColor = if (selected || checked) scheme.secondaryContainer else scheme.surfaceContainerLow,
        ),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                if (selectionMode) Checkbox(checked = checked, onCheckedChange = null, enabled = !blocked)
                AppIcon(row.packageName, loadIcon, size = 40.dp)
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BackgroundStatusPill(row.status)
                    row.bucket?.let { BucketPill(it) }
                    if (row.batteryOptimizationExempt == true) BatteryExemptPill()
                    if (row.isProtected) ProtectedPill()
                    if (row.risky && row.removal != null) RemovalPill(row.removal)
                    if (blocked) {
                        val colors = statusColors(StatusLevel.INFO)
                        MiniPill(
                            stringResource(R.string.apps_bg_blocked_pill),
                            Icons.Rounded.Lock,
                            colors.container,
                            colors.content,
                            colors.accent,
                        )
                    }
                }
            }
        },
    ) {
        Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
