package com.dragolabs.able.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.R
import com.dragolabs.able.core.designsystem.theme.AbleSpacing

/**
 * Contextual help (UX-002): a small "?" button next to advanced or risky options. The dialog
 * should say what the option does, its risks, and how to undo it.
 */
@Composable
fun HelpButton(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberAbleHaptics()
    IconButton(
        onClick = { haptics.click(); open = true },
        modifier = modifier,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.HelpOutline,
            contentDescription = stringResource(R.string.ds_help_about, title),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            icon = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null) },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.ds_got_it)) } },
        )
    }
}

/**
 * Compact row with a short summary and an expandable explanation (SET-002 progressive
 * disclosure). The whole header toggles; [trailing] shows status (e.g. a [StatusPill]).
 */
@Composable
fun ExpandableRow(
    title: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    /** Status shown under the title (e.g. a [StatusPill]) so it never squeezes the title. */
    status: @Composable (() -> Unit)? = null,
    initiallyExpanded: Boolean = false,
    expandedContent: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val haptics = rememberAbleHaptics()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val stateText = stringResource(if (expanded) R.string.ds_expanded else R.string.ds_collapsed)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { haptics.click(); expanded = !expanded }
                .semantics { stateDescription = stateText }
                .focusRing(shape),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            headlineContent = { Text(title) },
            supportingContent = if (summary != null || status != null) {
                {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (summary != null) Text(summary, maxLines = 2)
                        status?.invoke()
                    }
                }
            } else {
                null
            },
            leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    trailing?.invoke()
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(24.dp).rotate(rotation))
                }
            },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = AbleSpacing.lg, end = AbleSpacing.lg, bottom = AbleSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
            ) { expandedContent() }
        }
    }
}
