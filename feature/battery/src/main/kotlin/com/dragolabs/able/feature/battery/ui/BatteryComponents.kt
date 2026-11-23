package com.dragolabs.able.feature.battery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.MetricTile
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.database.TemperatureUnit
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.battery.R
import com.dragolabs.able.feature.battery.domain.BatteryFormat

/**
 * Adaptive card grid: one column on phones, two (or more) on wide windows so phone controls are
 * not stretched (adaptive-ui form-factor rules).
 */
@Composable
internal fun BatteryCardGrid(
    contentPadding: PaddingValues,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    content: LazyStaggeredGridScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalStaggeredGrid(
            state = state,
            columns = StaggeredGridCells.Adaptive(minSize = 360.dp),
            modifier = Modifier
                .widthIn(max = 1200.dp)
                .fillMaxSize(),
            contentPadding = contentPadding,
            verticalItemSpacing = AbleSpacing.md,
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            content = content,
        )
    }
}

internal fun LazyStaggeredGridScope.fullLine(key: Any, content: @Composable () -> Unit) =
    item(key = key, span = StaggeredGridItemSpan.FullLine) { Box(Modifier.animateItem()) { content() } }

internal fun LazyStaggeredGridScope.card(key: Any, content: @Composable () -> Unit) =
    item(key = key) { Box(Modifier.animateItem()) { content() } }

@Composable
internal fun temperatureText(celsius: Float?, unit: TemperatureUnit, digits: Int = 1): String {
    val res = LocalContext.current.resources
    return celsius?.let { BatteryFormat.temperature(res, it, unit, digits) } ?: stringResource(R.string.battery_value_unavailable)
}

@Composable
internal fun durationText(durationMs: Long): String = BatteryFormat.duration(LocalContext.current.resources, durationMs)

@Composable
internal fun rateText(rate: Float?): String =
    rate?.let { stringResource(R.string.battery_rate_estimate, BatteryFormat.decimal(it)) }
        ?: stringResource(R.string.battery_not_enough_data)

@Composable
internal fun unavailable(): String = stringResource(R.string.battery_value_unavailable)

/** Small bar chart of recent valid idle-drain rates (baseline trend). */
@Composable
internal fun TrendBars(values: List<Float>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.tertiary
    val description = stringResource(
        R.string.battery_baseline_trend_cd,
        values.size,
        BatteryFormat.decimal(values.min()),
        BatteryFormat.decimal(values.max()),
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = description },
    ) {
        val max = values.max().coerceAtLeast(0.1f)
        val slot = size.width / values.size
        val barWidth = (slot * 0.6f).coerceAtMost(24.dp.toPx())
        values.forEachIndexed { index, value ->
            val barHeight = (value / max) * size.height
            drawRoundRect(
                color = if (index == values.lastIndex) highlight else color,
                topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    }
}

@Composable
internal fun rateShort(rate: Float?): String =
    rate?.let { stringResource(R.string.battery_rate_per_hour, BatteryFormat.decimal(it)) }
        ?: stringResource(R.string.battery_not_enough_data)

/** A labeled value for [MetricTiles]; [supporting] holds the source, raw value or "estimate". */
internal data class Metric(val label: String, val value: String, val supporting: String? = null)

/**
 * Metric tiles in rows of equal height. Two columns normally; one column when the width at the
 * current font scale would squeeze the values (large font scales, narrow windows).
 */
@Composable
internal fun MetricTiles(metrics: List<Metric>, modifier: Modifier = Modifier) {
    if (metrics.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val columns = if (maxWidth / fontScale >= 240.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            metrics.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                ) {
                    row.forEach { metric ->
                        MetricTile(
                            label = metric.label,
                            value = metric.value,
                            supportingText = metric.supporting,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Section title with an optional help button, used inside cards and the settings group. */
@Composable
internal fun SectionTitle(text: String, helpText: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (helpText != null) HelpButton(title = text, text = helpText)
    }
}

@Composable
internal fun SupportingText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
