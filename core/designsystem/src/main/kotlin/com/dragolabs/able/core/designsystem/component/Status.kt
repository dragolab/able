package com.dragolabs.able.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.designsystem.theme.AbleThemeExtras

/** Status level used by banners and pills. Each level has its own icon and label, never color alone. */
enum class StatusLevel { INFO, SUCCESS, WARNING, CRITICAL }

@Immutable
data class StatusColors(val container: Color, val content: Color, val accent: Color)

@Composable
fun statusColors(level: StatusLevel): StatusColors {
    val scheme = MaterialTheme.colorScheme
    val semantic = AbleThemeExtras.semantic
    return when (level) {
        StatusLevel.INFO -> StatusColors(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary)
        StatusLevel.SUCCESS -> StatusColors(semantic.successContainer, semantic.onSuccessContainer, semantic.success)
        StatusLevel.WARNING -> StatusColors(semantic.warningContainer, semantic.onWarningContainer, semantic.warning)
        StatusLevel.CRITICAL -> StatusColors(scheme.errorContainer, scheme.onErrorContainer, scheme.error)
    }
}

fun StatusLevel.icon(): ImageVector = when (this) {
    StatusLevel.INFO -> Icons.Rounded.Info
    StatusLevel.SUCCESS -> Icons.Rounded.CheckCircle
    StatusLevel.WARNING -> Icons.Rounded.Warning
    StatusLevel.CRITICAL -> Icons.Rounded.Error
}

/**
 * High-priority banner (HOME-003, ADR-016). Used for safety alerts that must stay visible
 * regardless of Home card order, and for inline status/outcome reporting.
 */
@Composable
fun StatusBanner(
    level: StatusLevel,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val c = statusColors(level)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = c.container, contentColor = c.content),
    ) {
        Column(Modifier.padding(AbleSpacing.lg), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
                Icon(level.icon(), contentDescription = null, tint = c.accent)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            if ((actionLabel != null && onAction != null) || (secondaryActionLabel != null && onSecondaryAction != null)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm, Alignment.End),
                ) {
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        TextButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
                    }
                    if (actionLabel != null && onAction != null) {
                        FilledTonalButton(onClick = onAction) { Text(actionLabel) }
                    }
                }
            }
        }
    }
}

/** Compact labeled status indicator (icon + text) for rows and cards. */
@Composable
fun StatusPill(
    text: String,
    level: StatusLevel,
    modifier: Modifier = Modifier,
) {
    val c = statusColors(level)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = c.container,
        contentColor = c.content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AbleSpacing.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(level.icon(), contentDescription = null, modifier = Modifier.size(16.dp), tint = c.accent)
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
