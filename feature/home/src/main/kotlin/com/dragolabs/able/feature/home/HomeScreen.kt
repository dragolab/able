package com.dragolabs.able.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.AbleWordmark
import com.dragolabs.able.core.designsystem.component.AbleTopLevelScaffold
import com.dragolabs.able.core.designsystem.component.AbleSharedKeys
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.scrollToTopOnReselect
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.HomeCardType
import com.dragolabs.able.core.model.ToolId

/** User intents from the Home screen, forwarded by [HomeRoute]. */
class HomeActions(
    val onOpenSettings: () -> Unit,
    val onOpenBattery: () -> Unit,
    val onOpenApps: () -> Unit,
    val onOpenTools: () -> Unit,
    val onOpenTool: (ToolId) -> Unit,
    val onRequestShizukuPermission: () -> Unit,
    val onSetCardVisible: (HomeCardType, Boolean) -> Unit,
    val onMoveCard: (HomeCardType, Int) -> Unit,
    val onResetLayout: () -> Unit,
    val onLaunchShortcut: (HomeShortcut) -> Unit = {},
    val onOpenShortcutMaker: () -> Unit = {},
    /**
     * Opens a tool from a Home card/tile identified by `origin` (see [HomeOrigins]), so the tool
     * screen can grow out of that card (app-open transform). Defaults to [onOpenTool].
     */
    val onOpenToolFrom: (ToolId, String) -> Unit = { tool, _ -> onOpenTool(tool) },
    /** Hides the failed-actions notice until a newer failure occurs. */
    val onDismissFailures: () -> Unit = {},
)

/** Shared-element origins of Home cards that open a tool (prefix [AbleSharedKeys.ORIGIN_HOME]). */
internal object HomeOrigins {
    const val QUICK = AbleSharedKeys.ORIGIN_HOME + ".quick"
    const val SENSORS = AbleSharedKeys.ORIGIN_HOME + ".sensors"
    const val STORAGE = AbleSharedKeys.ORIGIN_HOME + ".storage"
    const val GEO = AbleSharedKeys.ORIGIN_HOME + ".geo"
}

/** Minimum card width: 1 column on compact windows, 2–3 columns on medium/expanded windows and TV. */
private val CardMinWidth = 340.dp

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    var showCustomize by rememberSaveable { mutableStateOf(false) }
    var showFailures by rememberSaveable { mutableStateOf(false) }
    var showAllActions by rememberSaveable { mutableStateOf(false) }
    // Saveable, so a card that opened a tool is back at the same place when the tool closes.
    val gridState = rememberLazyStaggeredGridState()

    AbleTopLevelScaffold(
        title = stringResource(R.string.home_title),
        modifier = modifier,
        onOpenSettings = actions.onOpenSettings,
        snackbarHostState = snackbarHostState,
        // Expanded header: the "Able" title only; collapsed bar: the brand lockup (BRAND-001).
        collapsedTitle = { AbleWordmark(height = 28.dp, contentDescription = null) },
        actions = {
            AbleIconButton(onClick = { showCustomize = true }) {
                Icon(Icons.Rounded.DashboardCustomize, contentDescription = stringResource(R.string.home_customize))
            }
        },
    ) { padding ->
        val visibleCards = state.visibleCards
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(CardMinWidth),
            state = gridState,
            // Re-selecting Home on the dashboard scrolls back to the top (SHELL-001 re-select).
            modifier = Modifier
                .fillMaxSize()
                .scrollToTopOnReselect("home") { gridState.animateScrollToItem(0) },
            contentPadding = screenContentPadding(padding),
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            verticalItemSpacing = AbleSpacing.md,
        ) {
            // Safety banners first, full width, independent of card order (HOME-003).
            state.alerts.forEach { alert ->
                item(key = alert.key(), span = StaggeredGridItemSpan.FullLine) {
                    SafetyBanner(
                        modifier = Modifier.animateItem(),
                        alert = alert,
                        onRequestShizukuPermission = actions.onRequestShizukuPermission,
                    )
                }
            }

            if (visibleCards.isEmpty()) {
                item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                    EmptyState(
                        modifier = Modifier.animateItem(),
                        title = stringResource(R.string.home_all_hidden_title),
                        message = stringResource(R.string.home_all_hidden_message),
                        actionLabel = stringResource(R.string.home_customize),
                        onAction = { showCustomize = true },
                    )
                }
            }

            visibleCards.forEach { card ->
                item(key = card.cardType.name) {
                    // Expressive motion when cards are reordered, shown or hidden (HOME-002).
                    Box(Modifier.animateItem()) {
                        HomeCard(
                            type = card.cardType,
                            state = state,
                            actions = actions,
                            onShowAllActions = { showAllActions = true },
                        )
                    }
                }
            }

            // Failed actions: at the end of the feed, independent of card visibility, and
            // hideable until a newer failure occurs (owner request 2026-11-23).
            state.failedActions?.let { notice ->
                item(key = "notice_failures", span = StaggeredGridItemSpan.FullLine) {
                    FailedActionsBanner(
                        modifier = Modifier.animateItem(),
                        notice = notice,
                        onShowDetails = { showFailures = true },
                        onHide = actions.onDismissFailures,
                    )
                }
            }
        }
    }

    if (showCustomize) {
        HomeCustomizeSheet(
            cards = state.cards,
            onDismiss = { showCustomize = false },
            onSetVisible = actions.onSetCardVisible,
            onMove = actions.onMoveCard,
            onReset = actions.onResetLayout,
        )
    }

    val failures = state.failedActions
    if (showFailures && failures != null) {
        RecentFailuresDialog(
            failures = failures,
            onDismiss = { showFailures = false },
            onOpenBattery = {
                showFailures = false
                actions.onOpenBattery()
            },
            onHide = {
                showFailures = false
                actions.onDismissFailures()
            },
        )
    }

    if (showAllActions) {
        RecentActionsSheet(
            actions = state.recentActions,
            onDismiss = { showAllActions = false },
        )
    }
}

private fun HomeSafetyAlert.key(): String = when (this) {
    HomeSafetyAlert.ShizukuWaitingForPermission -> "alert_shizuku_waiting"
    HomeSafetyAlert.ShizukuPermissionDenied -> "alert_shizuku_denied"
}

@Composable
private fun HomeCard(type: HomeCardType, state: HomeUiState, actions: HomeActions, onShowAllActions: () -> Unit) {
    when (type) {
        HomeCardType.BATTERY_PROFILE -> BatteryProfileCard(state, onOpenBattery = actions.onOpenBattery)
        HomeCardType.QUICK_ACTIONS -> QuickActionsCard(onOpenTool = { actions.onOpenToolFrom(it, HomeOrigins.QUICK) }, onOpenTools = actions.onOpenTools)
        HomeCardType.CAPABILITY_STATUS -> CapabilityStatusCard(state.access, onOpenSettings = actions.onOpenSettings)
        HomeCardType.STORAGE_SUMMARY -> StorageSummaryCard(state.storage, onOpenStorage = { actions.onOpenToolFrom(ToolId.STORAGE, HomeOrigins.STORAGE) })
        HomeCardType.FAVORITE_SHORTCUTS -> FavoriteShortcutsCard(
            shortcuts = state.shortcuts,
            onLaunch = actions.onLaunchShortcut,
            onOpenShortcutMaker = actions.onOpenShortcutMaker,
        )
        HomeCardType.RECENT_WARNINGS -> RecentActionsCard(state.recentActions, onShowAll = onShowAllActions)
        HomeCardType.SENSORS_OVERVIEW -> SensorsCard(
            hasAccelerometer = state.hasAccelerometer,
            hasMagnetometer = state.hasMagnetometer,
            onOpenTool = { actions.onOpenToolFrom(it, HomeOrigins.SENSORS) },
        )
        HomeCardType.GEO_POINT_QUICK -> GeoPointCard(onOpenGps = { actions.onOpenToolFrom(ToolId.GPS, HomeOrigins.GEO) })
    }
}
