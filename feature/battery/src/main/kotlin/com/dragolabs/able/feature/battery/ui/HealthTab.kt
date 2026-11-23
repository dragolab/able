package com.dragolabs.able.feature.battery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.dragolabs.able.core.designsystem.component.MetricTile
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.battery.domain.KernelBatteryValues
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.InlineLoading
import com.dragolabs.able.core.designsystem.component.MetricGrid
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.labelRes
import com.dragolabs.able.core.designsystem.component.statusLevel
import com.dragolabs.able.core.model.BatteryHealth
import com.dragolabs.able.feature.battery.AdvancedHealthState
import com.dragolabs.able.feature.battery.BatteryEvent
import com.dragolabs.able.feature.battery.BatteryUiState
import com.dragolabs.able.feature.battery.R
import com.dragolabs.able.feature.battery.domain.BatteryFormat

@Composable
internal fun HealthTab(
    state: BatteryUiState,
    contentPadding: PaddingValues,
    onEvent: (BatteryEvent) -> Unit,
) {
    BatteryCardGrid(contentPadding) {
        card("cycles") { CycleCountCard(state) }
        card("android") { AndroidHealthCard(state) }
        fullLine("advanced") { AdvancedHealthCard(state, onEvent) }
    }
}

@Composable
private fun CycleCountCard(state: BatteryUiState) {
    val cycles = state.status.cycleCount
    AbleCard(
        style = AbleCardStyle.Hero,
        title = stringResource(R.string.battery_cycles_title),
        icon = Icons.Rounded.Autorenew,
        iconShape = MaterialShapes.Cookie6Sided,
    ) {
        if (cycles != null) {
            Text(stringResource(R.string.battery_cycles_value, cycles), style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.battery_cycles_source), style = MaterialTheme.typography.bodySmall)
        } else {
            Text(unavailable(), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(
                    if (state.health.cycleCountApiSupported) R.string.battery_cycles_unavailable_detail else R.string.battery_cycles_requires_14,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
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

@Composable
private fun AndroidHealthCard(state: BatteryUiState) {
    val status = state.status
    AbleCard(
        title = stringResource(R.string.battery_android_health_title),
        subtitle = stringResource(R.string.battery_android_health_body),
        icon = Icons.Rounded.HealthAndSafety,
    ) {
        MetricGrid(
            metrics = listOf(
                stringResource(R.string.battery_metric_health_status) to healthLabel(status.health),
                stringResource(R.string.battery_metric_technology) to (status.technology ?: unavailable()),
                stringResource(R.string.battery_metric_voltage) to (status.voltageMillivolts?.let { stringResource(R.string.battery_mv_value, it) } ?: unavailable()),
                stringResource(R.string.battery_metric_temperature) to temperatureText(status.temperatureCelsius, state.temperatureUnit),
            ),
        )
        SupportingText(stringResource(R.string.battery_health_no_estimate))
    }
}

@Composable
private fun AdvancedHealthCard(state: BatteryUiState, onEvent: (BatteryEvent) -> Unit) {
    val shizuku = state.health.shizuku
    val advanced = state.health.advanced
    val title = stringResource(R.string.battery_advanced_title)
    AbleCard(
        title = title,
        subtitle = stringResource(R.string.battery_advanced_description),
        icon = Icons.Rounded.Memory,
        iconShape = MaterialShapes.Square,
        trailing = { HelpButton(title = title, text = stringResource(R.string.battery_help_advanced)) },
        status = { StatusPill(stringResource(shizuku.labelRes()), shizuku.statusLevel()) },
    ) {
        if (shizuku == ShizukuConnectionState.READY) {
            AbleButton(
                onClick = { onEvent(BatteryEvent.ReadAdvancedHealth) },
                enabled = advanced != AdvancedHealthState.Reading,
                modifier = Modifier.focusRing(),
            ) { Text(stringResource(R.string.battery_advanced_read)) }
        } else {
            Text(stringResource(R.string.battery_advanced_unavailable), style = MaterialTheme.typography.bodyMedium)
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
            is AdvancedHealthState.Result -> {
                val reading = advanced.reading
                if (reading.isEmpty) {
                    StatusBanner(level = StatusLevel.INFO, title = stringResource(R.string.battery_advanced_empty))
                } else {
                    MetricGrid(
                        metrics = listOf(
                            stringResource(R.string.battery_advanced_design_capacity) to (reading.designCapacityMah?.let { stringResource(R.string.battery_mah_value, it) } ?: unavailable()),
                            stringResource(R.string.battery_advanced_full_capacity) to (reading.fullCapacityMah?.let { stringResource(R.string.battery_mah_value, it) } ?: unavailable()),
                            stringResource(R.string.battery_advanced_soh) to (reading.capacityRatioPercent?.let { stringResource(R.string.battery_percent_estimate, BatteryFormat.decimal(it)) } ?: unavailable()),
                            stringResource(R.string.battery_advanced_cycles) to (reading.cycleCount?.toString() ?: unavailable()),
                        ),
                    )
                    KernelValueGrid(
                        listOf(
                            kernelDateValue(stringResource(R.string.battery_advanced_manufacture), reading.manufactureDate),
                            kernelDateValue(stringResource(R.string.battery_advanced_first_use), reading.firstUseDate),
                            chargingPolicyValue(reading.chargingPolicy),
                        ),
                    )
                    SupportingText(stringResource(R.string.battery_advanced_raw_note))
                }
            }
        }
    }
}

/** A kernel value: interpreted [value] plus the [raw] text when it differs (BAT-HLT-002 honesty). */
private data class KernelValue(val label: String, val value: String, val raw: String?)

@Composable
private fun kernelDateValue(label: String, raw: String?): KernelValue {
    if (raw == null) return KernelValue(label, unavailable(), null)
    val date = KernelBatteryValues.parseDate(raw)
    return if (date != null) {
        KernelValue(label, date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), stringResource(R.string.battery_raw_value, raw))
    } else {
        // Not a plausible date: show what the kernel reported instead of guessing.
        KernelValue(label, raw, null)
    }
}

@Composable
private fun chargingPolicyValue(raw: String?): KernelValue {
    val label = stringResource(R.string.battery_advanced_policy)
    if (raw == null) return KernelValue(label, unavailable(), null)
    val policy = KernelBatteryValues.parseChargingPolicy(raw)
    val text = stringResource(
        when (policy) {
            KernelBatteryValues.ChargingPolicy.DEFAULT -> R.string.battery_policy_default
            KernelBatteryValues.ChargingPolicy.ADAPTIVE_AON -> R.string.battery_policy_adaptive_aon
            KernelBatteryValues.ChargingPolicy.ADAPTIVE_AC -> R.string.battery_policy_adaptive_ac
            KernelBatteryValues.ChargingPolicy.ADAPTIVE_LONGLIFE -> R.string.battery_policy_adaptive_longlife
            null -> R.string.battery_policy_unknown
        },
    )
    return KernelValue(label, text, stringResource(R.string.battery_raw_value, raw))
}

@Composable
private fun KernelValueGrid(values: List<KernelValue>) {
    Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        values.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                row.forEach { item ->
                    MetricTile(label = item.label, value = item.value, supportingText = item.raw, modifier = Modifier.weight(1f))
                }
                repeat(2 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
