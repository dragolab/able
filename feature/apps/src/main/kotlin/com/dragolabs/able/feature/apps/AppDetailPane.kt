package com.dragolabs.able.feature.apps

import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleFilledTonalButton
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.ConfirmationDialog
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.InlineLoading
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.labelRes
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.feature.apps.data.AppArchiveRecord
import com.dragolabs.able.feature.apps.data.AppInstallState
import com.dragolabs.able.feature.apps.data.ArchivedAction
import com.dragolabs.able.feature.apps.data.PackageActionOutcome
import com.dragolabs.able.feature.apps.data.PackageKnowledge

/** Shown in the detail pane on wide windows when no app is selected. */
@Composable
internal fun AppDetailPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        EmptyState(
            icon = Icons.Rounded.TouchApp,
            title = stringResource(R.string.apps_detail_placeholder_title),
            message = stringResource(R.string.apps_detail_placeholder_message),
        )
    }
}

/** Callbacks of the detail pane. */
internal class AppDetailActions(
    val onSetProtected: (Boolean) -> Unit,
    /** Profile id and level. */
    val onSetLevel: (String, AppInterventionLevel) -> Unit,
    val onOpenAppInfo: () -> Unit,
    val onOpenUsageAccess: () -> Unit,
    val onRequestShizukuPermission: () -> Unit,
    val onForceStop: () -> Unit,
    val onDismissForceStopResult: () -> Unit,
    val onOperation: (PackageOperation) -> Unit,
    val onDismissOperationResult: () -> Unit,
    /** Loads the APK backup preview for the uninstall confirmation. */
    val onPrepareUninstall: () -> Unit = {},
    val onCancelUninstall: () -> Unit = {},
    /** Confirmed uninstall; [allowWithoutBackup] when the user accepted a store-only restore. */
    val onUninstall: (allowWithoutBackup: Boolean) -> Unit = { onOperation(PackageOperation.UNINSTALL) },
    /** Opens the store page of the package (installer's store first, then any store, then the web). */
    val onOpenStore: (installer: String?) -> Unit = {},
    val onDeleteBackup: () -> Unit = {},
)

@Composable
internal fun AppDetailPane(
    detail: AppDetailState,
    shizuku: ShizukuConnectionState,
    loadIcon: suspend (String) -> ImageBitmap?,
    onBack: (() -> Unit)?,
    actions: AppDetailActions,
    modifier: Modifier = Modifier,
    /** "Background activity" section (background/BackgroundDetailSection.kt), shown after Actions. */
    backgroundSection: @Composable () -> Unit = {},
) {
    val app = detail.app
    val haptics = rememberAbleHaptics()
    var pickerProfile by rememberSaveable(detail.packageName) { mutableStateOf<String?>(null) }
    var pendingProfile by rememberSaveable(detail.packageName) { mutableStateOf<String?>(null) }
    var pendingLevel by rememberSaveable(detail.packageName) { mutableStateOf<AppInterventionLevel?>(null) }
    var confirmForceStop by rememberSaveable(detail.packageName) { mutableStateOf(false) }
    var confirmOperation by rememberSaveable(detail.packageName) { mutableStateOf<PackageOperation?>(null) }
    var confirmDeleteBackup by rememberSaveable(detail.packageName) { mutableStateOf(false) }

    // UX-003: confirm on success, reject on failure.
    val operationDone = detail.operation as? PackageOperationUi.Done
    LaunchedEffect(operationDone) {
        when (operationDone?.outcome) {
            PackageActionOutcome.APPLIED -> haptics.confirm()
            PackageActionOutcome.REJECTED, PackageActionOutcome.NOT_CONFIRMED -> haptics.reject()
            null -> Unit
        }
    }
    val forceStopDone = detail.forceStop as? ForceStopUi.Done
    LaunchedEffect(forceStopDone) {
        when (forceStopDone?.outcome) {
            ForceStopOutcome.STOPPED -> haptics.confirm()
            null -> Unit
            else -> haptics.reject()
        }
    }

    AbleDetailScaffold(
        title = detail.label,
        subtitle = detail.packageName.takeIf { it != detail.label },
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        if (app == null && detail.archiveRecord == null) {
            EmptyState(
                title = stringResource(R.string.apps_detail_missing_title),
                message = stringResource(R.string.apps_detail_missing_message),
                modifier = Modifier.padding(padding),
            )
            return@AbleDetailScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(screenContentPadding(padding)),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
        ) {
            HeaderCard(detail, loadIcon)

            detail.archiveRecord?.let { record ->
                ArchiveCard(
                    record = record,
                    detail = detail,
                    shizuku = shizuku,
                    onRestore = { actions.onOperation(PackageOperation.RESTORE) },
                    onOpenStore = actions.onOpenStore,
                    onDeleteBackup = { confirmDeleteBackup = true },
                )
            }

            // Package information (APP-006)
            HelpSectionHeader(
                title = stringResource(R.string.apps_section_info),
                helpTitle = stringResource(R.string.apps_help_removal_title),
                helpText = stringResource(R.string.apps_help_removal_text),
                modifier = Modifier.padding(top = AbleSpacing.sm),
            )
            PackageInfoSection(detail)

            // Actions (APP-003, APP-004)
            SectionHeader(stringResource(R.string.apps_section_actions), Modifier.padding(top = AbleSpacing.sm))
            ForceStopResult(detail.forceStop, detail.label, actions.onDismissForceStopResult)
            OperationResult(detail.operation, detail.label, detail.restoreRoute, actions.onOpenStore, actions.onDismissOperationResult)
            ActionsGroup(
                detail = detail,
                shizuku = shizuku,
                actions = actions,
                onRequestForceStop = { confirmForceStop = true },
                onRequestOperation = { operation ->
                    when (operation) {
                        PackageOperation.DISABLE -> confirmOperation = operation
                        PackageOperation.UNINSTALL -> {
                            actions.onPrepareUninstall()
                            confirmOperation = operation
                        }
                        else -> actions.onOperation(operation)
                    }
                },
            )

            // Background activity (RUN_ANY_IN_BACKGROUND / RUN_IN_BACKGROUND, standby bucket)
            backgroundSection()

            if (app != null) {
                // Protection (BAT-003 exclusions)
                SectionHeader(stringResource(R.string.apps_section_protection), Modifier.padding(top = AbleSpacing.sm))
                ListGroup {
                    item { shape ->
                        SwitchRow(
                            title = stringResource(R.string.apps_protected_title),
                            supportingText = stringResource(R.string.apps_protected_message),
                            checked = detail.isProtected,
                            onCheckedChange = actions.onSetProtected,
                            icon = Icons.Rounded.Shield,
                            shape = shape,
                        )
                    }
                }

                // Per-profile intervention levels (BAT-003)
                HelpSectionHeader(
                    title = stringResource(R.string.apps_section_profiles),
                    helpTitle = stringResource(R.string.apps_help_levels_title),
                    helpText = stringResource(R.string.apps_help_levels_text),
                    modifier = Modifier.padding(top = AbleSpacing.sm),
                )
                if (detail.isProtected) {
                    StatusBanner(
                        level = StatusLevel.INFO,
                        title = stringResource(R.string.apps_profiles_protected_title),
                        message = stringResource(R.string.apps_profiles_protected_message),
                    )
                }
                if (!detail.isProtected) EffectiveActionBanner(detail)
                ListGroup {
                    detail.profileRules.forEach { rule ->
                        item { shape ->
                            ActionRow(
                                title = rule.displayName(),
                                supportingText = when {
                                    detail.isProtected -> stringResource(R.string.apps_profile_excluded)
                                    rule.active -> stringResource(R.string.apps_profile_level_active, stringResource(rule.level.labelRes()))
                                    !rule.enabled -> stringResource(R.string.apps_profile_level_off, stringResource(rule.level.labelRes()))
                                    else -> stringResource(rule.level.labelRes())
                                },
                                enabled = !detail.isProtected,
                                shape = shape,
                                onClick = if (detail.isProtected) null else ({ pickerProfile = rule.profileId }),
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.apps_profiles_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Usage evidence (APP-002)
                SectionHeader(stringResource(R.string.apps_section_usage), Modifier.padding(top = AbleSpacing.sm))
                UsageSection(detail.usage, actions.onOpenUsageAccess)
            }
        }
    }

    pickerProfile?.let { profile ->
        LevelPickerDialog(
            profileName = detail.profileName(profile),
            current = detail.levelFor(profile),
            onDismiss = { pickerProfile = null },
            onSelect = { level ->
                pickerProfile = null
                if (level == AppInterventionLevel.OSSERVA || level == detail.levelFor(profile)) {
                    actions.onSetLevel(profile, level)
                } else {
                    pendingProfile = profile
                    pendingLevel = level
                }
            },
        )
    }

    val confirmProfile = pendingProfile
    val confirmLevel = pendingLevel
    if (confirmProfile != null && confirmLevel != null) {
        val clear = { pendingProfile = null; pendingLevel = null }
        ConfirmationDialog(
            title = stringResource(R.string.apps_level_confirm_title, stringResource(confirmLevel.labelRes())),
            message = stringResource(
                R.string.apps_level_confirm_message,
                detail.label,
                detail.profileName(confirmProfile),
                stringResource(confirmLevel.consequenceRes()),
            ),
            confirmLabel = stringResource(R.string.apps_level_confirm_action),
            onConfirm = {
                actions.onSetLevel(confirmProfile, confirmLevel)
                clear()
            },
            onDismiss = clear,
        )
    }

    if (confirmForceStop) {
        ConfirmationDialog(
            title = stringResource(R.string.apps_force_stop_confirm_title, detail.label),
            message = stringResource(R.string.apps_force_stop_confirm_message),
            confirmLabel = stringResource(R.string.apps_force_stop_confirm_action),
            destructive = true,
            icon = Icons.Rounded.StopCircle,
            onConfirm = {
                confirmForceStop = false
                actions.onForceStop()
            },
            onDismiss = { confirmForceStop = false },
        )
    }

    confirmOperation?.let { operation ->
        PackageOperationDialog(
            operation = operation,
            label = detail.label,
            removal = detail.knowledge?.removal,
            isSystem = detail.app?.isSystem ?: detail.archiveRecord?.isSystem ?: false,
            isProtected = detail.isProtected,
            uninstallPlan = detail.uninstallPlan,
            onConfirm = {
                confirmOperation = null
                if (operation == PackageOperation.UNINSTALL) {
                    val problem = (detail.uninstallPlan as? UninstallPlanUi.Ready)?.problem
                    actions.onUninstall(problem != null && detail.app?.isSystem == false)
                } else {
                    actions.onOperation(operation)
                }
            },
            onDismiss = {
                confirmOperation = null
                if (operation == PackageOperation.UNINSTALL) actions.onCancelUninstall()
            },
        )
    }

    if (confirmDeleteBackup) {
        ConfirmationDialog(
            title = stringResource(R.string.apps_backup_delete_confirm_title),
            message = stringResource(R.string.apps_backup_delete_confirm_message, detail.label),
            confirmLabel = stringResource(R.string.apps_backup_delete),
            destructive = true,
            icon = Icons.Rounded.Delete,
            onConfirm = {
                confirmDeleteBackup = false
                actions.onDeleteBackup()
            },
            onDismiss = { confirmDeleteBackup = false },
        )
    }
}

@Composable
private fun HeaderCard(detail: AppDetailState, loadIcon: suspend (String) -> ImageBitmap?) {
    val app = detail.app
    AbleCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg)) {
            AppIcon(detail.packageName, loadIcon, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(detail.label, style = MaterialTheme.typography.titleLarge)
                Text(detail.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (app != null) {
                    Text(
                        stringResource(R.string.apps_detail_version, app.versionName ?: stringResource(R.string.apps_value_unavailable), app.versionCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            val isSystem = app?.isSystem ?: detail.archiveRecord?.isSystem
            if (isSystem != null) {
                StatusPill(stringResource(if (isSystem) R.string.apps_badge_system else R.string.apps_badge_user), StatusLevel.INFO)
            }
            detail.state.badgeRes()?.let { StatusPill(stringResource(it), StatusLevel.WARNING) }
            if (detail.knowledge != null) {
                StatusPill(stringResource(detail.knowledge.removal.labelRes()), detail.knowledge.removal.statusLevel())
            }
            if (detail.isProtected) StatusPill(stringResource(R.string.apps_badge_protected), StatusLevel.SUCCESS)
        }
    }
}

@Composable
private fun ArchiveCard(
    record: AppArchiveRecord,
    detail: AppDetailState,
    shizuku: ShizukuConnectionState,
    onRestore: () -> Unit,
    onOpenStore: (String?) -> Unit,
    onDeleteBackup: () -> Unit,
) {
    val context = LocalContext.current
    val time = DateUtils.getRelativeTimeSpanString(record.timestampEpochMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    val message = stringResource(
        if (record.action == ArchivedAction.DISABLED) R.string.apps_archive_disabled_on else R.string.apps_archive_uninstalled_on,
        time,
    )
    val running = detail.operation is PackageOperationUi.Running
    val canRestore = shizuku == ShizukuConnectionState.READY && detail.blockReason == null && !running &&
        detail.state != AppInstallState.INSTALLED
    AbleCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
            androidx.compose.material3.Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.apps_archive_title), style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                detail.backupBytes?.let { bytes ->
                    Text(
                        stringResource(R.string.apps_backup_present, android.text.format.Formatter.formatShortFileSize(context, bytes)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HelpButton(
                title = stringResource(R.string.apps_help_restore_title),
                text = stringResource(R.string.apps_help_restore_text),
            )
        }
        val storeRoute = detail.restoreRoute as? RestoreRoute.FromStore
        if (storeRoute != null) {
            Text(
                stringResource(R.string.apps_restore_store_needed_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
        ) {
            if (running) InlineLoading()
            if (detail.backupBytes != null && !running) {
                AbleTextButton(onClick = onDeleteBackup, haptic = AbleHaptic.Reject) { Text(stringResource(R.string.apps_backup_delete)) }
            }
            if (storeRoute != null) {
                AbleFilledTonalButton(onClick = { onOpenStore(storeRoute.installer) }) {
                    androidx.compose.material3.Icon(Icons.Rounded.Shop, contentDescription = null)
                    Text(stringResource(R.string.apps_action_reinstall_store), Modifier.padding(start = AbleSpacing.sm))
                }
            } else {
                AbleFilledTonalButton(onClick = onRestore, enabled = canRestore, haptic = AbleHaptic.Confirm) {
                    androidx.compose.material3.Icon(Icons.Rounded.Restore, contentDescription = null)
                    Text(stringResource(R.string.apps_action_restore), Modifier.padding(start = AbleSpacing.sm))
                }
            }
        }
        if (shizuku != ShizukuConnectionState.READY && storeRoute == null) {
            Text(
                stringResource(R.string.apps_operation_needs_shizuku),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PackageInfoSection(detail: AppDetailState) {
    val info: PackageKnowledge? = detail.knowledge
    when {
        !detail.knowledgeLoaded -> ListGroup {
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.apps_info_loading),
                    icon = Icons.Rounded.Info,
                    shape = shape,
                    trailing = { InlineLoading() },
                )
            }
        }
        info == null -> ListGroup {
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.apps_info_unknown_title),
                    supportingText = stringResource(R.string.apps_info_unknown_message),
                    icon = Icons.Rounded.Info,
                    shape = shape,
                )
            }
        }
        else -> {
            val linkColor = MaterialTheme.colorScheme.primary
            val description = remember(info.description, linkColor) { linkify(info.description, linkColor) }
            AbleCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                    StatusPill(stringResource(info.removal.labelRes()), info.removal.statusLevel())
                    info.list?.let { ListChip(it) }
                }
                Text(
                    stringResource(info.removal.descriptionRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (info.description.isNotEmpty()) {
                    Text(description, style = MaterialTheme.typography.bodyLarge)
                }
                InfoLine(stringResource(R.string.apps_info_dependencies), info.dependencies)
                InfoLine(stringResource(R.string.apps_info_needed_by), info.neededBy)
            }
        }
    }
    Text(
        stringResource(R.string.apps_info_attribution),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InfoLine(title: String, values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(
            if (values.isEmpty()) stringResource(R.string.apps_info_none) else values.joinToString("\n"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionsGroup(
    detail: AppDetailState,
    shizuku: ShizukuConnectionState,
    actions: AppDetailActions,
    onRequestForceStop: () -> Unit,
    onRequestOperation: (PackageOperation) -> Unit,
) {
    val running = (detail.operation as? PackageOperationUi.Running)?.operation
    ListGroup {
        if (detail.app != null) {
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.apps_action_app_info),
                    supportingText = stringResource(R.string.apps_action_app_info_message),
                    icon = Icons.Rounded.Info,
                    shape = shape,
                    onClick = actions.onOpenAppInfo,
                )
            }
        }
        val block = detail.blockReason
        if (block != null) {
            item { shape ->
                ActionRow(
                    title = stringResource(
                        when (block) {
                            BlockReason.SELF -> R.string.apps_operation_blocked_self
                            BlockReason.SHIZUKU -> R.string.apps_operation_blocked_shizuku
                            BlockReason.CORE_SYSTEM -> R.string.apps_operation_blocked_core
                        },
                    ),
                    icon = Icons.Rounded.Lock,
                    shape = shape,
                )
            }
            return@ListGroup
        }
        when (detail.state) {
            AppInstallState.INSTALLED -> {
                item { shape -> ForceStopRow(shizuku, detail.forceStop, shape, actions.onRequestShizukuPermission, onRequestForceStop) }
                item { shape -> OperationRow(PackageOperation.DISABLE, shizuku, running, shape, actions.onRequestShizukuPermission, onRequestOperation) }
                item { shape -> OperationRow(PackageOperation.UNINSTALL, shizuku, running, shape, actions.onRequestShizukuPermission, onRequestOperation) }
            }
            AppInstallState.DISABLED -> {
                item { shape -> OperationRow(PackageOperation.ENABLE, shizuku, running, shape, actions.onRequestShizukuPermission, onRequestOperation) }
                item { shape -> OperationRow(PackageOperation.UNINSTALL, shizuku, running, shape, actions.onRequestShizukuPermission, onRequestOperation) }
            }
            AppInstallState.UNINSTALLED_FOR_USER -> {
                item { shape -> RestoreRow(detail, shizuku, running, shape, actions, onRequestOperation) }
            }
            AppInstallState.NOT_FOUND -> {
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.apps_state_not_found),
                        supportingText = stringResource(R.string.apps_operation_not_found),
                        icon = Icons.Rounded.Info,
                        shape = shape,
                    )
                }
                item { shape -> RestoreRow(detail, shizuku, running, shape, actions, onRequestOperation) }
            }
            AppInstallState.ARCHIVED_BY_SYSTEM -> {
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.apps_state_archived_system),
                        supportingText = stringResource(R.string.apps_operation_archived_system),
                        icon = Icons.Rounded.Info,
                        shape = shape,
                    )
                }
            }
        }
    }
}

/** Restore row: through Shizuku, or "Reinstall from store" when Android deleted the APK and no backup exists. */
@Composable
private fun RestoreRow(
    detail: AppDetailState,
    shizuku: ShizukuConnectionState,
    running: PackageOperation?,
    shape: Shape,
    actions: AppDetailActions,
    onRequestOperation: (PackageOperation) -> Unit,
) {
    val store = detail.restoreRoute as? RestoreRoute.FromStore
    if (store == null) {
        OperationRow(PackageOperation.RESTORE, shizuku, running, shape, actions.onRequestShizukuPermission, onRequestOperation)
        return
    }
    ActionRow(
        title = stringResource(R.string.apps_action_reinstall_store),
        supportingText = stringResource(R.string.apps_restore_store_needed_message),
        icon = Icons.Rounded.Shop,
        shape = shape,
        trailing = {
            HelpButton(title = stringResource(R.string.apps_help_restore_title), text = stringResource(R.string.apps_help_restore_text))
        },
        onClick = { actions.onOpenStore(store.installer) },
    )
}

private data class OperationTexts(val title: Int, val message: Int, val icon: ImageVector, val helpTitle: Int, val helpText: Int)

private fun PackageOperation.texts(): OperationTexts = when (this) {
    PackageOperation.DISABLE -> OperationTexts(
        R.string.apps_action_disable, R.string.apps_disable_message, Icons.Rounded.Block,
        R.string.apps_help_disable_title, R.string.apps_help_disable_text,
    )
    PackageOperation.ENABLE -> OperationTexts(
        R.string.apps_action_enable, R.string.apps_enable_message, Icons.Rounded.CheckCircle,
        R.string.apps_help_disable_title, R.string.apps_help_disable_text,
    )
    PackageOperation.UNINSTALL -> OperationTexts(
        R.string.apps_action_uninstall_user, R.string.apps_uninstall_message, Icons.Rounded.Delete,
        R.string.apps_help_uninstall_title, R.string.apps_help_uninstall_text,
    )
    PackageOperation.RESTORE -> OperationTexts(
        R.string.apps_action_restore, R.string.apps_restore_message, Icons.Rounded.Restore,
        R.string.apps_help_restore_title, R.string.apps_help_restore_text,
    )
}

/** A Shizuku-backed operation row; without Shizuku it is shown unavailable with a "?" explanation. */
@Composable
private fun OperationRow(
    operation: PackageOperation,
    shizuku: ShizukuConnectionState,
    running: PackageOperation?,
    shape: Shape,
    onRequestShizukuPermission: () -> Unit,
    onRequest: (PackageOperation) -> Unit,
) {
    val texts = operation.texts()
    ShizukuActionRow(
        title = stringResource(texts.title),
        readyMessage = stringResource(texts.message),
        runningMessage = stringResource(R.string.apps_operation_running).takeIf { running == operation },
        busy = running != null,
        icon = texts.icon,
        helpTitle = stringResource(texts.helpTitle),
        helpText = stringResource(texts.helpText),
        permissionMessage = stringResource(R.string.apps_operation_needs_permission),
        unavailableMessage = stringResource(R.string.apps_operation_needs_shizuku),
        shizuku = shizuku,
        shape = shape,
        onRequestShizukuPermission = onRequestShizukuPermission,
        onClick = { onRequest(operation) },
    )
}

@Composable
private fun ForceStopRow(
    shizuku: ShizukuConnectionState,
    forceStop: ForceStopUi,
    shape: Shape,
    onRequestShizukuPermission: () -> Unit,
    onRequestForceStop: () -> Unit,
) {
    ShizukuActionRow(
        title = stringResource(R.string.apps_action_force_stop),
        readyMessage = stringResource(R.string.apps_force_stop_message),
        runningMessage = stringResource(R.string.apps_force_stop_running).takeIf { forceStop == ForceStopUi.Running },
        busy = forceStop == ForceStopUi.Running,
        icon = Icons.Rounded.StopCircle,
        helpTitle = stringResource(R.string.apps_help_force_stop_title),
        helpText = stringResource(R.string.apps_help_force_stop_text),
        permissionMessage = stringResource(R.string.apps_force_stop_needs_permission),
        unavailableMessage = stringResource(R.string.apps_force_stop_needs_shizuku),
        shizuku = shizuku,
        shape = shape,
        onRequestShizukuPermission = onRequestShizukuPermission,
        onClick = onRequestForceStop,
    )
}

@Composable
private fun ShizukuActionRow(
    title: String,
    readyMessage: String,
    runningMessage: String?,
    busy: Boolean,
    icon: ImageVector,
    helpTitle: String,
    helpText: String,
    permissionMessage: String,
    unavailableMessage: String,
    shizuku: ShizukuConnectionState,
    shape: Shape,
    onRequestShizukuPermission: () -> Unit,
    onClick: () -> Unit,
) {
    when {
        runningMessage != null -> ActionRow(
            title = title,
            supportingText = runningMessage,
            icon = icon,
            shape = shape,
            trailing = { InlineLoading() },
        )
        shizuku == ShizukuConnectionState.READY -> ActionRow(
            title = title,
            supportingText = readyMessage,
            icon = icon,
            enabled = !busy,
            shape = shape,
            trailing = { HelpButton(title = helpTitle, text = helpText) },
            onClick = if (busy) null else onClick,
        )
        shizuku == ShizukuConnectionState.WAITING_FOR_PERMISSION || shizuku == ShizukuConnectionState.PERMISSION_DENIED -> ActionRow(
            title = title,
            supportingText = permissionMessage,
            icon = icon,
            shape = shape,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AbleTextButton(onClick = onRequestShizukuPermission) { Text(stringResource(R.string.apps_shizuku_grant)) }
                    HelpButton(title = stringResource(R.string.apps_help_shizuku_title), text = stringResource(R.string.apps_help_shizuku_text))
                }
            },
        )
        else -> ActionRow(
            title = title,
            supportingText = unavailableMessage,
            icon = icon,
            enabled = false,
            shape = shape,
            trailing = {
                HelpButton(title = stringResource(R.string.apps_help_shizuku_title), text = stringResource(R.string.apps_help_shizuku_text))
            },
        )
    }
}

@Composable
private fun UsageSection(usage: UsageEvidence, onOpenUsageAccess: () -> Unit) {
    ListGroup {
        item { shape ->
            when (usage) {
                UsageEvidence.AccessMissing -> ActionRow(
                    title = stringResource(R.string.apps_usage_access_title),
                    supportingText = stringResource(R.string.apps_usage_access_message),
                    icon = Icons.Rounded.QueryStats,
                    shape = shape,
                    onClick = onOpenUsageAccess,
                )
                UsageEvidence.Loading -> ActionRow(
                    title = stringResource(R.string.apps_usage_loading),
                    icon = Icons.Rounded.QueryStats,
                    shape = shape,
                    trailing = { InlineLoading() },
                )
                UsageEvidence.NotReported -> ActionRow(
                    title = stringResource(R.string.apps_usage_not_reported),
                    supportingText = stringResource(R.string.apps_usage_note),
                    icon = Icons.Rounded.QueryStats,
                    shape = shape,
                )
                is UsageEvidence.Reported -> {
                    val summary = usage.summary
                    val minutes = summary.foregroundTimeMs / 60_000
                    val lastUsed = summary.lastTimeUsedMs?.let {
                        DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                    } ?: stringResource(R.string.apps_value_unavailable)
                    ActionRow(
                        title = stringResource(
                            R.string.apps_usage_foreground,
                            minutes / 60,
                            minutes % 60,
                            summary.windowDays,
                        ),
                        supportingText = stringResource(R.string.apps_usage_last_used, lastUsed) + "\n" +
                            stringResource(R.string.apps_usage_note),
                        icon = Icons.Rounded.QueryStats,
                        shape = shape,
                    )
                }
            }
        }
    }
}

@Composable
private fun ForceStopResult(forceStop: ForceStopUi, label: String, onDismiss: () -> Unit) {
    val done = forceStop as? ForceStopUi.Done ?: return
    val (level, titleRes, messageRes) = when (done.outcome) {
        ForceStopOutcome.STOPPED -> Triple(StatusLevel.SUCCESS, R.string.apps_force_stop_result_stopped, R.string.apps_force_stop_result_stopped_message)
        ForceStopOutcome.STILL_RUNNING -> Triple(StatusLevel.WARNING, R.string.apps_force_stop_result_running, R.string.apps_force_stop_result_running_message)
        ForceStopOutcome.NOT_CONFIRMED -> Triple(StatusLevel.WARNING, R.string.apps_force_stop_result_unconfirmed, R.string.apps_force_stop_result_unconfirmed_message)
        ForceStopOutcome.FAILED -> Triple(StatusLevel.CRITICAL, R.string.apps_force_stop_result_failed, R.string.apps_force_stop_result_failed_message)
    }
    StatusBanner(
        level = level,
        title = stringResource(titleRes, label),
        message = stringResource(messageRes) + (done.detail?.let { "\n$it" } ?: ""),
        actionLabel = stringResource(R.string.apps_dismiss),
        onAction = onDismiss,
    )
}

@Composable
private fun OperationResult(
    operation: PackageOperationUi,
    label: String,
    restoreRoute: RestoreRoute?,
    onOpenStore: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val done = operation as? PackageOperationUi.Done ?: return
    if (done.storeRequired) {
        StatusBanner(
            level = StatusLevel.WARNING,
            title = stringResource(R.string.apps_restore_store_needed_title, label),
            message = stringResource(R.string.apps_restore_store_needed_message),
            actionLabel = stringResource(R.string.apps_action_reinstall_store),
            onAction = { onOpenStore((restoreRoute as? RestoreRoute.FromStore)?.installer) },
            secondaryActionLabel = stringResource(R.string.apps_dismiss),
            onSecondaryAction = onDismiss,
        )
        return
    }
    done.backupFailed?.takeIf { done.outcome == PackageActionOutcome.REJECTED }?.let { problem ->
        StatusBanner(
            level = StatusLevel.CRITICAL,
            title = stringResource(R.string.apps_backup_failed_title, label),
            message = stringResource(R.string.apps_backup_failed_message, stringResource(problem.labelRes())),
            actionLabel = stringResource(R.string.apps_dismiss),
            onAction = onDismiss,
        )
        return
    }
    val (level, title, message) = when (done.outcome) {
        PackageActionOutcome.APPLIED -> Triple(
            StatusLevel.SUCCESS,
            stringResource(
                when (done.operation) {
                    PackageOperation.DISABLE -> R.string.apps_operation_applied_disable
                    PackageOperation.ENABLE -> R.string.apps_operation_applied_enable
                    PackageOperation.UNINSTALL -> R.string.apps_operation_applied_uninstall
                    PackageOperation.RESTORE -> R.string.apps_operation_applied_restore
                },
                label,
            ),
            stringResource(R.string.apps_operation_applied_message),
        )
        PackageActionOutcome.REJECTED -> Triple(
            StatusLevel.CRITICAL,
            stringResource(R.string.apps_operation_rejected, label),
            stringResource(R.string.apps_operation_rejected_message) + (done.detail?.let { "\n$it" } ?: ""),
        )
        PackageActionOutcome.NOT_CONFIRMED -> Triple(
            StatusLevel.WARNING,
            stringResource(R.string.apps_operation_unconfirmed, label),
            stringResource(R.string.apps_operation_unconfirmed_message) + (done.detail?.let { "\n$it" } ?: ""),
        )
    }
    StatusBanner(
        level = level,
        title = title,
        message = message,
        actionLabel = stringResource(R.string.apps_dismiss),
        onAction = onDismiss,
    )
}

@Composable
private fun LevelPickerDialog(
    profileName: String,
    current: AppInterventionLevel,
    onDismiss: () -> Unit,
    onSelect: (AppInterventionLevel) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { androidx.compose.material3.Icon(Icons.Rounded.Apps, contentDescription = null) },
        title = { Text(stringResource(R.string.apps_level_picker_title, profileName)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                ListGroup {
                    AppInterventionLevel.entries.forEach { level ->
                        item { shape ->
                            RadioRow(
                                title = stringResource(level.labelRes()),
                                supportingText = stringResource(level.descriptionRes()),
                                selected = level == current,
                                onSelect = { onSelect(level) },
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

/** Profile name: the user's name, else the localized built-in label. */
@Composable
private fun AppProfileRule.displayName(): String =
    name.trim().ifBlank { builtInType?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.apps_profile_untitled) }

@Composable
private fun AppDetailState.profileName(profileId: String): String =
    profileRules.firstOrNull { it.profileId == profileId }?.displayName() ?: stringResource(R.string.apps_profile_untitled)

/** What applies now from the active profiles and which profile causes it (most restrictive wins). */
@Composable
private fun EffectiveActionBanner(detail: AppDetailState) {
    val effective = detail.effectiveAction
    if (effective == null) {
        StatusPill(stringResource(R.string.apps_profile_effective_none), StatusLevel.INFO)
        return
    }
    val sources = effective.sourceProfileIds.map { detail.profileName(it) }.joinToString(", ")
    StatusBanner(
        level = StatusLevel.WARNING,
        title = stringResource(R.string.apps_profile_effective_title, stringResource(effective.level.labelRes())),
        message = stringResource(R.string.apps_profile_effective_message, sources),
    )
}

internal fun AppInterventionLevel.labelRes(): Int = when (this) {
    AppInterventionLevel.OSSERVA -> R.string.apps_level_osserva
    AppInterventionLevel.LIMITA_BACKGROUND -> R.string.apps_level_limita
    AppInterventionLevel.ARRESTA_QUANDO_ESCO -> R.string.apps_level_arresta
}

internal fun AppInterventionLevel.descriptionRes(): Int = when (this) {
    AppInterventionLevel.OSSERVA -> R.string.apps_level_osserva_desc
    AppInterventionLevel.LIMITA_BACKGROUND -> R.string.apps_level_limita_desc
    AppInterventionLevel.ARRESTA_QUANDO_ESCO -> R.string.apps_level_arresta_desc
}

internal fun AppInterventionLevel.consequenceRes(): Int = when (this) {
    AppInterventionLevel.OSSERVA -> R.string.apps_level_osserva_desc
    AppInterventionLevel.LIMITA_BACKGROUND -> R.string.apps_level_limita_consequence
    AppInterventionLevel.ARRESTA_QUANDO_ESCO -> R.string.apps_level_arresta_consequence
}
