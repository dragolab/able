package com.dragolabs.able.actions.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dragolabs.able.R
import com.dragolabs.able.actions.CatalogAction
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.WidgetGridFormat

@StringRes
fun WidgetGridFormat.labelRes(): Int = when (this) {
    WidgetGridFormat.GRID_2X1 -> R.string.tiles_format_2x1
    WidgetGridFormat.GRID_4X1 -> R.string.tiles_format_4x1
    WidgetGridFormat.GRID_4X2 -> R.string.tiles_format_4x2
}

/**
 * Widget builder (CUST-002): pick a grid format and assign a catalog action to each slot. The
 * preview mirrors the widget grid; tapping a slot opens the action picker.
 *
 * @param slots one entry per slot of [format]; `null` is an empty slot.
 */
@Composable
fun WidgetConfigScreen(
    format: WidgetGridFormat,
    slots: List<CatalogAction?>,
    onFormatChange: (WidgetGridFormat) -> Unit,
    onSlotChange: (index: Int, action: CatalogAction?) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val haptics = rememberAbleHaptics()
    var pickerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val canSave = slots.any { it != null }

    AbleDetailScaffold(
        title = stringResource(R.string.tiles_widget_config_title),
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (canSave) {
                        haptics.confirm()
                        onSave()
                    } else {
                        haptics.reject()
                    }
                },
                icon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                text = { Text(stringResource(R.string.tiles_widget_save)) },
                expanded = true,
            )
        },
    ) { padding ->
        ReadableWidth {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = screenContentPadding(padding),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            ) {
                item {
                    Text(
                        stringResource(R.string.tiles_widget_config_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { SectionHeader(stringResource(R.string.tiles_widget_format)) }
                item {
                    ListGroup {
                        WidgetGridFormat.entries.forEach { option ->
                            item { shape ->
                                RadioRow(
                                    title = stringResource(option.labelRes()),
                                    selected = option == format,
                                    onSelect = { onFormatChange(option) },
                                    shape = shape,
                                    supportingText = stringResource(R.string.tiles_format_slots, option.maxSlots),
                                )
                            }
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.tiles_widget_slots)) }
                item {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            Modifier.padding(AbleSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                        ) {
                            slots.withIndex().chunked(format.columns).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                                    row.forEach { (index, action) ->
                                        SlotPreview(
                                            index = index,
                                            action = action,
                                            onClick = { haptics.click(); pickerIndex = index },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (!canSave) {
                    item {
                        Text(
                            stringResource(R.string.tiles_widget_need_action),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    pickerIndex?.let { index ->
        ActionPickerSheet(
            title = stringResource(R.string.tiles_pick_for_widget_slot, index + 1),
            allowClear = slots.getOrNull(index) != null,
            onPick = { action ->
                onSlotChange(index, action)
                pickerIndex = null
            },
            onDismiss = { pickerIndex = null },
        )
    }
}

@Composable
private fun SlotPreview(index: Int, action: CatalogAction?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.large
    val label = action?.let { stringResource(it.titleRes) } ?: stringResource(R.string.tiles_widget_empty_slot)
    val container = if (action == null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer
    val content = if (action == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .focusRing(shape)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.tiles_pick_for_widget_slot, index + 1),
                onClick = onClick,
            ),
        shape = shape,
        color = container,
        contentColor = content,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(AbleSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(actionIconVector(action), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = AbleSpacing.xs),
            )
        }
    }
}
