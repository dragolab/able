package com.dragolabs.able.feature.tools.storage

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleToggleButton
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ConfirmationDialog
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.PermissionRequiredState
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.MessageEffect
import com.dragolabs.able.feature.tools.common.SelectionActionBar
import com.dragolabs.able.feature.tools.common.ToolsSearchField
import com.dragolabs.able.feature.tools.common.startFirstAvailable
import com.dragolabs.able.core.designsystem.R as DsR

@Composable
fun StorageRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: StorageViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            val appContext = container.appContext
            val stats = StorageStatsSource(appContext, container.permissions)
            StorageViewModel(
                scanner = StorageScanner(appContext),
                stats = stats,
                cleaner = SystemCacheCleaner(container.shizuku, stats),
                shizuku = container.shizuku,
                actionLog = container.actionLog,
                logScope = container.applicationScope,
                formatBytes = { Formatter.formatShortFileSize(appContext, it) },
                describe = { it.resolve(appContext) },
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onMediaPermissionResult()
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onFolderPicked(uri)
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onMediaDeleteResult(approved = result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(state.pendingDeleteRequest) {
        val request = state.pendingDeleteRequest ?: return@LaunchedEffect
        viewModel.consumeDeleteRequest()
        deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }
    // Usage access, media access and Shizuku can all change outside Able; re-read on resume.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshAccess()
        onPauseOrDispose { }
    }

    val actions = remember(viewModel, context) {
        StorageActions(
            onRequestMediaAccess = { permissionLauncher.launch(viewModel.mediaPermissions) },
            onRequestUsageAccess = {
                context.startFirstAvailable(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            },
            onRetryOverview = viewModel::loadOverview,
            onTrim = viewModel::askTrimCaches,
            onConfirmTrim = viewModel::trimAllCaches,
            onAllowShizuku = viewModel::requestShizukuPermission,
            onToggleApp = viewModel::toggleApp,
            onClearAppSelection = viewModel::clearAppSelection,
            onClearSelectedApps = viewModel::askClearSelectedApps,
            onConfirmClearApps = viewModel::clearSelectedAppCaches,
            onDismissCleanupDialog = viewModel::dismissCleanupDialogs,
            onOpenAppInfo = { pkg ->
                context.startFirstAvailable(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)),
                    Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
                )
            },
            onOpenStorageSettings = {
                context.startFirstAvailable(
                    Intent(StorageManager.ACTION_MANAGE_STORAGE),
                    Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                    Intent(Settings.ACTION_SETTINGS),
                )
            },
            onCleanCache = viewModel::cleanAbleCache,
            onUseMediaLibrary = viewModel::useMediaLibrary,
            onPickFolder = { folderLauncher.launch(null) },
            onToggleKind = viewModel::toggleKind,
            onClearKinds = viewModel::clearKinds,
            onSizeChange = viewModel::setSize,
            onCustomSize = viewModel::setCustomSize,
            onAgeChange = viewModel::setAge,
            onLocationChange = viewModel::setLocation,
            onSortChange = viewModel::setSort,
            onQueryChange = viewModel::setQuery,
            onRefresh = viewModel::scan,
            onToggleItem = viewModel::toggleSelection,
            onSelectAll = viewModel::selectAll,
            onClearSelection = viewModel::clearSelection,
            onDelete = viewModel::requestDelete,
            onConfirmFolderDelete = viewModel::confirmFolderDelete,
            onDismissFolderDelete = viewModel::dismissFolderDelete,
            onMessageConsumed = viewModel::consumeMessage,
        )
    }

    StorageScreen(state = state, actions = actions, onBack = onBack, modifier = modifier)
}

/** User intents of the Storage screen, grouped so the stateless screen stays readable. */
@Immutable
class StorageActions(
    val onRequestMediaAccess: () -> Unit,
    val onRequestUsageAccess: () -> Unit,
    val onRetryOverview: () -> Unit,
    val onTrim: () -> Unit,
    val onConfirmTrim: () -> Unit,
    val onAllowShizuku: () -> Unit,
    val onToggleApp: (String) -> Unit,
    val onClearAppSelection: () -> Unit,
    val onClearSelectedApps: () -> Unit,
    val onConfirmClearApps: () -> Unit,
    val onDismissCleanupDialog: () -> Unit,
    val onOpenAppInfo: (String) -> Unit,
    val onOpenStorageSettings: () -> Unit,
    val onCleanCache: () -> Unit,
    val onUseMediaLibrary: () -> Unit,
    val onPickFolder: () -> Unit,
    val onToggleKind: (FileKind) -> Unit,
    val onClearKinds: () -> Unit,
    val onSizeChange: (SizeThreshold) -> Unit,
    val onCustomSize: (Long) -> Unit,
    val onAgeChange: (AgeThreshold) -> Unit,
    val onLocationChange: (FileLocation) -> Unit,
    val onSortChange: (FileSort) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onToggleItem: (String) -> Unit,
    val onSelectAll: () -> Unit,
    val onClearSelection: () -> Unit,
    val onDelete: () -> Unit,
    val onConfirmFolderDelete: () -> Unit,
    val onDismissFolderDelete: () -> Unit,
    val onMessageConsumed: () -> Unit,
)

private val TwoColumnMinWidth = 840.dp
private val SelectionBarSpace = 160.dp

/**
 * Bulk-action bars (same pattern as the RAM tool): clear the cache of selected apps, and delete
 * selected files after review. Each shows its count, a clear button, progress and runs only after
 * a confirmation (Able's dialog for caches and folder files, Android's for media files).
 */
@Composable
private fun StorageSelectionBars(state: StorageUiState, actions: StorageActions) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        val appCount = state.selectedApps.size
        SelectionActionBar(
            visible = appCount > 0 && state.shizukuReady,
            title = pluralStringResource(R.plurals.storage_selected_apps, appCount, appCount),
            supportingText = stringResource(R.string.storage_selected_apps_cache, Formatter.formatShortFileSize(context, state.selectedAppsCacheBytes)),
            actionLabel = stringResource(R.string.storage_clear_cache_action),
            actionIcon = Icons.Rounded.CleaningServices,
            onAction = actions.onClearSelectedApps,
            onClear = actions.onClearAppSelection,
            clearLabel = stringResource(R.string.storage_clear_selection),
            busy = state.clearingApps,
            progressText = stringResource(R.string.storage_clear_progress, state.appClearProgress, appCount),
            actionEnabled = !state.cleanupBusy,
        )
        val fileCount = state.selected.size
        SelectionActionBar(
            visible = fileCount > 0,
            title = pluralStringResource(R.plurals.storage_selected_files, fileCount, fileCount),
            supportingText = stringResource(R.string.storage_selected_total, Formatter.formatShortFileSize(context, state.selectedBytes)),
            actionLabel = stringResource(R.string.storage_delete_action),
            actionIcon = Icons.Rounded.Delete,
            onAction = actions.onDelete,
            onClear = actions.onClearSelection,
            clearLabel = stringResource(R.string.storage_clear_selection),
            busy = state.deleting,
            progressText = stringResource(R.string.storage_deleting),
            destructive = true,
        )
    }
}

/**
 * Storage tool: Overview (STO-003) → Clean up (STO-002) → Review files (STO-001).
 * Wide windows show the overview beside clean-up and review.
 */
@Composable
fun StorageScreen(
    state: StorageUiState,
    actions: StorageActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberAbleHaptics()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, actions.onMessageConsumed)
    LaunchedEffect(state.cleanupReport?.id) {
        val report = state.cleanupReport ?: return@LaunchedEffect
        if (report.success) haptics.confirm() else haptics.reject()
    }

    AbleDetailScaffold(
        title = stringResource(R.string.tools_storage_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        actions = {
            AbleIconButton(
                onClick = { actions.onRetryOverview(); actions.onRefresh() },
                enabled = !state.scanning,
                modifier = Modifier.focusRing(),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.tools_storage_refresh))
            }
        },
        floatingActionButton = { StorageSelectionBars(state, actions) },
    ) { scaffoldPadding ->
        // Extra bottom space keeps the last rows clear of the selection bars.
        val barsVisible = state.selected.isNotEmpty() || state.selectedApps.isNotEmpty()
        val padding = PaddingValues(
            top = scaffoldPadding.calculateTopPadding(),
            bottom = scaffoldPadding.calculateBottomPadding() + if (barsVisible) SelectionBarSpace else 0.dp,
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= TwoColumnMinWidth) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = AbleSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm, Alignment.CenterHorizontally),
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).widthIn(max = AbleSpacing.maxContentWidth),
                        contentPadding = screenContentPadding(padding),
                        verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                    ) { overviewItems(state, actions) }
                    LazyColumn(
                        modifier = Modifier.weight(1f).widthIn(max = AbleSpacing.maxContentWidth),
                        contentPadding = screenContentPadding(padding),
                        verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                    ) {
                        cleanupItems(state, actions)
                        reviewItems(state, actions)
                    }
                }
            } else {
                ReadableWidth {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = screenContentPadding(padding),
                        verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                    ) {
                        overviewItems(state, actions)
                        cleanupItems(state, actions)
                        reviewItems(state, actions)
                    }
                }
            }
        }
    }

    StorageDialogs(state, actions)
}

@Composable
private fun StorageDialogs(state: StorageUiState, actions: StorageActions) {
    val context = LocalContext.current
    if (state.confirmFolderDelete) {
        ConfirmationDialog(
            title = stringResource(R.string.tools_storage_confirm_title, state.selected.size),
            message = stringResource(
                R.string.tools_storage_confirm_message,
                Formatter.formatShortFileSize(context, state.selectedBytes),
                (state.source as? StorageSource.Folder)?.name.orEmpty(),
            ),
            confirmLabel = stringResource(DsR.string.ds_delete),
            onConfirm = actions.onConfirmFolderDelete,
            onDismiss = actions.onDismissFolderDelete,
            destructive = true,
            icon = Icons.Rounded.Delete,
        )
    }
    if (state.confirmTrim) {
        ConfirmationDialog(
            title = stringResource(R.string.storage_trim_confirm_title),
            message = stringResource(R.string.storage_trim_confirm_message),
            confirmLabel = stringResource(R.string.storage_trim_action),
            onConfirm = actions.onConfirmTrim,
            onDismiss = actions.onDismissCleanupDialog,
            icon = Icons.Rounded.CleaningServices,
        )
    }
    if (state.confirmAppClear) {
        ConfirmationDialog(
            title = stringResource(R.string.storage_apps_confirm_title, state.selectedApps.size),
            message = stringResource(R.string.storage_apps_confirm_message, Formatter.formatShortFileSize(context, state.selectedAppsCacheBytes)),
            confirmLabel = stringResource(DsR.string.ds_confirm),
            onConfirm = actions.onConfirmClearApps,
            onDismiss = actions.onDismissCleanupDialog,
            icon = Icons.Rounded.CleaningServices,
        )
    }
}

private fun LazyListScope.overviewItems(state: StorageUiState, actions: StorageActions) {
    item(key = "overview-header") { SectionHeader(stringResource(R.string.storage_section_overview)) }
    val breakdown = state.breakdown
    when {
        breakdown != null -> {
            item(key = "hero") { StorageHeroCard(breakdown) }
            item(key = "breakdown") {
                StorageBreakdownCard(
                    breakdown = breakdown,
                    hasUsageAccess = state.hasUsageAccess,
                    hasMediaPermission = state.hasMediaPermission,
                    onRequestUsageAccess = actions.onRequestUsageAccess,
                    onRequestMediaAccess = actions.onRequestMediaAccess,
                )
            }
        }
        state.overviewFailed -> item(key = "overview-failed") { OverviewFailedBanner(actions.onRetryOverview) }
        else -> item(key = "overview-loading") {
            Box(Modifier.fillMaxWidth().padding(AbleSpacing.xl), contentAlignment = Alignment.Center) { LoadingIndicator() }
        }
    }
}

private fun LazyListScope.cleanupItems(state: StorageUiState, actions: StorageActions) {
    item(key = "cleanup-header") {
        SectionHeader(stringResource(R.string.storage_section_cleanup), Modifier.padding(top = AbleSpacing.lg))
    }
    item(key = "system-cache") {
        SystemCacheCard(
            state = state,
            onTrim = actions.onTrim,
            onAllowShizuku = actions.onAllowShizuku,
            onOpenStorageSettings = actions.onOpenStorageSettings,
        )
    }
    if (state.hasUsageAccess) {
        item(key = "largest-apps") {
            LargestAppsCard(
                state = state,
                onToggleApp = actions.onToggleApp,
                onOpenAppInfo = actions.onOpenAppInfo,
            )
        }
    }
    item(key = "able-cache") { AbleCacheCard(state, actions.onCleanCache) }
}

private fun LazyListScope.reviewItems(state: StorageUiState, actions: StorageActions) {
    item(key = "review-header") {
        Row(Modifier.padding(top = AbleSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(stringResource(R.string.storage_section_review), Modifier.weight(1f))
            HelpButton(title = stringResource(R.string.storage_delete_help_title), text = stringResource(R.string.storage_delete_help_text))
        }
    }
    item(key = "source") { SourceSelector(state, actions.onUseMediaLibrary, actions.onPickFolder) }

    if (state.source == StorageSource.MediaLibrary && !state.hasMediaPermission) {
        item(key = "permission") {
            PermissionRequiredState(
                title = stringResource(R.string.tools_storage_permission_title),
                rationale = stringResource(R.string.tools_storage_permission_rationale),
                actionLabel = stringResource(R.string.tools_storage_permission_action),
                onAction = actions.onRequestMediaAccess,
                secondaryActionLabel = stringResource(R.string.tools_storage_pick_folder),
                onSecondaryAction = actions.onPickFolder,
            )
        }
        return
    }
    if (state.source == StorageSource.MediaLibrary) {
        item(key = "scope") {
            StatusBanner(
                level = if (state.partialMediaAccess) StatusLevel.WARNING else StatusLevel.INFO,
                title = stringResource(if (state.partialMediaAccess) R.string.tools_storage_partial_title else R.string.tools_storage_scope_title),
                message = stringResource(if (state.partialMediaAccess) R.string.tools_storage_partial_message else R.string.tools_storage_scope_message),
            )
        }
    }
    item(key = "search") {
        ToolsSearchField(
            query = state.filters.query,
            onQueryChange = actions.onQueryChange,
            placeholder = stringResource(R.string.storage_search_files),
        )
    }
    item(key = "filters") { ReviewFilters(state, actions, Modifier.padding(top = AbleSpacing.xs)) }
    item(key = "results-header") { ReviewResultsHeader(state, actions.onSelectAll) }
    when {
        state.scanning && state.items.isEmpty() -> item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(AbleSpacing.xl), contentAlignment = Alignment.Center) { LoadingIndicator() }
        }
        state.scanFailed -> item(key = "failed") {
            StatusBanner(
                level = StatusLevel.CRITICAL,
                title = stringResource(R.string.tools_storage_scan_failed),
                actionLabel = stringResource(DsR.string.ds_retry),
                onAction = actions.onRefresh,
            )
        }
        state.scanned && state.visibleItems.isEmpty() -> item(key = "empty") {
            val mediaLibrary = state.source == StorageSource.MediaLibrary
            EmptyState(
                title = stringResource(R.string.tools_storage_empty_title),
                message = stringResource(if (mediaLibrary) R.string.storage_empty_media_hint else R.string.tools_storage_empty_message),
                icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                actionLabel = if (mediaLibrary) stringResource(R.string.tools_storage_pick_folder) else null,
                onAction = if (mediaLibrary) actions.onPickFolder else null,
            )
        }
        else -> {
            if (state.scanning) {
                // Keep the current rows while a new scan runs, so the list does not jump.
                item(key = "rescanning") { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
            }
            items(state.visibleItems, key = { it.uriString }, contentType = { "file" }) { item ->
                ReviewFileRow(
                    item = item,
                    query = state.filters.query,
                    selected = item.uriString in state.selected,
                    enabled = !state.deleting,
                    onToggle = { actions.onToggleItem(item.uriString) },
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                )
            }
        }
    }
}

@Composable
private fun SourceSelector(state: StorageUiState, onUseMediaLibrary: () -> Unit, onPickFolder: () -> Unit) {
    val haptics = rememberAbleHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        Text(stringResource(R.string.tools_storage_source), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            AbleToggleButton(
                checked = state.source == StorageSource.MediaLibrary,
                onCheckedChange = { onUseMediaLibrary() },
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                modifier = Modifier.weight(1f).focusRing().semantics { role = Role.RadioButton },
            ) {
                Icon(Icons.Rounded.PermMedia, contentDescription = null)
                Text(stringResource(R.string.tools_storage_source_media), modifier = Modifier.padding(start = AbleSpacing.xs), maxLines = 1)
            }
            AbleToggleButton(
                checked = state.source is StorageSource.Folder,
                onCheckedChange = { onPickFolder() },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                modifier = Modifier.weight(1f).focusRing().semantics { role = Role.RadioButton },
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null)
                Text(stringResource(R.string.tools_storage_source_folder), modifier = Modifier.padding(start = AbleSpacing.xs), maxLines = 1)
            }
        }
        Text(
            stringResource(R.string.storage_folder_limits),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val folder = state.source as? StorageSource.Folder
        if (folder != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tools_storage_folder_selected, folder.name),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                AbleTextButton(onClick = onPickFolder, modifier = Modifier.focusRing()) { Text(stringResource(R.string.tools_storage_change_folder)) }
            }
        }
    }
}
