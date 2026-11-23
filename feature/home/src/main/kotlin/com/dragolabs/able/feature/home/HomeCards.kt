package com.dragolabs.able.feature.home

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.data.AccessSnapshot
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleSharedKeys
import com.dragolabs.able.core.designsystem.component.ActionTile
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.AbleSharedBounds
import com.dragolabs.able.core.designsystem.component.labelRes
import com.dragolabs.able.core.designsystem.component.statusLevel
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.ActionAuditRecord
import com.dragolabs.able.core.model.HomeCardType
import com.dragolabs.able.core.model.ToolId

// ---------------------------------------------------------------------------------------------
// Safety banners (HOME-003)
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SafetyBanner(
    alert: HomeSafetyAlert,
    onRequestShizukuPermission: () -> Unit,
    onShowFailures: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (alert) {
        HomeSafetyAlert.ShizukuWaitingForPermission -> StatusBanner(
            modifier = modifier,
            level = StatusLevel.WARNING,
            title = stringResource(R.string.home_alert_shizuku_waiting_title),
            message = stringResource(R.string.home_alert_shizuku_waiting_message),
            actionLabel = stringResource(R.string.home_alert_shizuku_grant),
            onAction = onRequestShizukuPermission,
        )
        HomeSafetyAlert.ShizukuPermissionDenied -> StatusBanner(
            modifier = modifier,
            level = StatusLevel.WARNING,
            title = stringResource(R.string.home_alert_shizuku_denied_title),
            message = stringResource(R.string.home_alert_shizuku_denied_message),
            actionLabel = stringResource(R.string.home_alert_shizuku_retry),
            onAction = onRequestShizukuPermission,
        )
        is HomeSafetyAlert.RecentFailures -> {
            val latest = alert.failures.first()
            StatusBanner(
                modifier = modifier,
                level = StatusLevel.CRITICAL,
                title = pluralStringResource(R.plurals.home_alert_failures_title, alert.failures.size, alert.failures.size),
                message = latest.detailMessage,
                actionLabel = stringResource(R.string.home_alert_failures_details),
                onAction = onShowFailures,
            )
        }
    }
}

@Composable
internal fun RecentFailuresDialog(
    failures: List<ActionAuditRecord>,
    onDismiss: () -> Unit,
    onOpenBattery: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_failures_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            ) {
                Text(stringResource(R.string.home_failures_dialog_message), style = MaterialTheme.typography.bodyMedium)
                failures.forEach { ActionRecordRow(it) }
            }
        },
        confirmButton = { TextButton(onClick = onOpenBattery) { Text(stringResource(R.string.home_open_battery)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_close)) } },
    )
}

// ---------------------------------------------------------------------------------------------
// BATTERY_PROFILE (hero, HOME-001)
// ---------------------------------------------------------------------------------------------

@Composable
internal fun BatteryProfileCard(state: HomeUiState, onOpenBattery: () -> Unit) {
    val status = state.battery
    val profileNames = state.activeProfiles.map { p -> p.builtInType?.let { stringResource(it.labelRes()) } ?: p.name }
    AbleCard(
        title = stringResource(R.string.home_card_battery_title),
        subtitle = if (profileNames.isEmpty()) {
            stringResource(R.string.home_battery_no_active_profile)
        } else {
            stringResource(R.string.home_battery_active_profiles, profileNames.joinToString(", "))
        },
        icon = HomeCardType.BATTERY_PROFILE.icon(),
        iconShape = MaterialShapes.Sunny,
        style = AbleCardStyle.Hero,
        onClick = onOpenBattery,
    ) {
        val level = status?.levelPercent
        when {
            !state.hasBattery -> Text(
                stringResource(R.string.home_battery_not_present),
                style = MaterialTheme.typography.bodyLarge,
            )
            status == null -> Text(stringResource(R.string.home_loading), style = MaterialTheme.typography.bodyLarge)
            else -> {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
                    Text(
                        text = level?.let { stringResource(R.string.home_battery_percent, it) }
                            ?: stringResource(R.string.home_value_unavailable),
                        style = MaterialTheme.typography.displayMedium,
                    )
                    val plugLabel = status.plugType.labelRes()?.let { stringResource(it) }
                    val stateLabel = stringResource(status.state.labelRes())
                    Text(
                        text = if (plugLabel != null) stringResource(R.string.home_battery_state_with_plug, stateLabel, plugLabel) else stateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (level != null) {
                    LinearWavyProgressIndicator(
                        progress = { level / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            profileNames.forEach { name -> StatusPill(text = name, level = StatusLevel.INFO) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// QUICK_ACTIONS (HOME-001)
// ---------------------------------------------------------------------------------------------

private data class QuickTool(val id: ToolId, val icon: ImageVector, val labelRes: Int)

private val QuickTools = listOf(
    QuickTool(ToolId.TORCH, Icons.Rounded.FlashlightOn, R.string.home_tool_torch),
    QuickTool(ToolId.SCREEN_LOCK, Icons.Rounded.Lock, R.string.home_tool_lock),
    QuickTool(ToolId.GPS, Icons.Rounded.MyLocation, R.string.home_tool_gps),
    QuickTool(ToolId.STORAGE, Icons.Rounded.SdStorage, R.string.home_tool_storage),
    QuickTool(ToolId.SYSTEM_TOGGLES, Icons.Rounded.ToggleOn, R.string.home_tool_toggles),
)

@Composable
internal fun QuickActionsCard(onOpenTool: (ToolId) -> Unit, onOpenTools: () -> Unit) {
    AbleCard(
        title = stringResource(R.string.home_card_quick_actions_title),
        icon = HomeCardType.QUICK_ACTIONS.icon(),
        iconShape = MaterialShapes.Clover4Leaf,
    ) {
        TileGrid(
            tiles = QuickTools.map { tool ->
                TileSpec(
                    icon = tool.icon,
                    label = stringResource(tool.labelRes),
                    sharedKey = AbleSharedKeys.tool(HomeOrigins.QUICK, tool.id.key),
                ) { onOpenTool(tool.id) }
            } + TileSpec(Icons.Rounded.Apps, stringResource(R.string.home_tool_all), onClick = onOpenTools),
        )
    }
}

internal class TileSpec(
    val icon: ImageVector,
    val label: String,
    val supportingText: String? = null,
    val enabled: Boolean = true,
    /** App-open transform key of the screen this tile opens; null for tabs/unavailable tiles. */
    val sharedKey: String? = null,
    val onClick: () -> Unit,
)

/** Equal-width tile rows; 2 columns on narrow cards, 3 on wider ones. */
@Composable
internal fun TileGrid(tiles: List<TileSpec>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 420.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                    row.forEach { tile ->
                        AbleSharedBounds(
                            key = tile.sharedKey.takeIf { tile.enabled },
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, // ActionTile color
                        ) {
                            ActionTile(
                                icon = tile.icon,
                                label = tile.label,
                                supportingText = tile.supportingText,
                                enabled = tile.enabled,
                                onClick = tile.onClick,
                            )
                        }
                    }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// CAPABILITY_STATUS
// ---------------------------------------------------------------------------------------------

@Composable
internal fun CapabilityStatusCard(access: AccessSnapshot, onOpenSettings: () -> Unit) {
    AbleCard(
        title = stringResource(R.string.home_card_capabilities_title),
        subtitle = stringResource(R.string.home_capabilities_subtitle),
        icon = HomeCardType.CAPABILITY_STATUS.icon(),
        iconShape = MaterialShapes.Pentagon,
        onClick = onOpenSettings,
    ) {
        CapabilityLine(
            name = stringResource(R.string.home_capability_shizuku),
            value = stringResource(access.shizuku.labelRes()),
            level = access.shizuku.statusLevel(),
        )
        if (access.shizuku == ShizukuConnectionState.NOT_AVAILABLE) {
            Text(
                stringResource(R.string.home_capability_shizuku_optional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CapabilityLine(
            name = stringResource(R.string.home_capability_accessibility),
            value = grantLabel(access.accessibilityEnabled),
            level = grantLevel(access.accessibilityEnabled),
        )
        CapabilityLine(
            name = stringResource(R.string.home_capability_usage),
            value = grantLabel(access.usageAccess),
            level = grantLevel(access.usageAccess),
        )
        CapabilityLine(
            name = stringResource(R.string.home_capability_notifications),
            value = grantLabel(access.notifications),
            level = grantLevel(access.notifications),
        )
    }
}

@Composable
private fun grantLabel(granted: Boolean) =
    stringResource(if (granted) R.string.home_capability_granted else R.string.home_capability_not_granted)

private fun grantLevel(granted: Boolean) = if (granted) StatusLevel.SUCCESS else StatusLevel.INFO

@Composable
private fun CapabilityLine(name: String, value: String, level: StatusLevel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md),
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        StatusPill(text = value, level = level)
    }
}

// ---------------------------------------------------------------------------------------------
// RECENT_WARNINGS
// ---------------------------------------------------------------------------------------------

@Composable
internal fun RecentActionsCard(actions: List<ActionAuditRecord>, onOpenBattery: () -> Unit) {
    AbleCard(
        title = stringResource(R.string.home_card_recent_title),
        icon = HomeCardType.RECENT_WARNINGS.icon(),
        iconShape = MaterialShapes.Arch,
    ) {
        if (actions.isEmpty()) {
            Text(
                stringResource(R.string.home_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            actions.forEach { ActionRecordRow(it) }
        }
        OutlinedButton(onClick = onOpenBattery) { Text(stringResource(R.string.home_open_battery)) }
    }
}

@Composable
private fun ActionRecordRow(record: ActionAuditRecord) {
    val now = System.currentTimeMillis()
    val time = DateUtils.getRelativeTimeSpanString(record.timestampEpochMs, now, DateUtils.MINUTE_IN_MILLIS).toString()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                record.detailMessage.ifBlank { record.actionId },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(record.targetPackage, time).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusPill(text = stringResource(record.status.labelRes()), level = record.status.statusLevel())
    }
}

// ---------------------------------------------------------------------------------------------
// STORAGE_SUMMARY (STO-001)
// ---------------------------------------------------------------------------------------------

@Composable
internal fun StorageSummaryCard(storage: StorageCardState, onOpenStorage: () -> Unit) {
    val context = LocalContext.current
    AbleSharedBounds(
        key = AbleSharedKeys.tool(HomeOrigins.STORAGE, ToolId.STORAGE.key),
        containerColor = MaterialTheme.colorScheme.secondaryContainer, // Tonal card
    ) {
        AbleCard(
            title = stringResource(R.string.home_card_storage_title),
            icon = HomeCardType.STORAGE_SUMMARY.icon(),
            iconShape = MaterialShapes.Square,
            style = AbleCardStyle.Tonal,
            onClick = onOpenStorage,
        ) {
            when (storage) {
                StorageCardState.Loading -> Text(stringResource(R.string.home_loading))
                StorageCardState.Unavailable -> Text(stringResource(R.string.home_storage_unavailable))
                is StorageCardState.Available -> {
                    val s = storage.summary
                    val free = Formatter.formatShortFileSize(context, s.availableBytes)
                    val total = Formatter.formatShortFileSize(context, s.totalBytes)
                    val used = Formatter.formatShortFileSize(context, s.usedBytes)
                    val description = stringResource(R.string.home_storage_free_of_total, free, total)
                    Text(description, style = MaterialTheme.typography.titleLarge)
                    LinearWavyProgressIndicator(
                        progress = { s.usedFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = description },
                    )
                    Text(
                        stringResource(R.string.home_storage_used, used, (s.usedFraction * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(stringResource(R.string.home_storage_open), style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// SENSORS_OVERVIEW
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SensorsCard(hasAccelerometer: Boolean, hasMagnetometer: Boolean, onOpenTool: (ToolId) -> Unit) {
    val unavailable = stringResource(R.string.home_sensor_unavailable)
    AbleCard(
        title = stringResource(R.string.home_card_sensors_title),
        icon = HomeCardType.SENSORS_OVERVIEW.icon(),
        iconShape = MaterialShapes.Gem,
    ) {
        TileGrid(
            tiles = listOf(
                TileSpec(
                    icon = Icons.Rounded.Vibration,
                    label = stringResource(R.string.home_tool_level),
                    supportingText = if (hasAccelerometer) null else unavailable,
                    enabled = hasAccelerometer,
                    sharedKey = AbleSharedKeys.tool(HomeOrigins.SENSORS, ToolId.LEVEL.key),
                ) { onOpenTool(ToolId.LEVEL) },
                TileSpec(
                    icon = Icons.Rounded.Straighten,
                    label = stringResource(R.string.home_tool_ruler),
                    sharedKey = AbleSharedKeys.tool(HomeOrigins.SENSORS, ToolId.RULER.key),
                ) { onOpenTool(ToolId.RULER) },
                TileSpec(
                    icon = Icons.Rounded.Explore,
                    label = stringResource(R.string.home_tool_compass),
                    supportingText = if (hasMagnetometer) null else unavailable,
                    enabled = hasMagnetometer,
                    sharedKey = AbleSharedKeys.tool(HomeOrigins.SENSORS, ToolId.COMPASS.key),
                ) { onOpenTool(ToolId.COMPASS) },
            ),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// GEO_POINT_QUICK
// ---------------------------------------------------------------------------------------------

@Composable
internal fun GeoPointCard(onOpenGps: () -> Unit) {
    AbleSharedBounds(
        key = AbleSharedKeys.tool(HomeOrigins.GEO, ToolId.GPS.key),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, // Standard card
    ) {
        AbleCard(
            title = stringResource(R.string.home_card_geo_title),
            subtitle = stringResource(R.string.home_geo_subtitle),
            icon = HomeCardType.GEO_POINT_QUICK.icon(),
            iconShape = MaterialShapes.Ghostish,
            onClick = onOpenGps,
        ) {
            Text(
                stringResource(R.string.home_geo_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
