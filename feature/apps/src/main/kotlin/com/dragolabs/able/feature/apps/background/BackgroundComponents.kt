package com.dragolabs.able.feature.apps.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleSegmentedButton
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.statusColors
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.apps.MiniPill
import com.dragolabs.able.feature.apps.R

// region Labels

internal fun BackgroundStatus.labelRes(): Int = when (this) {
    BackgroundStatus.ALLOWED -> R.string.apps_bg_status_allowed
    BackgroundStatus.RESTRICTED -> R.string.apps_bg_status_restricted
    BackgroundStatus.PARTIAL -> R.string.apps_bg_status_partial
    BackgroundStatus.UNKNOWN -> R.string.apps_bg_status_unknown
}

internal fun BackgroundStatus.level(): StatusLevel = when (this) {
    BackgroundStatus.ALLOWED -> StatusLevel.SUCCESS
    BackgroundStatus.RESTRICTED -> StatusLevel.WARNING
    BackgroundStatus.PARTIAL -> StatusLevel.WARNING
    BackgroundStatus.UNKNOWN -> StatusLevel.INFO
}

internal fun BackgroundStatus.icon(): ImageVector = when (this) {
    BackgroundStatus.ALLOWED -> Icons.Rounded.CheckCircle
    BackgroundStatus.RESTRICTED -> Icons.Rounded.Block
    BackgroundStatus.PARTIAL -> Icons.Rounded.RemoveCircleOutline
    BackgroundStatus.UNKNOWN -> Icons.AutoMirrored.Rounded.HelpOutline
}

internal fun StandbyBucket.labelRes(): Int = when (this) {
    StandbyBucket.EXEMPTED -> R.string.apps_bucket_exempted
    StandbyBucket.ACTIVE -> R.string.apps_bucket_active
    StandbyBucket.WORKING_SET -> R.string.apps_bucket_working_set
    StandbyBucket.FREQUENT -> R.string.apps_bucket_frequent
    StandbyBucket.RARE -> R.string.apps_bucket_rare
    StandbyBucket.RESTRICTED -> R.string.apps_bucket_restricted
    StandbyBucket.NEVER -> R.string.apps_bucket_never
}

internal fun StandbyBucket.descriptionRes(): Int = when (this) {
    StandbyBucket.EXEMPTED -> R.string.apps_bucket_exempted_desc
    StandbyBucket.ACTIVE -> R.string.apps_bucket_active_desc
    StandbyBucket.WORKING_SET -> R.string.apps_bucket_working_set_desc
    StandbyBucket.FREQUENT -> R.string.apps_bucket_frequent_desc
    StandbyBucket.RARE -> R.string.apps_bucket_rare_desc
    StandbyBucket.RESTRICTED -> R.string.apps_bucket_restricted_desc
    StandbyBucket.NEVER -> R.string.apps_bucket_never_desc
}

// endregion

// region Pills

/** Background status pill: icon + text, never color alone. */
@Composable
internal fun BackgroundStatusPill(status: BackgroundStatus, modifier: Modifier = Modifier) {
    val colors = statusColors(status.level())
    MiniPill(stringResource(status.labelRes()), status.icon(), colors.container, colors.content, colors.accent, modifier)
}

@Composable
internal fun BucketPill(bucket: StandbyBucket, modifier: Modifier = Modifier) {
    MiniPill(
        text = stringResource(R.string.apps_bucket_pill, stringResource(bucket.labelRes())),
        icon = Icons.Rounded.Schedule,
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
internal fun BatteryExemptPill(modifier: Modifier = Modifier) {
    val colors = statusColors(StatusLevel.INFO)
    MiniPill(
        stringResource(R.string.apps_bg_exempt_pill),
        Icons.Rounded.BatteryChargingFull,
        colors.container,
        colors.content,
        colors.accent,
        modifier,
    )
}

// endregion

/** "All apps / Background" switch at the top of the Apps list. */
@Composable
internal fun AppsViewSwitch(view: AppsView, onChange: (AppsView) -> Unit, modifier: Modifier = Modifier) {
    val views = AppsView.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().padding(bottom = AbleSpacing.sm)) {
        views.forEachIndexed { index, option ->
            AbleSegmentedButton(
                selected = option == view,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, views.size),
                icon = {
                    SegmentedButtonDefaults.Icon(option == view) {
                        Icon(
                            if (option == AppsView.ALL) Icons.Rounded.Apps else Icons.Rounded.BatteryChargingFull,
                            contentDescription = null,
                            modifier = Modifier.padding(0.dp),
                        )
                    }
                },
            ) {
                Text(stringResource(if (option == AppsView.ALL) R.string.apps_view_all else R.string.apps_view_background), maxLines = 1)
            }
        }
    }
}

/**
 * Confirmation before restricting background activity of one or more apps. States the count, the
 * consequences and how to undo; protected and risky (Expert/Unsafe/unlisted system) packages are
 * called out and need an explicit acknowledgement.
 */
@Composable
internal fun RestrictConfirmDialog(
    summary: BulkSummary,
    /** Set for a single app (detail pane); null for a bulk selection. */
    label: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberAbleHaptics()
    val needsAck = summary.riskyCount > 0
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (needsAck) Icons.Rounded.Warning else Icons.Rounded.Block, contentDescription = null) },
        title = {
            Text(
                if (label != null) {
                    stringResource(R.string.apps_bg_restrict_confirm_title_one, label)
                } else {
                    pluralStringResource(R.plurals.apps_bg_restrict_confirm_title, summary.changeable, summary.changeable)
                },
            )
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            ) {
                Text(stringResource(R.string.apps_bg_restrict_consequences))
                Text(stringResource(R.string.apps_bg_restrict_undo), style = MaterialTheme.typography.bodyMedium)
                if (summary.protectedCount > 0) {
                    WarningBox(
                        if (label != null) stringResource(R.string.apps_bg_warn_protected_one)
                        else pluralStringResource(R.plurals.apps_bg_warn_protected, summary.protectedCount, summary.protectedCount),
                        StatusLevel.WARNING,
                    )
                }
                if (summary.riskyCount > 0) {
                    WarningBox(
                        if (label != null) stringResource(R.string.apps_bg_warn_risky_one)
                        else pluralStringResource(R.plurals.apps_bg_warn_risky, summary.riskyCount, summary.riskyCount),
                        StatusLevel.CRITICAL,
                    )
                }
                if (summary.skipped > 0) {
                    Text(
                        pluralStringResource(R.plurals.apps_bg_skipped, summary.skipped, summary.skipped),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (needsAck) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = acknowledged, role = Role.Checkbox) {
                                haptics.toggle(it)
                                acknowledged = it
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                    ) {
                        Checkbox(checked = acknowledged, onCheckedChange = null)
                        Text(stringResource(R.string.apps_confirm_acknowledge), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            AbleButton(
                onClick = onConfirm,
                enabled = summary.changeable > 0 && (!needsAck || acknowledged),
                haptic = AbleHaptic.Reject,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.apps_bg_restrict_action)) }
        },
        dismissButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(R.string.apps_cancel)) } },
    )
}

/** Confirmation before allowing background activity of the selected apps again. */
@Composable
internal fun AllowConfirmDialog(summary: BulkSummary, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
        title = { Text(pluralStringResource(R.plurals.apps_bg_allow_confirm_title, summary.changeable, summary.changeable)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
                Text(stringResource(R.string.apps_bg_allow_consequences))
                if (summary.skipped > 0) {
                    Text(pluralStringResource(R.plurals.apps_bg_skipped, summary.skipped, summary.skipped), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            AbleButton(onClick = onConfirm, enabled = summary.changeable > 0, haptic = AbleHaptic.Confirm) {
                Text(stringResource(R.string.apps_bg_allow_action))
            }
        },
        dismissButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(R.string.apps_cancel)) } },
    )
}

@Composable
private fun WarningBox(text: String, level: StatusLevel) {
    val colors = statusColors(level)
    Surface(shape = MaterialTheme.shapes.medium, color = colors.container, contentColor = colors.content) {
        Row(Modifier.padding(AbleSpacing.md), horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = colors.accent)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Standby bucket picker (detail pane). The system may move the app again based on usage. */
@Composable
internal fun BucketPickerDialog(
    current: StandbyBucket?,
    onSelect: (StandbyBucket) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
        title = { Text(stringResource(R.string.apps_bucket_picker_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                Text(stringResource(R.string.apps_bucket_picker_note), style = MaterialTheme.typography.bodyMedium)
                ListGroup {
                    StandbyBucket.SETTABLE.forEach { bucket ->
                        item { shape ->
                            RadioRow(
                                title = stringResource(bucket.labelRes()),
                                supportingText = stringResource(bucket.descriptionRes()),
                                selected = bucket == current,
                                onSelect = { onSelect(bucket) },
                                shape = shape,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(R.string.apps_cancel)) } },
    )
}
