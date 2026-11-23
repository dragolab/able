package com.dragolabs.able.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
    val Height = 64.dp
    val HorizontalMargin = 16.dp
    val BottomMargin = 12.dp
    val SearchGap = 10.dp

    /** Extra space kept between the last list item and the top of the bar. */
    private val ContentGap = 8.dp

    /** Bottom space the bar takes above the system navigation inset. */
    val Reserved: Dp = Height + BottomMargin + ContentGap
}

/**
 * Floating navigation bar for compact windows: a translucent pill hovering above the content
 * (which keeps scrolling underneath, lightly blurred), with the four top-level destinations,
 * and a separate circular Search button on the trailing side. Haptics for destination taps are
 * played by the caller's navigation handler; Search plays a click.
 *
 * The blur uses Haze on [hazeState] (the NavHost is the source). Where blur is not available
 * Haze draws [fallback] tint only, so the container becomes an almost opaque surface and labels
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
    val labelColor by animateColorAsState(
        targetValue = if (selected) colors.onSurface else colors.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "navLabelColor",
    )
    val indicatorColor = colors.secondaryContainer
    val interaction = remember { MutableInteractionSource() }
    Column(
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
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
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
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Translucent, lightly blurred container: Haze blur of the content behind plus a tint of the
 * surface-container color, and a hairline outline so the pill reads on any content.
 */
@Composable
private fun Modifier.floatingGlass(hazeState: HazeState, shape: Shape): Modifier {
    val colors = MaterialTheme.colorScheme
    val tint = colors.surfaceContainer
    val background = colors.surface
    val style = remember(tint, background) {
        HazeBlurStyle {
            blurRadius(20.dp)
            backgroundColor(background)
            colorEffects(listOf(HazeColorEffect.tint(tint.copy(alpha = 0.74f))))
            // No blur available: an almost opaque container keeps labels legible.
            fallbackColorEffect(HazeColorEffect.tint(tint.copy(alpha = 0.96f)))
            noiseFactor(0f)
        }
    }
    return this
        .clip(shape)
        .hazeBlur(HazeInput.Sources(hazeState), style)
        .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), shape)
}
