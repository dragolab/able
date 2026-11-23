package com.dragolabs.able.feature.apps.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.InlineLoading
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.apps.BlockReason
import com.dragolabs.able.feature.apps.HelpSectionHeader
import com.dragolabs.able.feature.apps.R
import com.dragolabs.able.feature.apps.data.PackageActionOutcome

/** Callbacks of the "Background activity" detail section. */
internal class BackgroundDetailActions(
    val onSetAllowed: (Boolean) -> Unit,
    val onSetBucket: (StandbyBucket) -> Unit,
    val onDismissResult: () -> Unit,
    val onRequestShizukuPermission: () -> Unit,
)

/**
 * "Background activity" section of the app detail pane: current state read back from Android,
 * the Allow / Restrict switch and the standby bucket. Without Shizuku only the battery
 * optimization exemption is shown (public API) together with how to get access.
 */
@Composable
internal fun BackgroundActivitySection(
    state: BackgroundDetailUi?,
    shizuku: ShizukuConnectionState,
    actions: BackgroundDetailActions,
) {
    if (state == null || !state.installed) return
    val haptics = rememberAbleHaptics()
    var confirmRestrict by rememberSaveable(state.packageName) { mutableStateOf(false) }
    var pickBucket by rememberSaveable(state.packageName) { mutableStateOf(false) }

    val done = state.operation as? BackgroundOperationUi.Done
    LaunchedEffect(done) {
        when (done?.outcome) {
            PackageActionOutcome.APPLIED -> haptics.confirm()
            PackageActionOutcome.REJECTED, PackageActionOutcome.NOT_CONFIRMED -> haptics.reject()
            null -> Unit
        }
    }

    HelpSectionHeader(
        title = stringResource(R.string.apps_section_background),
        helpTitle = stringResource(R.string.apps_help_background_title),
        helpText = stringResource(R.string.apps_help_background_text),
        modifier = Modifier.padding(top = AbleSpacing.sm),
    )

    val snapshot = (state.reading as? DetailReading.Loaded)?.snapshot
    val exempt = when (val reading = state.reading) {
        is DetailReading.Loaded -> reading.snapshot.batteryOptimizationExempt
        is DetailReading.Limited -> reading.batteryOptimizationExempt
        else -> null
    }
    val status = snapshot?.status ?: BackgroundStatus.UNKNOWN
    AbleCard(
        title = stringResource(R.string.apps_bg_card_title),
        icon = Icons.Rounded.PlayCircle,
        trailing = { if (state.reading == DetailReading.Loading) InlineLoading() },
        status = {
            StatusPill(stringResource(status.labelRes()), status.level())
            snapshot?.bucket?.let { StatusPill(stringResource(R.string.apps_bucket_pill, stringResource(it.labelRes())), StatusLevel.INFO) }
            when (exempt) {
                true -> StatusPill(stringResource(R.string.apps_bg_exempt_yes), StatusLevel.INFO)
                false -> StatusPill(stringResource(R.string.apps_bg_exempt_no), StatusLevel.INFO)
                null -> Unit
            }
        },
    ) {
        Text(
            stringResource(
                when (status) {
                    BackgroundStatus.ALLOWED -> R.string.apps_bg_state_allowed_message
                    BackgroundStatus.RESTRICTED -> R.string.apps_bg_state_restricted_message
                    BackgroundStatus.PARTIAL -> R.string.apps_bg_state_partial_message
                    BackgroundStatus.UNKNOWN -> if (state.reading is DetailReading.Failed) {
                        R.string.apps_bg_state_failed_message
                    } else {
                        R.string.apps_bg_state_unknown_message
                    }
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (exempt == true) {
            Text(
                stringResource(R.string.apps_bg_exempt_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    OperationBanner(state, actions.onDismissResult)

    val running = state.operation is BackgroundOperationUi.Running
    val block = state.blockReason
    ListGroup {
        when {
            block != null -> item { shape ->
                ActionRow(
                    title = stringResource(
                        when (block) {
                            BlockReason.SELF -> R.string.apps_bg_blocked_self
                            BlockReason.SHIZUKU -> R.string.apps_bg_blocked_shizuku
                            BlockReason.CORE_SYSTEM -> R.string.apps_bg_blocked_core
                        },
                    ),
                    icon = Icons.Rounded.Lock,
                    shape = shape,
                )
            }
            shizuku == ShizukuConnectionState.READY -> {
                item { shape ->
                    SwitchRow(
                        title = stringResource(R.string.apps_bg_switch_title),
                        supportingText = stringResource(
                            if (running) R.string.apps_operation_running else R.string.apps_bg_switch_message,
                        ),
                        checked = status == BackgroundStatus.ALLOWED,
                        enabled = !running && snapshot != null,
                        onCheckedChange = { allow ->
                            if (allow) actions.onSetAllowed(true) else confirmRestrict = true
                        },
                        icon = Icons.Rounded.BatteryChargingFull,
                        shape = shape,
                    )
                }
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.apps_bucket_title),
                        supportingText = snapshot?.let { snap ->
                            snap.bucket?.let { stringResource(it.labelRes()) }
                                ?: snap.bucketValue?.let { stringResource(R.string.apps_bucket_other, it) }
                        } ?: stringResource(R.string.apps_value_unavailable),
                        icon = Icons.Rounded.Schedule,
                        enabled = !running && snapshot != null,
                        shape = shape,
                        trailing = {
                            HelpButton(title = stringResource(R.string.apps_help_bucket_title), text = stringResource(R.string.apps_help_bucket_text))
                        },
                        onClick = if (running || snapshot == null) null else ({ pickBucket = true }),
                    )
                }
            }
            shizuku == ShizukuConnectionState.WAITING_FOR_PERMISSION || shizuku == ShizukuConnectionState.PERMISSION_DENIED -> item { shape ->
                ActionRow(
                    title = stringResource(R.string.apps_bg_switch_title),
                    supportingText = stringResource(R.string.apps_bg_needs_permission),
                    icon = Icons.Rounded.BatteryChargingFull,
                    shape = shape,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AbleTextButton(onClick = actions.onRequestShizukuPermission) { Text(stringResource(R.string.apps_shizuku_grant)) }
                            HelpButton(title = stringResource(R.string.apps_help_shizuku_title), text = stringResource(R.string.apps_help_bg_shizuku_text))
                        }
                    },
                )
            }
            else -> item { shape ->
                ActionRow(
                    title = stringResource(R.string.apps_bg_switch_title),
                    supportingText = stringResource(R.string.apps_bg_needs_shizuku),
                    icon = Icons.Rounded.BatteryChargingFull,
                    enabled = false,
                    shape = shape,
                    trailing = {
                        HelpButton(title = stringResource(R.string.apps_help_shizuku_title), text = stringResource(R.string.apps_help_bg_shizuku_text))
                    },
                )
            }
        }
    }

    if (confirmRestrict) {
        RestrictConfirmDialog(
            summary = BulkSummary(
                total = 1,
                changeable = 1,
                skipped = 0,
                protectedCount = if (state.isProtected) 1 else 0,
                riskyCount = if (state.risky) 1 else 0,
            ),
            label = state.label,
            onConfirm = {
                confirmRestrict = false
                actions.onSetAllowed(false)
            },
            onDismiss = { confirmRestrict = false },
        )
    }
    if (pickBucket) {
        BucketPickerDialog(
            current = snapshot?.bucket,
            onSelect = { bucket ->
                pickBucket = false
                if (bucket != snapshot?.bucket) actions.onSetBucket(bucket)
            },
            onDismiss = { pickBucket = false },
        )
    }
}

@Composable
private fun OperationBanner(state: BackgroundDetailUi, onDismiss: () -> Unit) {
    val done = state.operation as? BackgroundOperationUi.Done ?: return
    val what = when (val change = done.change) {
        is BackgroundChange.SetBackground -> stringResource(
            if (change.allowed) R.string.apps_bg_change_allow else R.string.apps_bg_change_restrict,
        )
        is BackgroundChange.SetBucket -> stringResource(R.string.apps_bg_change_bucket, stringResource(change.bucket.labelRes()))
    }
    val (level, titleRes, messageRes) = when (done.outcome) {
        PackageActionOutcome.APPLIED -> Triple(StatusLevel.SUCCESS, R.string.apps_bg_result_applied, R.string.apps_bg_result_applied_message)
        PackageActionOutcome.REJECTED -> Triple(StatusLevel.CRITICAL, R.string.apps_bg_result_rejected, R.string.apps_bg_result_rejected_message)
        PackageActionOutcome.NOT_CONFIRMED -> Triple(StatusLevel.WARNING, R.string.apps_bg_result_unconfirmed, R.string.apps_bg_result_unconfirmed_message)
    }
    val bucketNote = if (done.change is BackgroundChange.SetBucket && done.outcome != PackageActionOutcome.REJECTED) {
        "\n" + stringResource(R.string.apps_bucket_system_may_change)
    } else {
        ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        StatusBanner(
            level = level,
            title = stringResource(titleRes, what),
            message = stringResource(messageRes) + bucketNote + (done.detail?.let { "\n$it" } ?: ""),
            actionLabel = stringResource(R.string.apps_dismiss),
            onAction = onDismiss,
        )
    }
}
