package com.dragolabs.able.feature.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import com.dragolabs.able.core.designsystem.component.OnTopLevelReselect
import com.dragolabs.able.feature.apps.background.AppsView
import com.dragolabs.able.feature.apps.background.AppsViewSwitch
import com.dragolabs.able.feature.apps.background.BackgroundActivitySection
import com.dragolabs.able.feature.apps.background.BackgroundChange
import com.dragolabs.able.feature.apps.background.BackgroundDetailActions
import com.dragolabs.able.feature.apps.background.BackgroundListActions
import com.dragolabs.able.feature.apps.background.BackgroundListPane
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.feature.apps.data.PackageCommands
import kotlinx.coroutines.launch

/**
 * Apps destination (APP-001..006, BAT-003, background controls). Uses an adaptive list-detail pane scaffold: wide
 * windows show list and detail side by side; compact windows show one pane and Back returns to
 * the list while search and filters are kept in the ViewModel.
 */
@Composable
fun AppsRoute(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AppsViewModel = viewModel(factory = ableViewModelFactory { AppsViewModel(it) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val background = viewModel.background
    val backgroundState by background.listState.collectAsStateWithLifecycle()
    val backgroundDetail by background.detailState.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var view by rememberSaveable { mutableStateOf(AppsView.ALL) }
    val allListState = rememberLazyListState()
    val backgroundListState = rememberLazyListState()

    val selectedPackage = navigator.currentDestination?.contentKey
    LaunchedEffect(selectedPackage) {
        viewModel.select(selectedPackage)
        background.select(selectedPackage)
    }
    LaunchedEffect(view) { background.setVisible(view == AppsView.BACKGROUND) }

    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        background.onResume()
        onPauseOrDispose { }
    }

    // Re-selecting Apps in the navigation bar: back to the list, scrolled to the top. Search,
    // filters and the current view are kept.
    OnTopLevelReselect("apps") {
        while (navigator.canNavigateBack(BackNavigationBehavior.PopLatest)) {
            if (!navigator.navigateBack(BackNavigationBehavior.PopLatest)) break
        }
        (if (view == AppsView.BACKGROUND) backgroundListState else allListState).animateScrollToItem(0)
    }

    val listVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] != PaneAdaptedValue.Hidden
    val detailVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] != PaneAdaptedValue.Hidden
    val iconLoader: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap? = viewModel::loadIcon
    val openDetail: (String) -> Unit = remember(navigator, scope) {
        { pkg -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, pkg) } }
    }

    val listActions = remember(viewModel, navigator, scope) {
        AppsListActions(
            onOpenSettings = onOpenSettings,
            onQueryChange = viewModel::setQuery,
            onStateFilterChange = viewModel::setStateFilter,
            onTypeFilterChange = viewModel::setTypeFilter,
            onProtectedOnlyChange = viewModel::setProtectedOnly,
            onToggleList = viewModel::toggleList,
            onToggleRemoval = viewModel::toggleRemoval,
            onClearFilters = viewModel::clearFilters,
            onRetry = viewModel::retryLoad,
            onSelect = openDetail,
        )
    }
    val backgroundActions = remember(viewModel, navigator, scope) {
        BackgroundListActions(
            onOpenSettings = onOpenSettings,
            onQueryChange = background::setQuery,
            onTypeChange = background::setType,
            onRestrictedOnlyChange = background::setRestrictedOnly,
            onClearFilters = background::clearFilters,
            onRetry = {
                viewModel.onResume()
                background.refreshOverview()
            },
            onRequestShizukuPermission = viewModel::requestShizukuPermission,
            onSelect = openDetail,
            onSetSelectionMode = background::setSelectionMode,
            onToggleSelected = background::toggleSelected,
            onSelectAllShown = background::selectAllShown,
            onClearSelection = background::clearSelection,
            bulkSummary = background::bulkSummary,
            onRunBulk = background::runBulk,
            onDismissBulkResult = background::dismissBulkResult,
        )
    }
    val viewSwitch: @Composable () -> Unit = { AppsViewSwitch(view = view, onChange = { view = it }) }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                when (view) {
                    AppsView.ALL -> AppsListPane(
                        state = state,
                        selectedPackage = selectedPackage.takeIf { detailVisible },
                        loadIcon = iconLoader,
                        actions = listActions,
                        listState = allListState,
                        topContent = viewSwitch,
                    )
                    AppsView.BACKGROUND -> BackgroundListPane(
                        state = backgroundState,
                        totalApps = state.totalCount,
                        selectedPackage = selectedPackage.takeIf { detailVisible },
                        loadIcon = iconLoader,
                        actions = backgroundActions,
                        listState = backgroundListState,
                        topContent = viewSwitch,
                    )
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val detail = state.detail
                if (detail == null) {
                    AppDetailPlaceholder()
                } else {
                    val pkg = detail.packageName
                    val label = detail.label
                    val detailActions = remember(viewModel, pkg, label) {
                        AppDetailActions(
                            onSetProtected = { viewModel.setProtected(pkg, it) },
                            onSetLevel = { profile, level -> viewModel.setInterventionLevel(pkg, profile, level) },
                            onOpenAppInfo = { context.openAppInfo(pkg) },
                            onOpenUsageAccess = { context.openSettingsIntent(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                            onRequestShizukuPermission = viewModel::requestShizukuPermission,
                            onForceStop = { viewModel.forceStop(pkg, label) },
                            onDismissForceStopResult = viewModel::dismissForceStopResult,
                            onOperation = { viewModel.runOperation(pkg, it) },
                            onDismissOperationResult = viewModel::dismissOperationResult,
                            onPrepareUninstall = { viewModel.prepareUninstall(pkg) },
                            onCancelUninstall = viewModel::cancelUninstall,
                            onUninstall = { allow -> viewModel.runOperation(pkg, PackageOperation.UNINSTALL, allowWithoutBackup = allow) },
                            onOpenStore = { installer -> context.openStorePage(pkg, installer) },
                            onDeleteBackup = { viewModel.deleteBackup(pkg) },
                        )
                    }
                    val backgroundDetailActions = remember(viewModel, pkg, label) {
                        BackgroundDetailActions(
                            onSetAllowed = { allow -> background.apply(pkg, label, BackgroundChange.SetBackground(allow)) },
                            onSetBucket = { bucket -> background.apply(pkg, label, BackgroundChange.SetBucket(bucket)) },
                            onDismissResult = background::dismissOperationResult,
                            onRequestShizukuPermission = viewModel::requestShizukuPermission,
                        )
                    }
                    AppDetailPane(
                        detail = detail,
                        shizuku = state.shizuku,
                        loadIcon = iconLoader,
                        onBack = if (listVisible) null else ({ scope.launch { navigator.navigateBack() } }),
                        actions = detailActions,
                        backgroundSection = {
                            BackgroundActivitySection(
                                state = backgroundDetail?.takeIf { it.packageName == pkg },
                                shizuku = state.shizuku,
                                actions = backgroundDetailActions,
                            )
                        },
                    )
                }
            }
        },
    )
}

private fun Context.openAppInfo(packageName: String) {
    openSettingsIntent(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )
}

/**
 * Opens the store page of [packageName]: the installer's own store first (when it handles
 * `market://`), then any store, then the Play web page.
 */
private fun Context.openStorePage(packageName: String, installer: String?) {
    val (market, web) = PackageCommands.storeUris(packageName) ?: return
    val candidates = buildList {
        if (installer != null && PackageCommands.isValidPackageName(installer)) {
            add(Intent(Intent.ACTION_VIEW, Uri.parse(market)).setPackage(installer))
        }
        add(Intent(Intent.ACTION_VIEW, Uri.parse(market)))
        add(Intent(Intent.ACTION_VIEW, Uri.parse(web)))
    }
    for (intent in candidates) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
    Toast.makeText(this, R.string.apps_store_unavailable, Toast.LENGTH_LONG).show()
}

private fun Context.openSettingsIntent(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.apps_settings_unavailable, Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(this, R.string.apps_settings_unavailable, Toast.LENGTH_LONG).show()
    }
}
