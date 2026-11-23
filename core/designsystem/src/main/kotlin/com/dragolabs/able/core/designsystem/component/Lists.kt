package com.dragolabs.able.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp

/**
 * Expressive grouped list: rows are separated by small gaps; the first and last rows take the
 * large outer corners. Used for settings and searchable lists.
 */
@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    content: ListGroupScope.() -> Unit,
) {
    val scope = ListGroupScopeImpl().apply(content)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val count = scope.items.size
        scope.items.forEachIndexed { index, item ->
            item(listGroupItemShape(index, count))
        }
    }
}

interface ListGroupScope {
    fun item(content: @Composable (shape: Shape) -> Unit)
}

private class ListGroupScopeImpl : ListGroupScope {
    val items = mutableListOf<@Composable (Shape) -> Unit>()
    override fun item(content: @Composable (shape: Shape) -> Unit) {
        items += content
    }
}

fun listGroupItemShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 6.dp
    return when {
        count == 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

@Composable
private fun rowColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

/** Row title that wraps between words with balanced lines instead of squeezing. */
@Composable
private fun RowTitle(text: String) {
    Text(text, style = LocalTextStyle.current.copy(lineBreak = LineBreak.Heading))
}

@Composable
private fun RowSupportingText(text: String) {
    Text(text, style = LocalTextStyle.current.copy(lineBreak = LineBreak.Paragraph))
}

/** Navigation or action row. */
@Composable
fun ActionRow(
    title: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    showChevron: Boolean = true,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val haptics = rememberAbleHaptics()
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled) { haptics.click(); onClick() } else Modifier)
            .focusRing(shape),
        colors = rowColors(),
        headlineContent = { RowTitle(title) },
        supportingContent = supportingText?.let { { RowSupportingText(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = when {
            trailing != null -> trailing
            onClick != null && showChevron -> { { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) } }
            else -> null
        },
    )
}

/** Row with a switch. The whole row is toggleable for TalkBack and D-pad. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberAbleHaptics()
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch) {
                haptics.toggle(it)
                onCheckedChange(it)
            }
            .focusRing(shape),
        colors = rowColors(),
        headlineContent = { RowTitle(title) },
        supportingContent = supportingText?.let { { RowSupportingText(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.then(Modifier),
                    )
                },
                colors = SwitchDefaults.colors(),
            )
        },
    )
}

/** Row with a radio button for single choice groups (theme, language, retention). */
@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    val haptics = rememberAbleHaptics()
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.RadioButton) { haptics.tick(); onSelect() }
            .focusRing(shape),
        colors = rowColors(),
        headlineContent = { RowTitle(title) },
        supportingContent = supportingText?.let { { RowSupportingText(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
    )
}
