package com.dragolabs.able.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.dragolabs.able.core.designsystem.theme.AbleSpacing

/**
 * Visible focus ring for keyboard and D-pad navigation (NFR TV focus visibility). Applied to
 * every clickable card so focus stays visible from viewing distance.
 */
@Composable
fun Modifier.focusRing(shape: Shape = MaterialTheme.shapes.large): Modifier {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(BorderStroke(3.dp, color), shape) else Modifier)
}

/** Icon inside an expressive Material shape (cookie, clover, pill...). */
@Composable
fun ExpressiveIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    shape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = shape.toShape(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
        }
    }
}

enum class AbleCardStyle { Standard, Hero, Tonal, Outlined }

/**
 * Base card for summaries (adaptive-ui: "cards for summaries"). Optional header with an
 * expressive icon, title and compact [trailing] actions (for example a [HelpButton]). The
 * [subtitle] and [status] (for example one or more [StatusPill]s, laid out in a flow row) are
 * placed full width below the header row so they never squeeze the title.
 */
@Composable
fun AbleCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconShape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    style: AbleCardStyle = AbleCardStyle.Standard,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    status: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val colors = when (style) {
        AbleCardStyle.Standard -> CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow)
        AbleCardStyle.Hero -> CardDefaults.cardColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        )
        AbleCardStyle.Tonal -> CardDefaults.cardColors(
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        )
        AbleCardStyle.Outlined -> CardDefaults.outlinedCardColors()
    }
    val border = if (style == AbleCardStyle.Outlined) CardDefaults.outlinedCardBorder() else null
    val iconContainer = when (style) {
        AbleCardStyle.Hero -> scheme.primary
        AbleCardStyle.Tonal -> scheme.secondary
        else -> scheme.primaryContainer
    }
    val iconContent = when (style) {
        AbleCardStyle.Hero -> scheme.onPrimary
        AbleCardStyle.Tonal -> scheme.onSecondary
        else -> scheme.onPrimaryContainer
    }

    val body: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(AbleSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
        ) {
            // Header: icon + title (weight 1) + compact trailing actions on one row. The
            // subtitle and status pills go full width underneath so they never squeeze the
            // title into a narrow column.
            val hasHeader = title != null || icon != null || trailing != null
            if (hasHeader || subtitle != null) {
                Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                    if (hasHeader) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md),
                        ) {
                            if (icon != null) {
                                ExpressiveIcon(
                                    icon = icon,
                                    shape = iconShape,
                                    containerColor = iconContainer,
                                    contentColor = iconContent,
                                    size = 44.dp,
                                    iconSize = 22.dp,
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                if (title != null) {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.titleMedium.copy(lineBreak = LineBreak.Heading),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (trailing != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
                                ) { trailing() }
                            }
                        }
                    }
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph),
                            color = LocalContentColorOrVariant(style),
                        )
                    }
                }
            }
            if (status != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) { status() }
            }
            content()
        }
    }

    if (onClick != null) {
        val haptics = rememberAbleHaptics()
        Card(
            onClick = { haptics.click(); onClick() },
            modifier = modifier.fillMaxWidth().focusRing(shape),
            shape = shape,
            colors = colors,
            border = border,
            content = body,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            border = border,
            content = body,
        )
    }
}

@Composable
private fun LocalContentColorOrVariant(style: AbleCardStyle): Color = when (style) {
    AbleCardStyle.Standard, AbleCardStyle.Outlined -> MaterialTheme.colorScheme.onSurfaceVariant
    AbleCardStyle.Hero -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
    AbleCardStyle.Tonal -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
}

/**
 * Compact tile for tool grids and quick actions. Unavailable tiles stay focusable and explain
 * why instead of looking actionable (adaptive-ui unsupported capability rule).
 */
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    shape: RoundedPolygon = MaterialShapes.Cookie7Sided,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        !enabled -> scheme.surfaceContainer
        selected -> scheme.primaryContainer
        else -> scheme.surfaceContainerHigh
    }
    val content = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.6f)
        selected -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    val cardShape = MaterialTheme.shapes.large
    val haptics = rememberAbleHaptics()
    Card(
        onClick = { if (enabled) haptics.click() else haptics.reject(); onClick() },
        modifier = modifier.focusRing(cardShape),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AbleSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
        ) {
            ExpressiveIcon(
                icon = icon,
                shape = shape,
                containerColor = if (selected) scheme.primary else if (enabled) scheme.secondaryContainer else scheme.surfaceContainerHighest,
                contentColor = if (selected) scheme.onPrimary else if (enabled) scheme.onSecondaryContainer else content,
                size = 44.dp,
                iconSize = 22.dp,
            )
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (supportingText != null) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) scheme.onSurfaceVariant else content,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A single labeled measurement. Unavailable values must be passed as an explicit label. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (emphasized) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(AbleSpacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleLarge)
            if (supportingText != null) {
                Text(supportingText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Lays out metric tiles in rows of [columns]. */
@Composable
fun MetricGrid(
    metrics: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        metrics.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                row.forEach { (label, value) ->
                    MetricTile(label = label, value = value, modifier = Modifier.weight(1f))
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
