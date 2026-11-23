package com.dragolabs.able.feature.tools.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import kotlinx.coroutines.launch
import com.dragolabs.able.core.designsystem.R as DsR

private val Mono = FontFamily.Monospace

// region Output pane

/**
 * Terminal-like scrollback. Follows new output while the user is at the bottom; scrolling up
 * pauses following and shows a "latest output" button.
 */
@Composable
internal fun ShellOutputPane(
    lines: List<ShellLine>,
    droppedLines: Long,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var follow by remember { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }

    // Only user scrolling changes "follow": scrolling up pauses it, reaching the end resumes it.
    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.isScrollInProgress, listState.canScrollForward, autoScrolling) }
            .collect { (scrolling, canScrollForward, auto) ->
                if (!auto && (scrolling || !canScrollForward)) follow = !canScrollForward
            }
    }
    val lastId = lines.lastOrNull()?.id
    LaunchedEffect(lastId) {
        if (follow && lines.isNotEmpty()) {
            autoScrolling = true
            try {
                // Large offset is clamped by the list: shows the end of a long wrapped last line.
                listState.scrollToItem(lines.lastIndex + if (droppedLines > 0) 1 else 0, Int.MAX_VALUE / 2)
            } finally {
                autoScrolling = false
            }
        }
    }

    val outputDescription = stringResource(R.string.shell_output_description)
    Surface(
        modifier = modifier.semantics { contentDescription = outputDescription },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (lines.isEmpty()) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(AbleSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                ) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.shell_output_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.shell_output_empty_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(AbleSpacing.md),
                    ) {
                        if (droppedLines > 0) {
                            item(key = "dropped") {
                                DisableSelection {
                                    Text(
                                        stringResource(R.string.shell_dropped_lines, droppedLines, ShellOutputBuffer.MAX_LINES),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = AbleSpacing.sm),
                                    )
                                }
                            }
                        }
                        items(lines, key = { it.id }, contentType = { it.kind }) { line -> ShellLineRow(line) }
                    }
                }
            }
            AnimatedVisibility(
                visible = !follow && lines.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AbleSpacing.md),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        follow = true
                        scope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }
                    },
                    modifier = Modifier.focusRing(),
                ) {
                    Icon(Icons.Rounded.KeyboardDoubleArrowDown, contentDescription = stringResource(R.string.shell_jump_to_latest))
                }
            }
        }
    }
}

@Composable
private fun ShellLineRow(line: ShellLine) {
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono)
    when (line.kind) {
        ShellLineKind.COMMAND -> Text(
            "$ ${line.text}",
            style = mono.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = AbleSpacing.sm, bottom = 2.dp),
        )
        ShellLineKind.STDOUT -> Text(line.text, style = mono, color = MaterialTheme.colorScheme.onSurface)
        ShellLineKind.STDERR -> {
            val errorLabel = stringResource(R.string.shell_stderr_description)
            Row(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                DisableSelection {
                    Text(
                        stringResource(R.string.shell_stderr_label),
                        style = mono.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .width(28.dp)
                            .semantics { contentDescription = errorLabel },
                    )
                }
                Text(line.text, style = mono, color = MaterialTheme.colorScheme.error)
            }
        }
        ShellLineKind.INFO -> Text(
            line.text,
            style = mono.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ShellLineKind.EXIT -> DisableSelection {
            Box(Modifier.padding(vertical = AbleSpacing.xs)) { ShellEndPill(line.end, line.exitCode) }
        }
    }
}

/** Exit status chip: color plus icon plus text (never color alone). */
@Composable
internal fun ShellEndPill(end: ShellEnd?, exitCode: Int?, modifier: Modifier = Modifier) {
    val (text, level) = when (end) {
        ShellEnd.STOPPED -> stringResource(R.string.shell_end_stopped) to StatusLevel.WARNING
        ShellEnd.TIMED_OUT ->
            stringResource(R.string.shell_end_timed_out, (ShellViewModel.DEFAULT_TIMEOUT_MS / 60_000).toInt()) to StatusLevel.WARNING
        ShellEnd.FAILED -> stringResource(R.string.shell_end_failed) to StatusLevel.CRITICAL
        else -> if (exitCode == null) {
            stringResource(R.string.shell_exit_unknown) to StatusLevel.WARNING
        } else {
            stringResource(R.string.shell_exit_code, exitCode) to if (exitCode == 0) StatusLevel.SUCCESS else StatusLevel.CRITICAL
        }
    }
    StatusPill(text = text, level = level, modifier = modifier)
}

// endregion

// region Input bar

/** Running/finished status announced politely to TalkBack. */
@Composable
internal fun ShellStatusRow(state: ShellUiState, modifier: Modifier = Modifier) {
    val visible = state.running || state.lastEnd != null
    if (!visible) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
    ) {
        if (state.running) {
            LoadingIndicator(Modifier.size(32.dp))
            Text(
                stringResource(R.string.shell_running, state.runningCommand.orEmpty()),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = Mono),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            ShellEndPill(state.lastEnd, state.lastExitCode)
        }
    }
}

/** Bottom-docked command input with history navigation and Run/Stop. */
@Composable
internal fun ShellInputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    running: Boolean,
    canHistoryPrevious: Boolean,
    canHistoryNext: Boolean,
    onHistoryPrevious: () -> Unit,
    onHistoryNext: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAbleHaptics()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            Modifier.padding(horizontal = AbleSpacing.xs, vertical = AbleSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                IconButton(
                    onClick = { haptics.tick(); onHistoryPrevious() },
                    enabled = canHistoryPrevious,
                    modifier = Modifier.focusRing(),
                ) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.shell_history_previous)) }
                IconButton(
                    onClick = { haptics.tick(); onHistoryNext() },
                    enabled = canHistoryNext,
                    modifier = Modifier.focusRing(),
                ) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.shell_history_next)) }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = AbleSpacing.xs),
                label = { Text(stringResource(R.string.shell_input_label)) },
                placeholder = { Text(stringResource(R.string.shell_input_placeholder), fontFamily = Mono) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { if (!running) onRun() }),
                shape = MaterialTheme.shapes.large,
            )
            Box(Modifier.padding(start = AbleSpacing.xs, end = AbleSpacing.xs), contentAlignment = Alignment.Center) {
                if (running) {
                    FilledIconButton(
                        onClick = { haptics.reject(); onStop() },
                        modifier = Modifier
                            .size(56.dp)
                            .focusRing(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) { Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.shell_stop)) }
                } else {
                    FilledIconButton(
                        onClick = { haptics.click(); onRun() },
                        enabled = value.text.isNotBlank(),
                        modifier = Modifier
                            .size(56.dp)
                            .focusRing(),
                    ) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.shell_run)) }
                }
            }
        }
    }
}

// endregion

// region Snippets and history

/** Horizontal snippet chips for compact windows. Tapping a chip only fills the input. */
@Composable
internal fun ShellSnippetChips(
    snippets: List<ShellSnippet>,
    onInsert: (String) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAbleHaptics()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
    ) {
        item(key = "manage") {
            AssistChip(
                onClick = { haptics.click(); onManage() },
                label = { Text(stringResource(R.string.shell_snippets_manage)) },
                leadingIcon = { Icon(Icons.Rounded.Bookmarks, contentDescription = null) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .focusRing(),
            )
        }
        items(snippets, key = { it.id }) { snippet ->
            SuggestionChip(
                onClick = { haptics.tick(); onInsert(snippet.command) },
                label = { Text(snippet.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .focusRing(),
            )
        }
    }
}

/** Snippet and history lists, used in the bottom sheet (compact) and the side pane (wide). */
@Composable
internal fun ShellLibraryPanel(
    snippets: List<ShellSnippet>,
    history: List<String>,
    currentInput: String,
    onInsert: (String) -> Unit,
    onAddSnippet: (initialCommand: String) -> Unit,
    onEditSnippet: (ShellSnippet) -> Unit,
    onDeleteSnippet: (ShellSnippet) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(AbleSpacing.lg),
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
    ) {
        snippetSection(snippets, currentInput, onInsert, onAddSnippet, onEditSnippet, onDeleteSnippet)
        historySection(history, onInsert, onClearHistory)
    }
}

private fun LazyListScope.snippetSection(
    snippets: List<ShellSnippet>,
    currentInput: String,
    onInsert: (String) -> Unit,
    onAddSnippet: (String) -> Unit,
    onEditSnippet: (ShellSnippet) -> Unit,
    onDeleteSnippet: (ShellSnippet) -> Unit,
) {
    item(key = "snippets-header") {
        SectionHeader(
            stringResource(R.string.shell_snippets),
            Modifier
                .padding(vertical = AbleSpacing.sm)
                .semantics { heading() },
        )
    }
    item(key = "snippets-actions") {
        Row(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            TextButton(onClick = { onAddSnippet("") }, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.shell_snippet_add), Modifier.padding(start = AbleSpacing.xs))
            }
            if (currentInput.isNotBlank()) {
                TextButton(onClick = { onAddSnippet(currentInput) }, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null)
                    Text(stringResource(R.string.shell_snippet_save_current), Modifier.padding(start = AbleSpacing.xs))
                }
            }
        }
    }
    if (snippets.isEmpty()) {
        item(key = "snippets-empty") {
            Text(
                stringResource(R.string.shell_snippets_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(snippets, key = { "snippet-" + it.id }) { snippet ->
        LibraryRow(
            title = snippet.label,
            command = snippet.command,
            onInsert = { onInsert(snippet.command) },
            trailing = {
                IconButton(onClick = { onEditSnippet(snippet) }, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.shell_snippet_edit, snippet.label))
                }
                IconButton(onClick = { onDeleteSnippet(snippet) }, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.shell_snippet_delete, snippet.label))
                }
            },
        )
    }
}

private fun LazyListScope.historySection(
    history: List<String>,
    onInsert: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    item(key = "history-header") {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = AbleSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(
                stringResource(R.string.shell_history),
                Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearHistory, modifier = Modifier.focusRing()) {
                    Text(stringResource(R.string.shell_history_clear))
                }
            }
        }
    }
    if (history.isEmpty()) {
        item(key = "history-empty") {
            Text(
                stringResource(R.string.shell_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(history.asReversed(), key = { "history-$it" }) { command ->
        LibraryRow(title = null, command = command, onInsert = { onInsert(command) })
    }
}

@Composable
private fun LibraryRow(
    title: String?,
    command: String,
    onInsert: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val haptics = rememberAbleHaptics()
    val insertLabel = stringResource(R.string.shell_insert)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = insertLabel) { haptics.tick(); onInsert() }
                    .focusRing(MaterialTheme.shapes.large)
                    .padding(horizontal = AbleSpacing.lg, vertical = AbleSpacing.sm),
                verticalArrangement = Arrangement.Center,
            ) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    command,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing()
        }
    }
}

// endregion

// region Dialogs

@Composable
internal fun ShellWarningDialog(onAccept: () -> Unit, onLeave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLeave,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
        title = { Text(stringResource(R.string.shell_warning_title)) },
        text = { Text(stringResource(R.string.shell_warning_text)) },
        confirmButton = { Button(onClick = onAccept) { Text(stringResource(R.string.shell_warning_accept)) } },
        dismissButton = { TextButton(onClick = onLeave) { Text(stringResource(R.string.shell_warning_leave)) } },
    )
}

@Composable
internal fun ShellDangerDialog(confirmation: DangerConfirmation, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.shell_danger_title), Modifier.weight(1f, fill = false))
                HelpButton(
                    title = stringResource(R.string.shell_danger_help_title),
                    text = stringResource(R.string.shell_danger_help_text),
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(
                        confirmation.command,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono),
                        modifier = Modifier.padding(AbleSpacing.sm),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(stringResource(R.string.shell_danger_text))
                confirmation.risks.forEach { risk -> Text("• " + stringResource(risk.labelRes())) }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.shell_danger_run)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(DsR.string.ds_cancel)) } },
    )
}

/** Add/edit a snippet. [snippet] null means a new one prefilled with [initialCommand]. */
@Composable
internal fun ShellSnippetEditor(
    snippet: ShellSnippet?,
    initialCommand: String,
    onSave: (label: String, command: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf(snippet?.label.orEmpty()) }
    var command by rememberSaveable { mutableStateOf(snippet?.command ?: initialCommand) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (snippet == null) R.string.shell_snippet_new_title else R.string.shell_snippet_edit_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.shell_snippet_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text(stringResource(R.string.shell_snippet_command)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(label, command) }, enabled = command.isNotBlank()) {
                Text(stringResource(DsR.string.ds_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(DsR.string.ds_cancel)) } },
    )
}

internal fun ShellRisk.labelRes(): Int = when (this) {
    ShellRisk.DELETE_FILES -> R.string.shell_risk_delete_files
    ShellRisk.UNINSTALL_APP -> R.string.shell_risk_uninstall_app
    ShellRisk.CLEAR_APP_DATA -> R.string.shell_risk_clear_app_data
    ShellRisk.REBOOT -> R.string.shell_risk_reboot
    ShellRisk.CHANGE_SETTINGS -> R.string.shell_risk_change_settings
    ShellRisk.WINDOW_MANAGER -> R.string.shell_risk_window_manager
    ShellRisk.RAW_DISK_WRITE -> R.string.shell_risk_raw_disk_write
    ShellRisk.FORMAT -> R.string.shell_risk_format
}

// endregion
