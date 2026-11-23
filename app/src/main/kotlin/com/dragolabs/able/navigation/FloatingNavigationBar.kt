package com.dragolabs.able.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.R
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/** Geometry of the floating navigation bar used on compact windows (SHELL-001). */
internal object FloatingNavigationBarDefaults {
    /** Icon-only items (labels are TalkBack content descriptions), so the bar is compact. */
    val Height = 56.dp
    val HorizontalMargin = 16.dp
    val BottomMargin = 12.dp
    val SearchGap = 10.dp

    /** Extra space kept between the last list item and the top of the bar. */
    private val ContentGap = 8.dp

    /** Bottom space the bar takes above the system navigation inset. */
    val Reserved: Dp = Height + BottomMargin + ContentGap
}

/**
 * Floating navigation bar for compact windows: a tinted, blurred pill hovering above the content
 * (which keeps scrolling underneath), with the four top-level destinations shown as icons only
 * (each destination name is its TalkBack label), and a separate circular Search button on the
 * trailing side. Haptics for destination taps are
 * played by the caller's navigation handler; Search plays a click.
 *
 * The blur uses Haze on [hazeState] (the NavHost is the source). Where blur is not available
 * Haze draws the fallback tint only, so the container becomes an almost opaque surface and icons
 * stay legible.
 */
@Composable
internal fun FloatingNavigationBar(
    hazeState: HazeState,
    isSelected: (TopLevelDestination) -> Boolean,
    onNavigate: (TopLevelDestination) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = FloatingNavigationBarDefaults.HorizontalMargin,
                end = FloatingNavigationBarDefaults.HorizontalMargin,
                bottom = FloatingNavigationBarDefaults.BottomMargin,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FloatingNavigationBarDefaults.SearchGap),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(FloatingNavigationBarDefaults.Height)
                .floatingGlass(hazeState, CircleShape)
                .padding(horizontal = 6.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = isSelected(destination)
                FloatingBarItem(
                    selected = selected,
                    icon = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    label = stringResource(destination.labelRes),
                    onClick = { onNavigate(destination) },
                )
            }
        }
        val haptics = rememberAbleHaptics()
        Box(
            modifier = Modifier
                .size(FloatingNavigationBarDefaults.Height)
                .floatingGlass(hazeState, CircleShape)
                .focusRing(CircleShape)
                .clickable(role = Role.Button) {
                    haptics.click()
                    onSearch()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = stringResource(R.string.ds_search),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun RowScope.FloatingBarItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    // Expressive springs: the pill indicator grows from the icon with a slight overshoot.
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 56.dp else 28.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "navIndicatorWidth",
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.fastEffectsSpec(),
        label = "navIndicatorAlpha",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "navContentColor",
    )
    val indicatorColor = colors.secondaryContainer
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .clip(CircleShape)
            .focusRing(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Tab,
                onClick = onClick,
            )
            // Icon-only item: the destination name stays available to TalkBack.
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .width(56.dp)
                .drawBehind {
                    if (indicatorAlpha > 0f) {
                        val w = indicatorWidth.toPx()
                        drawRoundRect(
                            color = indicatorColor,
                            alpha = indicatorAlpha,
                            topLeft = Offset((size.width - w) / 2f, 0f),
                            size = Size(w, size.height),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * Tinted, lightly blurred container: Haze blur of the content behind under a fairly opaque
 * surfaceContainerHigh tint (so the bar stands out from the surface-colored background in light,
 * dark and dynamic-color themes), a 1dp outlineVariant border, and a soft shadow.
 */
@Composable
private fun Modifier.floatingGlass(hazeState: HazeState, shape: Shape): Modifier {
    val colors = MaterialTheme.colorScheme
    val tint = colors.surfaceContainerHigh
    val background = colors.surface
    val style = remember(tint, background) {
        HazeBlurStyle {
            blurRadius(20.dp)
            backgroundColor(background)
            colorEffects(listOf(HazeColorEffect.tint(tint.copy(alpha = 0.86f))))
            // No blur available: an opaque tonal container keeps icons legible.
            fallbackColorEffect(HazeColorEffect.tint(tint.copy(alpha = 0.98f)))
            noiseFactor(0f)
        }
    }
    return this
        .shadow(elevation = 4.dp, shape = shape, clip = false)
        .clip(shape)
        .hazeBlur(HazeInput.Sources(hazeState), style)
        .border(1.dp, colors.outlineVariant, shape)
}
