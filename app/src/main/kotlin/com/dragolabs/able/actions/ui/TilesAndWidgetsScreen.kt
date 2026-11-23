package com.dragolabs.able.actions.ui

import android.app.PendingIntent
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.R
import com.dragolabs.able.actions.ActionPrivilege
import com.dragolabs.able.actions.CustomTileSlots
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.tile.AutoBrightnessTileService
import com.dragolabs.able.tile.NfcTileService
import com.dragolabs.able.tile.PrivateDnsTileService
import com.dragolabs.able.tile.ScreenLockTileService
import com.dragolabs.able.tile.TorchTileService
import com.dragolabs.able.tile.UsbAdbTileService
import com.dragolabs.able.tile.WirelessAdbTileService
import com.dragolabs.able.widget.AbleBatteryWidgetReceiver
import com.dragolabs.able.widget.AbleCustomActionsWidgetReceiver
import com.dragolabs.able.widget.AbleQuickActionsWidgetReceiver
import com.dragolabs.able.widget.CustomWidgetConfigActivity

/**
 * "Tiles & widgets" (CUST-001, CUST-002): assign catalog actions to the custom Quick Settings
 * tile slots, add tiles and widgets through Android's own consent flows, and edit placed
 * custom widgets. Entry point for the navigation route `TilesWidgetsRoute`.
 */
@Composable
fun TilesAndWidgetsScreen(onBack: () -> Unit) {
    TilesAndWidgetsContent(onBack = onBack, initialSlot = null)
}

private data class BuiltInTile(val service: Class<*>, @StringRes val label: Int, @DrawableRes val icon: Int)

private val builtInTiles = listOf(
    BuiltInTile(TorchTileService::class.java, R.string.tile_torch_label, R.drawable.ic_tile_torch_on),
    BuiltInTile(NfcTileService::class.java, R.string.tile_nfc_label, R.drawable.ic_tile_nfc),
    BuiltInTile(AutoBrightnessTileService::class.java, R.string.tile_autobrightness_label, R.drawable.ic_tile_brightness),
    BuiltInTile(UsbAdbTileService::class.java, R.string.tile_usb_adb_label, R.drawable.ic_tile_adb),
    BuiltInTile(WirelessAdbTileService::class.java, R.string.tile_wireless_adb_label, R.drawable.ic_tile_wireless_adb),
    BuiltInTile(PrivateDnsTileService::class.java, R.string.tile_private_dns_label, R.drawable.ic_tile_dns),
    BuiltInTile(ScreenLockTileService::class.java, R.string.tile_lock_label, R.drawable.ic_tile_lock),
)

private data class WidgetType(
    val receiver: Class<*>,
    @StringRes val title: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int,
    val configurable: Boolean,
)

private val widgetTypes = listOf(
    WidgetType(AbleCustomActionsWidgetReceiver::class.java, R.string.widget_custom_actions_name, R.string.widget_custom_actions_description, R.drawable.ic_tile_able, true),
    WidgetType(AbleQuickActionsWidgetReceiver::class.java, R.string.widget_quick_actions_name, R.string.widget_quick_actions_description, R.drawable.ic_tile_torch_on, false),
    WidgetType(AbleBatteryWidgetReceiver::class.java, R.string.widget_battery_name, R.string.widget_battery_description, R.drawable.ic_action_battery, false),
)

@Composable
internal fun TilesAndWidgetsContent(onBack: () -> Unit, initialSlot: Int?) {
    val viewModel: TilesWidgetsViewModel = viewModel(factory = ableViewModelFactory { TilesWidgetsViewModel(it.appContext) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberAbleHaptics()
    val snackbar = remember { SnackbarHostState() }
    var pickerSlot by rememberSaveable { mutableStateOf(initialSlot) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshWidgets()
        onPauseOrDispose { }
    }

    AbleDetailScaffold(
        title = stringResource(R.string.tiles_screen_title),
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            HelpButton(
                title = stringResource(R.string.tiles_help_title),
                text = stringResource(R.string.tiles_help_text),
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
                        stringResource(R.string.tiles_screen_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item { SectionHeader(stringResource(R.string.tiles_section_custom)) }
                item {
                    ListGroup {
                        state.slots.forEach { slotUi ->
                            item { shape ->
                                val action = slotUi.action
                                val slotLabel = stringResource(R.string.tiles_slot_title, slotUi.slot)
                                ActionRow(
                                    title = action?.let { stringResource(it.titleRes) } ?: slotLabel,
                                    shape = shape,
                                    supportingText = if (action == null) {
                                        stringResource(R.string.tiles_slot_unassigned, slotUi.slot)
                                    } else {
                                        "$slotLabel · ${stringResource(action.descriptionRes)}"
                                    },
                                    icon = actionIconVector(action),
                                    trailing = {
                                        TextButton(onClick = {
                                            haptics.click()
                                            requestAddTile(
                                                context = context,
                                                component = CustomTileSlots.component(context, slotUi.slot),
                                                label = action?.let { context.getString(it.titleRes) }
                                                    ?: context.getString(R.string.tiles_slot_label, slotUi.slot),
                                                icon = action?.iconRes ?: R.drawable.ic_tile_able,
                                                onResult = viewModel::post,
                                            )
                                        }) { Text(stringResource(R.string.tiles_add_tile)) }
                                    },
                                    onClick = { pickerSlot = slotUi.slot },
                                )
                            }
                        }
                    }
                }

                item { SectionHeader(stringResource(R.string.tiles_section_builtin)) }
                item {
                    ListGroup {
                        builtInTiles.forEach { tile ->
                            item { shape ->
                                ActionRow(
                                    title = stringResource(tile.label),
                                    shape = shape,
                                    icon = ImageVector.vectorResource(tile.icon),
                                    trailing = {
                                        TextButton(onClick = {
                                            haptics.click()
                                            requestAddTile(
                                                context = context,
                                                component = ComponentName(context, tile.service),
                                                label = context.getString(tile.label),
                                                icon = tile.icon,
                                                onResult = viewModel::post,
                                            )
                                        }) { Text(stringResource(R.string.tiles_add_tile)) }
                                    },
                                )
                            }
                        }
                    }
                }

                item { SectionHeader(stringResource(R.string.tiles_section_widgets)) }
                item {
                    ListGroup {
                        widgetTypes.forEach { type ->
                            item { shape ->
                                ActionRow(
                                    title = stringResource(type.title),
                                    shape = shape,
                                    supportingText = stringResource(type.description),
                                    icon = ImageVector.vectorResource(type.icon),
                                    trailing = {
                                        TextButton(onClick = {
                                            haptics.click()
                                            if (state.pinWidgetSupported) {
                                                requestPinWidget(context, type)
                                                viewModel.post(context.getString(R.string.tiles_widget_pin_requested))
                                            } else {
                                                haptics.reject()
                                                viewModel.post(context.getString(R.string.tiles_widget_pin_unsupported))
                                            }
                                        }) { Text(stringResource(R.string.tiles_add_widget)) }
                                    },
                                )
                            }
                        }
                    }
                }

                item { SectionHeader(stringResource(R.string.tiles_section_placed)) }
                item {
                    if (state.widgets.isEmpty()) {
                        Text(
                            stringResource(R.string.tiles_placed_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ListGroup {
                            state.widgets.forEachIndexed { index, widget ->
                                item { shape ->
                                    val names = widget.actions.map { stringResource(it.titleRes) }
                                    ActionRow(
                                        title = stringResource(
                                            R.string.tiles_placed_widget,
                                            index + 1,
                                            widget.format?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.tiles_widget_not_configured),
                                        ),
                                        shape = shape,
                                        supportingText = names.joinToString(", ").ifEmpty { stringResource(R.string.tiles_widget_setup) },
                                        icon = actionIconVector(widget.actions.firstOrNull()),
                                        onClick = {
                                            context.startActivity(
                                                CustomWidgetConfigActivity.intent(context, widget.appWidgetId)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    PrivilegeFootnote()
                }
            }
        }
    }

    pickerSlot?.let { slot ->
        ActionPickerSheet(
            title = stringResource(R.string.tiles_pick_for_slot, slot),
            allowClear = state.slots.firstOrNull { it.slot == slot }?.action != null,
            onPick = { action ->
                viewModel.assign(slot, action)
                pickerSlot = null
            },
            onDismiss = { pickerSlot = null },
        )
    }
}

@Composable
private fun PrivilegeFootnote() {
    ListGroup {
        item { shape ->
            ActionRow(
                title = stringResource(R.string.action_privilege_secure),
                shape = shape,
                supportingText = stringResource(R.string.tiles_privileged_note),
                showChevron = false,
                trailing = { PrivilegeHelp(ActionPrivilege.SECURE_SETTINGS) },
            )
        }
    }
}

/** Asks System UI to add a tile (API 33+); Android shows its own confirmation dialog. */
private fun requestAddTile(
    context: Context,
    component: ComponentName,
    label: String,
    @DrawableRes icon: Int,
    onResult: (String) -> Unit,
) {
    val statusBar = context.getSystemService(StatusBarManager::class.java)
    if (statusBar == null) {
        onResult(context.getString(R.string.tiles_tile_error))
        return
    }
    runCatching {
        statusBar.requestAddTileService(component, label, Icon.createWithResource(context, icon), context.mainExecutor) { result ->
            onResult(
                context.getString(
                    when (result) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tiles_tile_added
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tiles_tile_already
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> R.string.tiles_tile_not_added
                        else -> R.string.tiles_tile_error
                    },
                ),
            )
        }
    }.onFailure { onResult(context.getString(R.string.tiles_tile_error)) }
}

/**
 * Asks the launcher to pin a widget. For the custom widget, the launcher's success callback
 * opens the widget builder with the new widget id filled in.
 */
private fun requestPinWidget(context: Context, type: WidgetType) {
    val manager = AppWidgetManager.getInstance(context)
    val callback = if (type.configurable) {
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, CustomWidgetConfigActivity::class.java),
            // Mutable so the launcher can add EXTRA_APPWIDGET_ID; the intent is explicit.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    } else {
        null
    }
    runCatching { manager.requestPinAppWidget(ComponentName(context, type.receiver), null, callback) }
}
