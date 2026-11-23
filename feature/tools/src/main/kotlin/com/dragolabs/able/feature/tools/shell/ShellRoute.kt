package com.dragolabs.able.feature.tools.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ClearRestoredFocusOnTouch
import com.dragolabs.able.core.designsystem.component.ConfirmationDialog
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.PermissionRequiredState
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.MessageEffect
import com.dragolabs.able.feature.tools.common.UiMessage
import com.dragolabs.able.core.designsystem.R as DsR

/** Command shell (aShell-style) running as the Shizuku shell user. */
@Composable
fun ShellRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ShellViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            ShellViewModel(
                store = ShellStore(container.appContext, container.applicationScope),
                runner = ShellProcessRunner(),
                shizukuState = container.shizuku.connectionState,
                isShizukuReady = container.shizuku::isReady,
                actionLog = container.actionLog,
                logScope = container.applicationScope,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberAbleHaptics()

    LifecycleResumeEffect(viewModel) {
        context.shizukuAdapter().updateState()
        onPauseOrDispose { }
    }
    LaunchedEffect(state.outcome) {
        val outcome = state.outcome ?: return@LaunchedEffect
        if (outcome.success) haptics.confirm() else haptics.reject()
        viewModel.consumeOutcome()
    }

    ShellScreen(
        state = state,
        onBack = onBack,
        onAuthorize = {
            val adapter = context.shizukuAdapter()
            if (!adapter.requestPermission() && !adapter.openShizukuApp(context)) {
                viewModel.showMessage(UiMessage(R.string.shell_shizuku_not_installed))
            }
        },
        onOpenShizuku = {
            if (!context.shizukuAdapter().openShizukuApp(context)) {
                viewModel.showMessage(UiMessage(R.string.shell_shizuku_not_installed))
            }
        },
        onCheckShizuku = { context.shizukuAdapter().updateState() },
        onSubmit = viewModel::submit,
        onStop = viewModel::stop,
        onHistoryPrevious = viewModel::historyPrevious,
        onHistoryNext = viewModel::historyNext,
        onClearHistory = viewModel::clearHistory,
        onClearOutput = viewModel::clearOutput,
        onCopy = {
            val (text, truncated) = viewModel.exportText()
            if (text.isEmpty()) {
                viewModel.showMessage(UiMessage(R.string.shell_nothing_to_export))
            } else {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.shell_output_description), text))
                haptics.confirm()
                // Android 13+ shows its own copy confirmation; only report what it cannot know.
                if (truncated) viewModel.showMessage(UiMessage(R.string.shell_copied_truncated))
            }
        },
        onShare = {
            val (text, truncated) = viewModel.exportText()
            if (text.isEmpty()) {
                viewModel.showMessage(UiMessage(R.string.shell_nothing_to_export))
            } else {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, context.getString(R.string.shell_share_chooser)))
                if (truncated) viewModel.showMessage(UiMessage(R.string.shell_shared_truncated))
            }
        },
        onConfirmDanger = viewModel::confirmDanger,
        onDismissDanger = viewModel::dismissDanger,
        onAcknowledgeWarning = viewModel::acknowledgeWarning,
        onSaveSnippet = viewModel::saveSnippet,
        onDeleteSnippet = viewModel::deleteSnippet,
        onMessageConsumed = viewModel::consumeMessage,
        modifier = modifier,
    )
}

private fun Context.shizukuAdapter() = ableContainer.shizuku

@Composable
fun ShellScreen(
    state: ShellUiState,
    onBack: () -> Unit,
    onAuthorize: () -> Unit,
    onOpenShizuku: () -> Unit,
    onCheckShizuku: () -> Unit,
    onSubmit: (String) -> Unit,
    onStop: () -> Unit,
    onHistoryPrevious: (current: String) -> String?,
    onHistoryNext: () -> String?,
    onClearHistory: () -> Unit,
    onClearOutput: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onConfirmDanger: () -> Unit,
    onDismissDanger: () -> Unit,
    onAcknowledgeWarning: () -> Unit,
    onSaveSnippet: (id: String?, label: String, command: String) -> Unit,
    onDeleteSnippet: (ShellSnippet) -> Unit,
    onMessageConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, onMessageConsumed)
    val ready = state.shizuku == ShizukuConnectionState.READY

    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    fun setInput(text: String) {
        input = TextFieldValue(text, selection = TextRange(text.length))
    }
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    // Snippet editor: null = closed; id "" = new snippet.
    var editingSnippetId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorInitialCommand by rememberSaveable { mutableStateOf("") }
    var deletingSnippetId by rememberSaveable { mutableStateOf<String?>(null) }

    val openEditorForNew: (String) -> Unit = { initial ->
        editorInitialCommand = initial
        editingSnippetId = ""
    }
    val insert: (String) -> Unit = { command ->
        setInput(command)
        showLibrary = false
    }

    AbleDetailScaffold(
        title = stringResource(R.string.tools_shell_title),
        subtitle = stringResource(R.string.shell_subtitle),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = {
            if (ready) {
                val hasOutput = state.lines.isNotEmpty()
                IconButton(onClick = onCopy, enabled = hasOutput, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.shell_copy_output))
                }
                IconButton(onClick = onShare, enabled = hasOutput, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.shell_share_output))
                }
                IconButton(onClick = onClearOutput, enabled = hasOutput && !state.running, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.shell_clear_output))
                }
            }
            HelpButton(
                title = stringResource(R.string.shell_help_title),
                text = stringResource(R.string.shell_help_text),
            )
        },
    ) { padding ->
        if (!ready) {
            ShizukuRequired(
                state = state.shizuku,
                onAuthorize = onAuthorize,
                onOpenShizuku = onOpenShizuku,
                onCheckAgain = onCheckShizuku,
                modifier = Modifier.padding(padding),
            )
            return@AbleDetailScaffold
        }
        ClearRestoredFocusOnTouch()
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            val wide = maxWidth >= 840.dp
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = AbleSpacing.screenGutter, vertical = AbleSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                ) {
                    if (!wide) {
                        ShellSnippetChips(
                            snippets = state.snippets,
                            onInsert = insert,
                            onManage = { showLibrary = true },
                        )
                    }
                    ShellOutputPane(
                        lines = state.lines,
                        droppedLines = state.droppedLines,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                    ShellStatusRow(state)
                    ShellInputBar(
                        value = input,
                        onValueChange = { input = it },
                        running = state.running,
                        canHistoryPrevious = state.canHistoryPrevious,
                        canHistoryNext = state.canHistoryNext,
                        onHistoryPrevious = { onHistoryPrevious(input.text)?.let(::setInput) },
                        onHistoryNext = { onHistoryNext()?.let(::setInput) },
                        onRun = { if (input.text.isNotBlank()) onSubmit(input.text) },
                        onStop = onStop,
                    )
                }
                if (wide) {
                    Surface(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        ShellLibraryPanel(
                            snippets = state.snippets,
                            history = state.history,
                            currentInput = input.text,
                            onInsert = { setInput(it) },
                            onAddSnippet = openEditorForNew,
                            onEditSnippet = { editingSnippetId = it.id },
                            onDeleteSnippet = { deletingSnippetId = it.id },
                            onClearHistory = onClearHistory,
                        )
                    }
                }
            }
        }
    }

    if (showLibrary && ready) {
        ModalBottomSheet(
            onDismissRequest = { showLibrary = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ShellLibraryPanel(
                snippets = state.snippets,
                history = state.history,
                currentInput = input.text,
                onInsert = insert,
                onAddSnippet = openEditorForNew,
                onEditSnippet = { editingSnippetId = it.id },
                onDeleteSnippet = { deletingSnippetId = it.id },
                onClearHistory = onClearHistory,
                contentPadding = PaddingValues(start = AbleSpacing.lg, end = AbleSpacing.lg, bottom = AbleSpacing.xxl),
            )
        }
    }

    editingSnippetId?.let { id ->
        val existing = state.snippets.firstOrNull { it.id == id }
        ShellSnippetEditor(
            snippet = existing,
            initialCommand = editorInitialCommand,
            onSave = { label, command ->
                onSaveSnippet(existing?.id, label, command)
                editingSnippetId = null
            },
            onDismiss = { editingSnippetId = null },
        )
    }

    deletingSnippetId?.let { id ->
        val snippet = state.snippets.firstOrNull { it.id == id }
        if (snippet == null) {
            deletingSnippetId = null
        } else {
            ConfirmationDialog(
                title = stringResource(R.string.shell_snippet_delete_title),
                message = stringResource(R.string.shell_snippet_delete_text, snippet.label),
                confirmLabel = stringResource(DsR.string.ds_delete),
                onConfirm = {
                    onDeleteSnippet(snippet)
                    deletingSnippetId = null
                },
                onDismiss = { deletingSnippetId = null },
                destructive = true,
            )
        }
    }

    if (ready && state.showWarning) {
        ShellWarningDialog(onAccept = onAcknowledgeWarning, onLeave = onBack)
    }
    state.pendingDanger?.let { pending ->
        ShellDangerDialog(confirmation = pending, onConfirm = onConfirmDanger, onDismiss = onDismissDanger)
    }
}

@Composable
private fun ShizukuRequired(
    state: ShizukuConnectionState,
    onAuthorize: () -> Unit,
    onOpenShizuku: () -> Unit,
    onCheckAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ReadableWidth(modifier) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(AbleSpacing.screenGutter),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            val running = state == ShizukuConnectionState.WAITING_FOR_PERMISSION || state == ShizukuConnectionState.PERMISSION_DENIED
            PermissionRequiredState(
                title = stringResource(R.string.shell_shizuku_title),
                rationale = stringResource(if (running) R.string.shell_shizuku_permission else R.string.shell_shizuku_not_running),
                actionLabel = stringResource(if (running) R.string.shell_shizuku_authorize else R.string.shell_shizuku_open),
                onAction = if (running) onAuthorize else onOpenShizuku,
                secondaryActionLabel = stringResource(if (running) R.string.shell_shizuku_open else R.string.shell_shizuku_check_again),
                onSecondaryAction = if (running) onOpenShizuku else onCheckAgain,
            )
            HelpButton(
                title = stringResource(R.string.shell_shizuku_help_title),
                text = stringResource(R.string.shell_shizuku_help_text),
            )
        }
    }
}
