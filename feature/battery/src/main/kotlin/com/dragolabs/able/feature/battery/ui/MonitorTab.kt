package com.dragolabs.able.feature.battery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.database.MonitorNotificationLayout
import com.dragolabs.able.core.database.TemperatureUnit
import com.dragolabs.able.core.designsystem.R as DsR
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleSlider
import com.dragolabs.able.core.designsystem.component.AbleSegmentedButton
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleButtonFlow
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.ConfirmationDialog
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.ExpandableRow
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.MetricGrid
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.listGroupItemShape
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.battery.AlertKind
import com.dragolabs.able.feature.battery.BatteryEvent
import com.dragolabs.able.feature.battery.MonitorSettingsUi
import com.dragolabs.able.feature.battery.MonitorUiState
import com.dragolabs.able.feature.battery.R
import com.dragolabs.able.feature.battery.SessionFilter
import com.dragolabs.able.feature.battery.SessionItem
import com.dragolabs.able.feature.battery.data.SessionTag
import com.dragolabs.able.feature.battery.domain.BaselineStatus
import com.dragolabs.able.feature.battery.domain.BatteryFormat
import com.dragolabs.able.feature.battery.domain.IdleBaseline
import com.dragolabs.able.feature.battery.monitor.ResetReason
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun MonitorTab(
    state: MonitorUiState,
    contentPadding: PaddingValues,
    onEvent: (BatteryEvent) -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    BatteryCardGrid(contentPadding) {
        if (state.interrupted) {
            fullLine("interrupted") {
                StatusBanner(
                    level = StatusLevel.WARNING,
                    title = stringResource(R.string.battery_monitor_interrupted_title),
                    message = stringResource(R.string.battery_monitor_interrupted_body),
                    actionLabel = stringResource(R.string.battery_monitor_start),
                    onAction = { onEvent(BatteryEvent.StartMonitor) },
                    secondaryActionLabel = stringResource(R.string.battery_monitor_dismiss_interrupted),
                    onSecondaryAction = { onEvent(BatteryEvent.StopMonitor) },
                )
            }
        }
        if (state.showBaselineOffer) {
            fullLine("baseline-offer") {
                StatusBanner(
                    level = StatusLevel.INFO,
                    title = stringResource(R.string.battery_baseline_offer_title),
                    message = stringResource(R.string.battery_baseline_offer_body),
                    actionLabel = stringResource(R.string.battery_baseline_offer_enable),
                    onAction = { onEvent(BatteryEvent.EnableAlert(AlertKind.BASELINE)) },
                    secondaryActionLabel = stringResource(DsR.string.ds_not_now),
                    onSecondaryAction = { onEvent(BatteryEvent.DismissBaselineOffer) },
                )
            }
        }
        card("control") { MonitorControlCard(state, onEvent) }
        card("live") { LiveReadingsCard(state) }
        card("stats") { SinceResetCard(state, onEvent) }
        card("baseline") { BaselineCard(state.baseline, onOpenDiagnostics) }
        card("alerts") { AlertsCard(state.settings, onEvent) }
        card("reset") { ResetRulesCard(state.settings, onEvent) }
        card("display") { NotificationSettingsCard(state.settings, onEvent) }
        card("history") { HistorySettingsCard(state, onEvent) }
        fullLine("filter") { SessionFilterRow(state.filter, onEvent) }
        if (state.sessions.isEmpty()) {
            fullLine("empty") {
                EmptyState(
                    title = stringResource(R.string.battery_history_empty_title),
                    message = stringResource(R.string.battery_history_empty_body),
                    icon = Icons.Rounded.History,
                )
            }
        } else {
            state.sessions.forEach { item ->
                card("session-${item.session.id}") {
                    SessionCard(item, state.settings, onEdit = { onEvent(BatteryEvent.EditSession(item.session.id)) })
                }
            }
        }
    }

    state.editingSession?.let { item ->
        NoteEditorDialog(item, onEvent)
    }
    if (state.confirmDeleteHistory) {
        ConfirmationDialog(
            title = stringResource(R.string.battery_delete_confirm_title),
            message = stringResource(R.string.battery_delete_confirm_body),
            confirmLabel = stringResource(R.string.battery_delete_history),
            onConfirm = { onEvent(BatteryEvent.ConfirmDeleteHistory) },
            onDismiss = { onEvent(BatteryEvent.DismissDeleteHistory) },
            destructive = true,
            icon = Icons.Rounded.Delete,
        )
    }
}

@Composable
private fun MonitorControlCard(state: MonitorUiState, onEvent: (BatteryEvent) -> Unit) {
    val haptics = rememberAbleHaptics()
    val title = stringResource(R.string.battery_monitor_title)
    AbleCard(
        style = if (state.running) AbleCardStyle.Hero else AbleCardStyle.Standard,
        title = title,
        subtitle = stringResource(R.string.battery_monitor_description),
        icon = Icons.Rounded.MonitorHeart,
        iconShape = MaterialShapes.Cookie12Sided,
        trailing = { HelpButton(title = title, text = stringResource(R.string.battery_help_monitor)) },
        status = {
            StatusPill(
                text = stringResource(if (state.running) R.string.battery_monitor_running else R.string.battery_monitor_stopped),
                level = if (state.running) StatusLevel.SUCCESS else StatusLevel.INFO,
            )
        },
    ) {
        AbleButtonFlow {
            if (state.running) {
                AbleButton(
                    onClick = { haptics.toggle(false); onEvent(BatteryEvent.StopMonitor) },
                    modifier = Modifier.focusRing(),
                    haptic = AbleHaptic.None,
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.battery_monitor_stop), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                }
            } else {
                AbleButton(
                    onClick = { haptics.toggle(true); onEvent(BatteryEvent.StartMonitor) },
                    modifier = Modifier.focusRing(),
                    haptic = AbleHaptic.None,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.battery_monitor_start), modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                }
            }
            AbleOutlinedButton(onClick = { onEvent(BatteryEvent.ResetStatistics) }, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.battery_monitor_reset_now), maxLines = 1)
            }
        }
        val resumeTitle = stringResource(R.string.battery_monitor_resume_title)
        ExpandableRow(
            title = stringResource(R.string.battery_monitor_details_title),
            summary = stringResource(R.string.battery_monitor_details_summary),
            shape = listGroupItemShape(0, 1),
        ) {
            SupportingText(stringResource(R.string.battery_monitor_battery_note))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SupportingText(stringResource(R.string.battery_monitor_resume_note), modifier = Modifier.weight(1f))
                HelpButton(title = resumeTitle, text = stringResource(R.string.battery_help_resume))
            }
        }
    }
}

@Composable
private fun LiveReadingsCard(state: MonitorUiState) {
    val status = state.status
    val unit = state.settings.temperatureUnit
    AbleCard(
        title = stringResource(R.string.battery_live_title),
        subtitle = stateLabel(status),
        icon = Icons.Rounded.Speed,
        iconShape = MaterialShapes.Gem,
    ) {
        val current = status.currentMicroAmperes?.let { abs(it) / 1000 }
        MetricGrid(
            metrics = listOf(
                stringResource(R.string.battery_metric_level) to (status.levelPercent?.let { stringResource(R.string.battery_percent_value, it) } ?: unavailable()),
                stringResource(R.string.battery_metric_temperature) to temperatureText(status.temperatureCelsius, unit),
                stringResource(R.string.battery_metric_voltage) to (status.voltageMillivolts?.let { stringResource(R.string.battery_mv_value, it) } ?: unavailable()),
                stringResource(R.string.battery_metric_current) to (current?.let { stringResource(R.string.battery_ma_value, it) } ?: unavailable()),
                stringResource(R.string.battery_metric_power) to (status.powerMilliWatts?.let { stringResource(R.string.battery_w_estimate, BatteryFormat.decimal(it / 1000f, 2)) } ?: unavailable()),
                stringResource(R.string.battery_metric_time_to_full) to run {
                    val remaining = status.chargeTimeRemainingMs
                    when {
                        !status.isCharging -> stringResource(R.string.battery_not_charging_short)
                        remaining != null -> durationText(remaining)
                        else -> unavailable()
                    }
                },
            ),
        )
        ExpandableRow(
            title = stringResource(R.string.battery_live_details_title),
            shape = listGroupItemShape(0, 1),
        ) {
            SupportingText(stringResource(R.string.battery_accuracy_note))
        }
    }
}

@Composable
private fun resetReasonLabel(reason: ResetReason): String = stringResource(
    when (reason) {
        ResetReason.FIRST_START -> R.string.battery_reset_reason_first
        ResetReason.MANUAL -> R.string.battery_reset_reason_manual
        ResetReason.PLUG_IN -> R.string.battery_reset_reason_plug
        ResetReason.THRESHOLD -> R.string.battery_reset_reason_threshold
        ResetReason.REBOOT -> R.string.battery_reset_reason_reboot
        ResetReason.HISTORY_DELETED -> R.string.battery_reset_reason_deleted
    },
)

@Composable
private fun SinceResetCard(state: MonitorUiState, onEvent: (BatteryEvent) -> Unit) {
    val live = state.live
    AbleCard(
        title = stringResource(R.string.battery_stats_title),
        subtitle = live?.let {
            stringResource(
                R.string.battery_stats_since,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.sinceEpochMs)),
                resetReasonLabel(it.resetReason),
            )
        } ?: stringResource(R.string.battery_stats_waiting),
        icon = Icons.Rounded.Insights,
    ) {
        if (live != null) {
            MetricGrid(
                metrics = listOf(
                    stringResource(R.string.battery_screen_on) to stringResource(R.string.battery_duration_drop, durationText(live.screenOnMs), live.screenOnDropPercent),
                    stringResource(R.string.battery_screen_off) to stringResource(R.string.battery_duration_drop, durationText(live.screenOffMs), live.screenOffDropPercent),
                    stringResource(R.string.battery_active_drain) to rateText(com.dragolabs.able.feature.battery.domain.DrainMath.ratePerHour(live.screenOnDropPercent, live.screenOnMs)),
                    stringResource(R.string.battery_idle_drain) to rateText(com.dragolabs.able.feature.battery.domain.DrainMath.ratePerHour(live.screenOffDropPercent, live.screenOffMs)),
                    stringResource(R.string.battery_charged) to stringResource(R.string.battery_percent_points, live.chargedPercent),
                    stringResource(R.string.battery_deep_sleep) to unavailable(),
                ),
            )
        }
        state.currentSession?.let { session ->
            Text(
                stringResource(
                    if (session.isCharging) R.string.battery_current_session_charging else R.string.battery_current_session_discharging,
                    durationText(session.durationMs),
                    session.startLevel,
                    session.lastLevel,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ExpandableRow(
            title = stringResource(R.string.battery_stats_details_title),
            shape = listGroupItemShape(0, 1),
        ) {
            SupportingText(stringResource(R.string.battery_stats_note))
            SupportingText(stringResource(R.string.battery_deep_sleep_unavailable))
        }
    }
}

@Composable
private fun BaselineCard(baseline: IdleBaseline, onOpenDiagnostics: () -> Unit) {
    val statusText = stringResource(
        when (baseline.status) {
            BaselineStatus.COLLECTING -> R.string.battery_baseline_status_collecting
            BaselineStatus.INSUFFICIENT_COMPARABLE -> R.string.battery_baseline_status_insufficient
            BaselineStatus.ESTABLISHED -> R.string.battery_baseline_status_established
        },
    )
    AbleCard(
        style = AbleCardStyle.Tonal,
        title = stringResource(R.string.battery_baseline_title),
        subtitle = statusText,
        icon = Icons.Rounded.Insights,
        iconShape = MaterialShapes.Flower,
    ) {
        Text(stringResource(R.string.battery_baseline_valid_count, baseline.validSessionsInWindow), style = MaterialTheme.typography.bodyMedium)
        val latest = baseline.latestRatePercentPerHour
        if (latest != null) {
            Text(
                stringResource(R.string.battery_baseline_latest, BatteryFormat.decimal(latest), durationText(baseline.latestDurationMs)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        when (baseline.status) {
            BaselineStatus.COLLECTING -> Text(stringResource(R.string.battery_baseline_collecting), style = MaterialTheme.typography.bodyMedium)
            BaselineStatus.INSUFFICIENT_COMPARABLE ->
                Text(stringResource(R.string.battery_baseline_insufficient, baseline.comparableCount), style = MaterialTheme.typography.bodyMedium)
            BaselineStatus.ESTABLISHED -> {
                Text(
                    stringResource(
                        R.string.battery_baseline_range,
                        BatteryFormat.decimal(baseline.medianRatePercentPerHour ?: 0f),
                        BatteryFormat.decimal(baseline.minimumRatePercentPerHour ?: 0f),
                        BatteryFormat.decimal(baseline.maximumRatePercentPerHour ?: 0f),
                        baseline.comparableCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (baseline.latestAboveRange) {
                    StatusPill(text = stringResource(R.string.battery_baseline_above_range), level = StatusLevel.WARNING)
                }
            }
        }
        TrendBars(baseline.trend)
        Text(stringResource(R.string.battery_baseline_method), style = MaterialTheme.typography.labelSmall)
        AbleTextButton(onClick = onOpenDiagnostics, modifier = Modifier.focusRing()) {
            Text(stringResource(R.string.battery_baseline_review_factors))
        }
    }
}

@Composable
private fun AlertsCard(settings: MonitorSettingsUi, onEvent: (BatteryEvent) -> Unit) {
    var idleThreshold by remember(settings.idleAlertThreshold) { mutableFloatStateOf(settings.idleAlertThreshold) }
    var minDiff by remember(settings.baselineMinDifference) { mutableFloatStateOf(settings.baselineMinDifference) }
    val idleTitle = stringResource(R.string.battery_alert_idle_toggle)
    val idleBody = stringResource(R.string.battery_alert_idle_toggle_body)
    val baselineTitle = stringResource(R.string.battery_alert_baseline_toggle)
    val baselineBody = stringResource(R.string.battery_alert_baseline_toggle_body)
    AbleCard(
        title = stringResource(R.string.battery_alerts_title),
        subtitle = stringResource(R.string.battery_alerts_body),
        icon = Icons.Rounded.NotificationsActive,
    ) {
        ListGroup {
            item { shape ->
                SwitchRow(
                    title = idleTitle,
                    supportingText = idleBody,
                    checked = settings.idleAlertEnabled,
                    onCheckedChange = { enable ->
                        onEvent(if (enable) BatteryEvent.EnableAlert(AlertKind.IDLE_THRESHOLD) else BatteryEvent.DisableAlert(AlertKind.IDLE_THRESHOLD))
                    },
                    shape = shape,
                )
            }
            item { shape ->
                SwitchRow(
                    title = baselineTitle,
                    supportingText = baselineBody,
                    checked = settings.baselineAlertEnabled,
                    onCheckedChange = { enable ->
                        onEvent(if (enable) BatteryEvent.EnableAlert(AlertKind.BASELINE) else BatteryEvent.DisableAlert(AlertKind.BASELINE))
                    },
                    shape = shape,
                )
            }
        }
        Text(stringResource(R.string.battery_alert_idle_threshold, BatteryFormat.decimal(idleThreshold)), style = MaterialTheme.typography.bodyMedium)
        AbleSlider(
            value = idleThreshold,
            onValueChange = { idleThreshold = (it * 2).roundToInt() / 2f },
            onValueChangeFinished = { onEvent(BatteryEvent.SetIdleAlertThreshold(idleThreshold)) },
            valueRange = 0.5f..10f,
            steps = 18,
        )
        Text(stringResource(R.string.battery_alert_min_difference, BatteryFormat.decimal(minDiff)), style = MaterialTheme.typography.bodyMedium)
        AbleSlider(
            value = minDiff,
            onValueChange = { minDiff = (it * 10).roundToInt() / 10f },
            onValueChangeFinished = { onEvent(BatteryEvent.SetBaselineMinDifference(minDiff)) },
            valueRange = 0.1f..2f,
            steps = 18,
        )
        SupportingText(stringResource(R.string.battery_alerts_window_note))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResetRulesCard(settings: MonitorSettingsUi, onEvent: (BatteryEvent) -> Unit) {
    var thermal by remember(settings.thermalThresholdCelsius) { mutableFloatStateOf(settings.thermalThresholdCelsius) }
    val plugTitle = stringResource(R.string.battery_reset_on_plug)
    val plugBody = stringResource(R.string.battery_reset_on_plug_body)
    val bootTitle = stringResource(R.string.battery_reset_on_boot)
    val bootBody = stringResource(R.string.battery_reset_on_boot_body)
    AbleCard(
        title = stringResource(R.string.battery_reset_title),
        subtitle = stringResource(R.string.battery_reset_body),
        icon = Icons.Rounded.RestartAlt,
        trailing = { HelpButton(title = stringResource(R.string.battery_reset_title), text = stringResource(R.string.battery_help_reset_rules)) },
    ) {
        ListGroup {
            item { shape ->
                SwitchRow(title = plugTitle, supportingText = plugBody, checked = settings.resetOnPlug, onCheckedChange = { onEvent(BatteryEvent.SetResetOnPlug(it)) }, shape = shape)
            }
            item { shape ->
                SwitchRow(title = bootTitle, supportingText = bootBody, checked = settings.resetOnBoot, onCheckedChange = { onEvent(BatteryEvent.SetResetOnBoot(it)) }, shape = shape)
            }
        }
        Text(stringResource(R.string.battery_reset_threshold), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            listOf<Int?>(null, 20, 30, 50).forEach { value ->
                val selected = settings.resetThresholdPercent == value
                AbleFilterChip(
                    selected = selected,
                    onClick = { onEvent(BatteryEvent.SetResetThreshold(value)) },
                    label = { Text(value?.let { stringResource(R.string.battery_percent_value, it) } ?: stringResource(R.string.battery_reset_threshold_off)) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                )
            }
        }
        Text(
            stringResource(R.string.battery_thermal_threshold, temperatureText(thermal, settings.temperatureUnit, digits = 0)),
            style = MaterialTheme.typography.bodyMedium,
        )
        AbleSlider(
            value = thermal,
            onValueChange = { thermal = it.roundToInt().toFloat() },
            onValueChangeFinished = { onEvent(BatteryEvent.SetThermalThreshold(thermal)) },
            valueRange = 40f..60f,
            steps = 19,
        )
        SupportingText(stringResource(R.string.battery_thermal_note))
    }
}

@Composable
private fun NotificationSettingsCard(settings: MonitorSettingsUi, onEvent: (BatteryEvent) -> Unit) {
    val powerTitle = stringResource(R.string.battery_notif_show_power)
    val powerBody = stringResource(R.string.battery_notif_show_power_body)
    AbleCard(
        title = stringResource(R.string.battery_outputs_title),
        subtitle = stringResource(R.string.battery_outputs_body),
        icon = Icons.Rounded.Tune,
    ) {
        Text(stringResource(R.string.battery_temperature_unit), style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val units = TemperatureUnit.entries
            units.forEachIndexed { index, unit ->
                AbleSegmentedButton(
                    selected = settings.temperatureUnit == unit,
                    onClick = { onEvent(BatteryEvent.SetTemperatureUnit(unit)) },
                    shape = SegmentedButtonDefaults.itemShape(index, units.size),
                    label = {
                        Text(stringResource(if (unit == TemperatureUnit.CELSIUS) R.string.battery_unit_celsius_name else R.string.battery_unit_fahrenheit_name))
                    },
                )
            }
        }
        Text(stringResource(R.string.battery_notif_layout), style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val layouts = MonitorNotificationLayout.entries
            layouts.forEachIndexed { index, layout ->
                AbleSegmentedButton(
                    selected = settings.notificationLayout == layout,
                    onClick = { onEvent(BatteryEvent.SetNotificationLayout(layout)) },
                    shape = SegmentedButtonDefaults.itemShape(index, layouts.size),
                    label = {
                        Text(stringResource(if (layout == MonitorNotificationLayout.COMPACT) R.string.battery_notif_layout_compact else R.string.battery_notif_layout_detailed))
                    },
                )
            }
        }
        ListGroup {
            item { shape ->
                SwitchRow(
                    title = powerTitle,
                    supportingText = powerBody,
                    checked = settings.notificationShowPower,
                    onCheckedChange = { onEvent(BatteryEvent.SetNotificationShowPower(it)) },
                    shape = shape,
                )
            }
        }
        SupportingText(stringResource(R.string.battery_widget_note))
    }
}

@Composable
private fun HistorySettingsCard(state: MonitorUiState, onEvent: (BatteryEvent) -> Unit) {
    val options = listOf(
        30 to stringResource(R.string.battery_retention_30),
        90 to stringResource(R.string.battery_retention_90),
        -1 to stringResource(R.string.battery_retention_manual),
    )
    AbleCard(
        title = stringResource(R.string.battery_history_title),
        subtitle = stringResource(R.string.battery_sessions_count, state.totalSessions),
        icon = Icons.Rounded.History,
    ) {
        Text(stringResource(R.string.battery_retention), style = MaterialTheme.typography.bodyMedium)
        ListGroup {
            options.forEach { (days, label) ->
                item { shape ->
                    RadioRow(
                        title = label,
                        selected = state.settings.retentionDays == days,
                        onSelect = { onEvent(BatteryEvent.SetRetention(days)) },
                        shape = shape,
                    )
                }
            }
        }
        ExpandableRow(
            title = stringResource(R.string.battery_retention_more_title),
            summary = stringResource(R.string.battery_retention_more_summary),
            shape = listGroupItemShape(0, 1),
        ) {
            Text(stringResource(R.string.battery_retention_note), style = MaterialTheme.typography.bodyMedium)
        }
        val deleteTitle = stringResource(R.string.battery_delete_history)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AbleOutlinedButton(
                onClick = { onEvent(BatteryEvent.RequestDeleteHistory) },
                enabled = state.totalSessions > 0 || state.live != null,
                modifier = Modifier.weight(1f, fill = false).focusRing(),
                haptic = AbleHaptic.Reject,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(deleteTitle, modifier = Modifier.padding(start = 8.dp))
            }
            HelpButton(title = deleteTitle, text = stringResource(R.string.battery_help_delete_history))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionFilterRow(filter: SessionFilter, onEvent: (BatteryEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
        com.dragolabs.able.core.designsystem.component.SectionHeader(stringResource(R.string.battery_sessions_header))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            SessionFilter.entries.forEach { option ->
                AbleFilterChip(
                    selected = filter == option,
                    onClick = { onEvent(BatteryEvent.SetSessionFilter(option)) },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    SessionFilter.ALL -> R.string.battery_filter_all
                                    SessionFilter.DISCHARGE -> R.string.battery_filter_discharge
                                    SessionFilter.CHARGING -> R.string.battery_filter_charging
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun tagLabel(tag: SessionTag): String = stringResource(
    when (tag) {
        SessionTag.TRAVEL -> R.string.battery_tag_travel
        SessionTag.GAMING -> R.string.battery_tag_gaming
        SessionTag.HOTSPOT -> R.string.battery_tag_hotspot
        SessionTag.NAVIGATION -> R.string.battery_tag_navigation
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionCard(item: SessionItem, settings: MonitorSettingsUi, onEdit: (() -> Unit)?) {
    val session = item.session
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.startTimeEpochMs))
    val kind = stringResource(if (session.isCharging) R.string.battery_session_charging else R.string.battery_session_discharge)
    AbleCard(
        title = date,
        subtitle = stringResource(R.string.battery_session_summary, kind, session.startLevelPercent, session.endLevelPercent, durationText(item.rates.durationMs)),
        onClick = onEdit,
        status = if (session.isPartial || session.hasThermalAlert) {
            {
                if (session.isPartial) StatusPill(stringResource(R.string.battery_session_partial), StatusLevel.WARNING)
                if (session.hasThermalAlert) StatusPill(stringResource(R.string.battery_session_warm), StatusLevel.CRITICAL)
            }
        } else null,
    ) {
        val metrics = buildList {
            if (session.isCharging) {
                add(stringResource(R.string.battery_charge_rate) to rateText(item.rates.overallRatePerHour))
            } else {
                add(stringResource(R.string.battery_overall_drain) to rateText(item.rates.overallRatePerHour))
                add(
                    stringResource(R.string.battery_screen_on) to (
                        session.screenOnDropPercent?.let { stringResource(R.string.battery_duration_drop, durationText(session.screenOnDurationMs), it) }
                            ?: durationText(session.screenOnDurationMs)
                        ),
                )
                add(
                    stringResource(R.string.battery_screen_off) to (
                        session.screenOffDropPercent?.let { stringResource(R.string.battery_duration_drop, durationText(session.screenOffDurationMs), it) }
                            ?: durationText(session.screenOffDurationMs)
                        ),
                )
                add(stringResource(R.string.battery_active_drain) to rateText(item.rates.activeRatePerHour))
                add(stringResource(R.string.battery_idle_drain) to rateText(item.rates.idleRatePerHour))
            }
            if (session.avgTemperatureCelsius > 0f) {
                add(
                    stringResource(R.string.battery_temp_avg_peak) to stringResource(
                        R.string.battery_two_values,
                        temperatureText(session.avgTemperatureCelsius, settings.temperatureUnit),
                        temperatureText(session.maxTemperatureCelsius, settings.temperatureUnit),
                    ),
                )
            }
            session.avgCurrentMilliAmperes?.let { add(stringResource(R.string.battery_avg_current) to stringResource(R.string.battery_ma_value, it.roundToInt())) }
            session.avgVoltageMillivolts?.let { add(stringResource(R.string.battery_avg_voltage) to stringResource(R.string.battery_mv_value, it.roundToInt())) }
            session.avgPowerMilliWatts?.let { add(stringResource(R.string.battery_avg_power) to stringResource(R.string.battery_w_estimate, BatteryFormat.decimal(it / 1000f, 2))) }
            add(stringResource(R.string.battery_deep_sleep) to unavailable())
        }
        MetricGrid(metrics)
        val note = item.note
        if (note != null) {
            if (note.text.isNotBlank()) Text(note.text, style = MaterialTheme.typography.bodyMedium)
            if (note.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                    note.tags.forEach { StatusPill(tagLabel(it), StatusLevel.INFO) }
                }
            }
        } else if (onEdit != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.battery_note_add), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteEditorDialog(item: SessionItem, onEvent: (BatteryEvent) -> Unit) {
    val id = item.session.id
    var text by rememberSaveable(id) { mutableStateOf(item.note?.text.orEmpty()) }
    var tags by remember(id) { mutableStateOf(item.note?.tags.orEmpty().map { it.name }.toSet()) }
    AlertDialog(
        onDismissRequest = { onEvent(BatteryEvent.DismissNoteEditor) },
        title = { Text(stringResource(R.string.battery_note_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.session.startTimeEpochMs)))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(500) },
                    label = { Text(stringResource(R.string.battery_note_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.battery_note_tags), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                    SessionTag.entries.forEach { tag ->
                        val selected = tag.name in tags
                        AbleFilterChip(
                            selected = selected,
                            onClick = { tags = if (selected) tags - tag.name else tags + tag.name },
                            label = { Text(tagLabel(tag)) },
                        )
                    }
                }
                SupportingText(stringResource(R.string.battery_note_privacy))
            }
        },
        confirmButton = {
            AbleTextButton(onClick = {
                onEvent(BatteryEvent.SaveNote(id, text, tags.mapNotNull { name -> SessionTag.entries.firstOrNull { it.name == name } }.toSet()))
            }) { Text(stringResource(DsR.string.ds_save)) }
        },
        dismissButton = {
            AbleTextButton(onClick = { onEvent(BatteryEvent.DismissNoteEditor) }) { Text(stringResource(DsR.string.ds_cancel)) }
        },
    )
}
