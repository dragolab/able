package com.dragolabs.able.feature.battery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.database.MonitorNotificationLayout
import com.dragolabs.able.core.database.TemperatureUnit
import com.dragolabs.able.core.designsystem.R as DsR
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleFilledTonalButton
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleSegmentedButton
import com.dragolabs.able.core.designsystem.component.AbleSlider
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleToggleButton
import com.dragolabs.able.core.designsystem.component.ConfirmationDialog
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.ExpandableRow
import com.dragolabs.able.core.designsystem.component.ExpressiveIcon
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.hapticClickable
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
import com.dragolabs.able.feature.battery.domain.DrainMath
import com.dragolabs.able.feature.battery.domain.IdleBaseline
import com.dragolabs.able.feature.battery.monitor.ResetReason
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Monitor tab, organised around the user's questions (owner request 2026-11-23):
 * "Is it on and what is happening now?" → "How fast does it drain?" → "What happened when?" →
 * settings, collapsed. Explanations live in help buttons instead of the page.
 */
@Composable
internal fun MonitorTab(
    state: MonitorUiState,
    contentPadding: PaddingValues,
    onEvent: (BatteryEvent) -> Unit,
    gridState: LazyStaggeredGridState,
    onOpenDiagnostics: () -> Unit,
) {
    var detailSessionId by rememberSaveable { mutableStateOf<Long?>(null) }

    BatteryCardGrid(contentPadding, state = gridState) {
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
        card("live") { LiveMonitorCard(state, onEvent) }
        card("stats") { StatisticsCard(state, onEvent) }
        card("baseline") { BaselineCard(state.baseline, onOpenDiagnostics) }

        fullLine("sessions-header") { SessionsHeader(state, onEvent) }
        if (state.sessions.isEmpty()) {
            fullLine("sessions-empty") {
                EmptyState(
                    title = stringResource(R.string.battery_history_empty_title),
                    message = stringResource(R.string.battery_history_empty_body),
                    icon = Icons.Rounded.History,
                )
            }
        } else {
            state.sessions.forEach { item ->
                card("session-${item.session.id}") {
                    SessionRow(item, onClick = { detailSessionId = item.session.id })
                }
            }
        }

        fullLine("settings") { MonitorSettingsSection(state, onEvent) }
    }

    val detail = detailSessionId?.let { id -> state.sessions.firstOrNull { it.session.id == id } }
    // The session was deleted (retention, history deleted): close the sheet.
    LaunchedEffect(detailSessionId, detail) {
        if (detailSessionId != null && detail == null) detailSessionId = null
    }
    if (detail != null) {
        SessionDetailSheet(
            item = detail,
            settings = state.settings,
            onDismiss = { detailSessionId = null },
            onEditNote = {
                detailSessionId = null
                onEvent(BatteryEvent.EditSession(detail.session.id))
            },
        )
    }
}

/** Dialogs driven by the monitor ViewModel; hosted by [BatteryScreen] outside the pager. */
@Composable
internal fun MonitorDialogs(state: MonitorUiState, onEvent: (BatteryEvent) -> Unit) {
    state.editingSession?.let { item -> NoteEditorDialog(item, onEvent) }
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

// ---------------------------------------------------------------------------------------------
// Live status

@Composable
private fun LiveMonitorCard(state: MonitorUiState, onEvent: (BatteryEvent) -> Unit) {
    val haptics = rememberAbleHaptics()
    val status = state.status
    val title = stringResource(R.string.battery_monitor_title)
    val help = listOf(
        stringResource(R.string.battery_help_monitor),
        stringResource(R.string.battery_help_resume),
        stringResource(R.string.battery_accuracy_note),
    ).joinToString("\n\n")
    AbleCard(
        style = if (state.running) AbleCardStyle.Hero else AbleCardStyle.Standard,
        title = title,
        icon = Icons.Rounded.MonitorHeart,
        iconShape = MaterialShapes.Cookie12Sided,
        trailing = { HelpButton(title = title, text = help) },
        status = {
            StatusPill(
                text = stringResource(if (state.running) R.string.battery_monitor_running else R.string.battery_monitor_stopped),
                level = if (state.running) StatusLevel.SUCCESS else StatusLevel.INFO,
            )
            StatusPill(text = stateLabel(status), level = StatusLevel.INFO)
        },
    ) {
        // One big toggle: the label says what a tap does.
        val buttonHeight = 56.dp
        AbleToggleButton(
            checked = state.running,
            onCheckedChange = { on ->
                haptics.toggle(on)
                onEvent(if (on) BatteryEvent.StartMonitor else BatteryEvent.StopMonitor)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = buttonHeight)
                .focusRing(),
            shapes = ToggleButtonDefaults.shapesFor(buttonHeight),
            haptic = AbleHaptic.None,
        ) {
            Icon(
                if (state.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                stringResource(if (state.running) R.string.battery_monitor_stop_long else R.string.battery_monitor_start),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = AbleSpacing.sm),
            )
        }
        if (!state.running) {
            SupportingText(stringResource(R.string.battery_monitor_off_hint))
        }

        val currentMa = status.currentMicroAmperes?.let { abs(it) / 1000 }
        val power = status.powerMilliWatts?.let { stringResource(R.string.battery_w_estimate, BatteryFormat.decimal(it / 1000f, 2)) }
        val today = state.screenToday
        val notRecorded = stringResource(R.string.battery_not_recorded)
        val metrics = buildList {
            add(
                Metric(
                    label = stringResource(if (status.isCharging) R.string.battery_live_charging_current else R.string.battery_live_current_label),
                    value = currentMa?.let { stringResource(R.string.battery_ma_value, it) } ?: unavailable(),
                    supporting = power ?: stringResource(R.string.battery_estimate),
                ),
            )
            add(
                Metric(
                    label = stringResource(R.string.battery_metric_level),
                    value = status.levelPercent?.let { stringResource(R.string.battery_percent_value, it) } ?: unavailable(),
                    supporting = temperatureText(status.temperatureCelsius, state.settings.temperatureUnit),
                ),
            )
            add(
                Metric(
                    label = stringResource(R.string.battery_screen_on_today),
                    value = today?.let { durationText(it.screenOnMs) } ?: notRecorded,
                    supporting = today?.let { stringResource(R.string.battery_today_estimate) },
                ),
            )
            add(
                Metric(
                    label = stringResource(R.string.battery_screen_off_today),
                    value = today?.let { durationText(it.screenOffMs) } ?: notRecorded,
                    supporting = today?.let { stringResource(R.string.battery_today_estimate) },
                ),
            )
            if (status.isCharging) {
                add(
                    Metric(
                        label = stringResource(R.string.battery_metric_time_to_full),
                        value = status.chargeTimeRemainingMs?.let { durationText(it) } ?: unavailable(),
                    ),
                )
            }
        }
        MetricTiles(metrics)

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
        if (status.timestampEpochMs > 0) {
            SupportingText(
                stringResource(
                    R.string.battery_last_update,
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(status.timestampEpochMs)),
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Statistics since reset

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
private fun StatisticsCard(state: MonitorUiState, onEvent: (BatteryEvent) -> Unit) {
    val live = state.live
    val title = stringResource(R.string.battery_stats_title)
    val help = listOf(
        stringResource(R.string.battery_stats_note),
        stringResource(R.string.battery_deep_sleep_unavailable),
    ).joinToString("\n\n")
    AbleCard(
        title = title,
        subtitle = live?.let {
            stringResource(
                R.string.battery_stats_since,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.sinceEpochMs)),
                resetReasonLabel(it.resetReason),
            )
        } ?: stringResource(R.string.battery_stats_waiting),
        icon = Icons.Rounded.Insights,
        trailing = { HelpButton(title = title, text = help) },
    ) {
        if (live != null) {
            MetricTiles(
                listOf(
                    Metric(
                        label = stringResource(R.string.battery_active_drain),
                        value = rateShort(DrainMath.ratePerHour(live.screenOnDropPercent, live.screenOnMs)),
                        supporting = stringResource(R.string.battery_duration_drop, durationText(live.screenOnMs), live.screenOnDropPercent),
                    ),
                    Metric(
                        label = stringResource(R.string.battery_idle_drain),
                        value = rateShort(DrainMath.ratePerHour(live.screenOffDropPercent, live.screenOffMs)),
                        supporting = stringResource(R.string.battery_duration_drop, durationText(live.screenOffMs), live.screenOffDropPercent),
                    ),
                    chargingSpeedMetric(state),
                    Metric(
                        label = stringResource(R.string.battery_charged),
                        value = stringResource(R.string.battery_percent_points, live.chargedPercent),
                    ),
                ),
            )
        }
        AbleOutlinedButton(
            onClick = { onEvent(BatteryEvent.ResetStatistics) },
            enabled = live != null,
            modifier = Modifier.focusRing(),
        ) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.battery_monitor_reset_now), modifier = Modifier.padding(start = AbleSpacing.sm))
        }
    }
}

/** Charging speed: the running charging session when there is one, otherwise the last saved one. */
@Composable
private fun chargingSpeedMetric(state: MonitorUiState): Metric {
    val label = stringResource(R.string.battery_charging_speed)
    val current = state.currentSession?.takeIf { it.isCharging }
    if (current != null) {
        return Metric(
            label = label,
            value = rateShort(DrainMath.ratePerHour(current.lastLevel - current.startLevel, current.durationMs)),
            supporting = stringResource(R.string.battery_charging_speed_now),
        )
    }
    val last = state.latestCharging
    return if (last != null) {
        Metric(
            label = label,
            value = rateShort(last.rates.overallRatePerHour),
            supporting = stringResource(
                R.string.battery_charging_speed_last,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(last.session.startTimeEpochMs)),
            ),
        )
    } else {
        Metric(label = label, value = stringResource(R.string.battery_not_enough_data))
    }
}

// ---------------------------------------------------------------------------------------------
// Idle baseline (BASE-001)

@Composable
private fun BaselineCard(baseline: IdleBaseline, onOpenDiagnostics: () -> Unit) {
    val title = stringResource(R.string.battery_baseline_title)
    AbleCard(
        style = AbleCardStyle.Tonal,
        title = title,
        subtitle = stringResource(
            when (baseline.status) {
                BaselineStatus.COLLECTING -> R.string.battery_baseline_status_collecting
                BaselineStatus.INSUFFICIENT_COMPARABLE -> R.string.battery_baseline_status_insufficient
                BaselineStatus.ESTABLISHED -> R.string.battery_baseline_status_established
            },
        ),
        icon = Icons.Rounded.Insights,
        iconShape = MaterialShapes.Flower,
        trailing = { HelpButton(title = title, text = stringResource(R.string.battery_baseline_method)) },
        status = if (baseline.status == BaselineStatus.ESTABLISHED && baseline.latestAboveRange) {
            { StatusPill(text = stringResource(R.string.battery_baseline_above_range), level = StatusLevel.WARNING) }
        } else {
            null
        },
    ) {
        val summary = when (baseline.status) {
            BaselineStatus.COLLECTING -> stringResource(R.string.battery_baseline_collecting)
            BaselineStatus.INSUFFICIENT_COMPARABLE -> stringResource(R.string.battery_baseline_insufficient, baseline.comparableCount)
            BaselineStatus.ESTABLISHED -> stringResource(
                R.string.battery_baseline_range,
                BatteryFormat.decimal(baseline.medianRatePercentPerHour ?: 0f),
                BatteryFormat.decimal(baseline.minimumRatePercentPerHour ?: 0f),
                BatteryFormat.decimal(baseline.maximumRatePercentPerHour ?: 0f),
                baseline.comparableCount,
            )
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium)
        val latest = baseline.latestRatePercentPerHour
        if (latest != null) {
            Text(
                stringResource(R.string.battery_baseline_latest, BatteryFormat.decimal(latest), durationText(baseline.latestDurationMs)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TrendBars(baseline.trend)
        AbleTextButton(onClick = onOpenDiagnostics, modifier = Modifier.focusRing()) {
            Text(stringResource(R.string.battery_baseline_review_factors))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Sessions timeline (MON-009, BAT-CHG-001, BAT-NOTE-001)

@Composable
private fun SessionsHeader(state: MonitorUiState, onEvent: (BatteryEvent) -> Unit) {
    Column(
        modifier = Modifier.padding(top = AbleSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
    ) {
        SectionHeader(stringResource(R.string.battery_sessions_header))
        SupportingText(stringResource(R.string.battery_sessions_count, state.totalSessions))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
        ) {
            SessionFilter.entries.forEach { option ->
                val selected = state.filter == option
                AbleFilterChip(
                    selected = selected,
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
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
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

/** One timeline row: kind and time, a mini bar of the level range, levels, duration and rate. */
@Composable
private fun SessionRow(item: SessionItem, onClick: () -> Unit) {
    val session = item.session
    val shape = MaterialTheme.shapes.large
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.startTimeEpochMs))
    val kind = stringResource(if (session.isCharging) R.string.battery_session_charging else R.string.battery_session_discharge)
    val detail = listOf(
        stringResource(R.string.battery_session_levels, session.startLevelPercent, session.endLevelPercent),
        durationText(item.rates.durationMs),
        rateShort(item.rates.overallRatePerHour),
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .hapticClickable(onClickLabel = stringResource(R.string.battery_session_open_details), role = Role.Button, onClick = onClick)
            .focusRing(shape)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = AbleSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                ExpressiveIcon(
                    icon = if (session.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                    shape = if (session.isCharging) MaterialShapes.Cookie6Sided else MaterialShapes.Circle,
                    containerColor = if (session.isCharging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (session.isCharging) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                    size = 32.dp,
                    iconSize = 18.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(kind, style = MaterialTheme.typography.titleSmall)
                    Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LevelRangeBar(session.startLevelPercent, session.endLevelPercent, charging = session.isCharging)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (session.isPartial || session.hasThermalAlert || item.note != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
                ) {
                    if (session.isPartial) StatusPill(stringResource(R.string.battery_session_partial), StatusLevel.WARNING)
                    if (session.hasThermalAlert) StatusPill(stringResource(R.string.battery_session_warm), StatusLevel.CRITICAL)
                    if (item.note != null) StatusPill(stringResource(R.string.battery_session_has_note), StatusLevel.INFO)
                }
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
    }
}

/** Track 0–100 % with the session's level range filled (charging: primary, on battery: tertiary). */
@Composable
private fun LevelRangeBar(start: Int, end: Int, charging: Boolean) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = if (charging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val low = minOf(start, end).coerceIn(0, 100)
    val high = maxOf(start, end).coerceIn(0, 100)
    // Decorative: the levels are also written out in the row text.
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = track, size = size, cornerRadius = radius)
        val left = size.width * low / 100f
        val width = (size.width * (high - low) / 100f).coerceAtLeast(size.height)
        drawRoundRect(
            color = fill,
            topLeft = Offset(left.coerceAtMost(size.width - width), 0f),
            size = Size(width, size.height),
            cornerRadius = radius,
        )
    }
}

@Composable
private fun SessionDetailSheet(
    item: SessionItem,
    settings: MonitorSettingsUi,
    onDismiss: () -> Unit,
    onEditNote: () -> Unit,
) {
    val session = item.session
    val kind = stringResource(if (session.isCharging) R.string.battery_session_charging else R.string.battery_session_discharge)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = AbleSpacing.screenGutter, end = AbleSpacing.screenGutter, bottom = AbleSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
        ) {
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.startTimeEpochMs)),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.battery_session_summary, kind, session.startLevelPercent, session.endLevelPercent, durationText(item.rates.durationMs)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LevelRangeBar(session.startLevelPercent, session.endLevelPercent, charging = session.isCharging)
            if (session.isPartial || session.hasThermalAlert) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                    if (session.isPartial) StatusPill(stringResource(R.string.battery_session_partial), StatusLevel.WARNING)
                    if (session.hasThermalAlert) StatusPill(stringResource(R.string.battery_session_warm), StatusLevel.CRITICAL)
                }
            }
            MetricTiles(sessionMetrics(item, settings))
            val note = item.note
            if (note != null) {
                SectionTitle(stringResource(R.string.battery_note_title))
                if (note.text.isNotBlank()) Text(note.text, style = MaterialTheme.typography.bodyMedium)
                if (note.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                        note.tags.forEach { StatusPill(tagLabel(it), StatusLevel.INFO) }
                    }
                }
            }
            AbleFilledTonalButton(onClick = onEditNote, modifier = Modifier.focusRing()) {
                Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(if (note != null) R.string.battery_note_edit else R.string.battery_note_add),
                    modifier = Modifier.padding(start = AbleSpacing.sm),
                )
            }
            SupportingText(stringResource(R.string.battery_accuracy_note))
        }
    }
}

@Composable
private fun sessionMetrics(item: SessionItem, settings: MonitorSettingsUi): List<Metric> {
    val session = item.session
    return buildList {
        if (session.isCharging) {
            add(Metric(stringResource(R.string.battery_charge_rate), rateShort(item.rates.overallRatePerHour), stringResource(R.string.battery_estimate)))
        } else {
            add(Metric(stringResource(R.string.battery_overall_drain), rateShort(item.rates.overallRatePerHour), stringResource(R.string.battery_estimate)))
            add(Metric(stringResource(R.string.battery_active_drain), rateShort(item.rates.activeRatePerHour), stringResource(R.string.battery_estimate)))
            add(Metric(stringResource(R.string.battery_idle_drain), rateShort(item.rates.idleRatePerHour), stringResource(R.string.battery_estimate)))
        }
        add(
            Metric(
                stringResource(R.string.battery_screen_on),
                durationText(session.screenOnDurationMs),
                session.screenOnDropPercent?.let { stringResource(R.string.battery_percent_drop, it) },
            ),
        )
        add(
            Metric(
                stringResource(R.string.battery_screen_off),
                durationText(session.screenOffDurationMs),
                session.screenOffDropPercent?.let { stringResource(R.string.battery_percent_drop, it) },
            ),
        )
        if (session.avgTemperatureCelsius > 0f) {
            add(
                Metric(
                    stringResource(R.string.battery_temp_avg_peak),
                    stringResource(
                        R.string.battery_two_values,
                        temperatureText(session.avgTemperatureCelsius, settings.temperatureUnit),
                        temperatureText(session.maxTemperatureCelsius, settings.temperatureUnit),
                    ),
                ),
            )
        }
        session.avgCurrentMilliAmperes?.let { add(Metric(stringResource(R.string.battery_avg_current), stringResource(R.string.battery_ma_value, it.roundToInt()))) }
        session.avgVoltageMillivolts?.let { add(Metric(stringResource(R.string.battery_avg_voltage), stringResource(R.string.battery_mv_value, it.roundToInt()))) }
        session.avgPowerMilliWatts?.let { add(Metric(stringResource(R.string.battery_avg_power), stringResource(R.string.battery_w_estimate, BatteryFormat.decimal(it / 1000f, 2)))) }
    }
}

@Composable
private fun NoteEditorDialog(item: SessionItem, onEvent: (BatteryEvent) -> Unit) {
    val id = item.session.id
    var text by rememberSaveable(id) { mutableStateOf(item.note?.text.orEmpty()) }
    var tags by remember(id) { mutableStateOf(item.note?.tags.orEmpty().map { it.name }.toSet()) }
    AlertDialog(
        onDismissRequest = { onEvent(BatteryEvent.DismissNoteEditor) },
        title = { Text(stringResource(R.string.battery_note_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            ) {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.session.startTimeEpochMs)))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(500) },
                    label = { Text(stringResource(R.string.battery_note_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.battery_note_tags), style = MaterialTheme.typography.bodySmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
                ) {
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
            AbleTextButton(
                onClick = {
                    onEvent(BatteryEvent.SaveNote(id, text, tags.mapNotNull { name -> SessionTag.entries.firstOrNull { it.name == name } }.toSet()))
                },
                haptic = AbleHaptic.Confirm,
            ) { Text(stringResource(DsR.string.ds_save)) }
        },
        dismissButton = {
            AbleTextButton(onClick = { onEvent(BatteryEvent.DismissNoteEditor) }) { Text(stringResource(DsR.string.ds_cancel)) }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Monitor settings: one collapsed group, each row summarising its current value.

@Composable
private fun MonitorSettingsSection(state: MonitorUiState, onEvent: (BatteryEvent) -> Unit) {
    val settings = state.settings
    Column(
        modifier = Modifier.padding(top = AbleSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
    ) {
        SectionHeader(stringResource(R.string.battery_settings_title))
        ListGroup {
            item { shape ->
                ExpandableRow(
                    title = stringResource(R.string.battery_settings_notification),
                    summary = notificationSummary(settings),
                    icon = Icons.Rounded.Tune,
                    shape = shape,
                ) { NotificationSettings(settings, onEvent) }
            }
            item { shape ->
                ExpandableRow(
                    title = stringResource(R.string.battery_temperature_unit),
                    summary = stringResource(unitNameRes(settings.temperatureUnit)),
                    icon = Icons.Rounded.Thermostat,
                    shape = shape,
                ) { TemperatureUnitSettings(settings, onEvent) }
            }
            item { shape ->
                ExpandableRow(
                    title = stringResource(R.string.battery_reset_title),
                    summary = resetSummary(settings),
                    icon = Icons.Rounded.RestartAlt,
                    shape = shape,
                ) { ResetRulesSettings(settings, onEvent) }
            }
            item { shape ->
                ExpandableRow(
                    title = stringResource(R.string.battery_alerts_title),
                    summary = alertsSummary(settings),
                    icon = Icons.Rounded.NotificationsActive,
                    shape = shape,
                ) { AlertSettings(settings, onEvent) }
            }
            item { shape ->
                ExpandableRow(
                    title = stringResource(R.string.battery_history_title),
                    summary = stringResource(R.string.battery_history_summary, retentionLabel(settings.retentionDays), state.totalSessions),
                    icon = Icons.Rounded.History,
                    shape = shape,
                ) { HistorySettings(state, onEvent) }
            }
        }
    }
}

private fun unitNameRes(unit: TemperatureUnit) =
    if (unit == TemperatureUnit.CELSIUS) R.string.battery_unit_celsius_name else R.string.battery_unit_fahrenheit_name

private fun layoutNameRes(layout: MonitorNotificationLayout) =
    if (layout == MonitorNotificationLayout.COMPACT) R.string.battery_notif_layout_compact else R.string.battery_notif_layout_detailed

@Composable
private fun notificationSummary(settings: MonitorSettingsUi): String {
    val layout = stringResource(layoutNameRes(settings.notificationLayout))
    return if (settings.notificationShowPower) stringResource(R.string.battery_notif_summary_power, layout) else layout
}

@Composable
private fun resetSummary(settings: MonitorSettingsUi): String {
    val rules = buildList {
        if (settings.resetOnPlug) add(stringResource(R.string.battery_reset_summary_plug))
        if (settings.resetOnBoot) add(stringResource(R.string.battery_reset_summary_boot))
        settings.resetThresholdPercent?.let { add(stringResource(R.string.battery_reset_summary_level, it)) }
    }
    return if (rules.isEmpty()) stringResource(R.string.battery_reset_summary_none) else stringResource(R.string.battery_reset_summary_auto, rules.joinToString(", "))
}

@Composable
private fun alertsSummary(settings: MonitorSettingsUi): String = when {
    settings.idleAlertEnabled && settings.baselineAlertEnabled -> stringResource(R.string.battery_alerts_summary_both)
    settings.idleAlertEnabled -> stringResource(R.string.battery_alerts_summary_fixed, BatteryFormat.decimal(settings.idleAlertThreshold))
    settings.baselineAlertEnabled -> stringResource(R.string.battery_alerts_summary_range)
    else -> stringResource(R.string.battery_alerts_summary_off)
}

@Composable
private fun retentionLabel(days: Int): String = stringResource(
    when (days) {
        30 -> R.string.battery_retention_30
        90 -> R.string.battery_retention_90
        else -> R.string.battery_retention_manual
    },
)

@Composable
private fun NotificationSettings(settings: MonitorSettingsUi, onEvent: (BatteryEvent) -> Unit) {
    SectionTitle(
        stringResource(R.string.battery_notif_layout),
        helpText = listOf(stringResource(R.string.battery_outputs_body), stringResource(R.string.battery_widget_note)).joinToString("\n\n"),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val layouts = MonitorNotificationLayout.entries
        layouts.forEachIndexed { index, layout ->
            AbleSegmentedButton(
                selected = settings.notificationLayout == layout,
                onClick = { onEvent(BatteryEvent.SetNotificationLayout(layout)) },
                shape = SegmentedButtonDefaults.itemShape(index, layouts.size),
                label = { Text(stringResource(layoutNameRes(layout))) },
            )
        }
    }
    ListGroup {
        item { shape ->
            SwitchRow(
                title = stringResource(R.string.battery_notif_show_power),
                supportingText = stringResource(R.string.battery_notif_show_power_body),
                checked = settings.notificationShowPower,
                onCheckedChange = { onEvent(BatteryEvent.SetNotificationShowPower(it)) },
                shape = shape,
            )
        }
    }
}

@Composable
private fun TemperatureUnitSettings(settings: MonitorSettingsUi, onEvent: (BatteryEvent) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val units = TemperatureUnit.entries
        units.forEachIndexed { index, unit ->
            AbleSegmentedButton(
                selected = settings.temperatureUnit == unit,
                onClick = { onEvent(BatteryEvent.SetTemperatureUnit(unit)) },
                shape = SegmentedButtonDefaults.itemShape(index, units.size),
                label = { Text(stringResource(unitNameRes(unit))) },
            )
        }
    }
}

@Composable
private fun ResetRulesSettings(settings: MonitorSettingsUi, onEvent: (BatteryEvent) -> Unit) {
    var thermal by remember(settings.thermalThresholdCelsius) { mutableFloatStateOf(settings.thermalThresholdCelsius) }
    SectionTitle(stringResource(R.string.battery_reset_title), helpText = stringResource(R.string.battery_help_reset_rules))
    ListGroup {
        item { shape ->
            SwitchRow(
                title = stringResource(R.string.battery_reset_on_plug),
                supportingText = stringResource(R.string.battery_reset_on_plug_body),
                checked = settings.resetOnPlug,
                onCheckedChange = { onEvent(BatteryEvent.SetResetOnPlug(it)) },
                shape = shape,
            )
        }
        item { shape ->
            SwitchRow(
                title = stringResource(R.string.battery_reset_on_boot),
                supportingText = stringResource(R.string.battery_reset_on_boot_body),
                checked = settings.resetOnBoot,
                onCheckedChange = { onEvent(BatteryEvent.SetResetOnBoot(it)) },
                shape = shape,
            )
        }
    }
    Text(stringResource(R.string.battery_reset_threshold), style = MaterialTheme.typography.bodyMedium)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
    ) {
        listOf<Int?>(null, 20, 30, 50).forEach { value ->
            val selected = settings.resetThresholdPercent == value
            AbleFilterChip(
                selected = selected,
                onClick = { onEvent(BatteryEvent.SetResetThreshold(value)) },
                label = { Text(value?.let { stringResource(R.string.battery_percent_value, it) } ?: stringResource(R.string.battery_reset_threshold_off)) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
    SectionTitle(
        stringResource(R.string.battery_thermal_threshold, temperatureText(thermal, settings.temperatureUnit, digits = 0)),
        helpText = stringResource(R.string.battery_thermal_note),
    )
    AbleSlider(
        value = thermal,
        onValueChange = { thermal = it.roundToInt().toFloat() },
        onValueChangeFinished = { onEvent(BatteryEvent.SetThermalThreshold(thermal)) },
        valueRange = 40f..60f,
        steps = 19,
    )
}

@Composable
private fun AlertSettings(settings: MonitorSettingsUi, onEvent: (BatteryEvent) -> Unit) {
    var idleThreshold by remember(settings.idleAlertThreshold) { mutableFloatStateOf(settings.idleAlertThreshold) }
    var minDiff by remember(settings.baselineMinDifference) { mutableFloatStateOf(settings.baselineMinDifference) }
    SectionTitle(
        stringResource(R.string.battery_alerts_title),
        helpText = listOf(stringResource(R.string.battery_alerts_body), stringResource(R.string.battery_alerts_window_note)).joinToString("\n\n"),
    )
    ListGroup {
        item { shape ->
            SwitchRow(
                title = stringResource(R.string.battery_alert_idle_toggle),
                supportingText = stringResource(R.string.battery_alert_idle_toggle_body),
                checked = settings.idleAlertEnabled,
                onCheckedChange = { enable ->
                    onEvent(if (enable) BatteryEvent.EnableAlert(AlertKind.IDLE_THRESHOLD) else BatteryEvent.DisableAlert(AlertKind.IDLE_THRESHOLD))
                },
                shape = shape,
            )
        }
        item { shape ->
            SwitchRow(
                title = stringResource(R.string.battery_alert_baseline_toggle),
                supportingText = stringResource(R.string.battery_alert_baseline_toggle_body),
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
}

@Composable
private fun HistorySettings(state: MonitorUiState, onEvent: (BatteryEvent) -> Unit) {
    val options = listOf(30, 90, -1)
    SectionTitle(stringResource(R.string.battery_retention), helpText = stringResource(R.string.battery_retention_note))
    ListGroup {
        options.forEach { days ->
            item { shape ->
                RadioRow(
                    title = retentionLabel(days),
                    selected = state.settings.retentionDays == days,
                    onSelect = { onEvent(BatteryEvent.SetRetention(days)) },
                    shape = shape,
                )
            }
        }
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
            Text(deleteTitle, modifier = Modifier.padding(start = AbleSpacing.sm))
        }
        HelpButton(title = deleteTitle, text = stringResource(R.string.battery_help_delete_history))
    }
}
