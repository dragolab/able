package com.dragolabs.able.actions.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.dragolabs.able.R
import com.dragolabs.able.actions.ActionCatalog
import com.dragolabs.able.actions.ActionPrivilege
import com.dragolabs.able.actions.CatalogAction
import com.dragolabs.able.actions.labelRes
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing

/**
 * Bottom sheet listing the Action Catalog grouped by category (ADR-024). Privileged actions
 * carry a help button explaining the access they need and what happens without it.
 *
 * @param onPick called with the chosen action, or `null` when the user clears the slot.
 */
@Composable
fun ActionPickerSheet(
    title: String,
    allowClear: Boolean,
    onPick: (CatalogAction?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberAbleHaptics()
    val context = LocalContext.current
    val supported = remember(context) { ActionCatalog.all.associate { it.id to it.isSupported(context) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            contentPadding = PaddingValues(start = AbleSpacing.lg, end = AbleSpacing.lg, bottom = AbleSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
        ) {
            item {
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            if (allowClear) {
                item {
                    ListGroup {
                        item { shape ->
                            ActionRow(
                                title = stringResource(R.string.tiles_clear),
                                shape = shape,
                                icon = Icons.Rounded.Close,
                                showChevron = false,
                                onClick = { haptics.reject(); onPick(null) },
                            )
                        }
                    }
                }
            }
            ActionCatalog.categories.forEach { category ->
                val actions = ActionCatalog.all.filter { it.category == category }
                if (actions.isEmpty()) return@forEach
                item(key = category.name) {
                    SectionHeader(stringResource(category.labelRes()), Modifier.padding(top = AbleSpacing.sm))
                }
                item(key = "${category.name}_list") {
                    ListGroup {
                        actions.forEach { action ->
                            item { shape ->
                                val isSupported = supported[action.id] == true
                                ActionRow(
                                    title = stringResource(action.titleRes),
                                    shape = shape,
                                    supportingText = actionSupportingText(action, isSupported),
                                    icon = ImageVector.vectorResource(action.iconRes),
                                    enabled = isSupported,
                                    trailing = if (action.requiresPrivilege) {
                                        { PrivilegeHelp(action.privilege) }
                                    } else {
                                        null
                                    },
                                    onClick = { haptics.confirm(); onPick(action) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun actionSupportingText(action: CatalogAction, supported: Boolean): String {
    val description = stringResource(action.descriptionRes)
    val note = when {
        !supported -> stringResource(R.string.tiles_not_supported)
        action.privilege.labelRes != null -> stringResource(action.privilege.labelRes)
        else -> null
    }
    return if (note != null) "$description\n$note" else description
}

/** Help button explaining the access a privileged action needs (UX-002). */
@Composable
fun PrivilegeHelp(privilege: ActionPrivilege, modifier: Modifier = Modifier) {
    val label = privilege.labelRes ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        HelpButton(title = stringResource(label), text = stringResource(privilege.helpRes()))
    }
}

@StringRes
private fun ActionPrivilege.helpRes(): Int = when (this) {
    ActionPrivilege.NONE -> R.string.action_privilege_secure_help
    ActionPrivilege.SECURE_SETTINGS -> R.string.action_privilege_secure_help
    ActionPrivilege.WRITE_SETTINGS -> R.string.action_privilege_write_settings_help
    ActionPrivilege.ACCESSIBILITY -> R.string.action_privilege_accessibility_help
    ActionPrivilege.NOTIFICATION_POLICY -> R.string.action_privilege_notification_policy_help
}

/** Icon of [action], or the Able mark for an empty slot. */
@Composable
internal fun actionIconVector(action: CatalogAction?): ImageVector =
    ImageVector.vectorResource(action?.iconRes ?: R.drawable.ic_tile_able)
