package com.dragolabs.able.feature.tools.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing

/** What the leading selection slot of a [SelectableRow] shows. */
internal enum class SelectionSlot { CHECKBOX, BUSY, LOCKED, NONE }

/**
 * List row with a fixed-size selection slot, a leading visual (app icon, thumbnail) and text, all
 * centered vertically. The slot keeps its width whether it shows a checkbox, a progress indicator
 * or a lock, so selecting or processing a row never moves its content.
 */
@Composable
internal fun SelectableRow(
    selected: Boolean,
    onToggle: (() -> Unit)?,
    slot: SelectionSlot,
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    selectedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    lockedIcon: ImageVector? = null,
    lockedDescription: String? = null,
    leading: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val haptics = rememberAbleHaptics()
    val interactive = onToggle != null && slot == SelectionSlot.CHECKBOX
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) selectedContainerColor else containerColor)
            .then(
                if (interactive) {
                    Modifier.toggleable(value = selected, role = Role.Checkbox) { on ->
                        haptics.toggle(on)
                        onToggle?.invoke()
                    }
                } else {
                    Modifier
                },
            )
            .focusRing(shape)
            .heightIn(min = 64.dp)
            .padding(start = AbleSpacing.xs, end = AbleSpacing.xs, top = AbleSpacing.sm, bottom = AbleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (slot != SelectionSlot.NONE) {
            Box(Modifier.size(SlotSize), contentAlignment = Alignment.Center) {
                when (slot) {
                    SelectionSlot.CHECKBOX -> Checkbox(checked = selected, onCheckedChange = null)
                    SelectionSlot.BUSY -> LoadingIndicator(modifier = Modifier.size(32.dp))
                    SelectionSlot.LOCKED -> lockedIcon?.let {
                        Icon(it, contentDescription = lockedDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SelectionSlot.NONE -> Unit
                }
            }
        } else {
            Spacer(Modifier.width(AbleSpacing.sm))
        }
        if (leading != null) {
            leading()
            Spacer(Modifier.width(AbleSpacing.md))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge,
                LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            ) { headline() }
            if (supporting != null) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                ) { supporting() }
            }
        }
        if (trailing != null) trailing()
    }
}

private val SlotSize = 48.dp
private val BarMaxWidth = 560.dp

/**
 * Floating bulk-action bar shared by the Storage and RAM tools: selection count, a clear button,
 * the primary action and, while the action runs, a progress indicator with a progress label.
 * Meant for the scaffold's floating-action-button slot; it keeps a gutter on compact windows.
 */
@Composable
internal fun SelectionActionBar(
    visible: Boolean,
    title: String,
    actionLabel: String,
    actionIcon: ImageVector,
    onAction: () -> Unit,
    onClear: () -> Unit,
    clearLabel: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    busy: Boolean = false,
    progressText: String? = null,
    destructive: Boolean = false,
    actionEnabled: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        BoxWithConstraints {
            // The FAB slot is measured with the full window width and placed at the end, so a bar
            // narrower by twice the gutter lands centered on phones and at the end on tablets.
            val width = min(maxWidth - AbleSpacing.screenGutter * 2, BarMaxWidth)
            Surface(
                modifier = Modifier.width(width),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 64.dp)
                        .padding(start = AbleSpacing.xs, end = AbleSpacing.sm, top = AbleSpacing.sm, bottom = AbleSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AbleIconButton(onClick = onClear, enabled = !busy, modifier = Modifier.focusRing()) {
                        Icon(Icons.Rounded.Close, contentDescription = clearLabel)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = AbleSpacing.xs)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val secondary = if (busy) progressText else supportingText
                        if (secondary != null) {
                            Text(secondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (busy) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(modifier = Modifier.size(40.dp))
                        }
                    } else {
                        AbleButton(
                            onClick = onAction,
                            enabled = actionEnabled,
                            haptic = if (destructive) AbleHaptic.Reject else AbleHaptic.Click,
                            colors = if (destructive) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                            modifier = Modifier.focusRing(),
                        ) {
                            Icon(actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(actionLabel, modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
