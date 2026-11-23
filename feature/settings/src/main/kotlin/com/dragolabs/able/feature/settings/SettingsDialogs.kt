package com.dragolabs.able.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.DialogProperties
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.settings.backup.BackupArchive
import com.dragolabs.able.feature.settings.backup.BackupError
import com.dragolabs.able.feature.settings.backup.BackupExclusion
import com.dragolabs.able.feature.settings.backup.BackupSection
import com.dragolabs.able.feature.settings.backup.ConflictPolicy
import com.dragolabs.able.feature.settings.backup.HistorySummary
import com.dragolabs.able.feature.settings.backup.ImportMode
import com.dragolabs.able.feature.settings.backup.ImportReport
import com.dragolabs.able.feature.settings.backup.SectionPreview

fun BackupSection.labelRes(): Int = when (this) {
    BackupSection.BATTERY_PROFILES -> R.string.settings_backup_section_battery
    BackupSection.HOME_APPEARANCE -> R.string.settings_backup_section_home
    BackupSection.GPS_POINTS -> R.string.settings_backup_section_gps
    BackupSection.DNS_PROFILES -> R.string.settings_backup_section_dns
    BackupSection.SHORTCUTS -> R.string.settings_backup_section_shortcuts
    BackupSection.BATTERY_HISTORY -> R.string.settings_backup_section_history
}

private fun BackupSection.descriptionRes(): Int = when (this) {
    BackupSection.BATTERY_PROFILES -> R.string.settings_backup_section_battery_desc
    BackupSection.HOME_APPEARANCE -> R.string.settings_backup_section_home_desc
    BackupSection.GPS_POINTS -> R.string.settings_backup_section_gps_desc
    BackupSection.DNS_PROFILES -> R.string.settings_backup_section_dns_desc
    BackupSection.SHORTCUTS -> R.string.settings_backup_section_shortcuts_desc
    BackupSection.BATTERY_HISTORY -> R.string.settings_backup_section_history_desc
}

private fun BackupExclusion.labelRes(): Int = when (this) {
    BackupExclusion.BATTERY_HISTORY -> R.string.settings_backup_excluded_battery_history
    BackupExclusion.USAGE_DATA -> R.string.settings_backup_excluded_usage
    BackupExclusion.PERMISSION_GRANTS -> R.string.settings_backup_excluded_permissions
    BackupExclusion.SHIZUKU_STATE -> R.string.settings_backup_excluded_shizuku
    BackupExclusion.INSTALLED_APP_DATA -> R.string.settings_backup_excluded_app_data
    BackupExclusion.HOTSPOT_PASSWORDS -> R.string.settings_backup_excluded_hotspot
    BackupExclusion.TELEMETRY -> R.string.settings_backup_excluded_telemetry
    BackupExclusion.CUSTOM_ICON_FILES -> R.string.settings_backup_excluded_icons
}

@Composable
private fun DialogText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}

@Composable
private fun DialogLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

// --- Export ------------------------------------------------------------------------------------

@Composable
fun ExportDialog(
    draft: ExportDraft,
    onToggle: (BackupSection) -> Unit,
    onChooseDestination: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_backup_export_dialog_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
            ) {
                DialogText(stringResource(R.string.settings_backup_export_dialog_body))
                BackupSection.standard.forEach { section ->
                    ExportSectionRow(
                        section = section,
                        checked = section in draft.selected,
                        supporting = stringResource(section.descriptionRes()),
                        onToggle = { onToggle(section) },
                    )
                }
                // CFG-003: personal measurements are a separate, opt-in choice, never preselected.
                DialogLabel(stringResource(R.string.settings_backup_optional_title))
                val history = draft.history
                val historySelected = BackupSection.BATTERY_HISTORY in draft.selected
                ExportSectionRow(
                    section = BackupSection.BATTERY_HISTORY,
                    checked = historySelected,
                    enabled = history != null && history.sessions > 0,
                    supporting = stringResource(BackupSection.BATTERY_HISTORY.descriptionRes()) + "\n" + historySummaryText(history),
                    onToggle = { onToggle(BackupSection.BATTERY_HISTORY) },
                )
                StatusBanner(
                    level = if (historySelected) StatusLevel.WARNING else StatusLevel.INFO,
                    title = stringResource(R.string.settings_backup_history_privacy_title),
                    message = stringResource(R.string.settings_backup_history_privacy_body),
                )
                DialogLabel(stringResource(R.string.settings_backup_excluded_title))
                BackupExclusion.entries.forEach { DialogText("• " + stringResource(it.labelRes())) }
            }
        },
        confirmButton = {
            AbleButton(onClick = onChooseDestination, enabled = draft.selected.isNotEmpty()) {
                Text(stringResource(R.string.settings_backup_export_choose_destination))
            }
        },
        dismissButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_cancel)) } },
    )
}

@Composable
private fun ExportSectionRow(
    section: BackupSection,
    checked: Boolean,
    supporting: String,
    onToggle: () -> Unit,
    enabled: Boolean = true,
) {
    val haptics = rememberAbleHaptics()
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { haptics.toggle(it); onToggle() },
            )
            .focusRing(MaterialTheme.shapes.large),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        headlineContent = { Text(stringResource(section.labelRes())) },
        supportingContent = { Text(supporting) },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

@Composable
private fun historySummaryText(history: HistorySummary?): String = when {
    history == null -> stringResource(R.string.settings_backup_history_counting)
    history.sessions == 0 -> stringResource(R.string.settings_backup_history_empty)
    else -> stringResource(
        R.string.settings_backup_history_summary,
        pluralStringResource(R.plurals.settings_backup_history_sessions, history.sessions, history.sessions),
        pluralStringResource(R.plurals.settings_backup_history_notes, history.notes, history.notes),
        Formatter.formatShortFileSize(LocalContext.current, history.approxBytes),
    )
}

// --- Import ------------------------------------------------------------------------------------

@Composable
fun ImportDialog(
    stage: ImportStage,
    onSelectMode: (ImportMode) -> Unit,
    onSelectPolicy: (ConflictPolicy) -> Unit,
    onConfirm: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (stage) {
        ImportStage.Reading -> ProgressDialog(stringResource(R.string.settings_backup_import_reading))
        ImportStage.Applying -> ProgressDialog(stringResource(R.string.settings_backup_import_applying))
        is ImportStage.Preview -> PreviewDialog(stage, onSelectMode, onSelectPolicy, onConfirm, onDismiss)
        is ImportStage.Done -> ResultDialog(stage.report, stage.restore, onRestore, onDismiss)
        is ImportStage.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_backup_import_invalid_title)) },
            text = { DialogText(errorText(stage.error)) },
            confirmButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_close)) } },
        )
        ImportStage.RollbackFailed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_backup_import_not_applied_title)) },
            text = { DialogText(stringResource(R.string.settings_backup_import_rollback_failed)) },
            confirmButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_close)) } },
        )
    }
}

@Composable
private fun ProgressDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
                LoadingIndicator()
                DialogText(message)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun errorText(error: BackupError): String = when (error) {
    BackupError.NotAnArchive -> stringResource(R.string.settings_backup_error_not_archive)
    BackupError.TooLarge -> stringResource(R.string.settings_backup_error_too_large, BackupArchive.MAX_ARCHIVE_BYTES / (1024 * 1024))
    BackupError.TooManyEntries -> stringResource(R.string.settings_backup_error_too_many_entries)
    is BackupError.UnsafePath -> stringResource(R.string.settings_backup_error_unsafe_path)
    is BackupError.DuplicateEntry -> stringResource(R.string.settings_backup_error_corrupt)
    BackupError.MissingManifest -> stringResource(R.string.settings_backup_error_missing_manifest)
    BackupError.MalformedManifest -> stringResource(R.string.settings_backup_error_corrupt)
    is BackupError.UnsupportedSchema -> stringResource(R.string.settings_backup_error_schema, error.version, BackupArchive.SCHEMA_VERSION)
    is BackupError.MissingEntry -> stringResource(R.string.settings_backup_error_corrupt)
    is BackupError.ChecksumMismatch -> stringResource(R.string.settings_backup_error_checksum)
    is BackupError.MalformedSection -> stringResource(R.string.settings_backup_error_section, stringResource(error.section.labelRes()))
    BackupError.NoSupportedSections -> stringResource(R.string.settings_backup_error_no_sections)
    BackupError.ReadFailed -> stringResource(R.string.settings_backup_error_read)
} + "\n\n" + stringResource(R.string.settings_backup_error_no_changes)

@Composable
private fun PreviewDialog(
    stage: ImportStage.Preview,
    onSelectMode: (ImportMode) -> Unit,
    onSelectPolicy: (ConflictPolicy) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val preview = stage.preview
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_backup_preview_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
            ) {
                DialogText(stringResource(R.string.settings_backup_preview_source, preview.appVersion, preview.createdAt))
                preview.sections.forEach { SectionPreviewCard(it) }
                if (preview.unsupported.isNotEmpty()) {
                    StatusBanner(
                        level = StatusLevel.WARNING,
                        title = stringResource(R.string.settings_backup_preview_unsupported_title),
                        message = preview.unsupported.joinToString(", "),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { DialogLabel(stringResource(R.string.settings_backup_mode_title)) }
                    // UX-002: Replace overwrites whole sections; explain it before the choice.
                    HelpButton(
                        title = stringResource(R.string.settings_backup_mode_replace),
                        text = stringResource(R.string.settings_help_replace_import),
                    )
                }
                ListGroup {
                    item { shape ->
                        RadioRow(
                            title = stringResource(R.string.settings_backup_mode_merge),
                            selected = stage.mode == ImportMode.MERGE,
                            onSelect = { onSelectMode(ImportMode.MERGE) },
                            shape = shape,
                            supportingText = stringResource(R.string.settings_backup_mode_merge_desc),
                        )
                    }
                    item { shape ->
                        RadioRow(
                            title = stringResource(R.string.settings_backup_mode_replace),
                            selected = stage.mode == ImportMode.REPLACE,
                            onSelect = { onSelectMode(ImportMode.REPLACE) },
                            shape = shape,
                            supportingText = stringResource(R.string.settings_backup_mode_replace_desc),
                        )
                    }
                }
                if (stage.mode == ImportMode.REPLACE) {
                    StatusBanner(
                        level = StatusLevel.WARNING,
                        title = stringResource(R.string.settings_backup_replace_summary_title),
                        message = preview.sections.map {
                            if (it.section == BackupSection.BATTERY_HISTORY) {
                                stringResource(R.string.settings_backup_replace_history)
                            } else {
                                stringResource(it.section.labelRes())
                            }
                        }.joinToString("\n") { "• $it" },
                    )
                }
                if (stage.mode == ImportMode.MERGE && preview.totalConflicts > 0) {
                    DialogLabel(pluralStringResource(R.plurals.settings_backup_conflicts_title, preview.totalConflicts, preview.totalConflicts))
                    ListGroup {
                        item { shape ->
                            RadioRow(
                                title = stringResource(R.string.settings_backup_policy_keep),
                                selected = stage.policy == ConflictPolicy.KEEP_EXISTING,
                                onSelect = { onSelectPolicy(ConflictPolicy.KEEP_EXISTING) },
                                shape = shape,
                            )
                        }
                        item { shape ->
                            RadioRow(
                                title = stringResource(R.string.settings_backup_policy_imported),
                                selected = stage.policy == ConflictPolicy.USE_IMPORTED,
                                onSelect = { onSelectPolicy(ConflictPolicy.USE_IMPORTED) },
                                shape = shape,
                            )
                        }
                    }
                }
                DialogText(
                    stringResource(R.string.settings_backup_rollback_note),
                    Modifier.padding(top = AbleSpacing.xs),
                )
            }
        },
        confirmButton = {
            AbleButton(onClick = onConfirm, enabled = stage.canApply) {
                Text(stringResource(R.string.settings_backup_apply))
            }
        },
        dismissButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_cancel)) } },
    )
}

@Composable
private fun SectionPreviewCard(p: SectionPreview) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AbleSpacing.md), verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
            Text(stringResource(p.section.labelRes()), style = MaterialTheme.typography.titleSmall)
            if (p.section == BackupSection.BATTERY_HISTORY) {
                DialogText(pluralStringResource(R.plurals.settings_backup_history_sessions, p.items, p.items))
                if (p.notes > 0) DialogText(pluralStringResource(R.plurals.settings_backup_history_notes, p.notes, p.notes))
                if (p.duplicates > 0) DialogText(pluralStringResource(R.plurals.settings_backup_preview_duplicates, p.duplicates, p.duplicates))
            } else {
                DialogText(pluralStringResource(R.plurals.settings_backup_preview_items, p.items, p.items))
            }
            if (p.legacy) DialogText(stringResource(R.string.settings_backup_preview_legacy_battery))
            if (p.conflicts > 0) DialogText(pluralStringResource(R.plurals.settings_backup_preview_conflicts, p.conflicts, p.conflicts))
            if (p.invalid > 0) DialogText(pluralStringResource(R.plurals.settings_backup_preview_invalid, p.invalid, p.invalid))
            if (p.unresolved > 0) DialogText(pluralStringResource(R.plurals.settings_backup_preview_unresolved, p.unresolved, p.unresolved))
            if (p.needRepin > 0) DialogText(pluralStringResource(R.plurals.settings_backup_preview_repin, p.needRepin, p.needRepin))
            if (p.iconsReset > 0) DialogText(pluralStringResource(R.plurals.settings_backup_preview_icons, p.iconsReset, p.iconsReset))
            if (p.expiring > 0) {
                StatusBanner(
                    level = StatusLevel.WARNING,
                    title = pluralStringResource(R.plurals.settings_backup_preview_expiring_title, p.expiring, p.expiring),
                    message = stringResource(R.string.settings_backup_preview_expiring_body, p.retentionDays),
                )
            }
        }
    }
}

@Composable
private fun ResultDialog(report: ImportReport, restore: Boolean, onRestore: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when {
                        report.anyFailed -> R.string.settings_backup_result_partial
                        restore -> R.string.settings_backup_result_restored
                        else -> R.string.settings_backup_result_done
                    },
                ),
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
            ) {
                report.sections.forEach { s ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(AbleSpacing.md), verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                            Text(stringResource(s.section.labelRes()), style = MaterialTheme.typography.titleSmall)
                            if (s.failed) {
                                DialogText(stringResource(R.string.settings_backup_result_section_failed))
                            } else {
                                DialogText(pluralStringResource(R.plurals.settings_backup_result_applied, s.applied, s.applied))
                                if (s.skipped > 0) DialogText(pluralStringResource(R.plurals.settings_backup_result_skipped, s.skipped, s.skipped))
                                if (s.duplicates > 0) DialogText(pluralStringResource(R.plurals.settings_backup_preview_duplicates, s.duplicates, s.duplicates))
                                if (s.expired > 0) DialogText(pluralStringResource(R.plurals.settings_backup_result_expired, s.expired, s.expired))
                                if (s.unresolved > 0) DialogText(pluralStringResource(R.plurals.settings_backup_preview_unresolved, s.unresolved, s.unresolved))
                            }
                        }
                    }
                }
                if (report.needRepin > 0) {
                    StatusBanner(
                        level = StatusLevel.INFO,
                        title = stringResource(R.string.settings_backup_result_repin_title),
                        message = pluralStringResource(R.plurals.settings_backup_preview_repin, report.needRepin, report.needRepin),
                    )
                }
                if (report.iconsReset > 0) {
                    DialogText(pluralStringResource(R.plurals.settings_backup_preview_icons, report.iconsReset, report.iconsReset))
                }
                if (!restore && report.rollbackSaved) {
                    DialogText(stringResource(R.string.settings_backup_result_rollback_available))
                }
            }
        },
        confirmButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_close)) } },
        dismissButton = if (!restore && report.rollbackSaved) {
            { AbleTextButton(onClick = onRestore) { Text(stringResource(R.string.settings_backup_restore_title)) } }
        } else {
            null
        },
    )
}

// --- Access helpers ---------------------------------------------------------------------------

@Composable
fun AdbGrantDialog(command: String, granted: Boolean, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_access_secure_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            ) {
                DialogText(
                    stringResource(
                        if (granted) R.string.settings_access_secure_dialog_granted else R.string.settings_access_secure_dialog_body,
                    ),
                )
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Box(Modifier.padding(AbleSpacing.md)) {
                        Text(command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
                DialogText(stringResource(R.string.settings_access_secure_dialog_revoke))
            }
        },
        confirmButton = { AbleButton(onClick = onCopy) { Text(stringResource(R.string.settings_action_copy_command)) } },
        dismissButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_close)) } },
    )
}

/** Shown when no app can handle a link or email (ABOUT-001). */
@Composable
fun CopyFallbackDialog(value: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_no_handler_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                DialogText(stringResource(R.string.settings_no_handler_body))
                Text(value, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = { AbleButton(onClick = onCopy) { Text(stringResource(R.string.settings_action_copy)) } },
        dismissButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_cancel)) } },
    )
}
