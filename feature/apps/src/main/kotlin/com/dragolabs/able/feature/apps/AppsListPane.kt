package com.dragolabs.able.feature.apps

import com.dragolabs.able.core.designsystem.component.hapticClickable
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.component.AbleTopLevelScaffold
import com.dragolabs.able.core.designsystem.component.ClearRestoredFocusOnTouch
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.LoadingState
import com.dragolabs.able.core.designsystem.component.MessageState
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.icon
import com.dragolabs.able.core.designsystem.component.listGroupItemShape
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.apps.data.AppInstallState
import com.dragolabs.able.feature.apps.data.UadList

/** Callbacks of the list pane, grouped to keep signatures readable. */
internal class AppsListActions(
    val onOpenSettings: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onStateFilterChange: (AppStateFilter) -> Unit,
    val onTypeFilterChange: (AppTypeFilter) -> Unit,
    val onProtectedOnlyChange: (Boolean) -> Unit,
    val onToggleList: (UadList) -> Unit,
    val onToggleRemoval: (RemovalFilter) -> Unit,
    val onClearFilters: () -> Unit,
    val onRetry: () -> Unit,
    val onSelect: (String) -> Unit,
)

@Composable
internal fun AppsListPane(
    state: AppsUiState,
    selectedPackage: String?,
    loadIcon: suspend (String) -> ImageBitmap?,
    actions: AppsListActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    /** Shown above the search field (the All apps / Background switch). */
    topContent: @Composable () -> Unit = {},
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val loaded = state.catalog is CatalogState.Loaded
    val subtitle = if (loaded) pluralStringResource(R.plurals.apps_count, state.totalCount, state.totalCount) else null
    val filters = state.filters

    AbleTopLevelScaffold(
        title = stringResource(R.string.apps_title),
        subtitle = subtitle,
        onOpenSettings = actions.onOpenSettings,
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = screenContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "view_switch", contentType = "view_switch") { topContent() }
            item(key = "search", contentType = "search") {
                AppsSearchField(query = filters.query, onQueryChange = actions.onQueryChange)
            }
            item(key = "state_filters", contentType = "filters") {
                StateFilterRow(state, actions)
            }
            item(key = "type_filters", contentType = "filters") {
                TypeFilterRow(filters, actions, onOpenSheet = { showSheet = true })
            }
            if (loaded) {
                item(key = "count", contentType = "count") {
                    ResultCountRow(state.items.size, state.totalRows, !filters.isDefault, actions.onClearFilters)
                }
            }
            when (state.catalog) {
                CatalogState.Loading -> item(key = "loading") {
                    LoadingState(message = stringResource(R.string.apps_loading))
                }
                CatalogState.Failed -> item(key = "failed") {
                    MessageState(
                        icon = Icons.Rounded.Android,
                        title = stringResource(R.string.apps_load_failed_title),
                        message = stringResource(R.string.apps_load_failed_message),
                        actionLabel = stringResource(R.string.apps_retry),
                        onAction = actions.onRetry,
                    )
                }
                is CatalogState.Loaded -> {
                    if (state.items.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                icon = if (filters.state == AppStateFilter.ABLE_ARCHIVE) Icons.Rounded.Inventory2 else Icons.Rounded.SearchOff,
                                title = stringResource(R.string.apps_empty_title),
                                message = stringResource(R.string.apps_empty_message),
                            )
                        }
                    }
                    val count = state.items.size
                    itemsIndexed(
                        state.items,
                        key = { _, item -> item.packageName },
                        contentType = { _, _ -> "app" },
                    ) { index, item ->
                        AppRow(
                            item = item,
                            selected = item.packageName == selectedPackage,
                            shape = listGroupItemShape(index, count),
                            loadIcon = loadIcon,
                            onClick = actions.onSelect,
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        MoreFiltersSheet(filters = filters, actions = actions, onDismiss = { showSheet = false })
    }
}

/**
 * Rounded, filled search field. The keyboard opens only on an explicit tap (never on initial or
 * restored focus), and Search closes it.
 */
@Composable
internal fun AppsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = stringResource(R.string.apps_search_hint_all),
) {
    ClearRestoredFocusOnTouch()
    val focusManager = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(bottom = AbleSpacing.sm),
        singleLine = true,
        shape = CircleShape,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                AbleIconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.apps_search_clear))
                }
            }
        } else {
            null
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedContainerColor = scheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun StateFilterRow(state: AppsUiState, actions: AppsListActions) {
    val haptics = rememberAbleHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
    ) {
        AppStateFilter.entries.forEach { filter ->
            // Android archiving only exists on Android 15+; hide the chip when nothing is archived.
            if (filter == AppStateFilter.ARCHIVED_BY_SYSTEM && state.systemArchivedCount == 0 &&
                state.filters.state != filter
            ) {
                return@forEach
            }
            val label = if (filter == AppStateFilter.ABLE_ARCHIVE && state.archiveCount > 0) {
                stringResource(R.string.apps_state_able_archive_count, state.archiveCount)
            } else {
                stringResource(filter.labelRes())
            }
            FilterChip(
                selected = state.filters.state == filter,
                onClick = { haptics.tick(); actions.onStateFilterChange(filter) },
                label = { Text(label) },
                leadingIcon = if (filter == AppStateFilter.ABLE_ARCHIVE) {
                    { Icon(Icons.Rounded.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun TypeFilterRow(filters: AppFilters, actions: AppsListActions, onOpenSheet: () -> Unit) {
    val haptics = rememberAbleHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
    ) {
        AppTypeFilter.entries.forEach { filter ->
            FilterChip(
                selected = filters.type == filter,
                onClick = { haptics.tick(); actions.onTypeFilterChange(filter) },
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
        FilterChip(
            selected = filters.protectedOnly,
            onClick = { haptics.tick(); actions.onProtectedOnlyChange(!filters.protectedOnly) },
            label = { Text(stringResource(R.string.apps_filter_protected)) },
            leadingIcon = { Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        val count = filters.sheetFilterCount
        FilterChip(
            selected = count > 0,
            onClick = { haptics.click(); onOpenSheet() },
            label = {
                Text(
                    if (count > 0) stringResource(R.string.apps_filters_more_count, count)
                    else stringResource(R.string.apps_filters_more),
                )
            },
            leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun ResultCountRow(shown: Int, total: Int, filtered: Boolean, onClear: () -> Unit) {
    val description = stringResource(R.string.apps_results_count_description, shown, total)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = AbleSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.apps_results_count, shown, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = description },
        )
        if (filtered) {
            AbleTextButton(onClick = onClear) { Text(stringResource(R.string.apps_filters_clear)) }
        }
    }
}

@Composable
private fun MoreFiltersSheet(filters: AppFilters, actions: AppsListActions, onDismiss: () -> Unit) {
    val haptics = rememberAbleHaptics()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbleSpacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
        ) {
            Text(stringResource(R.string.apps_filters_sheet_title), style = MaterialTheme.typography.titleLarge)

            SectionHeader(stringResource(R.string.apps_filters_removal))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm), verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                RemovalFilter.entries.forEach { removal ->
                    FilterChip(
                        selected = removal in filters.removals,
                        onClick = { haptics.tick(); actions.onToggleRemoval(removal) },
                        label = { Text(stringResource(removal.labelRes())) },
                        leadingIcon = {
                            Icon(
                                removal.level().statusLevel().icon(),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            SectionHeader(stringResource(R.string.apps_filters_list))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm), verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                UadList.entries.forEach { list ->
                    FilterChip(
                        selected = list in filters.lists,
                        onClick = { haptics.tick(); actions.onToggleList(list) },
                        label = { Text(stringResource(list.labelRes())) },
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = AbleSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm, Alignment.End),
            ) {
                if (filters.sheetFilterCount > 0) {
                    AbleTextButton(onClick = {
                        filters.removals.forEach(actions.onToggleRemoval)
                        filters.lists.forEach(actions.onToggleList)
                    }) { Text(stringResource(R.string.apps_filters_clear)) }
                }
                AbleTextButton(onClick = onDismiss) { Text(stringResource(R.string.apps_filters_done)) }
            }
        }
    }
}

@Composable
private fun AppRow(
    item: AppListItem,
    selected: Boolean,
    shape: Shape,
    loadIcon: suspend (String) -> ImageBitmap?,
    onClick: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val inactive = item.state != AppInstallState.INSTALLED
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .hapticClickable(onClickLabel = stringResource(R.string.apps_row_open_details)) { onClick(item.packageName) }
            .focusRing(shape),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow,
        ),
        leadingContent = {
            AppIcon(
                item.packageName,
                loadIcon,
                size = 40.dp,
                modifier = if (inactive) Modifier.alpha(0.5f) else Modifier,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val hasPills = item.removal != null || item.list != null || inactive || item.isProtected || item.inAbleArchive
                if (hasPills) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (item.removal != null) RemovalPill(item.removal)
                        item.list?.let { ListChip(it) }
                        if (inactive) StatePill(item.state)
                        if (item.isProtected) ProtectedPill()
                        if (item.inAbleArchive) ArchivePill()
                    }
                }
            }
        },
    ) {
        Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Lazily loaded app icon with a neutral placeholder when Android cannot provide one. */
@Composable
internal fun AppIcon(
    packageName: String,
    loadIcon: suspend (String) -> ImageBitmap?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) { value = loadIcon(packageName) }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        Icon(
            Icons.Rounded.Android,
            contentDescription = null,
            modifier = modifier.size(size),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
