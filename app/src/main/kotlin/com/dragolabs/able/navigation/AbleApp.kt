package com.dragolabs.able.navigation

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.drawWithContent
import androidx.lifecycle.Lifecycle
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clipToBounds
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import com.dragolabs.able.core.designsystem.component.AbleSharedKeys
import com.dragolabs.able.core.designsystem.component.LocalAbleAnimatedVisibilityScope
import com.dragolabs.able.core.designsystem.component.LocalAbleSharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.IntOffset
import com.dragolabs.able.core.designsystem.component.LocalAbleReselectEvents
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.Alignment
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.LocalAbleBottomBarPadding
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.tv.material3.NavigationDrawerItem
import androidx.window.core.layout.WindowSizeClass
import com.dragolabs.able.core.designsystem.R
import com.dragolabs.able.feature.apps.AppsRoute as AppsScreenRoute
import com.dragolabs.able.feature.battery.BatteryRoute as BatteryScreenRoute
import com.dragolabs.able.feature.home.HomeRoute as HomeScreenRoute
import com.dragolabs.able.actions.ui.TilesAndWidgetsScreen
import com.dragolabs.able.feature.settings.OnboardingRoute as OnboardingScreenRoute
import com.dragolabs.able.feature.settings.OpenSourceLicensesRoute
import com.dragolabs.able.search.GlobalSearchScreen
import com.dragolabs.able.search.SearchDeepLink
import com.dragolabs.able.core.designsystem.component.LocalAbleSearchAction
import com.dragolabs.able.feature.settings.TourRoute as TourScreenRoute
import com.dragolabs.able.feature.settings.SettingsRoute as SettingsScreenRoute
import com.dragolabs.able.feature.tools.ToolDetailRoute as ToolDetailScreenRoute
import com.dragolabs.able.feature.tools.ToolsRoute as ToolsScreenRoute
import kotlinx.coroutines.launch

private enum class ShellLayout { BottomBar, Rail, TvDrawer }

/**
 * Adaptive shell (docs/ux/adaptive-ui.md): the layout follows the current window size and
 * input, not a device label. Compact windows use the expressive short navigation bar, wider
 * windows a wide navigation rail, and TV a remote-first Compose for TV navigation drawer.
 */
@Composable
fun AbleApp(
    isTv: Boolean,
    showOnboarding: Boolean,
    deepLink: Any?,
    /** UX-001: onboarding was completed earlier but the tour was neither finished nor skipped. */
    showTour: Boolean = false,
    onDeepLinkConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    // Decide the start destination once; finishing onboarding navigates explicitly.
    val startDestination: Any = androidx.compose.runtime.remember {
        when {
            showOnboarding -> OnboardingRoute
            showTour -> TourRoute
            else -> HomeRoute
        }
    }
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val layout = when {
        isTv -> ShellLayout.TvDrawer
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> ShellLayout.Rail
        else -> ShellLayout.BottomBar
    }
    val railExpandedByDefault = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            navController.navigate(deepLink) { launchSingleTop = true }
            onDeepLinkConsumed()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // The section that owns the current back stack: the top-most section root. A tool detail
    // belongs to the section it was opened from (Home or Tools), not always to Tools.
    val backStack by navController.currentBackStack.collectAsState()
    val currentSection = backStack.lastOrNull { it.sectionIndex() != null }
        ?.sectionIndex()?.let { TopLevelDestination.entries[it] }
    // The bar is persistent (SHELL-001, revised 2026-11-23): the section that owns the current
    // stack stays highlighted on every screen, including tools, Settings and Search.
    val isSelected: (TopLevelDestination) -> Boolean = { currentSection == it }
    val showChrome = currentDestination?.hasRoute<OnboardingRoute>() != true &&
        currentDestination?.hasRoute<TourRoute>() != true

    val reselectEvents = remember { MutableSharedFlow<String>(extraBufferCapacity = 1) }
    val haptics = rememberAbleHaptics()
    val navigateTopLevel: (TopLevelDestination) -> Unit = { destination ->
        // UX-003: the lightest tick for every destination tap (bar, rail, TV drawer).
        haptics.navigate()
        if (currentSection == destination) {
            // Re-tapping the current section returns to its start: first pop nested screens
            // (a tool detail, Settings opened from the TV drawer); when already on the root, tell
            // the section to reset its own state (panes, scroll, tabs). Emitting only on the root
            // keeps it deterministic: a screen that is just being restored never receives it.
            val popped = navController.popBackStack(destination.route, inclusive = false)
            if (!popped) reselectEvents.tryEmit(destination.key)
        } else {
            navController.navigateToSection(destination.route)
        }
    }

    // SEARCH-001: the floating bar has its own Search button; rail and TV layouts show it in
    // every top-level app bar instead.
    val host: @Composable (Modifier, Boolean) -> Unit = { modifier, searchInTopBar ->
        CompositionLocalProvider(LocalAbleReselectEvents provides reselectEvents) {
        AbleNavHost(
            navController = navController,
            startDestination = startDestination,
            searchInTopBar = searchInTopBar,
            modifier = modifier,
        )
        }
    }

    when (layout) {
        ShellLayout.BottomBar -> {
            // Floating bar (SHELL-001): content runs edge to edge underneath the translucent,
            // blurred bar; screens get the space it covers through LocalAbleBottomBarPadding.
            val hazeState = rememberHazeState()
            // The floating bar is persistent (owner request 2026-11-23): it stays fully visible on
            // every screen after onboarding; screens keep their controls above it through
            // LocalAbleBottomBarPadding. Re-tapping the current section returns to its start.
            val showBar = showChrome
            val navigationInset = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
            val navigationPadding = navigationInset.asPaddingValues().calculateBottomPadding()
            val barPadding = if (showBar) navigationPadding + FloatingNavigationBarDefaults.Reserved else 0.dp
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalAbleBottomBarPadding provides barPadding) {
                    host(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (showBar) {
                                    Modifier.consumeWindowInsets(navigationInset)
                                } else {
                                    Modifier.windowInsetsPadding(navigationInset)
                                },
                            )
                            .hazeSource(hazeState),
                        false,
                    )
                }
                AnimatedVisibility(
                    visible = showBar,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    FloatingNavigationBar(
                        hazeState = hazeState,
                        isSelected = isSelected,
                        onNavigate = navigateTopLevel,
                        onSearch = { navController.navigate(SearchRoute) { launchSingleTop = true } },
                        modifier = Modifier.windowInsetsPadding(navigationInset),
                    )
                }
            }
        }

        ShellLayout.Rail -> {
            val railState = rememberWideNavigationRailState(
                if (railExpandedByDefault) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed,
            )
            val scope = rememberCoroutineScope()
            Row(Modifier.fillMaxSize()) {
                if (showChrome) {
                    WideNavigationRail(
                        state = railState,
                        header = {
                            AbleIconButton(
                                modifier = Modifier.padding(start = 24.dp),
                                onClick = {
                                    scope.launch {
                                        if (railState.targetValue == WideNavigationRailValue.Expanded) railState.collapse()
                                        else railState.expand()
                                    }
                                },
                            ) {
                                Icon(
                                    if (railState.targetValue == WideNavigationRailValue.Expanded) {
                                        Icons.AutoMirrored.Rounded.MenuOpen
                                    } else {
                                        Icons.Rounded.Menu
                                    },
                                    contentDescription = stringResource(R.string.app_name),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        TopLevelDestination.entries.forEach { destination ->
                            val selected = isSelected(destination)
                            WideNavigationRailItem(
                                railExpanded = railState.targetValue == WideNavigationRailValue.Expanded,
                                selected = selected,
                                onClick = { navigateTopLevel(destination) },
                                icon = {
                                    Icon(
                                        if (selected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                }
                host(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.End)),
                    true,
                )
            }
        }

        ShellLayout.TvDrawer -> TvShell(
            showChrome = showChrome,
            currentDestination = currentDestination,
            isSelected = isSelected,
            onNavigateTopLevel = navigateTopLevel,
            onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
        ) {
            host(Modifier.fillMaxSize(), true)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AbleNavHost(
    navController: NavHostController,
    startDestination: Any,
    searchInTopBar: Boolean,
    modifier: Modifier = Modifier,
) {
    val openSettings: () -> Unit = { navController.navigate(SettingsRoute) { launchSingleTop = true } }
    // Debounced: only the resumed (settled, on-screen) entry may open a tool, so a double tap or
    // a tap while a transition runs cannot start a second container transform.
    val openTool: (NavBackStackEntry, com.dragolabs.able.core.model.ToolId, String?) -> Unit = { from, tool, origin ->
        if (from.lifecycle.currentState == Lifecycle.State.RESUMED) {
            navController.navigate(ToolDetailRoute(tool, origin)) { launchSingleTop = true }
        }
    }
    val navigateTopLevel: (Any) -> Unit = { route -> navController.navigateToSection(route) }
    val visibleEntries = navController.visibleEntries.collectAsState()

    // Motion (UX-004): directional springs between sections, an app-open container transform
    // between a card and the tool it opens, and a forward/back slide for other detail screens.
    // navigation-compose seeks the same transitions from the predictive back gesture.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val motion = remember(spatial, effects) { AbleNavMotion(spatial, effects) }
    // The shared-element overlay and the section slides stay inside the NavHost area, so the
    // bottom bar / rail never moves or gets covered during a transition.
    SharedTransitionLayout(modifier = modifier.clipToBounds()) {
        CompositionLocalProvider(
            LocalAbleSharedTransitionScope provides this,
            // SEARCH-001: top-level scaffolds show the app-wide search button unless the
            // floating navigation bar already offers it.
            LocalAbleSearchAction provides if (searchInTopBar) {
                { navController.navigate(SearchRoute) { launchSingleTop = true } }
            } else {
                null
            },
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { motion.enter(initialState, targetState, pop = false) },
                exitTransition = { motion.exit(initialState, targetState, pop = false) },
                popEnterTransition = { motion.enter(initialState, targetState, pop = true) },
                popExitTransition = { motion.exit(initialState, targetState, pop = true) },
                predictivePopEnterTransition = { _ -> motion.enter(initialState, targetState, pop = true) },
                predictivePopExitTransition = { _ -> motion.exit(initialState, targetState, pop = true) },
            ) {
                ableComposable<OnboardingRoute>(visibleEntries) {
                    OnboardingScreenRoute(
                        onFinished = {
                            // UX-001: the skippable tour follows first-run configuration.
                            navController.navigate(TourRoute) {
                                popUpTo<OnboardingRoute> { inclusive = true }
                            }
                        },
                    )
                }
                ableComposable<TourRoute>(visibleEntries) {
                    TourScreenRoute(
                        onFinished = {
                            // Replayed from Settings: return there. First run: the tour is the root, go Home.
                            if (navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            } else {
                                navController.navigate(HomeRoute) {
                                    popUpTo<TourRoute> { inclusive = true }
                                }
                            }
                        },
                    )
                }
                ableComposable<HomeRoute>(visibleEntries) { entry ->
                    HomeScreenRoute(
                        onOpenSettings = openSettings,
                        onOpenBattery = { navigateTopLevel(BatteryRoute) },
                        onOpenApps = { navigateTopLevel(AppsRoute) },
                        onOpenTools = { navigateTopLevel(ToolsRoute) },
                        onOpenTool = { tool -> openTool(entry, tool, null) },
                        onOpenToolFrom = { tool, origin -> openTool(entry, tool, origin) },
                    )
                }
                ableComposable<BatteryRoute>(visibleEntries) {
                    BatteryScreenRoute(
                        onOpenSettings = openSettings,
                        onOpenApps = { navigateTopLevel(AppsRoute) },
                    )
                }
                ableComposable<AppsRoute>(visibleEntries) {
                    AppsScreenRoute(onOpenSettings = openSettings)
                }
                ableComposable<ToolsRoute>(visibleEntries) { entry ->
                    ToolsScreenRoute(
                        onOpenSettings = openSettings,
                        onOpenTool = { tool -> openTool(entry, tool, AbleSharedKeys.ORIGIN_TOOLS) },
                    )
                }
                ableComposable<ToolDetailRoute>(visibleEntries) { entry ->
                    val route = entry.toRoute<ToolDetailRoute>()
                    ToolDetailScreenRoute(
                        toolId = route.tool,
                        // Ignore a second tap on the back arrow once this entry is already popping.
                        onBack = { if (navController.currentBackStackEntry?.id == entry.id) navController.navigateUp() },
                        sharedKey = route.origin?.let { AbleSharedKeys.tool(it, route.tool.key) },
                    )
                }
                ableComposable<SettingsRoute>(visibleEntries) {
                    SettingsScreenRoute(
                        onBack = { navController.navigateUp() },
                        onOpenTour = { navController.navigate(TourRoute) { launchSingleTop = true } },
                        onOpenTilesWidgets = { navController.navigate(TilesWidgetsRoute) { launchSingleTop = true } },
                        onOpenLicenses = { navController.navigate(LicensesRoute) { launchSingleTop = true } },
                    )
                }
                ableComposable<TilesWidgetsRoute>(visibleEntries) {
                    TilesAndWidgetsScreen(onBack = { navController.navigateUp() })
                }
                ableComposable<LicensesRoute>(visibleEntries) {
                    OpenSourceLicensesRoute(onBack = { navController.navigateUp() })
                }
                ableComposable<SearchRoute>(visibleEntries) {
                    GlobalSearchScreen(
                        onBack = { navController.navigateUp() },
                        onOpen = { link ->
                            routeFromDeepLink(SearchDeepLink.base(link))?.let { route ->
                                navController.navigate(route) {
                                    popUpTo<SearchRoute> { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Every destination exposes its visibility scope so cards/screens can join shared transitions.
 * A section behind an expanding/shrinking card is dimmed by a subtle scrim (it does not move or
 * scale, so the card's target bounds stay fixed while it returns).
 */
private inline fun <reified T : Any> NavGraphBuilder.ableComposable(
    visibleEntries: State<List<NavBackStackEntry>>,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        CompositionLocalProvider(LocalAbleAnimatedVisibilityScope provides this) {
            if (entry.sectionIndex() == null) {
                content(entry)
            } else {
                val scrimColor = MaterialTheme.colorScheme.scrim
                val scrim = transition.animateFloat(
                    transitionSpec = { SectionScrimSpec },
                    label = "sectionScrim",
                ) { state ->
                    val cardOpen = state != EnterExitState.Visible &&
                        visibleEntries.value.any { isCardPair(section = entry, detail = it) }
                    if (cardOpen) SECTION_SCRIM_ALPHA else 0f
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            val alpha = scrim.value
                            if (alpha > 0f) drawRect(scrimColor, alpha = alpha)
                        },
                ) { content(entry) }
            }
        }
    }
}

private const val SECTION_SCRIM_ALPHA = 0.22f

/** Section slides move by 1/[SECTION_SLIDE_FRACTION] of the width (shared-axis X). */
private const val SECTION_SLIDE_FRACTION = 4

/** Sections recede to / grow from this scale while switching. */
private const val SECTION_SCALE = 0.92f
private val SectionScrimSpec = spring<Float>(dampingRatio = 1f, stiffness = 380f)

/** Switches section, saving the current section's stack and restoring the target's. */
private fun NavHostController.navigateToSection(route: Any) {
    navigate(route) {
        // Home is the root once onboarding/tour are done; the graph start can still be the
        // onboarding route, which is no longer on the back stack (state would not be saved).
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Chooses the NavHost transition for a pair of destinations:
 * - section ↔ section: shared-axis X with depth — a quarter-width slide following the
 *   [TopLevelDestination] order plus a 92 % scale, on lightly underdamped springs (a small,
 *   visible settle), with a quick fade-out / delayed fade-in;
 * - section ↔ tool opened from one of its cards: the card's shared bounds do the app-open/close
 *   motion; the NavHost runs no competing slide/scale (the section is only dimmed);
 * - anything else: forward/back slide with fade.
 * With "Remove animations" (animator duration scale 0) everything is instant.
 */
private class AbleNavMotion(
    private val spatial: FiniteAnimationSpec<IntOffset>,
    private val effects: FiniteAnimationSpec<Float>,
) {
    private val sectionSpring = spring(
        dampingRatio = 0.78f,
        stiffness = 420f,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )
    private val sectionScaleSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 380f)
    private val sectionFadeIn = tween<Float>(durationMillis = 240, delayMillis = 70, easing = LinearOutSlowInEasing)
    private val sectionFadeOut = tween<Float>(durationMillis = 110, easing = FastOutLinearInEasing)

    fun enter(initial: NavBackStackEntry, target: NavBackStackEntry, pop: Boolean): EnterTransition {
        if (!ValueAnimator.areAnimatorsEnabled()) return EnterTransition.None
        val from = initial.sectionIndex()
        val to = target.sectionIndex()
        return when {
            from != null && to != null -> {
                // Shared-axis X: a short directional slide with a cross-fade. Moving both whole
                // screens across the display (and re-blurring the bar under them) every frame was
                // the heaviest part of switching sections.
                val direction = if (to > from) 1 else -1
                // The incoming section glides in from the tab's side, grows from slightly behind
                // and settles with a light spring overshoot, while the old one recedes.
                slideInHorizontally(sectionSpring) { width -> direction * width / SECTION_SLIDE_FRACTION } +
                    scaleIn(sectionScaleSpring, initialScale = SECTION_SCALE) +
                    fadeIn(sectionFadeIn)
            }
            // Tool screen grows out of its card: everything is drawn by the shared bounds.
            isCardPair(section = initial, detail = target) -> EnterTransition.None
            // Back into the section: it stays still (only its scrim lifts, see ableComposable)
            // so the card the screen shrinks into does not move.
            isCardPair(section = target, detail = initial) -> EnterTransition.None
            pop -> slideInHorizontally(spatial) { -it / 10 } + fadeIn(effects)
            else -> slideInHorizontally(spatial) { it / 6 } + fadeIn(effects)
        }
    }

    fun exit(initial: NavBackStackEntry, target: NavBackStackEntry, pop: Boolean): ExitTransition {
        if (!ValueAnimator.areAnimatorsEnabled()) return ExitTransition.None
        val from = initial.sectionIndex()
        val to = target.sectionIndex()
        return when {
            from != null && to != null -> {
                val direction = if (to > from) 1 else -1
                slideOutHorizontally(sectionSpring) { width -> -direction * width / SECTION_SLIDE_FRACTION } +
                    scaleOut(sectionScaleSpring, targetScale = SECTION_SCALE) +
                    fadeOut(sectionFadeOut)
            }
            // No NavHost animation on either end: both stay composed (and still) while the
            // transition's own animations run (shared bounds, corner morph, section scrim), so
            // the section only dims and the screen shrinks into the card via its shared bounds.
            isCardPair(section = initial, detail = target) -> ExitTransition.None
            isCardPair(section = target, detail = initial) -> ExitTransition.None
            pop -> slideOutHorizontally(spatial) { it / 6 } + fadeOut(effects) + scaleOut(effects, targetScale = 0.94f)
            else -> slideOutHorizontally(spatial) { -it / 10 } + fadeOut(effects)
        }
    }
}

/** Index in the bar/rail order when this entry is a section root, else null. */
private fun NavBackStackEntry.sectionIndex(): Int? {
    val d = destination
    return when {
        d.hasRoute<HomeRoute>() -> TopLevelDestination.HOME.ordinal
        d.hasRoute<BatteryRoute>() -> TopLevelDestination.BATTERY.ordinal
        d.hasRoute<AppsRoute>() -> TopLevelDestination.APPS.ordinal
        d.hasRoute<ToolsRoute>() -> TopLevelDestination.TOOLS.ordinal
        else -> null
    }
}

/** True when [detail] is a tool opened from a card that lives on the [section] screen. */
private fun isCardPair(section: NavBackStackEntry, detail: NavBackStackEntry): Boolean {
    if (!detail.destination.hasRoute<ToolDetailRoute>()) return false
    val origin = runCatching { detail.toRoute<ToolDetailRoute>().origin }.getOrNull() ?: return false
    val originSection = when (origin.substringBefore('.')) {
        AbleSharedKeys.ORIGIN_TOOLS -> TopLevelDestination.TOOLS
        AbleSharedKeys.ORIGIN_HOME -> TopLevelDestination.HOME
        else -> return false
    }
    return section.sectionIndex() == originSection.ordinal
}

/**
 * Remote-first TV shell built with Compose for TV (ADR-008). Every destination, including
 * Settings, is reachable with the D-pad; the drawer expands when it receives focus.
 */
@Composable
private fun TvShell(
    showChrome: Boolean,
    currentDestination: NavDestination?,
    isSelected: (TopLevelDestination) -> Boolean,
    onNavigateTopLevel: (TopLevelDestination) -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!showChrome) {
        content()
        return
    }
    TvThemeBridge {
        androidx.tv.material3.NavigationDrawer(
            drawerContent = { drawerValue ->
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.CenterVertically),
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = isSelected(destination)
                        NavigationDrawerItem(
                            selected = selected,
                            onClick = { onNavigateTopLevel(destination) },
                            leadingContent = {
                                androidx.tv.material3.Icon(
                                    if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = null,
                                )
                            },
                        ) { androidx.tv.material3.Text(stringResource(destination.labelRes)) }
                    }
                    NavigationDrawerItem(
                        selected = currentDestination?.hasRoute<SettingsRoute>() == true,
                        onClick = onOpenSettings,
                        leadingContent = { androidx.tv.material3.Icon(Icons.Rounded.Settings, contentDescription = null) },
                    ) { androidx.tv.material3.Text(stringResource(R.string.nav_settings)) }
                }
            },
        ) {
            // Content keeps the Material 3 Expressive theme; only the drawer uses TV components.
            Box(Modifier.fillMaxSize().padding(start = 8.dp)) { content() }
        }
    }
}
