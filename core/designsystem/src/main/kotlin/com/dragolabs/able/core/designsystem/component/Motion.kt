package com.dragolabs.able.core.designsystem.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Shared-element plumbing for the "app-open" container transform (a card expands into the
 * screen it opens and shrinks back into it on back, like opening an app from the launcher).
 *
 * The app shell wraps its NavHost in a `SharedTransitionLayout` and provides both locals; every
 * destination provides its own [AnimatedVisibilityScope]. When either local is absent (previews,
 * screenshot/unit tests, screens hosted outside the NavHost) [AbleSharedBounds] and
 * [ableSharedBounds] are no-ops.
 *
 * How the transform is drawn (deterministic, no scaling of text):
 * - both ends use `sharedBounds` with [SharedTransitionScope.ResizeMode.RemeasureToBounds]; their
 *   inner content is `skipToLookaheadSize()`, i.e. laid out once at its own final size and only
 *   clipped by the animated bounds, so tile labels and tool screens never stretch or reflow;
 * - the screen (container) end is opaque the whole time, so the morphing surface never flashes
 *   the section behind it; only its content fades (the tile end is drawn above it and cross-fades);
 * - its color morphs from the origin card's color (registered by the tile end) to the screen
 *   color, so the hand-off at card size shows no light/dark flash;
 * - corners morph on the same spring as the bounds, so they stay in sync (also when the
 *   predictive back gesture seeks the transition);
 * - when the origin card is not on screen (no match), the screen falls back to a plain
 *   fade + slight scale instead of jumping.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalAbleSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The navigation destination's visibility scope (the `composable {}` content receiver). */
val LocalAbleAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Key scheme shared by sources (cards/tiles) and destinations (detail screens). */
object AbleSharedKeys {
    /** Origin of the Tools hub grid. Home origins start with [ORIGIN_HOME] (e.g. `home.quick`). */
    const val ORIGIN_TOOLS = "tools"
    const val ORIGIN_HOME = "home"

    /** Key for a tool opened from [origin]; the detail screen uses the same key to match. */
    fun tool(origin: String, toolKey: String): String = "tool/$origin/$toolKey"
}

/** Corner radius of cards and tiles (`MaterialTheme.shapes.large`). */
val AbleCardCornerRadius: Dp = 28.dp

/**
 * Container spring: the stiffness of the expressive spatial spec (~350–400 ms to settle) with a
 * near-critical damping, so a full-screen container does not visibly overshoot (an overshoot
 * of the bounds shifts the tool's top bar by several pixels at the end of the motion).
 */
private const val ContainerStiffness = 380f
private const val ContainerDamping = 0.9f

@OptIn(ExperimentalSharedTransitionApi::class)
private val AbleContainerBoundsTransform = BoundsTransform { _, _ ->
    spring(
        dampingRatio = ContainerDamping,
        stiffness = ContainerStiffness,
        visibilityThreshold = Rect.VisibilityThreshold,
    )
}

private val ColorSpec: FiniteAnimationSpec<Color> = tween(durationMillis = 260)

private val CornerSpec: FiniteAnimationSpec<Dp> =
    spring(dampingRatio = ContainerDamping, stiffness = ContainerStiffness, visibilityThreshold = Dp.VisibilityThreshold)

/*
 * Cross-fade timings. Tweens (not springs) so they can start late: under the predictive back
 * gesture navigation-compose seeks the transition by time, and a delayed fade keeps the screen
 * readable during the first part of the swipe instead of blanking it immediately.
 */
private val ContentInSpec = tween<Float>(durationMillis = 210, delayMillis = 30, easing = LinearOutSlowInEasing)
private val ContentOutSpec = tween<Float>(durationMillis = 180, delayMillis = 70)
private val TileOutSpec = tween<Float>(durationMillis = 120)
private val TileInSpec = tween<Float>(durationMillis = 180, delayMillis = 110, easing = LinearOutSlowInEasing)
private val FallbackSpec = tween<Float>(durationMillis = 220, easing = LinearOutSlowInEasing)
private const val FallbackScale = 0.94f

/** Container colors of the origin cards currently composed, by shared key (UI state only). */
private val sourceColors = mutableStateMapOf<String, Color>()

/**
 * One end of an app-open container transform identified by [key].
 *
 * Use `isContainer = false` (default) around the source card/tile and `isContainer = true` around
 * the root of the screen it opens. On the container end [containerColor] is its opaque
 * background; on the tile end it is the card's own container color (not drawn: the card draws
 * itself), used by the container end as its starting/ending color.
 * The [content] is laid out at its own size (never stretched) and clipped while the bounds move.
 * A null [key] or missing shared-transition locals render a plain [Box].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AbleSharedBounds(
    key: String?,
    modifier: Modifier = Modifier,
    isContainer: Boolean = false,
    containerColor: Color = Color.Unspecified,
    cornerRadius: Dp = AbleCardCornerRadius,
    content: @Composable BoxScope.() -> Unit,
) {
    val sharedScope = LocalAbleSharedTransitionScope.current
    val visibilityScope = LocalAbleAnimatedVisibilityScope.current
    val staticBackground =
        if (isContainer && containerColor.isSpecified()) Modifier.background(containerColor) else Modifier
    if (key == null || sharedScope == null || visibilityScope == null) {
        Box(modifier.then(staticBackground), propagateMinConstraints = true, content = content)
        return
    }
    val transition = visibilityScope.transition
    if (!isContainer && containerColor.isSpecified()) {
        SideEffect { sourceColors[key] = containerColor }
    }
    val background = if (isContainer && containerColor.isSpecified()) {
        val color = transition.animateColor(transitionSpec = { ColorSpec }, label = "ableSharedColor") { state ->
            if (state == EnterExitState.Visible) containerColor else sourceColors[key] ?: containerColor
        }
        Modifier.drawBehind { drawRect(color.value) }
    } else {
        Modifier
    }
    val sharedState = sharedScope.rememberSharedContentState(key)
    val outer = sharedBoundsModifier(sharedScope, visibilityScope, sharedState, isContainer, cornerRadius)
    val contentAlpha = if (isContainer) containerContentAlpha(transition) else null
    Box(modifier.then(outer).then(background), propagateMinConstraints = true) {
        val inner = with(sharedScope) { Modifier.skipToLookaheadSize() }
        Box(
            inner.then(
                if (contentAlpha == null) {
                    Modifier
                } else {
                    // Only while the matched transform runs; otherwise the fallback handles alpha.
                    Modifier.graphicsLayer { alpha = if (sharedState.isMatchFound) contentAlpha.value else 1f }
                },
            ),
            propagateMinConstraints = true,
            content = content,
        )
    }
}

/**
 * Modifier form of [AbleSharedBounds] for callers that cannot wrap their content (the content is
 * then re-measured to the animated bounds). Prefer [AbleSharedBounds].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.ableSharedBounds(
    key: String?,
    isContainer: Boolean = false,
    cornerRadius: Dp = AbleCardCornerRadius,
): Modifier {
    val sharedScope = LocalAbleSharedTransitionScope.current
    val visibilityScope = LocalAbleAnimatedVisibilityScope.current
    if (key == null || sharedScope == null || visibilityScope == null) return this
    val sharedState = sharedScope.rememberSharedContentState(key)
    return this.then(sharedBoundsModifier(sharedScope, visibilityScope, sharedState, isContainer, cornerRadius))
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun sharedBoundsModifier(
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
    sharedState: SharedTransitionScope.SharedContentState,
    isContainer: Boolean,
    cornerRadius: Dp,
): Modifier {
    val transition = visibilityScope.transition
    // Card side keeps its rounded corners while visible; the screen side is square when shown.
    val radius = transition.animateDp(transitionSpec = { CornerSpec }, label = "ableSharedCorner") { state ->
        val shown = state == EnterExitState.Visible
        if (shown == isContainer) 0.dp else cornerRadius
    }
    val shape = remember(radius) { AnimatedRoundedShape(radius) }
    // Fallback for a container whose origin card is gone (scrolled away, hidden, relaid out):
    // fade and recede slightly instead of disappearing at the end of the transition.
    val fallback = if (isContainer) {
        transition.animateFloat(transitionSpec = { FallbackSpec }, label = "ableSharedFallback") { state ->
            if (state == EnterExitState.PostExit) 0f else 1f
        }
    } else {
        null
    }
    return with(sharedScope) {
        Modifier
            .sharedBounds(
                sharedContentState = sharedState,
                animatedVisibilityScope = visibilityScope,
                // The container stays opaque (its content fades separately); the tile cross-fades.
                enter = if (isContainer) EnterTransition.None else fadeIn(TileInSpec),
                exit = if (isContainer) ExitTransition.None else fadeOut(TileOutSpec),
                boundsTransform = AbleContainerBoundsTransform,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                // Tile above the screen so it can cross-fade on top of the opaque container.
                zIndexInOverlay = if (isContainer) 0f else 1f,
                clipInOverlayDuringTransition = OverlayClip(shape),
            )
            .then(
                if (fallback == null) {
                    Modifier
                } else {
                    Modifier.graphicsLayer {
                        if (!sharedState.isMatchFound) {
                            val p = fallback.value
                            alpha = p
                            val s = FallbackScale + (1f - FallbackScale) * p
                            scaleX = s
                            scaleY = s
                        }
                    }
                },
            )
    }
}

@Composable
private fun containerContentAlpha(transition: Transition<EnterExitState>): State<Float> =
    transition.animateFloat(
        transitionSpec = { if (targetState == EnterExitState.Visible) ContentInSpec else ContentOutSpec },
        label = "ableSharedContentAlpha",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }

private fun Color.isSpecified(): Boolean = this != Color.Unspecified

/** Rounded rectangle whose radius is read at draw time, so the morph does not recompose. */
private class AnimatedRoundedShape(private val radius: State<Dp>) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        RoundedCornerShape(radius.value).createOutline(size, layoutDirection, density)
}
