package com.dragolabs.able.feature.battery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.ExpressiveIcon
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.InlineLoading
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.labelRes
import com.dragolabs.able.core.designsystem.component.statusLevel
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.BatteryHealth
import com.dragolabs.able.feature.battery.AdvancedHealthState
import com.dragolabs.able.feature.battery.BatteryEvent
import com.dragolabs.able.feature.battery.BatteryUiState
import com.dragolabs.able.feature.battery.R
import com.dragolabs.able.feature.battery.data.AdvancedBatteryReading
import com.dragolabs.able.feature.battery.domain.BatteryFormat
import com.dragolabs.able.feature.battery.domain.KernelBatteryValues
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

/**
 * Health tab (owner request 2026-11-23): a summary hero answers "how is my battery?", then
 * capacity, lifecycle and charging details, and the optional Shizuku reading at the bottom.
 * Kernel-derived values are labelled as estimates and keep their raw value as secondary text.
 */
@Composable
internal fun HealthTab(
    state: BatteryUiState,
    contentPadding: PaddingValues,
    onEvent: (BatteryEvent) -> Unit,
    gridState: LazyStaggeredGridState,
) {
    val reading = (state.health.advanced as? AdvancedHealthState.Result)?.reading?.takeUnless { it.isEmpty }
    BatteryCardGrid(contentPadding, state = gridState) {
        fullLine("summary") { HealthSummaryCard(state, reading) }
        card("capacity") { CapacityCard(reading) }
        card("lifecycle") { LifecycleCard(state, reading) }
        card("charging") { ChargingCard(state, reading) }
        fullLine("advanced") { AdvancedReadCard(state, onEvent) }
    }
}

@Composable
private fun healthLabel(health: BatteryHealth): String = stringResource(
    when (health) {
        BatteryHealth.GOOD -> R.string.battery_health_good
        BatteryHealth.OVERHEAT -> R.string.battery_health_overheat
        BatteryHealth.DEAD -> R.string.battery_health_dead
        BatteryHealth.OVER_VOLTAGE -> R.string.battery_health_over_voltage
        BatteryHealth.COLD -> R.string.battery_health_cold
        BatteryHealth.FAILURE -> R.string.battery_health_failure
        BatteryHealth.UNKNOWN -> R.string.battery_health_unknown
    },
)

private fun BatteryHealth.level(): StatusLevel = when (this) {
    BatteryHealth.GOOD -> StatusLevel.SUCCESS
    BatteryHealth.UNKNOWN -> StatusLevel.INFO
    BatteryHealth.COLD, BatteryHealth.OVERHEAT -> StatusLevel.WARNING
    BatteryHealth.DEAD, BatteryHealth.OVER_VOLTAGE, BatteryHealth.FAILURE -> StatusLevel.CRITICAL
}

// ---------------------------------------------------------------------------------------------
// Summary hero

@Composable
private fun HealthSummaryCard(state: BatteryUiState, reading: AdvancedBatteryReading?) {
    val status = state.status
    val title = stringResource(R.string.battery_health_summary_title)
    val ratio = reading?.capacityRatioPercent
    AbleCard(
        style = AbleCardStyle.Hero,
        title = title,
        icon = Icons.Rounded.HealthAndSafety,
        iconShape = MaterialShapes.Cookie6Sided,
        trailing = {
            HelpButton(
                title = title,
                text = listOf(
                    stringResource(R.string.battery_android_health_body),
                    stringResource(R.string.battery_health_no_estimate),
                ).joinToString("\n\n"),
            )
        },
        status = { StatusPill(text = healthLabel(status.health), level = status.health.level()) },
    ) {
        // Ring and headline wrap onto two lines on narrow widths or large font scales.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            CapacityRing(ratio)
            Column(
                modifier = Modifier
                    .widthIn(min = 160.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
            ) {
                Text(
                    stringResource(R.string.battery_metric_health_status),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(healthLabel(status.health), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(if (ratio != null) R.string.battery_capacity_ratio_note else R.string.battery_capacity_ratio_missing),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        MetricTiles(
            listOf(
                cyclesMetric(state, reading),
                Metric(
                    label = stringResource(R.string.battery_metric_temperature),
                    value = temperatureText(status.temperatureCelsius, state.temperatureUnit),
                ),
                Metric(
                    label = stringResource(R.string.battery_metric_voltage),
                    value = status.voltageMillivolts?.let { stringResource(R.string.battery_mv_value, it) } ?: unavailable(),
                ),
                Metric(
                    label = stringResource(R.string.battery_metric_technology),
                    value = status.technology ?: unavailable(),
                ),
            ),
        )
    }
}

/** Capacity ratio ring, labelled as an estimate; an icon placeholder when no ratio is known. */
@Composable
private fun CapacityRing(ratio: Float?) {
    val ringSize = 112.dp
    if (ratio == null) {
        ExpressiveIcon(
            icon = Icons.Rounded.HealthAndSafety,
            shape = MaterialShapes.Cookie9Sided,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            size = ringSize,
            iconSize = 48.dp,
        )
        return
    }
    val percent = BatteryFormat.decimal(ratio, 0)
    val description = stringResource(R.string.battery_capacity_ring_cd, percent)
    Box(
        modifier = Modifier
            .size(ringSize)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularWavyProgressIndicator(
            progress = { (ratio / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.size(ringSize),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.battery_percent_text, percent), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.battery_estimate),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun cyclesMetric(state: BatteryUiState, reading: AdvancedBatteryReading?): Metric {
    val label = stringResource(R.string.battery_cycles_title)
    val api = state.status.cycleCount
    val kernel = reading?.cycleCount
    return when {
        api != null -> Metric(label, api.toString(), stringResource(R.string.battery_cycles_source_api))
        kernel != null -> Metric(label, kernel.toString(), stringResource(R.string.battery_cycles_source_kernel))
        !state.health.cycleCountApiSupported -> Metric(label, unavailable(), stringResource(R.string.battery_cycles_needs_14))
        else -> Metric(label, unavailable(), stringResource(R.string.battery_cycles_not_exposed))
    }
}

// ---------------------------------------------------------------------------------------------
// Capacity

@Composable
private fun CapacityCard(reading: AdvancedBatteryReading?) {
    val design = reading?.designCapacityMah
    val full = reading?.fullCapacityMah
    AbleCard(
        title = stringResource(R.string.battery_capacity_section),
        icon = Icons.Rounded.Straighten,
        iconShape = MaterialShapes.Square,
    ) {
        if (design == null && full == null) {
            Text(stringResource(R.string.battery_not_read), style = MaterialTheme.typography.titleMedium)
            SupportingText(stringResource(R.string.battery_capacity_needs_advanced))
            return@AbleCard
        }
        val max = maxOf(design ?: 0, full ?: 0).coerceAtLeast(1)
        CapacityBar(
            label = stringResource(R.string.battery_advanced_design_capacity),
            mah = design,
            fraction = (design ?: 0) / max.toFloat(),
            emphasized = false,
        )
        CapacityBar(
            label = stringResource(R.string.battery_advanced_full_capacity),
            mah = full,
            fraction = (full ?: 0) / max.toFloat(),
            emphasized = true,
        )
        reading.capacityRatioPercent?.let {
            Text(
                stringResource(R.string.battery_capacity_ratio_line, stringResource(R.string.battery_percent_estimate, BatteryFormat.decimal(it))),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SupportingText(stringResource(R.string.battery_advanced_raw_note))
    }
}

@Composable
private fun CapacityBar(label: String, mah: Int?, fraction: Float, emphasized: Boolean) {
    val value = mah?.let { stringResource(R.string.battery_mah_value, it) } ?: unavailable()
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(12.dp),
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = track, size = size, cornerRadius = radius)
            if (mah != null) {
                drawRoundRect(
                    color = fill,
                    size = Size((size.width * fraction.coerceIn(0f, 1f)).coerceAtLeast(size.height), size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Lifecycle

@Composable
private fun LifecycleCard(state: BatteryUiState, reading: AdvancedBatteryReading?) {
    AbleCard(
        title = stringResource(R.string.battery_lifecycle_section),
        icon = Icons.Rounded.Event,
        iconShape = MaterialShapes.Pill,
    ) {
        MetricTiles(
            listOf(
                kernelDateMetric(stringResource(R.string.battery_advanced_first_use), reading?.firstUseDate, reading != null),
                kernelDateMetric(stringResource(R.string.battery_advanced_manufacture), reading?.manufactureDate, reading != null),
            ),
        )
        if (reading == null) SupportingText(stringResource(R.string.battery_lifecycle_needs_advanced))
    }
}

@Composable
private fun kernelDateMetric(label: String, raw: String?, wasRead: Boolean): Metric {
    if (raw == null) return Metric(label, if (wasRead) unavailable() else stringResource(R.string.battery_not_read))
    val date = KernelBatteryValues.parseDate(raw)
    return if (date != null) {
        Metric(label, date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), stringResource(R.string.battery_raw_value, raw))
    } else {
        // Not a plausible date: show what the kernel reported instead of guessing.
        Metric(label, raw, stringResource(R.string.battery_raw_unparsed))
    }
}

// ---------------------------------------------------------------------------------------------
// Charging

@Composable
private fun ChargingCard(state: BatteryUiState, reading: AdvancedBatteryReading?) {
    val status = state.status
    AbleCard(
        title = stringResource(R.string.battery_charging_section),
        icon = Icons.Rounded.BatteryChargingFull,
        iconShape = MaterialShapes.Cookie4Sided,
        status = { StatusPill(text = stateLabel(status), level = if (status.isCharging) StatusLevel.SUCCESS else StatusLevel.INFO) },
    ) {
        val notCharging = stringResource(R.string.battery_not_charging_short)
        val currentMa = status.currentMicroAmperes?.let { abs(it) / 1000 }
        MetricTiles(
            buildList {
                add(chargingPolicyMetric(reading))
                add(
                    Metric(
                        label = stringResource(R.string.battery_metric_current),
                        value = when {
                            !status.isCharging -> notCharging
                            currentMa != null -> stringResource(R.string.battery_ma_value, currentMa)
                            else -> unavailable()
                        },
                        supporting = if (status.isCharging && currentMa != null) stringResource(R.string.battery_estimate) else null,
                    ),
                )
                add(
                    Metric(
                        label = stringResource(R.string.battery_metric_power),
                        value = when {
                            !status.isCharging -> notCharging
                            else -> status.powerMilliWatts?.let { stringResource(R.string.battery_w_value, BatteryFormat.decimal(it / 1000f, 1)) } ?: unavailable()
                        },
                        supporting = if (status.isCharging && status.powerMilliWatts != null) stringResource(R.string.battery_estimate) else null,
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
            },
        )
    }
}

@Composable
private fun chargingPolicyMetric(reading: AdvancedBatteryReading?): Metric {
    val label = stringResource(R.string.battery_advanced_policy)
    val raw = reading?.chargingPolicy
        ?: return Metric(label, if (reading == null) stringResource(R.string.battery_not_read) else unavailable())
    val text = stringResource(
        when (KernelBatteryValues.parseChargingPolicy(raw)) {
            KernelBatteryValues.ChargingPolicy.DEFAULT -> R.string.battery_policy_default
            KernelBatteryValues.ChargingPolicy.ADAPTIVE_AON -> R.string.battery_policy_adaptive_aon
            KernelBatteryValues.ChargingPolicy.ADAPTIVE_AC -> R.string.battery_policy_adaptive_ac
            KernelBatteryValues.ChargingPolicy.ADAPTIVE_LONGLIFE -> R.string.battery_policy_adaptive_longlife
            null -> R.string.battery_policy_unknown
        },
    )
    return Metric(label, text, stringResource(R.string.battery_raw_value, raw))
}

// ---------------------------------------------------------------------------------------------
// Advanced (Shizuku)

@Composable
private fun AdvancedReadCard(state: BatteryUiState, onEvent: (BatteryEvent) -> Unit) {
    val shizuku = state.health.shizuku
    val advanced = state.health.advanced
    val title = stringResource(R.string.battery_advanced_title)
    AbleCard(
        style = AbleCardStyle.Outlined,
        title = title,
        subtitle = stringResource(R.string.battery_advanced_short),
        icon = Icons.Rounded.Memory,
        iconShape = MaterialShapes.Square,
        trailing = {
            HelpButton(
                title = title,
                text = listOf(
                    stringResource(R.string.battery_help_advanced),
                    stringResource(R.string.battery_advanced_raw_note),
                ).joinToString("\n\n"),
            )
        },
        status = { StatusPill(stringResource(shizuku.labelRes()), shizuku.statusLevel()) },
    ) {
        if (shizuku == ShizukuConnectionState.READY) {
            AbleButton(
                onClick = { onEvent(BatteryEvent.ReadAdvancedHealth) },
                enabled = advanced != AdvancedHealthState.Reading,
                modifier = Modifier.focusRing(),
            ) {
                Text(
                    stringResource(if (advanced is AdvancedHealthState.Result) R.string.battery_advanced_read_again else R.string.battery_advanced_read),
                )
            }
        } else {
            SupportingText(stringResource(R.string.battery_advanced_unavailable))
            AbleOutlinedButton(onClick = { onEvent(BatteryEvent.ConnectShizuku) }, modifier = Modifier.focusRing()) {
                Text(
                    stringResource(
                        if (shizuku == ShizukuConnectionState.NOT_AVAILABLE) R.string.battery_advanced_open_shizuku else R.string.battery_advanced_authorize,
                    ),
                )
            }
        }
        when (advanced) {
            AdvancedHealthState.Idle -> Unit
            AdvancedHealthState.Reading -> InlineLoading()
            AdvancedHealthState.Failed -> StatusBanner(level = StatusLevel.CRITICAL, title = stringResource(R.string.battery_advanced_failed))
            is AdvancedHealthState.Result ->
                if (advanced.reading.isEmpty) {
                    StatusBanner(level = StatusLevel.INFO, title = stringResource(R.string.battery_advanced_empty))
                } else {
                    StatusPill(text = stringResource(R.string.battery_advanced_done), level = StatusLevel.SUCCESS)
                }
        }
    }
}
