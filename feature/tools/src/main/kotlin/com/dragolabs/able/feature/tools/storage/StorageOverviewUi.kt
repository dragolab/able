package com.dragolabs.able.feature.tools.storage

import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.core.designsystem.R as DsR

/** Hero card: animated ring of used vs free internal storage (STO-003). */
@Composable
internal fun StorageHeroCard(breakdown: StorageBreakdown, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val used = Formatter.formatShortFileSize(context, breakdown.usedBytes)
    val total = Formatter.formatShortFileSize(context, breakdown.totalBytes)
    val free = Formatter.formatShortFileSize(context, breakdown.freeBytes)
    val description = stringResource(R.string.storage_chart_description, used, total, breakdown.usedPercent, free)
    AbleCard(
        modifier = modifier,
        title = stringResource(R.string.storage_internal_title),
        icon = Icons.Rounded.DataUsage,
        style = AbleCardStyle.Hero,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
        ) {
            Box(contentAlignment = Alignment.Center) {
                StorageRing(fraction = breakdown.usedFraction, diameter = 148.dp, thickness = 18.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.storage_percent, breakdown.usedPercent), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.storage_ring_used_label), style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                Text(stringResource(R.string.storage_used_value, used), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.storage_of_total, total), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.storage_free_value, free),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = AbleSpacing.sm),
                )
            }
        }
    }
}

/** Donut ring with rounded caps; animates with the theme's expressive spatial motion. */
@Composable
internal fun StorageRing(fraction: Float, diameter: Dp, thickness: Dp, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "storageRing",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val usedColor = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(diameter)) {
        val stroke = thickness.toPx()
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        val sweep = (animated.coerceIn(0f, 1f) * 360f)
        if (sweep > 0.5f) {
            drawArc(
                usedColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
internal fun storageKindColor(kind: StorageKind): Color {
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        StorageKind.APPS -> scheme.primary
        StorageKind.IMAGES -> scheme.tertiary
        StorageKind.VIDEO -> scheme.secondary
        StorageKind.AUDIO -> scheme.error
        StorageKind.DOCUMENTS -> scheme.inversePrimary
        StorageKind.DOWNLOADS -> scheme.tertiaryContainer
        StorageKind.OTHER -> scheme.outline
    }
}

internal fun StorageKind.labelRes(): Int = when (this) {
    StorageKind.APPS -> R.string.storage_kind_apps
    StorageKind.IMAGES -> R.string.storage_kind_images
    StorageKind.VIDEO -> R.string.storage_kind_video
    StorageKind.AUDIO -> R.string.storage_kind_audio
    StorageKind.DOCUMENTS -> R.string.storage_kind_documents
    StorageKind.DOWNLOADS -> R.string.storage_kind_downloads
    StorageKind.OTHER -> R.string.storage_kind_other
}

@Composable
private fun percentText(bytes: Long, percent: Int): String =
    if (bytes > 0 && percent == 0) stringResource(R.string.storage_percent_below_one) else stringResource(R.string.storage_percent, percent)

/** Category breakdown: segmented bar plus a legend with value and percent for every category. */
@Composable
internal fun StorageBreakdownCard(
    breakdown: StorageBreakdown,
    hasUsageAccess: Boolean,
    hasMediaPermission: Boolean,
    onRequestUsageAccess: () -> Unit,
    onRequestMediaAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val barDescription = stringResource(R.string.storage_breakdown_description)
    AbleCard(
        modifier = modifier,
        title = stringResource(R.string.storage_breakdown_title),
        icon = Icons.Rounded.PieChart,
    ) {
        SegmentedStorageBar(
            breakdown = breakdown,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = barDescription },
        )
        Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            breakdown.segments.forEach { segment ->
                val value = segment.bytes?.let {
                    stringResource(R.string.storage_legend_value, Formatter.formatShortFileSize(context, it), percentText(it, segment.percent))
                } ?: stringResource(
                    if (segment.missing == MissingAccess.USAGE_ACCESS) R.string.storage_needs_usage_access else R.string.storage_needs_media_access,
                )
                val supporting = if (segment.kind == StorageKind.OTHER && breakdown.hasUnmeasured) {
                    stringResource(R.string.storage_other_includes_unmeasured)
                } else {
                    null
                }
                LegendRow(color = storageKindColor(segment.kind), label = stringResource(segment.kind.labelRes()), value = value, supporting = supporting, muted = segment.bytes == null)
            }
            val freePercent = percentOf(breakdown.freeBytes, breakdown.totalBytes)
            LegendRow(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                label = stringResource(R.string.storage_kind_free),
                value = stringResource(
                    R.string.storage_legend_value,
                    Formatter.formatShortFileSize(context, breakdown.freeBytes),
                    percentText(breakdown.freeBytes, freePercent),
                ),
            )
        }
        if (hasMediaPermission) {
            Text(
                stringResource(R.string.storage_media_visible_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!hasUsageAccess) {
            StatusBanner(
                level = StatusLevel.INFO,
                title = stringResource(R.string.storage_usage_title),
                message = stringResource(R.string.storage_usage_rationale),
                actionLabel = stringResource(R.string.storage_usage_action),
                onAction = onRequestUsageAccess,
            )
        }
        if (!hasMediaPermission) {
            StatusBanner(
                level = StatusLevel.INFO,
                title = stringResource(R.string.storage_media_title),
                message = stringResource(R.string.storage_media_rationale),
                actionLabel = stringResource(R.string.tools_storage_permission_action),
                onAction = onRequestMediaAccess,
            )
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String, supporting: String? = null, muted: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md),
    ) {
        Box(
            Modifier
                .size(14.dp)
                .background(if (muted) Color.Transparent else color, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Horizontal bar: one rounded segment per measured category, the rest of the track is free space. */
@Composable
private fun SegmentedStorageBar(breakdown: StorageBreakdown, modifier: Modifier = Modifier) {
    val reveal = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(breakdown) { reveal.animateTo(1f, spec) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val colors = breakdown.segments.map { storageKindColor(it.kind) }
    Canvas(modifier.height(20.dp)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(track, cornerRadius = radius)
        val total = breakdown.totalBytes.toFloat().takeIf { it > 0f } ?: return@Canvas
        val gap = 3.dp.toPx()
        val minWidth = size.height / 2f
        val progress = reveal.value.coerceIn(0f, 1f)
        var x = 0f
        breakdown.segments.forEachIndexed { index, segment ->
            val bytes = segment.bytes ?: 0L
            if (bytes <= 0L) return@forEachIndexed
            val width = (bytes / total * size.width * progress).coerceAtLeast(minWidth * progress)
            val drawn = (width - gap).coerceAtLeast(1f).coerceAtMost(size.width - x)
            if (drawn <= 0f) return@forEachIndexed
            drawRoundRect(colors[index], topLeft = Offset(x, 0f), size = Size(drawn, size.height), cornerRadius = radius)
            x += width
        }
    }
}

@Composable
internal fun OverviewFailedBanner(onRetry: () -> Unit) {
    StatusBanner(
        level = StatusLevel.WARNING,
        title = stringResource(R.string.storage_overview_failed),
        actionLabel = stringResource(DsR.string.ds_retry),
        onAction = onRetry,
    )
}
