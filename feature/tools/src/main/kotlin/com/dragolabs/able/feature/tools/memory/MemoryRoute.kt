package com.dragolabs.able.feature.tools.memory

import android.text.format.Formatter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.ClearRestoredFocusOnTouch
import com.dragolabs.able.core.designsystem.component.ConfirmationDialog
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.AppIcon
import com.dragolabs.able.feature.tools.common.MessageEffect
import com.dragolabs.able.feature.tools.common.SelectableRow
import com.dragolabs.able.feature.tools.common.SelectionActionBar
import com.dragolabs.able.feature.tools.common.SelectionSlot
import com.dragolabs.able.feature.tools.common.ToolsSearchField
import com.dragolabs.able.feature.tools.common.highlightMatches
import com.dragolabs.able.feature.tools.common.startFirstAvailable
import com.dragolabs.able.feature.tools.storage.StorageRing
import kotlinx.coroutines.delay
import com.dragolabs.able.core.designsystem.R as DsR

/** RAM and running apps tool (next to Storage). Entry point wired to `ToolId.MEMORY`. */
@Composable
fun MemoryRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: MemoryViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            MemoryViewModel(
                source = MemoryDataSource(container.appContext, container.shizuku),
                shizuku = container.shizuku,
                actionLog = container.actionLog,
                logScope = container.applicationScope,
                describe = { it.resolve(container.appContext) },
                appContext = container.appContext,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    // Memory changes constantly: refresh the summary while the screen is shown.
    LaunchedEffect(viewModel) {
        while (true) {
            delay(RAM_REFRESH_MS)
            viewModel.refreshSummary()
        }
    }

    MemoryScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onFilter = viewModel::setFilter,
        onQueryChange = viewModel::setQuery,
        onToggleSelected = viewModel::toggleSelected,
        onClearSelection = viewModel::clearSelection,
        onRequestStop = viewModel::requestStop,
        onStopSelected = viewModel::requestStopSelected,
        onConfirmPending = viewModel::confirmPending,
        onDismissPending = viewModel::dismissPending,
        onOpenAppInfo = { app -> if (!context.startFirstAvailable(viewModel.appInfoIntent(app))) viewModel.reportNoHandler() },
        onOpenRunningServices = { if (!context.startFirstAvailable(*viewModel.runningServicesIntents())) viewModel.reportNoHandler() },
        onShizukuAction = viewModel::connectShizuku,
        onMessageConsumed = viewModel::consumeMessage,
        modifier = modifier,
    )
}

private const val RAM_REFRESH_MS = 3_000L

@Composable
fun MemoryScreen(
    state: MemoryUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onFilter: (MemoryAppFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSelected: (RunningApp) -> Unit,
    onClearSelection: () -> Unit,
    onRequestStop: (RunningApp, StopKind) -> Unit,
    onStopSelected: () -> Unit,
    onConfirmPending: () -> Unit,
    onDismissPending: () -> Unit,
    onOpenAppInfo: (RunningApp) -> Unit,
    onOpenRunningServices: () -> Unit,
    onShizukuAction: () -> Unit,
    onMessageConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, onMessageConsumed)
    ClearRestoredFocusOnTouch()

    val selectedCount = state.selectedApps.size
    val visibleApps = remember(state.apps, state.filter, state.query) { state.visibleApps }
    AbleDetailScaffold(
        title = stringResource(R.string.memory_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        actions = {
            AbleIconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.memory_refresh))
            }
        },
        floatingActionButton = {
            val context = LocalContext.current
            val selectedRss = state.selectedApps.sumOf { it.rssBytes }
            SelectionActionBar(
                visible = selectedCount > 0 || (state.progress != null && state.progress.total > 1),
                title = pluralStringResource(R.plurals.memory_selected_count, selectedCount, selectedCount),
                supportingText = stringResource(R.string.memory_selected_rss, Formatter.formatShortFileSize(context, selectedRss)),
                actionLabel = stringResource(R.string.memory_action_force_stop),
                actionIcon = Icons.Rounded.StopCircle,
                onAction = onStopSelected,
                onClear = onClearSelection,
                clearLabel = stringResource(R.string.memory_clear_selection),
                busy = state.progress != null,
                progressText = state.progress?.let { stringResource(R.string.memory_stop_progress, it.done, it.total) },
                destructive = true,
            )
        },
    ) { padding ->
        ReadableWidth {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Extra bottom space keeps the last row clear of the selection bar.
                contentPadding = PaddingValues(
                    start = AbleSpacing.screenGutter,
                    end = AbleSpacing.screenGutter,
                    top = padding.calculateTopPadding() + AbleSpacing.sm,
                    bottom = padding.calculateBottomPadding() + 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
            ) {
                item(key = "hero") { RamHeroCard(state.ram) }
                item(key = "honesty") {
                    StatusBanner(
                        level = StatusLevel.INFO,
                        title = stringResource(R.string.memory_honest_title),
                        message = stringResource(R.string.memory_honest_message),
                    )
                }
                if (!state.shizukuReady) {
                    item(key = "noshizuku") {
                        StatusBanner(
                            level = StatusLevel.INFO,
                            title = stringResource(R.string.memory_no_shizuku_title),
                            message = stringResource(R.string.memory_no_shizuku_message),
                            actionLabel = stringResource(
                                if (state.shizuku == ShizukuConnectionState.NOT_AVAILABLE) DsR.string.alert_open_shizuku else DsR.string.alert_authorize,
                            ),
                            onAction = onShizukuAction,
                            secondaryActionLabel = stringResource(R.string.memory_running_services),
                            onSecondaryAction = onOpenRunningServices,
                        )
                    }
                    return@LazyColumn
                }
                item(key = "apps_header") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = AbleSpacing.sm)) {
                        SectionHeader(stringResource(R.string.memory_section_apps), modifier = Modifier.weight(1f))
                        HelpButton(title = stringResource(R.string.memory_apps_help_title), text = stringResource(R.string.memory_apps_help_text))
                    }
                }
                item(key = "search") {
                    ToolsSearchField(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        placeholder = stringResource(R.string.memory_search),
                    )
                }
                item(key = "filters") {
                    // Fixed content: selection never adds controls here, so rows below never shift.
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MemoryAppFilter.entries.forEach { filter ->
                            AbleFilterChip(
                                selected = state.filter == filter,
                                onClick = { onFilter(filter) },
                                label = { Text(stringResource(filter.labelRes()), maxLines = 1) },
                            )
                        }
                    }
                }
                val apps = visibleApps
                when {
                    state.apps == null && state.loadingApps -> item(key = "loading") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md),
                            modifier = Modifier.padding(AbleSpacing.lg),
                        ) {
                            LoadingIndicator()
                            Text(stringResource(R.string.memory_loading_apps), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    state.apps == null -> item(key = "failed") {
                        StatusBanner(
                            level = StatusLevel.WARNING,
                            title = stringResource(R.string.memory_load_failed),
                            actionLabel = stringResource(DsR.string.ds_retry),
                            onAction = onRefresh,
                        )
                    }
                    apps.isEmpty() -> item(key = "empty") {
                        EmptyState(title = stringResource(R.string.memory_empty), icon = Icons.AutoMirrored.Rounded.ListAlt)
                    }
                    else -> {
                        item(key = "count") {
                            Text(
                                pluralStringResource(R.plurals.memory_apps_count, apps.size, apps.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = AbleSpacing.xs),
                            )
                        }
                        items(apps, key = { it.key }, contentType = { "app" }) { app ->
                            RunningAppRow(
                                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                app = app,
                                query = state.query,
                                selected = app.key in state.selected,
                                busy = app.key in state.busy,
                                onToggle = { onToggleSelected(app) },
                                onForceStop = { onRequestStop(app, StopKind.FORCE_STOP) },
                                onKillBackground = { onRequestStop(app, StopKind.KILL_BACKGROUND) },
                                onAppInfo = { onOpenAppInfo(app) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.pending?.let { pending ->
        val single = pending.apps.singleOrNull()
        val title = when {
            single == null -> pluralStringResource(R.plurals.memory_confirm_bulk_title, pending.apps.size, pending.apps.size)
            pending.kind == StopKind.FORCE_STOP -> stringResource(R.string.memory_confirm_force_stop_title, single.label)
            else -> stringResource(R.string.memory_confirm_kill_title, single.label)
        }
        val message = if (pending.kind == StopKind.FORCE_STOP) {
            stringResource(R.string.memory_confirm_force_stop_message)
        } else {
            stringResource(R.string.memory_confirm_kill_message)
        }
        ConfirmationDialog(
            title = title,
            message = message,
            confirmLabel = stringResource(if (pending.kind == StopKind.FORCE_STOP) R.string.memory_action_force_stop else R.string.memory_action_kill),
            onConfirm = onConfirmPending,
            onDismiss = onDismissPending,
            destructive = true,
            icon = Icons.Rounded.Warning,
        )
    }
}

/** Hero: ring of used vs available RAM, Android's low-memory threshold and swap. */
@Composable
private fun RamHeroCard(ram: RamSummary?) {
    val context = LocalContext.current
    AbleCard(
        title = stringResource(R.string.memory_hero_title),
        icon = Icons.Rounded.Memory,
        style = AbleCardStyle.Hero,
        trailing = { HelpButton(title = stringResource(R.string.memory_hero_help_title), text = stringResource(R.string.memory_hero_help_text)) },
        status = if (ram?.lowMemory == true) {
            { StatusPill(text = stringResource(R.string.memory_low_memory), level = StatusLevel.WARNING) }
        } else {
            null
        },
    ) {
        if (ram == null) {
            Text(stringResource(R.string.memory_summary_unavailable), style = MaterialTheme.typography.bodyMedium)
            return@AbleCard
        }
        val used = Formatter.formatShortFileSize(context, ram.usedBytes)
        val total = Formatter.formatShortFileSize(context, ram.totalBytes)
        val available = Formatter.formatShortFileSize(context, ram.availableBytes)
        val description = stringResource(R.string.memory_chart_description, used, total, ram.usedPercent, available)
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
        ) {
            Box(contentAlignment = Alignment.Center) {
                StorageRing(fraction = ram.usedFraction, diameter = 136.dp, thickness = 16.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.memory_percent, ram.usedPercent), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.memory_ring_in_use), style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                Text(stringResource(R.string.memory_used_value, used), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.memory_of_total, total), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.memory_available_value, available),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = AbleSpacing.sm),
                )
            }
        }
        Text(
            stringResource(R.string.memory_threshold, Formatter.formatShortFileSize(context, ram.thresholdBytes)),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = AbleSpacing.sm),
        )
        val swapTotal = ram.swapTotalBytes
        val swapUsed = ram.swapUsedBytes
        Text(
            if (swapTotal != null && swapUsed != null && swapTotal > 0) {
                stringResource(
                    R.string.memory_swap_value,
                    Formatter.formatShortFileSize(context, swapUsed),
                    Formatter.formatShortFileSize(context, swapTotal),
                )
            } else if (swapTotal == 0L) {
                stringResource(R.string.memory_swap_none)
            } else {
                stringResource(R.string.memory_swap_unknown)
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RunningAppRow(
    app: RunningApp,
    query: String,
    selected: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onForceStop: () -> Unit,
    onKillBackground: () -> Unit,
    onAppInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberAbleHaptics()
    var menuOpen by rememberSaveable(app.key) { mutableStateOf(false) }
    val size = Formatter.formatShortFileSize(context, app.rssBytes)
    val details = buildList {
        add(stringResource(R.string.memory_app_size, size))
        add(pluralStringResource(R.plurals.memory_processes, app.processCount, app.processCount))
        add(stringResource(app.importance.labelRes()))
        if (app.userId != 0) add(stringResource(R.string.memory_app_user, app.userId))
    }.joinToString(" · ")
    SelectableRow(
        selected = selected,
        onToggle = onToggle,
        slot = when {
            busy -> SelectionSlot.BUSY
            app.stoppable -> SelectionSlot.CHECKBOX
            else -> SelectionSlot.LOCKED
        },
        lockedIcon = Icons.Rounded.Lock,
        lockedDescription = stringResource(R.string.memory_protected),
        modifier = modifier,
        leading = { AppIcon(app.packageName) },
        headline = { Text(highlightMatches(app.label, query), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supporting = {
            Text(highlightMatches(app.packageName, query), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(details, maxLines = 2, overflow = TextOverflow.Ellipsis)
            app.protectReason?.let { reason ->
                Text(stringResource(reason.labelRes()), style = MaterialTheme.typography.bodySmall)
            }
        },
        trailing = {
            Box {
                AbleIconButton(onClick = { menuOpen = true }, enabled = !busy) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.memory_actions_for, app.label))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (app.stoppable) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.memory_action_force_stop)) },
                            leadingIcon = { Icon(Icons.Rounded.StopCircle, contentDescription = null) },
                            onClick = { haptics.click(); menuOpen = false; onForceStop() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.memory_action_kill)) },
                            leadingIcon = { Icon(Icons.Rounded.Memory, contentDescription = null) },
                            onClick = { haptics.click(); menuOpen = false; onKillBackground() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.memory_action_app_info)) },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = { haptics.click(); menuOpen = false; onAppInfo() },
                    )
                }
            }
        },
    )
}

private fun MemoryAppFilter.labelRes(): Int = when (this) {
    MemoryAppFilter.USER -> R.string.memory_filter_user
    MemoryAppFilter.SYSTEM -> R.string.memory_filter_system
    MemoryAppFilter.ALL -> R.string.memory_filter_all
}

private fun ProcessImportance.labelRes(): Int = when (this) {
    ProcessImportance.FOREGROUND -> R.string.memory_state_foreground
    ProcessImportance.VISIBLE -> R.string.memory_state_visible
    ProcessImportance.SERVICE -> R.string.memory_state_service
    ProcessImportance.HOME -> R.string.memory_state_home
    ProcessImportance.RECENT -> R.string.memory_state_recent
    ProcessImportance.CACHED -> R.string.memory_state_cached
    ProcessImportance.PERSISTENT -> R.string.memory_state_persistent
    ProcessImportance.OTHER -> R.string.memory_state_other
}

private fun ProtectReason.labelRes(): Int = when (this) {
    ProtectReason.SELF -> R.string.memory_protect_self
    ProtectReason.SHIZUKU -> R.string.memory_protect_shizuku
    ProtectReason.CORE_SYSTEM -> R.string.memory_protect_core
    ProtectReason.LAUNCHER -> R.string.memory_protect_launcher
    ProtectReason.PHONE -> R.string.memory_protect_phone
    ProtectReason.KEYBOARD -> R.string.memory_protect_keyboard
    ProtectReason.PERSISTENT -> R.string.memory_protect_persistent
}
