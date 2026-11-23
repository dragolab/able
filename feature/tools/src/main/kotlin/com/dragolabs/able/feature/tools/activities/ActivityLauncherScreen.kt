package com.dragolabs.able.feature.tools.activities

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleButtonFlow
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ClearRestoredFocusOnTouch
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.ExpressiveIcon
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.LoadingState
import com.dragolabs.able.core.designsystem.component.MessageState
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.MessageEffect
import com.dragolabs.able.feature.tools.common.ToolsSearchField
import com.dragolabs.able.feature.tools.common.highlightMatches
import com.dragolabs.able.feature.tools.shortcuts.ShortcutEditorRoute
import com.dragolabs.able.core.data.shortcut.ShortcutTarget
import java.util.UUID
import com.dragolabs.able.core.designsystem.R as DsR

private typealias IconLoader = suspend (LaunchableActivity, Int) -> Bitmap?

@Composable
fun ActivityLauncherRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ActivityLauncherViewModel = viewModel(
        factory = ableViewModelFactory { container -> ActivityLauncherViewModel(ActivityCatalog(container.appContext)) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Create shortcut opens the shared Shortcut Maker editor, prefilled (SHORT-001, ACT-002).
    var editorKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editorPackage by rememberSaveable { mutableStateOf("") }
    var editorClass by rememberSaveable { mutableStateOf("") }
    var editorLabel by rememberSaveable { mutableStateOf("") }
    val key = editorKey
    if (key != null) {
        ShortcutEditorRoute(
            sessionKey = key,
            initialTarget = ShortcutTarget.Activity(editorPackage, editorClass),
            initialLabel = editorLabel,
            onClose = { editorKey = null },
            modifier = modifier,
        )
        return
    }

    ActivityLauncherScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::setQuery,
        onMainOnly = viewModel::setMainOnly,
        onGroupByApp = viewModel::setGroupByApp,
        onToggleGroup = viewModel::toggleGroup,
        onSelect = viewModel::select,
        onOpen = viewModel::open,
        onCreateShortcut = { activity ->
            editorPackage = activity.packageName
            editorClass = activity.className
            editorLabel = activity.label
            editorKey = UUID.randomUUID().toString()
        },
        onRetry = viewModel::reload,
        iconLoader = viewModel::loadIcon,
        onMessageConsumed = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@Composable
fun ActivityLauncherScreen(
    state: ActivityLauncherUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onMainOnly: (Boolean) -> Unit,
    onGroupByApp: (Boolean) -> Unit,
    onToggleGroup: (String) -> Unit,
    onSelect: (LaunchableActivity?) -> Unit,
    onOpen: (LaunchableActivity) -> Unit,
    onCreateShortcut: (LaunchableActivity) -> Unit,
    onRetry: () -> Unit,
    iconLoader: IconLoader,
    onMessageConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, onMessageConsumed)
    ClearRestoredFocusOnTouch()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 840.dp
        val showDetailOnly = !twoPane && state.selected != null
        BackHandler(enabled = showDetailOnly) { onSelect(null) }

        AbleDetailScaffold(
            title = stringResource(R.string.tools_activities_title),
            subtitle = if (!state.loading && !state.loadFailed) {
                stringResource(R.string.tools_activities_count, state.totalActivities)
            } else {
                null
            },
            onBack = if (showDetailOnly) ({ onSelect(null) }) else onBack,
            snackbarHostState = snackbarHostState,
        ) { padding ->
            val contentPadding = screenContentPadding(padding)
            when {
                state.loading -> LoadingState(modifier = Modifier.padding(padding), message = stringResource(R.string.tools_activities_loading))
                state.loadFailed -> MessageState(
                    icon = Icons.Rounded.Android,
                    title = stringResource(R.string.tools_activities_load_failed),
                    modifier = Modifier.padding(padding),
                    actionLabel = stringResource(DsR.string.ds_retry),
                    onAction = onRetry,
                )
                twoPane -> Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg)) {
                    SearchAndList(state, padding, onQueryChange, onMainOnly, onGroupByApp, onToggleGroup, onSelect, iconLoader, Modifier.weight(1f))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        val selected = state.selected
                        if (selected == null) {
                            EmptyState(
                                title = stringResource(R.string.tools_activities_select_title),
                                message = stringResource(R.string.tools_activities_select_message),
                                icon = Icons.Rounded.TouchApp,
                                modifier = Modifier.padding(padding),
                            )
                        } else {
                            ActivityDetail(selected, contentPadding, onOpen, onCreateShortcut, iconLoader)
                        }
                    }
                }
                showDetailOnly -> ActivityDetail(state.selected!!, contentPadding, onOpen, onCreateShortcut, iconLoader)
                else -> ReadableWidth {
                    SearchAndList(state, padding, onQueryChange, onMainOnly, onGroupByApp, onToggleGroup, onSelect, iconLoader, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** Search field and filter chips stay pinned under the top bar; only the results scroll. */
@Composable
private fun SearchAndList(
    state: ActivityLauncherUiState,
    scaffoldPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onMainOnly: (Boolean) -> Unit,
    onGroupByApp: (Boolean) -> Unit,
    onToggleGroup: (String) -> Unit,
    onSelect: (LaunchableActivity) -> Unit,
    iconLoader: IconLoader,
    modifier: Modifier,
) {
    val haptics = rememberAbleHaptics()
    val q = state.query.trim()
    Column(modifier.padding(top = scaffoldPadding.calculateTopPadding())) {
        ToolsSearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.tools_activities_search),
            modifier = Modifier.padding(horizontal = AbleSpacing.screenGutter).padding(top = AbleSpacing.sm),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = AbleSpacing.screenGutter),
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = AbleSpacing.sm),
        ) {
            item(key = "all") {
                LauncherFilterChip(!state.mainOnly, stringResource(R.string.tools_activities_filter_all)) { haptics.tick(); onMainOnly(false) }
            }
            item(key = "main") {
                LauncherFilterChip(state.mainOnly, stringResource(R.string.tools_activities_filter_main)) { haptics.tick(); onMainOnly(true) }
            }
            item(key = "group") {
                LauncherFilterChip(state.groupByApp, stringResource(R.string.tools_activities_group_by_app)) { haptics.toggle(!state.groupByApp); onGroupByApp(!state.groupByApp) }
            }
            item(key = "help") {
                HelpButton(title = stringResource(R.string.tools_activities_filter_help_title), text = stringResource(R.string.tools_activities_filter_help))
            }
        }
        Text(
            text = pluralStringResource(R.plurals.tools_activities_results, state.resultActivityCount, state.resultActivityCount, state.results.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AbleSpacing.screenGutter + AbleSpacing.xs, vertical = AbleSpacing.sm),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AbleSpacing.screenGutter,
                end = AbleSpacing.screenGutter,
                bottom = scaffoldPadding.calculateBottomPadding() + AbleSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (state.results.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = if (q.isEmpty()) stringResource(R.string.tools_activities_empty_title) else stringResource(R.string.tools_activities_no_results, q),
                        message = stringResource(R.string.tools_activities_empty_message),
                        icon = Icons.Rounded.Search,
                        actionLabel = if (q.isNotEmpty()) stringResource(DsR.string.ds_clear_search) else null,
                        onAction = if (q.isNotEmpty()) ({ onQueryChange("") }) else null,
                    )
                }
            }
            if (state.groupByApp) {
                state.results.forEach { app ->
                    val expanded = state.isExpanded(app.packageName)
                    item(key = "app-${app.packageName}") {
                        AppGroupHeader(app, expanded, q, iconLoader, onClick = { haptics.click(); onToggleGroup(app.packageName) })
                    }
                    if (expanded) {
                        items(app.activities, key = { "act-${it.key}" }) { activity ->
                            ActivityRow(activity, q, showApp = false, iconLoader, selected = state.selected?.key == activity.key, onClick = { haptics.click(); onSelect(activity) })
                        }
                    }
                }
            } else {
                items(state.flatResults, key = { "flat-${it.key}" }) { activity ->
                    ActivityRow(activity, q, showApp = true, iconLoader, selected = state.selected?.key == activity.key, onClick = { haptics.click(); onSelect(activity) })
                }
            }
        }
    }
}

@Composable
private fun LauncherFilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    AbleFilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        // Callers perform the haptic (tick for choices, toggle for "group by app").
        haptic = AbleHaptic.None,
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        modifier = Modifier.focusRing(MaterialTheme.shapes.small),
    )
}

@Composable
private fun AppGroupHeader(app: AppActivities, expanded: Boolean, query: String, iconLoader: IconLoader, onClick: () -> Unit) {
    val stateText = stringResource(if (expanded) R.string.tools_activities_collapse else R.string.tools_activities_expand)
    ListItem(
        modifier = Modifier
            .padding(top = AbleSpacing.sm)
            .clip(MaterialTheme.shapes.large)
            .focusRing()
            .clickable(onClickLabel = stateText, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { ActivityIcon(app.activities.first(), iconLoader, 40.dp) },
        headlineContent = { Text(highlightMatches(app.appLabel, query), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                highlightMatches(stringResource(R.string.tools_activities_group_count, app.activities.size, app.packageName), query),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null) },
    )
}

@Composable
private fun ActivityRow(
    activity: LaunchableActivity,
    query: String,
    showApp: Boolean,
    iconLoader: IconLoader,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .padding(start = if (showApp) 0.dp else AbleSpacing.lg)
            .clip(MaterialTheme.shapes.medium)
            .focusRing(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        leadingContent = if (showApp) ({ ActivityIcon(activity, iconLoader, 36.dp) }) else null,
        headlineContent = { Text(highlightMatches(activity.label, query), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            // The launcher-entry pill sits under the text so it never squeezes the label.
            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                Text(
                    highlightMatches(if (showApp) "${activity.appLabel} · ${activity.shortClassName}" else activity.shortClassName, query),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (activity.isLauncherEntry) {
                    StatusPill(text = stringResource(R.string.tools_activities_launcher_entry), level = StatusLevel.INFO)
                }
            }
        },
    )
}

@Composable
private fun ActivityDetail(
    activity: LaunchableActivity,
    contentPadding: PaddingValues,
    onOpen: (LaunchableActivity) -> Unit,
    onCreateShortcut: (LaunchableActivity) -> Unit,
    iconLoader: IconLoader,
) {
    val haptics = rememberAbleHaptics()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
    ) {
        AbleCard(style = AbleCardStyle.Hero) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg)) {
                ActivityIcon(activity, iconLoader, 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(activity.label, style = MaterialTheme.typography.titleLarge)
                    Text(activity.appLabel, style = MaterialTheme.typography.bodyMedium)
                }
            }
            SelectionContainer {
                Column {
                    Text(stringResource(R.string.tools_activities_package, activity.packageName), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.tools_activities_class, activity.className), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        AbleButtonFlow {
            AbleButton(onClick = { onOpen(activity) }, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                Text(stringResource(R.string.tools_activities_open), modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
            }
            AbleOutlinedButton(onClick = { onCreateShortcut(activity) }, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.AddToHomeScreen, contentDescription = null)
                Text(stringResource(R.string.tools_activities_create_shortcut), modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
            }
        }
        Text(
            stringResource(R.string.tools_activities_open_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActivityIcon(activity: LaunchableActivity, iconLoader: IconLoader, size: Dp) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, activity.key, sizePx) {
        value = iconLoader(activity, sizePx)?.asImageBitmap()
    }
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = Modifier.size(size))
    } else {
        ExpressiveIcon(icon = Icons.Rounded.Android, size = size, iconSize = size / 2)
    }
}
