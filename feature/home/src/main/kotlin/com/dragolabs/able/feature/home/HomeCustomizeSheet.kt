package com.dragolabs.able.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.listGroupItemShape
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.HomeCardConfig
import com.dragolabs.able.core.model.HomeCardType

/**
 * Home customization (HOME-002): show/hide every card, move up/down with buttons (D-pad and
 * TalkBack friendly, no drag-only interaction) and reset to the default layout. Safety banners
 * are not listed because they cannot be hidden (HOME-003).
 */
@Composable
internal fun HomeCustomizeSheet(
    cards: List<HomeCardConfig>,
    onDismiss: () -> Unit,
    onSetVisible: (HomeCardType, Boolean) -> Unit,
    onMove: (HomeCardType, Int) -> Unit,
    onReset: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = AbleSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
        ) {
            Text(stringResource(R.string.home_customize_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.home_customize_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(AbleSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(cards, key = { _, card -> card.cardType.name }) { index, card ->
                CustomizeRow(
                    card = card,
                    index = index,
                    count = cards.size,
                    onSetVisible = onSetVisible,
                    onMove = onMove,
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "reset") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = AbleSpacing.lg),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onReset) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                        Text(
                            stringResource(R.string.home_customize_reset),
                            modifier = Modifier.padding(start = AbleSpacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizeRow(
    card: HomeCardConfig,
    index: Int,
    count: Int,
    onSetVisible: (HomeCardType, Boolean) -> Unit,
    onMove: (HomeCardType, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = listGroupItemShape(index, count)
    val title = stringResource(card.cardType.titleRes())
    val moveUp = stringResource(R.string.home_customize_move_up, title)
    val moveDown = stringResource(R.string.home_customize_move_down, title)
    val visibility = stringResource(R.string.home_customize_visible, title)
    ListItem(
        modifier = modifier.fillMaxWidth().clip(shape),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { Icon(card.cardType.icon(), contentDescription = null) },
        supportingContent = {
            Text(
                stringResource(
                    if (card.isVisible) R.string.home_customize_state_shown else R.string.home_customize_state_hidden,
                ) + " · " + stringResource(card.cardType.descriptionRes()),
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onMove(card.cardType, -1) },
                    enabled = index > 0,
                    modifier = Modifier.focusRing(CircleShape),
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = moveUp)
                }
                IconButton(
                    onClick = { onMove(card.cardType, 1) },
                    enabled = index < count - 1,
                    modifier = Modifier.focusRing(CircleShape),
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = moveDown)
                }
                Switch(
                    checked = card.isVisible,
                    onCheckedChange = { onSetVisible(card.cardType, it) },
                    modifier = Modifier
                        .focusRing(CircleShape)
                        .semantics { contentDescription = visibility },
                )
            }
        },
    ) {
        Text(title)
    }
}
