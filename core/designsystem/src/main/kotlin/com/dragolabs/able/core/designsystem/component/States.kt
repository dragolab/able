package com.dragolabs.able.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.R
import com.dragolabs.able.core.designsystem.theme.AbleSpacing

/**
 * Shared UI states required by the screen inventory: loading, empty, permission denied,
 * capability missing/unsupported (docs/ux/adaptive-ui.md).
 */

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val description = message ?: stringResource(R.string.ds_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AbleSpacing.xxl)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
    ) {
        ContainedLoadingIndicator()
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

/** Small inline loading indicator, e.g. inside a card while an operation runs. */
@Composable
fun InlineLoading(modifier: Modifier = Modifier) {
    LoadingIndicator(modifier = modifier.size(32.dp))
}

@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AbleSpacing.xl, vertical = AbleSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ExpressiveIcon(
                icon = icon,
                shape = MaterialShapes.Cookie9Sided,
                size = 88.dp,
                iconSize = 40.dp,
            )
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            AbleButton(onClick = onAction) { Text(actionLabel) }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            AbleOutlinedButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = Icons.Rounded.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = MessageState(icon, title, modifier, message, actionLabel, onAction)

/** Capability missing on this device / Android version. No action is offered when no route exists. */
@Composable
fun UnsupportedState(
    reason: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.ds_unsupported_title),
) = MessageState(Icons.Rounded.Block, title, modifier, reason)

/** Permission or access required, with an in-context request (NFR: no bulk requests at launch). */
@Composable
fun PermissionRequiredState(
    title: String,
    rationale: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) = MessageState(
    icon = Icons.Rounded.Lock,
    title = title,
    modifier = modifier,
    message = rationale,
    actionLabel = actionLabel,
    onAction = onAction,
    secondaryActionLabel = secondaryActionLabel,
    onSecondaryAction = onSecondaryAction,
)
