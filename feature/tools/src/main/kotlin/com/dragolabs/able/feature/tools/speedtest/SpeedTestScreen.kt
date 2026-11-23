package com.dragolabs.able.feature.tools.speedtest

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.ConfirmationDialog
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.MetricTile
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.perform
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.MessageEffect
import java.text.DateFormat
import java.util.Date

@Composable
fun SpeedTestRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SpeedTestViewModel = viewModel(
        factory = ableViewModelFactory { container -> SpeedTestViewModel(container.appContext) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshConnection()
        onPauseOrDispose { }
    }
    SpeedTestScreen(
        state = state,
        onBack = onBack,
        onStart = viewModel::requestStart,
        onCancel = viewModel::cancel,
        onConfirmMetered = viewModel::confirmMetered,
        onDismissMetered = viewModel::dismissMetered,
        onClearHistory = viewModel::clearHistory,
        onMessageConsumed = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@Composable
fun SpeedTestScreen(
    state: SpeedTestUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onConfirmMetered: () -> Unit,
    onDismissMetered: () -> Unit,
    onClearHistory: () -> Unit,
    onMessageConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, onMessageConsumed)
    val haptics = rememberAbleHaptics()
    LaunchedEffect(state.phase) {
        when (state.phase) {
            SpeedPhase.DONE -> haptics.confirm()
            SpeedPhase.FAILED -> haptics.reject()
            else -> Unit
        }
    }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    AbleDetailScaffold(
        title = stringResource(R.string.tools_speedtest_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        actions = {
            HelpButton(
                title = stringResource(R.string.speedtest_help_title),
                text = stringResource(R.string.speedtest_help_text),
            )
        },
        floatingActionButton = {
            if (state.running) {
                ExtendedFloatingActionButton(
                    onClick = { haptics.perform(AbleHaptic.Reject); onCancel() },
                    icon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                    text = { Text(stringResource(R.string.speedtest_cancel)) },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.focusRing(),
                )
            } else {
                val again = state.phase != SpeedPhase.IDLE
                ExtendedFloatingActionButton(
                    onClick = { haptics.perform(AbleHaptic.Confirm); onStart() },
                    icon = { Icon(if (again) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow, contentDescription = null) },
                    text = { Text(stringResource(if (again) R.string.speedtest_start_again else R.string.speedtest_start)) },
                    modifier = Modifier.focusRing(),
                )
            }
        },
    ) { padding ->
        ReadableWidth {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(screenContentPadding(padding)),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
            ) {
                if (state.connection.type == ConnectionType.NONE && !state.running) {
                    StatusBanner(
                        level = StatusLevel.WARNING,
                        title = stringResource(R.string.speedtest_offline_title),
                        message = stringResource(R.string.speedtest_offline_message),
                    )
                }
                GaugeCard(state)
                ResultTiles(state)
                ConnectionCard(state)
                if (state.phase == SpeedPhase.CANCELLED) {
                    StatusBanner(
                        level = StatusLevel.INFO,
                        title = stringResource(R.string.speedtest_cancelled_title),
                        message = stringResource(R.string.speedtest_data_used, SpeedTestMath.formatMegabytes(state.bytesUsed)),
                    )
                }
                if (state.phase == SpeedPhase.FAILED) {
                    StatusBanner(
                        level = StatusLevel.CRITICAL,
                        title = stringResource(R.string.speedtest_failed_title),
                        message = stringResource(R.string.speedtest_failed_message),
                    )
                }
                Text(
                    stringResource(R.string.speedtest_honest_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HistorySection(state.history, onClear = { confirmClear = true })
            }
        }
    }

    state.meteredWarningBytes?.let { bytes ->
        ConfirmationDialog(
            title = stringResource(R.string.speedtest_metered_title),
            message = stringResource(R.string.speedtest_metered_message, SpeedTestMath.formatMegabytes(bytes)),
            confirmLabel = stringResource(R.string.speedtest_metered_confirm),
            onConfirm = onConfirmMetered,
            onDismiss = onDismissMetered,
            icon = Icons.Rounded.SignalCellularAlt,
        )
    }
    if (confirmClear) {
        ConfirmationDialog(
            title = stringResource(R.string.speedtest_history_clear_title),
            message = stringResource(R.string.speedtest_history_clear_message),
            confirmLabel = stringResource(R.string.speedtest_history_clear),
            onConfirm = { confirmClear = false; onClearHistory() },
            onDismiss = { confirmClear = false },
            destructive = true,
        )
    }
}

@Composable
private fun GaugeCard(state: SpeedTestUiState) {
    val (value, unit) = when (state.phase) {
        SpeedPhase.LATENCY -> (state.pingMs?.let { SpeedTestMath.formatMs(it) } ?: "–") to stringResource(R.string.speedtest_unit_ms)
        SpeedPhase.DOWNLOAD, SpeedPhase.UPLOAD -> SpeedTestMath.formatMbps(state.liveMbps) to stringResource(R.string.speedtest_unit_mbps)
        SpeedPhase.DONE -> (state.downMbps?.let { SpeedTestMath.formatMbps(it) } ?: "–") to stringResource(R.string.speedtest_unit_mbps)
        else -> "–" to stringResource(R.string.speedtest_unit_mbps)
    }
    val phaseLabel = stringResource(state.phase.labelRes())
    val gaugeMbps = when (state.phase) {
        SpeedPhase.DOWNLOAD, SpeedPhase.UPLOAD -> state.liveMbps
        SpeedPhase.DONE -> state.downMbps ?: 0.0
        else -> 0.0
    }
    val fraction by animateFloatAsState(
        targetValue = SpeedTestMath.gaugeFraction(gaugeMbps),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "gauge",
    )
    val accent = if (state.phase == SpeedPhase.UPLOAD) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    AbleCard(style = AbleCardStyle.Hero) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "$phaseLabel: $value $unit"
                },
            contentAlignment = Alignment.Center,
        ) {
            ArcGauge(
                fraction = fraction,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                color = accent,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(phaseLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    value,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(unit, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (state.running) {
            LinearWavyProgressIndicator(
                progress = { state.phaseProgress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.liveSamples.size >= 2 && (state.phase == SpeedPhase.DOWNLOAD || state.phase == SpeedPhase.UPLOAD)) {
            LiveGraph(
                samples = state.liveSamples,
                color = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )
        }
        if (state.bytesUsed > 0) {
            Text(
                stringResource(R.string.speedtest_data_used, SpeedTestMath.formatMegabytes(state.bytesUsed)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 270° arc gauge with rounded caps; [fraction] is 0..1 on a log scale (see [SpeedTestMath.gaugeFraction]). */
@Composable
private fun ArcGauge(
    fraction: Float,
    trackColor: androidx.compose.ui.graphics.Color,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.08f
        val inset = stroke / 2f + 4.dp.toPx()
        val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
        val topLeft = Offset(inset, inset)
        drawArc(trackColor, START_ANGLE, SWEEP, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (fraction > 0.001f) {
            drawArc(color, START_ANGLE, SWEEP * fraction, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        // Scale ticks at 1, 10, 100, 1000 Mbps.
        val radius = arcSize.width / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        listOf(1.0, 10.0, 100.0, 1_000.0).forEach { mark ->
            val angle = Math.toRadians((START_ANGLE + SWEEP * SpeedTestMath.gaugeFraction(mark)).toDouble())
            val outer = radius - stroke
            val inner = outer - stroke * 0.6f
            val dir = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
            drawLine(trackColor, center + dir * inner, center + dir * outer, strokeWidth = stroke * 0.25f, cap = StrokeCap.Round)
        }
    }
}

private const val START_ANGLE = 135f
private const val SWEEP = 270f

@Composable
private fun LiveGraph(samples: List<Float>, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val max = (samples.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val step = size.width / (samples.size - 1).coerceAtLeast(1)
        val path = Path()
        samples.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v / max) * size.height * 0.9f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun ResultTiles(state: SpeedTestUiState) {
    val dash = "–"
    val ms = stringResource(R.string.speedtest_unit_ms)
    val mbps = stringResource(R.string.speedtest_unit_mbps)
    Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            MetricTile(
                label = stringResource(R.string.speedtest_download),
                value = state.downMbps?.let { "${SpeedTestMath.formatMbps(it)} $mbps" } ?: dash,
                emphasized = state.phase == SpeedPhase.DOWNLOAD,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = stringResource(R.string.speedtest_upload),
                value = state.upMbps?.let { "${SpeedTestMath.formatMbps(it)} $mbps" } ?: dash,
                emphasized = state.phase == SpeedPhase.UPLOAD,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            MetricTile(
                label = stringResource(R.string.speedtest_ping),
                value = state.pingMs?.let { "${SpeedTestMath.formatMs(it)} $ms" } ?: dash,
                emphasized = state.phase == SpeedPhase.LATENCY,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = stringResource(R.string.speedtest_jitter),
                value = state.jitterMs?.let { "${SpeedTestMath.formatMs(it)} $ms" } ?: dash,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConnectionCard(state: SpeedTestUiState) {
    AbleCard(
        style = AbleCardStyle.Standard,
        title = stringResource(R.string.speedtest_connection),
        icon = state.connection.type.icon(),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
        ) {
            StatusPill(
                text = stringResource(state.connection.type.labelRes()),
                level = if (state.connection.type == ConnectionType.NONE) StatusLevel.WARNING else StatusLevel.INFO,
            )
            if (state.connection.metered) {
                StatusPill(text = stringResource(R.string.speedtest_metered), level = StatusLevel.WARNING)
            }
        }
        val server = state.server
        val serverText = when {
            server?.colo != null && server.countryCode != null -> stringResource(R.string.speedtest_server_colo_country, server.colo, server.countryCode)
            server?.colo != null -> stringResource(R.string.speedtest_server_colo, server.colo)
            else -> stringResource(R.string.speedtest_server_unknown)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            Icon(Icons.Rounded.Dns, contentDescription = null)
            Text(serverText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HistorySection(history: List<SpeedTestRecord>, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(stringResource(R.string.speedtest_history), modifier = Modifier.weight(1f))
        if (history.isNotEmpty()) {
            AbleTextButton(onClick = onClear, haptic = AbleHaptic.Reject) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                Text(stringResource(R.string.speedtest_history_clear), modifier = Modifier.padding(start = AbleSpacing.xs))
            }
        }
    }
    if (history.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.speedtest_history_empty_title),
            message = stringResource(R.string.speedtest_history_empty_message),
        )
        return
    }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val dash = "–"
    ListGroup {
        history.forEach { record ->
            item { shape ->
                val down = record.downMbps?.let { SpeedTestMath.formatMbps(it) } ?: dash
                val up = record.upMbps?.let { SpeedTestMath.formatMbps(it) } ?: dash
                val ping = record.pingMs?.let { SpeedTestMath.formatMs(it) } ?: dash
                ActionRow(
                    title = stringResource(R.string.speedtest_history_row_title, down, up),
                    supportingText = stringResource(
                        R.string.speedtest_history_row_supporting,
                        format.format(Date(record.timestampMs)),
                        stringResource(record.type.labelRes()),
                        ping,
                    ),
                    icon = record.type.icon(),
                    shape = shape,
                )
            }
        }
    }
}

private fun SpeedPhase.labelRes(): Int = when (this) {
    SpeedPhase.IDLE -> R.string.speedtest_phase_idle
    SpeedPhase.LATENCY -> R.string.speedtest_phase_latency
    SpeedPhase.DOWNLOAD -> R.string.speedtest_phase_download
    SpeedPhase.UPLOAD -> R.string.speedtest_phase_upload
    SpeedPhase.DONE -> R.string.speedtest_phase_done
    SpeedPhase.CANCELLED -> R.string.speedtest_phase_cancelled
    SpeedPhase.FAILED -> R.string.speedtest_phase_failed
}

private fun ConnectionType.labelRes(): Int = when (this) {
    ConnectionType.WIFI -> R.string.speedtest_type_wifi
    ConnectionType.CELLULAR -> R.string.speedtest_type_cellular
    ConnectionType.ETHERNET -> R.string.speedtest_type_ethernet
    ConnectionType.OTHER -> R.string.speedtest_type_other
    ConnectionType.NONE -> R.string.speedtest_type_none
}

private fun ConnectionType.icon(): ImageVector = when (this) {
    ConnectionType.WIFI -> Icons.Rounded.Wifi
    ConnectionType.CELLULAR -> Icons.Rounded.SignalCellularAlt
    ConnectionType.ETHERNET -> Icons.Rounded.SettingsEthernet
    ConnectionType.OTHER -> Icons.Rounded.Dns
    ConnectionType.NONE -> Icons.Rounded.WifiOff
}
