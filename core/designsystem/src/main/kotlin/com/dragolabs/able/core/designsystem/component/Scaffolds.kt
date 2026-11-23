package com.dragolabs.able.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.R
import com.dragolabs.able.core.designsystem.theme.AbleSpacing

/**
 * App-wide search action (global options search). Provided by the app shell only where the
 * layout has no other search entry point (navigation rail, TV); when set, every top-level
 * scaffold shows a search button before Settings. With the floating navigation bar the search
 * button lives in the bar, so the shell provides null.
 */
val LocalAbleSearchAction = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Space taken at the bottom of the window by the app shell's floating navigation bar
 * (bar height, its margins and the system navigation-bar inset), or 0.dp when no bar overlays
 * the content. Content scrolls underneath the translucent bar, so scrolling screens must add
 * this to their bottom padding to keep the last item reachable. [AbleTopLevelScaffold] and
 * [AbleDetailScaffold] already include it in the padding they pass to their content (and
 * [screenContentPadding] therefore too), and lift FABs and snackbars above the bar.
 */
val LocalAbleBottomBarPadding = compositionLocalOf { 0.dp }

/**
 * Scaffold for a primary destination (Home, Battery, Apps, Tools). Uses the expressive large
 * flexible top app bar that collapses on scroll and exposes Settings from the top bar
 * (SHELL-001). Window insets for navigation bars are handled by the app shell.
 */
@Composable
fun AbleTopLevelScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onOpenSettings: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    collapsedTitle: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(bottom = LocalAbleBottomBarPadding.current),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    if (collapsedTitle == null) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        CrossfadingTitle(title, collapsedTitle) { scrollBehavior.state.collapsedFraction }
                    }
                },
                subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                actions = {
                    actions()
                    LocalAbleSearchAction.current?.let { openSearch ->
                        AbleIconButton(onClick = openSearch) {
                            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.ds_search))
                        }
                    }
                    if (onOpenSettings != null) {
                        AbleIconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.ds_open_settings))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                titleHorizontalAlignment = Alignment.Start,
                colors = ableTopAppBarColors(),
            )
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/**
 * Title that shows [title] text while the header is expanded and crossfades to [collapsedTitle]
 * (for example the brand mark) as the bar collapses. The fraction is read in the draw phase
 * only, so scrolling does not recompose. Exposed to accessibility as one heading named [title].
 */
@Composable
private fun CrossfadingTitle(
    title: String,
    collapsedTitle: @Composable () -> Unit,
    collapsedFraction: () -> Float,
) {
    Box(
        modifier = Modifier.clearAndSetSemantics {
            heading()
            contentDescription = title
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = 1f - collapsedFraction().coerceIn(0f, 1f) },
        )
        Box(
            modifier = Modifier.graphicsLayer { alpha = collapsedFraction().coerceIn(0f, 1f) },
            contentAlignment = Alignment.CenterStart,
        ) {
            collapsedTitle()
        }
    }
}

/** Opaque colors so scrolled content never shows through the collapsed title (NFR-ACC-002). */
@Composable
private fun ableTopAppBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

/**
 * Scaffold for a drill-down screen with a back affordance (adaptive-ui back behavior).
 *
 * [sharedKey], when set, makes the whole screen the container end of an app-open transform
 * from the card that opened it (see [ableSharedBounds]); it is a no-op outside the NavHost.
 */
@Composable
fun AbleDetailScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    sharedKey: String? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier
            .ableSharedBounds(sharedKey, isContainer = true)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(bottom = LocalAbleBottomBarPadding.current),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                navigationIcon = {
                    if (onBack != null) {
                        AbleIconButton(onClick = onBack, haptic = AbleHaptic.Navigate) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.ds_navigate_back),
                            )
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                titleHorizontalAlignment = Alignment.Start,
                colors = ableTopAppBarColors(),
            )
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/**
 * Centers content and caps its width on wide windows so phone-sized controls are not stretched
 * across tablets and TVs (adaptive-ui: "avoid simply enlarging phone controls").
 */
@Composable
fun ReadableWidth(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = AbleSpacing.maxContentWidth).fillMaxWidth()) { content() }
    }
}

/**
 * Default padding for scrolling screen content, combined with the scaffold padding.
 *
 * The scaffold's padding values change while the flexible top app bar expands/collapses, so the
 * returned object delegates on every read instead of caching a snapshot (a cached value made
 * content slide under the header).
 */
@Composable
fun screenContentPadding(scaffoldPadding: PaddingValues): PaddingValues =
    remember(scaffoldPadding) { ScreenContentPadding(scaffoldPadding) }

private class ScreenContentPadding(private val scaffold: PaddingValues) : PaddingValues {
    override fun calculateTopPadding(): Dp = scaffold.calculateTopPadding() + AbleSpacing.sm
    override fun calculateBottomPadding(): Dp = scaffold.calculateBottomPadding() + AbleSpacing.xxl
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = AbleSpacing.screenGutter
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = AbleSpacing.screenGutter
}

/** Section title used between groups of cards or list rows. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}
