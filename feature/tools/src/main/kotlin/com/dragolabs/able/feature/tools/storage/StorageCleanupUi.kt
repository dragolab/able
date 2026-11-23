package com.dragolabs.able.feature.tools.storage

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.AbleFilledTonalButton
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.labelRes
import com.dragolabs.able.core.designsystem.component.listGroupItemShape
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.statusLevel
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.asString

/** "Clear all caches" through Shizuku, or the honest fallback to Android's own screens (STO-002). */
@Composable
internal fun SystemCacheCard(
    state: StorageUiState,
    onTrim: () -> Unit,
    onAllowShizuku: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AbleCard(
        modifier = modifier,
        title = stringResource(R.string.storage_system_cache_title),
        icon = Icons.Rounded.CleaningServices,
        style = AbleCardStyle.Tonal,
        trailing = {
            if (state.shizukuReady) {
                HelpButton(title = stringResource(R.string.storage_trim_help_title), text = stringResource(R.string.storage_trim_help_text))
            } else {
                HelpButton(title = stringResource(R.string.storage_shizuku_help_title), text = stringResource(R.string.storage_shizuku_help_text))
            }
        },
        status = { StatusPill(text = stringResource(state.shizukuState.labelRes()), level = state.shizukuState.statusLevel()) },
    ) {
        if (state.shizukuReady) {
            Text(
                stringResource(R.string.storage_system_cache_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
                AbleButton(
                    onClick = { onTrim() },
                    enabled = !state.cleanupBusy,
                    modifier = Modifier.focusRing(),
                ) {
                    Icon(Icons.Rounded.CleaningServices, contentDescription = null)
                    Text(stringResource(R.string.storage_trim_action), modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
                }
                if (state.trimming) LoadingIndicator(modifier = Modifier.padding(2.dp))
            }
        } else {
            Text(
                stringResource(R.string.storage_shizuku_needed_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.shizukuState == ShizukuConnectionState.WAITING_FOR_PERMISSION) {
                AbleFilledTonalButton(onClick = { onAllowShizuku() }, modifier = Modifier.focusRing()) {
                    Text(stringResource(R.string.storage_shizuku_allow))
                }
            }
        }
        state.cleanupReport?.let { report ->
            StatusBanner(
                level = if (report.success) StatusLevel.SUCCESS else StatusLevel.WARNING,
                title = report.message.asString(),
            )
        }
        AbleTextButton(onClick = { onOpenStorageSettings() }, modifier = Modifier.focusRing()) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
            Text(stringResource(R.string.storage_open_storage_settings), modifier = Modifier.padding(start = AbleSpacing.sm))
        }
    }
}

/** Top apps by size with their cache. Multi-select clears caches with Shizuku; otherwise App info. */
@Composable
internal fun LargestAppsCard(
    state: StorageUiState,
    onToggleApp: (String) -> Unit,
    onClearSelected: () -> Unit,
    onOpenAppInfo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberAbleHaptics()
    AbleCard(
        modifier = modifier,
        title = stringResource(R.string.storage_largest_apps),
        icon = Icons.Rounded.Apps,
        trailing = if (state.shizukuReady) {
            { HelpButton(title = stringResource(R.string.storage_apps_help_title), text = stringResource(R.string.storage_apps_help_text)) }
        } else {
            null
        },
    ) {
        if (state.largestApps.isEmpty()) {
            Text(stringResource(R.string.storage_apps_empty), style = MaterialTheme.typography.bodyMedium)
            return@AbleCard
        }
        if (state.shizukuReady) {
            Text(
                stringResource(R.string.storage_apps_select_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            state.largestApps.forEachIndexed { index, app ->
                val shape = listGroupItemShape(index, state.largestApps.size)
                val selected = app.packageName in state.selectedApps
                val meta = stringResource(
                    R.string.storage_app_meta,
                    Formatter.formatShortFileSize(context, app.totalBytes),
                    Formatter.formatShortFileSize(context, app.cacheBytes),
                )
                val rowModifier = if (state.shizukuReady) {
                    Modifier
                        .clip(shape)
                        .focusRing(shape)
                        .toggleable(value = selected, role = Role.Checkbox, enabled = !state.cleanupBusy) {
                            haptics.tick(); onToggleApp(app.packageName)
                        }
                } else {
                    Modifier.clip(shape)
                }
                ListItem(
                    modifier = rowModifier.fillMaxWidth(),
                    colors = ListItemDefaults.colors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    leadingContent = if (state.shizukuReady) {
                        { Checkbox(checked = selected, onCheckedChange = null) }
                    } else {
                        null
                    },
                    headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(meta) },
                    trailingContent = {
                        val infoLabel = stringResource(R.string.storage_app_info, app.label)
                        AbleIconButton(
                            onClick = { onOpenAppInfo(app.packageName) },
                            modifier = Modifier.focusRing().semantics { contentDescription = infoLabel },
                        ) { Icon(Icons.Rounded.Info, contentDescription = null) }
                    },
                )
            }
        }
        if (state.shizukuReady) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
                AbleFilledTonalButton(
                    onClick = { onClearSelected() },
                    enabled = state.selectedApps.isNotEmpty() && !state.cleanupBusy,
                    modifier = Modifier.weight(1f, fill = false).focusRing(),
                ) {
                    Text(
                        stringResource(
                            R.string.storage_apps_clear_action,
                            state.selectedApps.size,
                            Formatter.formatShortFileSize(context, state.selectedAppsCacheBytes),
                        ),
                    )
                }
                if (state.clearingApps) LoadingIndicator(modifier = Modifier.padding(2.dp))
            }
        }
    }
}

/** Able's own cache: works without any special access. */
@Composable
internal fun AbleCacheCard(state: StorageUiState, onCleanCache: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AbleCard(
        modifier = modifier,
        title = stringResource(R.string.tools_storage_cache_title),
        subtitle = stringResource(R.string.tools_storage_cache_size, Formatter.formatShortFileSize(context, state.cacheBytes)),
        icon = Icons.Rounded.DeleteSweep,
        style = AbleCardStyle.Outlined,
    ) {
        Text(
            stringResource(R.string.storage_able_cache_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
            AbleOutlinedButton(
                onClick = { onCleanCache() },
                enabled = !state.cleaningCache && state.cacheBytes > 0,
                modifier = Modifier.focusRing(),
            ) {
                Text(stringResource(R.string.tools_storage_cache_action))
            }
            if (state.cleaningCache) LoadingIndicator(modifier = Modifier.padding(2.dp))
        }
    }
}
