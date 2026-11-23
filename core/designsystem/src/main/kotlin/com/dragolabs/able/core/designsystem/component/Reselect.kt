package com.dragolabs.able.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Emits the route key of a primary destination ("home", "battery", "apps", "tools") when the
 * user taps its navigation item while already on its root screen (when nested screens are open,
 * the shell pops them instead and emits nothing). Screens reset to their initial state
 * (close detail panes, scroll to top, first tab).
 */
val LocalAbleReselectEvents = staticCompositionLocalOf<Flow<String>> { emptyFlow() }

/** Runs [onReselect] whenever the destination [key] is re-selected in the navigation bar. */
@Composable
fun OnTopLevelReselect(key: String, onReselect: suspend () -> Unit) {
    val events = LocalAbleReselectEvents.current
    val action = rememberUpdatedState(onReselect)
    LaunchedEffect(events, key) {
        events.collect { if (it == key) action.value() }
    }
}

/**
 * Scrolls the scrolling container this modifier is applied to back to the top when [key] is
 * re-selected ([scrollToTop], e.g. `gridState.animateScrollToItem(0)`), and expands the
 * collapsing top app bar of the enclosing [AbleTopLevelScaffold]: programmatic scrolls do not go
 * through nested scrolling, so the expansion is dispatched explicitly to the parent.
 */
@Composable
fun Modifier.scrollToTopOnReselect(key: String, scrollToTop: suspend () -> Unit): Modifier {
    val dispatcher = remember { NestedScrollDispatcher() }
    val connection = remember { object : NestedScrollConnection {} }
    val density = LocalDensity.current
    OnTopLevelReselect(key) {
        coroutineScope {
            launch { scrollToTop() }
            launch {
                // The app bar clamps its own offset; dispatch more than its collapsible height.
                val distance = with(density) { 480.dp.toPx() }
                var previous = 0f
                animate(0f, distance, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                    dispatcher.dispatchPostScroll(Offset.Zero, Offset(0f, value - previous), NestedScrollSource.UserInput)
                    previous = value
                }
            }
        }
    }
    return this.nestedScroll(connection, dispatcher)
}
