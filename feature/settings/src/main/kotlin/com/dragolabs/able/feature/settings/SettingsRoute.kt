package com.dragolabs.able.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.ConfirmationDialog
import com.dragolabs.able.feature.settings.backup.BackupArchive

/**
 * Settings entry point (SET-001, SET-002, PRIV-001, CFG-001..007, ABOUT-001, UX-001). Owns Android
 * launchers and intents; all state lives in [SettingsViewModel].
 *
 * Navigation callbacks are optional so older call sites keep compiling; a row whose destination
 * is not provided is hidden.
 *
 * @param onOpenTour opens the guided tour after `tourCompleted` has been reset (UX-001).
 * @param onOpenTilesWidgets opens the Tiles & widgets guide.
 * @param onOpenLicenses opens [OpenSourceLicensesRoute].
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTour: (() -> Unit)? = null,
    onOpenTilesWidgets: (() -> Unit)? = null,
    onOpenLicenses: (() -> Unit)? = null,
) {
    val viewModel: SettingsViewModel = viewModel(factory = ableViewModelFactory { SettingsViewModel.create(it) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showAdbDialog by rememberSaveable { mutableStateOf(false) }
    var confirmHomeReset by rememberSaveable { mutableStateOf(false) }
    var copyFallback by rememberSaveable { mutableStateOf<String?>(null) }
    var notificationRequestDenied by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupArchive.MIME_TYPE),
    ) { uri -> viewModel.onExportDestination(uri) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onImportFile(uri)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) notificationRequestDenied = true
        viewModel.refresh()
    }

    fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        viewModel.message(SettingsMessage.NoHandler)
        false
    } catch (_: SecurityException) {
        viewModel.message(SettingsMessage.NoHandler)
        false
    }

    val appSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    val actions = SettingsActions(
        onThemeSelected = viewModel::setTheme,
        onLanguageSelected = viewModel::setLanguage,
        onDynamicColorChanged = viewModel::setUseDynamicColor,
        onHapticsChanged = viewModel::setHapticsEnabled,
        onHapticsIntensitySelected = viewModel::setHapticsIntensity,
        onAccessAction = { kind ->
            val access = state.access
            when (kind) {
                AccessKind.SHIZUKU -> when (access.shizuku) {
                    ShizukuConnectionState.WAITING_FOR_PERMISSION -> viewModel.requestShizukuPermission()
                    else -> viewModel.openShizukuApp()
                }
                AccessKind.USAGE_ACCESS -> launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                AccessKind.ACCESSIBILITY -> launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                AccessKind.NOTIFICATIONS -> if (!access.notifications && !notificationRequestDenied) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    launch(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
                AccessKind.WRITE_SETTINGS -> launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                AccessKind.WRITE_SECURE_SETTINGS -> showAdbDialog = true
                AccessKind.PRECISE_LOCATION -> launch(appSettings)
            }
        },
        onTelemetryChanged = viewModel::setTelemetry,
        onExport = viewModel::startExport,
        onImport = {
            try {
                openDocument.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
            } catch (_: ActivityNotFoundException) {
                viewModel.message(SettingsMessage.NoHandler)
            }
        },
        onRestorePrevious = viewModel::restorePrevious,
        onResetHomeLayout = { confirmHomeReset = true },
        onOpenWebsite = {
            val opened = runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SettingsLinks.WEBSITE)))
            }.isSuccess
            if (!opened) copyFallback = SettingsLinks.WEBSITE
        },
        onOpenGitHub = {
            val opened = runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SettingsLinks.GITHUB)))
            }.isSuccess
            if (!opened) copyFallback = SettingsLinks.GITHUB
        },
        onSendEmail = {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(SettingsLinks.EMAIL))
            val opened = runCatching { context.startActivity(intent) }.isSuccess
            if (!opened) copyFallback = SettingsLinks.EMAIL
        },
        onReplayTour = onOpenTour?.let { open -> { viewModel.replayTour(open) } },
        onOpenTilesWidgets = onOpenTilesWidgets,
        onOpenLicenses = onOpenLicenses,
        updates = UpdateActions(
            onCheck = viewModel::checkForUpdates,
            onAutoCheckChanged = viewModel::setUpdateAutoCheck,
            onDownload = viewModel::downloadUpdate,
            onCancelDownload = viewModel::cancelUpdateDownload,
            // UPD-001: Android's own "Install unknown apps" page for Able.
            onAllowInstalls = {
                launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            },
            onContinueInstall = { confirm -> launch(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            onOpenPage = { url ->
                val opened = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
                if (!opened) copyFallback = url
            },
            onDismissInstall = viewModel::dismissUpdateInstall,
        ),
    )

    val messageText = state.message?.let { messageText(context, it) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    SettingsScreen(state = state, actions = actions, onBack = onBack, snackbarHostState = snackbarHostState, modifier = modifier)

    state.exportDraft?.takeIf { !it.awaitingDestination }?.let { draft ->
        ExportDialog(
            draft = draft,
            onToggle = viewModel::toggleExportSection,
            onChooseDestination = {
                viewModel.chooseExportDestination()
                try {
                    createDocument.launch(viewModel.suggestedExportName())
                } catch (_: ActivityNotFoundException) {
                    viewModel.cancelExport()
                    viewModel.message(SettingsMessage.NoHandler)
                }
            },
            onDismiss = viewModel::cancelExport,
        )
    }

    state.importStage?.let { stage ->
        ImportDialog(
            stage = stage,
            onSelectMode = viewModel::selectImportMode,
            onSelectPolicy = viewModel::selectConflictPolicy,
            onConfirm = viewModel::confirmImport,
            onRestore = viewModel::restorePrevious,
            onDismiss = viewModel::dismissImport,
        )
    }

    if (showAdbDialog) {
        val command = SettingsLinks.adbGrantCommand(context.packageName)
        AdbGrantDialog(
            command = command,
            granted = state.access.writeSecureSettings,
            onCopy = {
                copyToClipboard(context, command)
                viewModel.message(SettingsMessage.Copied)
            },
            onDismiss = { showAdbDialog = false },
        )
    }

    copyFallback?.let { value ->
        CopyFallbackDialog(
            value = value,
            onCopy = {
                copyToClipboard(context, value)
                copyFallback = null
                viewModel.message(SettingsMessage.Copied)
            },
            onDismiss = { copyFallback = null },
        )
    }

    if (confirmHomeReset) {
        ConfirmationDialog(
            title = stringResource(R.string.settings_home_reset_confirm_title),
            message = stringResource(R.string.settings_home_reset_confirm_body),
            confirmLabel = stringResource(R.string.settings_home_reset_confirm_action),
            onConfirm = {
                confirmHomeReset = false
                viewModel.resetHomeLayout()
            },
            onDismiss = { confirmHomeReset = false },
            destructive = true,
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.settings_clip_label), text))
}

private fun messageText(context: Context, message: SettingsMessage): String = when (message) {
    is SettingsMessage.Exported -> context.getString(R.string.settings_backup_export_done, message.fileName)
    SettingsMessage.ExportFailed -> context.getString(R.string.settings_backup_export_failed)
    SettingsMessage.HomeLayoutReset -> context.getString(R.string.settings_home_reset_done)
    SettingsMessage.Copied -> context.getString(R.string.settings_copied)
    SettingsMessage.NoHandler -> context.getString(R.string.settings_no_handler_generic)
    SettingsMessage.ShizukuUnavailable -> context.getString(R.string.settings_access_shizuku_unavailable)
    SettingsMessage.LanguageNotApplied -> context.getString(R.string.settings_language_not_applied)
    SettingsMessage.RestoreFailed -> context.getString(R.string.settings_backup_restore_failed)
}
