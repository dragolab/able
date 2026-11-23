package com.dragolabs.able.feature.battery.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.InlineLoading
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.feature.battery.BatteryEvent
import com.dragolabs.able.feature.battery.BatteryUiState
import com.dragolabs.able.feature.battery.ChecklistItem
import com.dragolabs.able.feature.battery.DiagnosticsUiState
import com.dragolabs.able.feature.battery.R
import com.dragolabs.able.feature.battery.SettingsTarget

/**
 * Diagnostics tab: quick checks (BAT-CHK-001), battery usage by apps (BAT-REV-001), Android
 * settings shortcuts and the session diary. Able links to Android settings and never changes them.
 */
@Composable
internal fun DiagnosticsTab(
    state: BatteryUiState,
    contentPadding: PaddingValues,
    onEvent: (BatteryEvent) -> Unit,
    onOpenMonitor: () -> Unit,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    val diagnostics = state.diagnostics
    BatteryCardGrid(contentPadding, state = gridState) {
        fullLine("checks") { QuickChecksCard(state, onEvent, onOpenMonitor) }
        card("usage") { AppUsageCard(diagnostics, onEvent) }
        card("settings") { AndroidSettingsCard(onEvent, onOpenMonitor) }
    }
}

private data class Check(
    val item: ChecklistItem,
    val icon: ImageVector,
    val title: String,
    val detail: String,
    val actionLabel: String,
    val onAction: () -> Unit,
)

@Composable
private fun QuickChecksCard(state: BatteryUiState, onEvent: (BatteryEvent) -> Unit, onOpenMonitor: () -> Unit) {
    val diagnostics = state.diagnostics
    val latest = diagnostics.latestDischarge
    val title = stringResource(R.string.battery_diag_checks_title)
    val checks = listOf(
        Check(
            ChecklistItem.RECENT_DRAIN,
            Icons.Rounded.Insights,
            stringResource(R.string.battery_diag_recent),
            if (latest == null) {
                stringResource(R.string.battery_diag_recent_none_short)
            } else {
                stringResource(
                    R.string.battery_diag_recent_value,
                    rateText(latest.rates.activeRatePerHour),
                    rateText(latest.rates.idleRatePerHour),
                )
            },
            stringResource(R.string.battery_diag_action_monitor),
            onOpenMonitor,
        ),
        Check(
            ChecklistItem.SIGNAL,
            Icons.Rounded.SignalCellularAlt,
            stringResource(R.string.battery_diag_signal),
            stringResource(R.string.battery_diag_signal_detail),
            stringResource(R.string.battery_diag_action_open),
        ) { onEvent(BatteryEvent.OpenSettings(SettingsTarget.Network)) },
        Check(
            ChecklistItem.HOTSPOT,
            Icons.Rounded.WifiTethering,
            stringResource(R.string.battery_diag_hotspot),
            stringResource(R.string.battery_diag_hotspot_detail),
            stringResource(R.string.battery_diag_action_open),
        ) { onEvent(BatteryEvent.OpenSettings(SettingsTarget.Hotspot)) },
        Check(
            ChecklistItem.SCREEN,
            Icons.Rounded.Brightness6,
            stringResource(R.string.battery_diag_display),
            stringResource(R.string.battery_diag_display_detail),
            stringResource(R.string.battery_diag_action_open),
        ) { onEvent(BatteryEvent.OpenSettings(SettingsTarget.Display)) },
        Check(
            ChecklistItem.BACKGROUND_APPS,
            Icons.Rounded.Apps,
            stringResource(R.string.battery_diag_apps),
            stringResource(R.string.battery_diag_apps_short),
            stringResource(R.string.battery_diag_action_apps),
        ) { onEvent(BatteryEvent.OpenApps) },
    )
    val done = diagnostics.reviewed.size
    AbleCard(
        title = title,
        subtitle = stringResource(R.string.battery_diag_checks_body),
        icon = Icons.Rounded.Checklist,
        style = AbleCardStyle.Tonal,
        trailing = { HelpButton(title = title, text = stringResource(R.string.battery_help_checks)) },
        status = {
            StatusPill(
                text = stringResource(R.string.battery_diag_progress, done, ChecklistItem.entries.size),
                level = if (done == ChecklistItem.entries.size) StatusLevel.SUCCESS else StatusLevel.INFO,
            )
        },
    ) {
        ListGroup {
            checks.forEach { check ->
                item { shape -> CheckRow(check, check.item in diagnostics.reviewed, shape, onEvent) }
            }
        }
    }
}

/** Compact actionable check: the checkbox marks it reviewed, the trailing button opens the fix. */
@Composable
private fun CheckRow(check: Check, reviewed: Boolean, shape: Shape, onEvent: (BatteryEvent) -> Unit) {
    val haptics = rememberAbleHaptics()
    val reviewedText = stringResource(if (reviewed) R.string.battery_diag_reviewed else R.string.battery_diag_not_reviewed)
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = {
            Checkbox(
                checked = reviewed,
                onCheckedChange = { haptics.toggle(it); onEvent(BatteryEvent.ToggleReviewed(check.item)) },
                modifier = Modifier
                    .focusRing()
                    .semantics { contentDescription = "${check.title}, $reviewedText" },
            )
        },
        headlineContent = { Text(check.title) },
        supportingContent = { Text(check.detail) },
        trailingContent = {
            AbleTextButton(onClick = check.onAction, modifier = Modifier.focusRing()) {
                Text(check.actionLabel, maxLines = 1)
            }
        },
    )
}

@Composable
private fun AppUsageCard(diagnostics: DiagnosticsUiState, onEvent: (BatteryEvent) -> Unit) {
    val title = stringResource(R.string.battery_diag_usage_title)
    val refreshLabel = stringResource(R.string.battery_diag_usage_refresh)
    AbleCard(
        title = title,
        subtitle = stringResource(if (diagnostics.usageAccessGranted) R.string.battery_diag_usage_window else R.string.battery_diag_usage_needs_access),
        icon = Icons.Rounded.Apps,
        iconShape = MaterialShapes.Clover4Leaf,
        trailing = {
            if (diagnostics.usageAccessGranted) {
                AbleIconButton(
                    onClick = { onEvent(BatteryEvent.RefreshUsage) },
                    enabled = !diagnostics.usageLoading,
                    modifier = Modifier.focusRing(),
                ) { Icon(Icons.Rounded.Refresh, contentDescription = refreshLabel) }
            }
            HelpButton(title = title, text = stringResource(R.string.battery_diag_usage_coverage))
        },
    ) {
        if (!diagnostics.usageAccessGranted) {
            ListGroup {
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.battery_diag_usage_grant),
                        supportingText = stringResource(R.string.battery_diag_usage_rationale),
                        icon = Icons.Rounded.Lock,
                        shape = shape,
                        onClick = { onEvent(BatteryEvent.OpenSettings(SettingsTarget.UsageAccess)) },
                    )
                }
            }
            return@AbleCard
        }
        val apps = diagnostics.topApps
        when {
            diagnostics.usageLoading -> InlineLoading()
            apps.isNullOrEmpty() -> SupportingText(stringResource(R.string.battery_diag_usage_empty))
            else -> {
                val rows = apps.map { app ->
                    Triple(
                        app.packageName,
                        app.label,
                        stringResource(R.string.battery_diag_usage_row, durationText(app.foregroundMs), durationText(app.foregroundServiceMs)),
                    )
                }
                ListGroup(Modifier.fillMaxWidth()) {
                    rows.forEach { (pkg, label, detail) ->
                        item { shape ->
                            ActionRow(
                                title = label,
                                supportingText = detail,
                                shape = shape,
                                onClick = { onEvent(BatteryEvent.OpenSettings(SettingsTarget.AppDetails(pkg))) },
                            )
                        }
                    }
                }
                SupportingText(stringResource(R.string.battery_diag_usage_note))
            }
        }
    }
}

@Composable
private fun AndroidSettingsCard(onEvent: (BatteryEvent) -> Unit, onOpenMonitor: () -> Unit) {
    AbleCard(
        title = stringResource(R.string.battery_diag_settings_title),
        subtitle = stringResource(R.string.battery_diag_settings_body),
        icon = Icons.Rounded.Settings,
        style = AbleCardStyle.Outlined,
    ) {
        ListGroup {
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.battery_diag_battery_usage),
                    icon = Icons.Rounded.BatteryChargingFull,
                    shape = shape,
                    onClick = { onEvent(BatteryEvent.OpenSettings(SettingsTarget.BatteryUsage)) },
                )
            }
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.battery_saver_title),
                    icon = Icons.Rounded.BatterySaver,
                    shape = shape,
                    onClick = { onEvent(BatteryEvent.OpenSettings(SettingsTarget.BatterySaver)) },
                )
            }
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.battery_diag_diary),
                    supportingText = stringResource(R.string.battery_diag_diary_body),
                    icon = Icons.Rounded.History,
                    shape = shape,
                    onClick = onOpenMonitor,
                )
            }
        }
    }
}
