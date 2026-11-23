package com.dragolabs.able.feature.battery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.dragolabs.able.core.designsystem.component.AbleTopLevelScaffold
import com.dragolabs.able.core.designsystem.component.LoadingState
import com.dragolabs.able.core.designsystem.component.UnsupportedState
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.designsystem.theme.AbleTheme
import com.dragolabs.able.feature.battery.BatteryEvent
import com.dragolabs.able.feature.battery.BatteryTab
import com.dragolabs.able.feature.battery.BatteryUiState
import com.dragolabs.able.feature.battery.MonitorUiState
import com.dragolabs.able.feature.battery.R

/**
 * Stateless Battery destination with in-screen tabs. The tab row and a [HorizontalPager] are
 * kept in sync: swiping changes the tab, tapping a tab animates the pager. Each page keeps its
 * own scroll position; re-selecting Battery scrolls the visible page to the top and returns to
 * the first tab.
 */
@Composable
fun BatteryScreen(
    state: BatteryUiState,
    monitor: MonitorUiState,
    selectedTab: BatteryTab,
    onSelectTab: (BatteryTab) -> Unit,
    onOpenSettings: () -> Unit,
    onEvent: (BatteryEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    /** Incremented when the destination is re-selected: scroll the visible tab to the top. */
    scrollToTopRequests: Int = 0,
) {
    val tabs = BatteryTab.entries
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) { tabs.size }
    // One saveable scroll state per page, so swiping away and back keeps the position.
    val gridStates = tabs.map { rememberLazyStaggeredGridState() }
    val haptics = rememberAbleHaptics()
    val currentSelected by rememberUpdatedState(selectedTab)
    val currentOnSelect by rememberUpdatedState(onSelectTab)

    // Tab tap (or external selection) -> pager.
    LaunchedEffect(selectedTab) {
        if (pagerState.targetPage != selectedTab.ordinal) pagerState.animateScrollToPage(selectedTab.ordinal)
    }
    // Swipe -> tab. Only settled pages update the selection, so a tab-tap animation that passes
    // through intermediate pages does not bounce the selection.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = tabs[page]
            if (tab != currentSelected && !pagerState.isScrollInProgress) {
                haptics.tick()
                currentOnSelect(tab)
            }
        }
    }
    // Re-select: scroll the visible page to the top, then go back to the first tab.
    // Requests already handled (a fresh composition, e.g. after the editor closes, must not replay one).
    var handledScrollRequests by rememberSaveable { mutableIntStateOf(scrollToTopRequests) }
    LaunchedEffect(scrollToTopRequests) {
        if (scrollToTopRequests != handledScrollRequests) {
            handledScrollRequests = scrollToTopRequests
            gridStates[pagerState.currentPage].animateScrollToItem(0)
            if (pagerState.currentPage != 0) {
                gridStates[0].scrollToItem(0)
                pagerState.animateScrollToPage(0)
            }
        }
    }

    AbleTopLevelScaffold(
        title = stringResource(R.string.battery_title),
        modifier = modifier,
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
    ) { scaffoldPadding ->
        // The tab row is pinned directly below the (opaque) collapsing top bar and the tab content
        // scrolls beneath the tab row, never over the title. The top inset is read in the layout
        // phase so the bar can collapse smoothly without recomposing the whole screen.
        val topOnly = remember(scaffoldPadding) { TopPadding(scaffoldPadding) }
        val scope = rememberCoroutineScope()
        Column(
            Modifier
                .fillMaxSize()
                .padding(topOnly),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.TopCenter,
            ) {
                // The indicator follows the pager (also mid-swipe); short labels fit a fixed row on
                // phones; with large font scales the row scrolls instead of truncating labels.
                val selectedIndex = pagerState.targetPage
                val tabContent: @Composable () -> Unit = {
                    tabs.forEach { tab ->
                        Tab(
                            selected = tab.ordinal == selectedIndex,
                            onClick = {
                                if (tab.ordinal != pagerState.currentPage) {
                                    haptics.tick()
                                    onSelectTab(tab)
                                    scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                                }
                            },
                            icon = { Icon(tab.icon(), contentDescription = null) },
                            text = { Text(stringResource(tab.labelRes()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                if (LocalDensity.current.fontScale > 1.3f) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        modifier = Modifier.widthIn(max = AbleSpacing.maxContentWidth),
                        edgePadding = 0.dp,
                        tabs = tabContent,
                    )
                } else {
                    PrimaryTabRow(
                        selectedTabIndex = selectedIndex,
                        modifier = Modifier.widthIn(max = AbleSpacing.maxContentWidth),
                        tabs = tabContent,
                    )
                }
            }
            val contentPadding = remember(scaffoldPadding) { TabContentPadding(scaffoldPadding) }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                when {
                    state.loading || monitor.loading -> LoadingState()
                    !state.hasBattery -> UnsupportedState(reason = stringResource(R.string.battery_no_battery))
                    else -> HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        // Keep the neighbours composed so a swipe never shows an empty page; pages
                        // further away are disposed but keep their hoisted scroll state.
                        beyondViewportPageCount = 1,
                        key = { tabs[it].name },
                        verticalAlignment = Alignment.Top,
                    ) { page ->
                        val gridState = gridStates[page]
                        when (tabs[page]) {
                            BatteryTab.OVERVIEW -> OverviewTab(state, contentPadding, onEvent, gridState)
                            BatteryTab.MONITOR -> MonitorTab(
                                monitor,
                                contentPadding,
                                onEvent,
                                gridState,
                                onOpenDiagnostics = { onSelectTab(BatteryTab.DIAGNOSTICS) },
                            )
                            BatteryTab.HEALTH -> HealthTab(state, contentPadding, onEvent, gridState)
                            BatteryTab.DIAGNOSTICS -> DiagnosticsTab(
                                state,
                                contentPadding,
                                onEvent,
                                onOpenMonitor = { onSelectTab(BatteryTab.MONITOR) },
                                gridState = gridState,
                            )
                        }
                    }
                }
            }
        }
    }

    // Monitor dialogs live outside the pager so they never depend on which pages are composed.
    MonitorDialogs(monitor, onEvent)
}

/** Only the scaffold's top inset, delegated on every read (the flexible top bar changes it while collapsing). */
private class TopPadding(private val scaffold: PaddingValues) : PaddingValues {
    override fun calculateTopPadding(): Dp = scaffold.calculateTopPadding()
    override fun calculateBottomPadding(): Dp = 0.dp
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
}

/** Tab content padding: gutters, a small gap under the tab row and the scaffold's live bottom inset. */
private class TabContentPadding(private val scaffold: PaddingValues) : PaddingValues {
    override fun calculateTopPadding(): Dp = AbleSpacing.lg
    override fun calculateBottomPadding(): Dp = scaffold.calculateBottomPadding() + AbleSpacing.xxl
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = AbleSpacing.screenGutter
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = AbleSpacing.screenGutter
}

private fun BatteryTab.labelRes(): Int = when (this) {
    BatteryTab.OVERVIEW -> R.string.battery_tab_overview
    BatteryTab.MONITOR -> R.string.battery_tab_monitor
    BatteryTab.HEALTH -> R.string.battery_tab_health
    BatteryTab.DIAGNOSTICS -> R.string.battery_tab_diagnostics
}

private fun BatteryTab.icon() = when (this) {
    BatteryTab.OVERVIEW -> Icons.Rounded.BatteryChargingFull
    BatteryTab.MONITOR -> Icons.Rounded.MonitorHeart
    BatteryTab.HEALTH -> Icons.Rounded.HealthAndSafety
    BatteryTab.DIAGNOSTICS -> Icons.Rounded.Checklist
}

@Preview
@Composable
private fun BatteryScreenPreview() {
    AbleTheme {
        BatteryScreen(
            state = BatteryUiState(loading = false),
            monitor = MonitorUiState(loading = false),
            selectedTab = BatteryTab.OVERVIEW,
            onSelectTab = {},
            onOpenSettings = {},
            onEvent = {},
        )
    }
}
