package com.dragolabs.able.feature.battery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
internal fun SupportingText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
