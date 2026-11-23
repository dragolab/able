package com.dragolabs.able.feature.settings

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.settings.update.AvailableUpdate
import com.dragolabs.able.feature.settings.update.CheckState
import com.dragolabs.able.feature.settings.update.InstallFailure
import com.dragolabs.able.feature.settings.update.InstallState
import com.dragolabs.able.feature.settings.update.UpdateError
import com.dragolabs.able.feature.settings.update.UpdaterState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** User intents of the Updates section (UPD-001). */
class UpdateActions(
    val onCheck: () -> Unit = {},
    val onAutoCheckChanged: (Boolean) -> Unit = {},
    val onDownload: (AvailableUpdate) -> Unit = {},
    val onCancelDownload: () -> Unit = {},
    val onAllowInstalls: () -> Unit = {},
    val onContinueInstall: (Intent) -> Unit = {},
    val onOpenPage: (String) -> Unit = {},
    val onDismissInstall: () -> Unit = {},
)

/**
 * Only an update found by the automatic check (not yet acted on) is announced at the top of
 * Settings. Results of a manual check, downloads and installs stay inside the Updates section,
 * right under the button the user tapped (it used to jump to the top, out of view, so a found
 * update looked like nothing happened). The card is never shown twice.
 */
internal val UpdaterState.isProminent: Boolean
    get() = check is CheckState.KnownNewer && install == InstallState.Idle

internal fun LazyListScope.updateTopCard(state: UpdaterState, actions: UpdateActions) {
    if (!state.isProminent) return
    item(key = "update_top") { UpdateStatusCard(state, actions) }
}

internal fun LazyListScope.updatesSection(state: UpdaterState, appVersion: String, actions: UpdateActions) {
    header(R.string.settings_section_updates, help = R.string.settings_section_updates to R.string.settings_help_updates)
    item(key = "updates") {
        val context = LocalContext.current
        val checking = state.check == CheckState.Checking
        ListGroup {
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.settings_updates_current_title),
                    shape = shape,
                    supportingText = appVersion.ifEmpty { stringResource(R.string.settings_about_version_unknown) },
                    icon = Icons.Rounded.Info,
                    showChevron = false,
                )
            }
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.settings_updates_check_title),
                    shape = shape,
                    supportingText = when {
                        checking -> stringResource(R.string.settings_updates_checking)
                        state.lastCheckEpochMs > 0 -> stringResource(
                            R.string.settings_updates_last_check,
                            DateUtils.formatDateTime(
                                context,
                                state.lastCheckEpochMs,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                            ),
                        )
                        else -> stringResource(R.string.settings_updates_check_supporting)
                    },
                    icon = Icons.Rounded.Refresh,
                    enabled = !checking && state.install !is InstallState.Downloading && state.install != InstallState.Installing,
                    onClick = actions.onCheck,
                )
            }
            item { shape ->
                SwitchRow(
                    title = stringResource(R.string.settings_updates_auto_title),
                    checked = state.autoCheck,
                    onCheckedChange = actions.onAutoCheckChanged,
                    shape = shape,
                    supportingText = stringResource(R.string.settings_updates_auto_supporting),
                    icon = Icons.Rounded.Update,
                )
            }
        }
    }
    if (!state.isProminent && (state.check != CheckState.Idle || state.install != InstallState.Idle)) {
        item(key = "updates_result") { UpdateStatusCard(state, actions) }
    }
}

@Composable
private fun UpdateStatusCard(state: UpdaterState, actions: UpdateActions) {
    val context = LocalContext.current
    when (val install = state.install) {
        InstallState.Idle -> CheckResult(state.check, actions)
        InstallState.NeedsPermission -> StatusBanner(
            level = StatusLevel.WARNING,
            title = stringResource(R.string.settings_updates_permission_title),
            message = stringResource(R.string.settings_updates_permission_body),
            actionLabel = stringResource(R.string.settings_updates_action_allow),
            onAction = actions.onAllowInstalls,
            secondaryActionLabel = stringResource(R.string.settings_action_cancel),
            onSecondaryAction = actions.onDismissInstall,
        )
        is InstallState.Downloading -> AbleCard(style = AbleCardStyle.Tonal) {
            Column(
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
            ) {
                Text(
                    stringResource(
                        R.string.settings_updates_downloading,
                        Formatter.formatShortFileSize(context, install.bytes),
                        Formatter.formatShortFileSize(context, install.total),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val fraction = if (install.total > 0) (install.bytes.toFloat() / install.total).coerceIn(0f, 1f) else 0f
                LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AbleTextButton(onClick = actions.onCancelDownload) {
                        Text(stringResource(R.string.settings_updates_action_cancel_download))
                    }
                }
            }
        }
        InstallState.Installing -> AbleCard(style = AbleCardStyle.Tonal) {
            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                Text(stringResource(R.string.settings_updates_installing), style = MaterialTheme.typography.bodyMedium)
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        is InstallState.AwaitingConfirmation -> StatusBanner(
            level = StatusLevel.INFO,
            title = stringResource(R.string.settings_updates_confirm),
            actionLabel = stringResource(R.string.settings_updates_action_continue),
            onAction = { actions.onContinueInstall(install.confirmIntent) },
            secondaryActionLabel = stringResource(R.string.settings_action_cancel),
            onSecondaryAction = actions.onDismissInstall,
        )
        InstallState.Installed -> StatusBanner(
            level = StatusLevel.SUCCESS,
            title = stringResource(R.string.settings_updates_installed),
            secondaryActionLabel = stringResource(R.string.settings_action_close),
            onSecondaryAction = actions.onDismissInstall,
        )
        is InstallState.Failed -> {
            val retry = (state.check as? CheckState.Available)?.update
            val body = installFailureText(context, install.failure)
            val detail = install.detail?.takeIf { it.isNotBlank() && install.failure !is InstallFailure.Download }
            StatusBanner(
                level = if (install.failure == InstallFailure.Aborted) StatusLevel.INFO else StatusLevel.CRITICAL,
                title = stringResource(
                    if (install.failure is InstallFailure.Download) {
                        R.string.settings_updates_install_download_failed_title
                    } else {
                        R.string.settings_updates_install_failed_title
                    },
                ),
                message = if (detail != null) body + "\n" + stringResource(R.string.settings_updates_install_detail, detail) else body,
                actionLabel = retry?.let { stringResource(R.string.settings_updates_action_retry) },
                onAction = retry?.let { { actions.onDownload(it) } },
                secondaryActionLabel = stringResource(R.string.settings_action_close),
                onSecondaryAction = actions.onDismissInstall,
            )
        }
    }
}

@Composable
private fun CheckResult(check: CheckState, actions: UpdateActions) {
    val context = LocalContext.current
    when (check) {
        CheckState.Idle -> Unit
        CheckState.Checking -> LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        is CheckState.UpToDate -> StatusBanner(
            level = StatusLevel.SUCCESS,
            title = stringResource(R.string.settings_updates_up_to_date),
            message = check.latest?.let { stringResource(R.string.settings_updates_up_to_date_latest, it.toString()) }
                ?: stringResource(R.string.settings_updates_no_releases),
        )
        is CheckState.KnownNewer -> StatusBanner(
            level = StatusLevel.INFO,
            title = stringResource(R.string.settings_updates_available_title, check.version.toString()),
            message = stringResource(R.string.settings_updates_known_newer_body),
            actionLabel = stringResource(R.string.settings_updates_action_check_now),
            onAction = actions.onCheck,
        )
        is CheckState.Available -> AvailableCard(check.update, actions)
        is CheckState.NoApk -> StatusBanner(
            level = StatusLevel.WARNING,
            title = stringResource(R.string.settings_updates_no_apk_title, check.version.toString()),
            message = stringResource(R.string.settings_updates_no_apk_body),
            actionLabel = stringResource(R.string.settings_updates_action_open_page),
            onAction = { actions.onOpenPage(check.pageUrl) },
        )
        is CheckState.Failed -> StatusBanner(
            level = StatusLevel.WARNING,
            title = stringResource(R.string.settings_updates_error_title),
            message = updateErrorText(context, check.error),
            actionLabel = stringResource(R.string.settings_updates_action_retry),
            onAction = actions.onCheck,
        )
    }
}

@Composable
private fun AvailableCard(update: AvailableUpdate, actions: UpdateActions) {
    val context = LocalContext.current
    var showNotes by rememberSaveable(update.tag) { mutableStateOf(false) }
    val size = Formatter.formatShortFileSize(context, update.apkSize)
    val published = formatPublished(update.publishedAt)
    StatusBanner(
        level = StatusLevel.INFO,
        title = stringResource(R.string.settings_updates_available_title, update.version.toString()),
        message = if (published != null) {
            stringResource(R.string.settings_updates_available_meta, published, size)
        } else {
            stringResource(R.string.settings_updates_available_size, size)
        },
        actionLabel = stringResource(R.string.settings_updates_action_download),
        onAction = { actions.onDownload(update) },
        secondaryActionLabel = stringResource(R.string.settings_updates_action_notes),
        onSecondaryAction = { showNotes = true },
    )
    if (showNotes) {
        AlertDialog(
            onDismissRequest = { showNotes = false },
            title = { Text(stringResource(R.string.settings_updates_notes_title, update.title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                    Text(
                        update.notes.ifEmpty { stringResource(R.string.settings_updates_notes_empty) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                AbleButton(onClick = { showNotes = false; actions.onDownload(update) }) {
                    Text(stringResource(R.string.settings_updates_action_download))
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AbleTextButton(onClick = { actions.onOpenPage(update.pageUrl) }) {
                        Text(stringResource(R.string.settings_updates_action_open_page))
                    }
                    AbleTextButton(onClick = { showNotes = false }) { Text(stringResource(R.string.settings_action_close)) }
                }
            },
        )
    }
}

private fun formatPublished(iso: String?): String? = runCatching {
    Instant.parse(iso ?: return null)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.getOrNull()

internal fun updateErrorText(context: Context, error: UpdateError): String = when (error) {
    UpdateError.Offline -> context.getString(R.string.settings_updates_error_offline)
    UpdateError.Timeout -> context.getString(R.string.settings_updates_error_timeout)
    is UpdateError.RateLimited -> error.resetEpochSeconds
        ?.let { DateUtils.formatDateTime(context, it * 1000, DateUtils.FORMAT_SHOW_TIME) }
        ?.let { context.getString(R.string.settings_updates_error_rate_limited_until, it) }
        ?: context.getString(R.string.settings_updates_error_rate_limited)
    is UpdateError.Http -> context.getString(R.string.settings_updates_error_http, error.code)
    UpdateError.InvalidResponse -> context.getString(R.string.settings_updates_error_invalid)
    UpdateError.InstalledVersionUnknown -> context.getString(R.string.settings_updates_error_version_unknown)
    UpdateError.Storage -> context.getString(R.string.settings_updates_error_storage)
    is UpdateError.SizeMismatch -> context.getString(
        R.string.settings_updates_error_size,
        Formatter.formatShortFileSize(context, error.expected),
        Formatter.formatShortFileSize(context, error.actual),
    )
    UpdateError.NotAbleApk -> context.getString(R.string.settings_updates_error_not_able)
}

private fun installFailureText(context: Context, failure: InstallFailure): String = when (failure) {
    is InstallFailure.Download -> updateErrorText(context, failure.error)
    InstallFailure.Signature -> context.getString(R.string.settings_updates_install_signature)
    InstallFailure.Aborted -> context.getString(R.string.settings_updates_install_aborted)
    InstallFailure.Blocked -> context.getString(R.string.settings_updates_install_blocked)
    InstallFailure.InvalidApk -> context.getString(R.string.settings_updates_install_invalid)
    InstallFailure.StorageFull -> context.getString(R.string.settings_updates_install_storage)
    InstallFailure.Generic -> context.getString(R.string.settings_updates_install_generic)
}
