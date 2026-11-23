package com.dragolabs.able.feature.apps.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MobiledataOff
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import com.dragolabs.able.core.designsystem.component.AbleSingleChoiceToggleGroup
import java.text.DateFormat
import java.util.Date
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
    val onSetMode: (BackgroundMode) -> Unit,
    val onSetBucket: (StandbyBucket) -> Unit,
    val onSetBackgroundDataBlocked: (Boolean) -> Unit,
    val onMarkReviewed: () -> Unit,
    val onForgetReview: () -> Unit,
    val onRestoreReview: () -> Unit,
    val onDismissResult: () -> Unit,
    val onRequestShizukuPermission: () -> Unit,
)

/**
 * "Background activity" section of the app detail pane: current state read back from Android,
 * the Restricted / Optimized / Unrestricted choice, background mobile data, the standby bucket
 * and the "Reviewed" snapshot. Without Shizuku only the battery
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
            val mode = snapshot?.mode
            if (mode != null) StatusPill(stringResource(R.string.apps_bg_mode_pill, stringResource(mode.labelRes())), mode.level())
            if (mode == null || status == BackgroundStatus.PARTIAL) StatusPill(stringResource(status.labelRes()), status.level())
            if (snapshot?.backgroundDataBlocked == true) StatusPill(stringResource(R.string.apps_bg_data_pill), StatusLevel.WARNING)
            when (state.reviewState) {
                ReviewState.NONE -> Unit
                ReviewState.REVIEWED -> StatusPill(stringResource(R.string.apps_bg_reviewed_pill), StatusLevel.SUCCESS)
                ReviewState.CHANGED -> StatusPill(stringResource(R.string.apps_bg_changed_pill), StatusLevel.WARNING)
            }
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

    val ready = shizuku == ShizukuConnectionState.READY && state.blockReason == null
    if (state.reviewState == ReviewState.CHANGED && ready) {
        val separator = stringResource(R.string.apps_bg_review_separator)
        val fields = state.reviewDiff.map { stringResource(it.labelRes()) }.joinToString(separator)
        StatusBanner(
            level = StatusLevel.WARNING,
            title = stringResource(R.string.apps_bg_review_changed_title),
            message = stringResource(R.string.apps_bg_review_changed_message, fields),
            actionLabel = stringResource(R.string.apps_bg_review_restore),
            onAction = if (state.operation is BackgroundOperationUi.Running) null else actions.onRestoreReview,
            secondaryActionLabel = stringResource(R.string.apps_bg_review_keep),
            onSecondaryAction = actions.onMarkReviewed,
        )
    }
    if (state.reviewSaveFailed) {
        StatusBanner(level = StatusLevel.CRITICAL, title = stringResource(R.string.apps_bg_review_save_failed))
    }

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
                    ModeChoice(
                        snapshot = snapshot,
                        running = running,
                        shape = shape,
                        onSelect = { mode ->
                            if (mode == BackgroundMode.RESTRICTED) confirmRestrict = true else actions.onSetMode(mode)
                        },
                    )
                }
                item { shape ->
                    val blocked = snapshot?.backgroundDataBlocked
                    if (blocked != null) {
                        SwitchRow(
                            title = stringResource(R.string.apps_bg_data_title),
                            supportingText = stringResource(if (running) R.string.apps_operation_running else R.string.apps_bg_data_message),
                            checked = blocked,
                            enabled = !running,
                            onCheckedChange = actions.onSetBackgroundDataBlocked,
                            icon = Icons.Rounded.MobiledataOff,
                            shape = shape,
                        )
                    } else {
                        ActionRow(
                            title = stringResource(R.string.apps_bg_data_title),
                            supportingText = stringResource(
                                if (state.reading == DetailReading.Loading) R.string.apps_bg_loading else R.string.apps_bg_data_unavailable,
                            ),
                            icon = Icons.Rounded.MobiledataOff,
                            enabled = false,
                            shape = shape,
                            trailing = {
                                HelpButton(title = stringResource(R.string.apps_help_bg_data_title), text = stringResource(R.string.apps_help_bg_data_text))
                            },
                        )
                    }
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
                item { shape -> ReviewRow(state, snapshot, running, shape, actions) }
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
                actions.onSetMode(BackgroundMode.RESTRICTED)
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
        is BackgroundChange.SetMode -> stringResource(R.string.apps_bg_change_mode, stringResource(change.mode.labelRes()))
        is BackgroundChange.SetBackgroundData -> stringResource(
            if (change.blocked) R.string.apps_bg_change_data_block else R.string.apps_bg_change_data_allow,
        )
        is BackgroundChange.RestoreReview -> stringResource(R.string.apps_bg_change_review_restore)
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

/** Restricted / Optimized / Unrestricted, as in Android App battery usage. */
@Composable
private fun ModeChoice(snapshot: BackgroundSnapshot?, running: Boolean, shape: Shape, onSelect: (BackgroundMode) -> Unit) {
    val mode = snapshot?.mode
    val systemAllowlisted = snapshot?.systemAllowlisted == true
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = AbleSpacing.lg, vertical = AbleSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.apps_bg_mode_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                HelpButton(title = stringResource(R.string.apps_help_background_title), text = stringResource(R.string.apps_help_background_text))
            }
            AbleSingleChoiceToggleGroup(
                options = BackgroundMode.SELECTABLE,
                selected = mode,
                onSelect = onSelect,
                enabled = { option ->
                    !running && mode != null && (!systemAllowlisted || option == BackgroundMode.UNRESTRICTED)
                },
            ) { option ->
                Text(
                    stringResource(option.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
            Text(
                when {
                    running -> stringResource(R.string.apps_operation_running)
                    mode == null -> stringResource(R.string.apps_bg_mode_unknown_desc)
                    else -> stringResource(mode.descriptionRes())
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (systemAllowlisted) {
                Text(
                    stringResource(R.string.apps_bg_mode_system_allowlisted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (snapshot != null && PowerAllowlistKind.SYSTEM_EXCEPT_IDLE in snapshot.allowlistKinds) {
                Text(
                    stringResource(R.string.apps_bg_mode_system_excidle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Mark as reviewed" / "Reviewed on ..." row with Update and Forget. */
@Composable
private fun ReviewRow(
    state: BackgroundDetailUi,
    snapshot: BackgroundSnapshot?,
    running: Boolean,
    shape: Shape,
    actions: BackgroundDetailActions,
) {
    val review = state.review
    val canReview = !running && snapshot != null && reviewOf(snapshot, 0L) != null
    val help: @Composable () -> Unit = {
        HelpButton(title = stringResource(R.string.apps_help_bg_review_title), text = stringResource(R.string.apps_help_bg_review_text))
    }
    if (review == null) {
        ActionRow(
            title = stringResource(R.string.apps_bg_review_mark),
            supportingText = stringResource(if (canReview) R.string.apps_bg_review_mark_message else R.string.apps_bg_review_mark_unavailable),
            icon = Icons.Rounded.Verified,
            enabled = canReview,
            shape = shape,
            trailing = help,
            onClick = if (canReview) actions.onMarkReviewed else null,
        )
        return
    }
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(review.reviewedAtMs))
    ActionRow(
        title = stringResource(if (state.reviewState == ReviewState.CHANGED) R.string.apps_bg_review_changed_title else R.string.apps_bg_reviewed_title),
        supportingText = stringResource(
            if (state.reviewState == ReviewState.CHANGED) R.string.apps_bg_review_changed_row else R.string.apps_bg_reviewed_message,
            date,
        ),
        icon = Icons.Rounded.Verified,
        shape = shape,
        showChevron = false,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbleTextButton(onClick = actions.onMarkReviewed, enabled = canReview) { Text(stringResource(R.string.apps_bg_review_update)) }
                AbleTextButton(onClick = actions.onForgetReview) { Text(stringResource(R.string.apps_bg_review_forget)) }
            }
        },
    )
}
