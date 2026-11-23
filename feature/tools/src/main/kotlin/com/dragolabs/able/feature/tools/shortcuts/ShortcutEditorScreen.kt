package com.dragolabs.able.feature.tools.shortcuts

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.data.shortcut.GlyphSize
import com.dragolabs.able.core.data.shortcut.IconShape
import com.dragolabs.able.core.data.shortcut.IconSource
import com.dragolabs.able.core.data.shortcut.ShortcutTarget
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ActionTile
import com.dragolabs.able.core.designsystem.component.ClearRestoredFocusOnTouch
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.LoadingState
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.listGroupItemShape
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.activities.ActivityCatalog
import com.dragolabs.able.feature.tools.activities.filterActivities
import com.dragolabs.able.feature.tools.common.MessageEffect
import com.dragolabs.able.feature.tools.common.ToolsSearchField
import com.dragolabs.able.feature.tools.common.highlightMatches
import java.io.File

/**
 * Shared shortcut editor (SHORT-001): used by Shortcut Maker and by Activity Launcher's
 * Create shortcut. [sessionKey] scopes the ViewModel to one editing session.
 */
@Composable
fun ShortcutEditorRoute(
    sessionKey: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    editId: String? = null,
    initialTarget: ShortcutTarget? = null,
    initialLabel: String? = null,
    initialCollectionId: String? = null,
) {
    val context = LocalContext.current
    val viewModel: ShortcutEditorViewModel = viewModel(
        key = "shortcut-editor-$sessionKey",
        factory = ableViewModelFactory { container ->
            ShortcutEditorViewModel(
                repository = ShortcutRepository(container.appContext, container.database.shortcutDao()),
                launcher = ShortcutLauncher(container.appContext),
                catalog = ActivityCatalog(container.appContext),
                cleanupScope = container.applicationScope,
                editId = editId,
                initialTarget = initialTarget,
                initialLabel = initialLabel,
                initialCollectionId = initialCollectionId,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = remember(context) { ShortcutLauncher(context.applicationContext) }
    val loader = remember(launcher) { TargetDrawableLoader { launcher.targetDrawable(it) } }
    val iconDir = remember(context) { File(context.filesDir, ShortcutRepository.ICON_DIR) }

    val close = {
        viewModel.discardUnsavedAssets()
        onClose()
    }
    BackHandler { if (!viewModel.back()) close() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        viewModel.onDocumentPicked(uri, isFolder = false, mimeType = uri?.let { context.contentResolver.getType(it) })
    }
    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        viewModel.onDocumentPicked(uri, isFolder = true, mimeType = null)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        viewModel.onImagePicked(uri)
    }
    var pickerFailed by remember { mutableStateOf(false) }
    val launchFilePicker: (Boolean) -> Unit = { folder ->
        try {
            if (folder) openTree.launch(null) else openDocument.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            pickerFailed = true
        }
    }

    ShortcutEditorScreen(
        state = state,
        loader = loader,
        iconDir = iconDir,
        actions = EditorActions(
            onClose = close,
            onBack = { if (!viewModel.back()) close() },
            onChooseKind = { kind ->
                viewModel.chooseKind(kind)
                if (kind == TargetKind.FILE || kind == TargetKind.FOLDER) launchFilePicker(kind == TargetKind.FOLDER)
            },
            onPickFile = launchFilePicker,
            onPickTarget = viewModel::pickTarget,
            onPickerQuery = viewModel::setPickerQuery,
            onUrlInput = viewModel::setUrlInput,
            onSubmitUrl = viewModel::submitUrl,
            onChangeTarget = viewModel::changeTarget,
            onLabel = viewModel::setLabel,
            onSource = viewModel::setSource,
            onMaterialIcon = viewModel::setMaterialIcon,
            onIconText = viewModel::setIconText,
            onPickImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onBackground = viewModel::setBackground,
            onShape = viewModel::setShape,
            onGlyphSize = viewModel::setGlyphSize,
            onShowLabel = viewModel::setShowLabel,
            onFavorite = viewModel::setFavorite,
            onCollection = viewModel::setCollection,
            onCreateCollection = viewModel::createCollection,
            onSave = viewModel::saveOnly,
            onPin = { bitmap -> viewModel.pin(bitmap?.asAndroidBitmap()) },
            onTry = viewModel::tryTarget,
            onDismissPinResult = viewModel::dismissPinResult,
            onMessageConsumed = viewModel::consumeMessage,
        ),
        pickerFailed = pickerFailed,
        onPickerFailedShown = { pickerFailed = false },
        modifier = modifier,
    )
}

internal class EditorActions(
    val onClose: () -> Unit,
    val onBack: () -> Unit,
    val onChooseKind: (TargetKind) -> Unit,
    val onPickFile: (Boolean) -> Unit,
    val onPickTarget: (ShortcutTarget, String?) -> Unit,
    val onPickerQuery: (String) -> Unit,
    val onUrlInput: (String) -> Unit,
    val onSubmitUrl: () -> Unit,
    val onChangeTarget: () -> Unit,
    val onLabel: (String) -> Unit,
    val onSource: (IconSource) -> Unit,
    val onMaterialIcon: (String) -> Unit,
    val onIconText: (String) -> Unit,
    val onPickImage: () -> Unit,
    val onBackground: (Int?) -> Unit,
    val onShape: (IconShape) -> Unit,
    val onGlyphSize: (GlyphSize) -> Unit,
    val onShowLabel: (Boolean) -> Unit,
    val onFavorite: (Boolean) -> Unit,
    val onCollection: (String?) -> Unit,
    val onCreateCollection: (String) -> Unit,
    val onSave: () -> Unit,
    val onPin: (ImageBitmap?) -> Unit,
    val onTry: () -> Unit,
    val onDismissPinResult: () -> Unit,
    val onMessageConsumed: () -> Unit,
)

@Composable
internal fun ShortcutEditorScreen(
    state: ShortcutEditorUiState,
    loader: TargetDrawableLoader,
    iconDir: File,
    actions: EditorActions,
    pickerFailed: Boolean,
    onPickerFailedShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    MessageEffect(state.message, snackbarHostState, actions.onMessageConsumed)
    LaunchedEffect(pickerFailed) {
        if (pickerFailed) {
            onPickerFailedShown()
            snackbarHostState.showSnackbar(context.getString(R.string.shortcuts_picker_unavailable))
        }
    }
    ClearRestoredFocusOnTouch()

    val title = stringResource(if (state.isExisting) R.string.shortcuts_editor_edit_title else R.string.shortcuts_editor_new_title)
    val stepIndex = when (state.step) {
        EditorStep.TYPE -> 1
        EditorStep.TARGET -> 2
        EditorStep.CUSTOMIZE -> 3
    }
    AbleDetailScaffold(
        title = title,
        subtitle = stringResource(
            when (state.step) {
                EditorStep.TYPE -> R.string.shortcuts_step_type
                EditorStep.TARGET -> R.string.shortcuts_step_target
                EditorStep.CUSTOMIZE -> R.string.shortcuts_step_customize
            },
            stepIndex,
        ),
        onBack = actions.onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) { padding ->
        val contentPadding = screenContentPadding(padding)
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.step == EditorStep.TYPE -> TypeStep(contentPadding, actions)
            state.step == EditorStep.TARGET -> TargetStep(state, padding, contentPadding, loader, actions)
            else -> CustomizeStep(state, contentPadding, loader, iconDir, actions)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Step 1: target type
// ---------------------------------------------------------------------------------------------

internal data class KindPresentation(val icon: ImageVector, val labelRes: Int, val hintRes: Int)

internal fun TargetKind.presentation(): KindPresentation = when (this) {
    TargetKind.APP -> KindPresentation(Icons.Rounded.Apps, R.string.shortcuts_kind_app, R.string.shortcuts_kind_app_hint)
    TargetKind.ACTIVITY -> KindPresentation(Icons.Rounded.Layers, R.string.shortcuts_kind_activity, R.string.shortcuts_kind_activity_hint)
    TargetKind.SETTINGS -> KindPresentation(Icons.Rounded.Settings, R.string.shortcuts_kind_settings, R.string.shortcuts_kind_settings_hint)
    TargetKind.FILE -> KindPresentation(Icons.Rounded.Description, R.string.shortcuts_kind_file, R.string.shortcuts_kind_file_hint)
    TargetKind.FOLDER -> KindPresentation(Icons.Rounded.Folder, R.string.shortcuts_kind_folder, R.string.shortcuts_kind_folder_hint)
    TargetKind.WEBSITE -> KindPresentation(Icons.Rounded.Language, R.string.shortcuts_kind_website, R.string.shortcuts_kind_website_hint)
    TargetKind.ABLE -> KindPresentation(Icons.Rounded.Handyman, R.string.shortcuts_kind_able, R.string.shortcuts_kind_able_hint)
}

private val kindShapes = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Pill, MaterialShapes.Cookie7Sided, MaterialShapes.Square,
    MaterialShapes.Clover4Leaf, MaterialShapes.Circle, MaterialShapes.Sunny,
)

@Composable
private fun TypeStep(contentPadding: PaddingValues, actions: EditorActions) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
    ) {
        items(TargetKind.entries, key = { it.name }) { kind ->
            val p = kind.presentation()
            ActionTile(
                icon = p.icon,
                label = stringResource(p.labelRes),
                supportingText = stringResource(p.hintRes),
                shape = kindShapes[kind.ordinal % kindShapes.size],
                onClick = { actions.onChooseKind(kind) },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Step 2: pick the target
// ---------------------------------------------------------------------------------------------

@Composable
private fun TargetStep(
    state: ShortcutEditorUiState,
    scaffoldPadding: PaddingValues,
    contentPadding: PaddingValues,
    loader: TargetDrawableLoader,
    actions: EditorActions,
) {
    when (state.kind) {
        TargetKind.WEBSITE -> WebsitePicker(state, contentPadding, actions)
        TargetKind.FILE, TargetKind.FOLDER -> DocumentPicker(state.kind == TargetKind.FOLDER, contentPadding, actions)
        else -> SearchablePicker(state, scaffoldPadding, loader, actions)
    }
}

private data class PickerEntry(val key: String, val target: ShortcutTarget, val title: String, val subtitle: String?, val label: String)

@Composable
private fun SearchablePicker(
    state: ShortcutEditorUiState,
    scaffoldPadding: PaddingValues,
    loader: TargetDrawableLoader,
    actions: EditorActions,
) {
    val context = LocalContext.current
    val query = state.pickerQuery
    val entries: List<PickerEntry>? = when (state.kind) {
        TargetKind.APP -> state.apps?.map { PickerEntry(it.packageName, ShortcutTarget.App(it.packageName), it.label, it.packageName, it.label) }
        TargetKind.ACTIVITY -> state.activities?.let { apps ->
            filterActivities(apps, query).flatMap { app ->
                app.activities.map { a ->
                    PickerEntry(a.key, ShortcutTarget.Activity(a.packageName, a.className), a.label, "${a.appLabel} · ${a.shortClassName}", a.label)
                }
            }
        }
        TargetKind.SETTINGS -> state.settings?.map { action ->
            val label = context.getString(settingsPresentation(action).labelRes)
            PickerEntry(action, ShortcutTarget.SettingsPage(action), label, null, label)
        }
        TargetKind.ABLE -> ableActionOptions.map { option ->
            val label = context.getString(option.labelRes)
            PickerEntry(option.route, ShortcutTarget.AbleAction(option.route), label, null, label)
        }
        else -> emptyList()
    }
    val q = query.trim()
    val filtered = entries?.let { list ->
        if (q.isEmpty() || state.kind == TargetKind.ACTIVITY) list
        else list.filter { it.title.contains(q, ignoreCase = true) || it.subtitle?.contains(q, ignoreCase = true) == true }
    }

    Column(Modifier.fillMaxSize().padding(top = scaffoldPadding.calculateTopPadding())) {
        ReadableWidth(Modifier.weight(1f)) {
            Column {
                ToolsSearchField(
                    query = query,
                    onQueryChange = actions.onPickerQuery,
                    placeholder = stringResource(R.string.shortcuts_picker_search),
                    supportingText = filtered?.let { stringResource(R.string.shortcuts_picker_count, it.size) },
                    modifier = Modifier.padding(horizontal = AbleSpacing.screenGutter, vertical = AbleSpacing.sm),
                )
                when {
                    filtered == null -> LoadingState(message = stringResource(R.string.shortcuts_picker_loading))
                    filtered.isEmpty() -> EmptyState(
                        title = stringResource(if (q.isEmpty()) R.string.shortcuts_picker_empty else R.string.shortcuts_picker_no_match, q),
                        message = if (state.kind == TargetKind.SETTINGS) stringResource(R.string.shortcuts_settings_filtered_note) else null,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = AbleSpacing.screenGutter,
                            end = AbleSpacing.screenGutter,
                            bottom = scaffoldPadding.calculateBottomPadding() + AbleSpacing.xxl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (state.kind == TargetKind.SETTINGS) {
                            item(key = "note") {
                                Text(
                                    stringResource(R.string.shortcuts_settings_filtered_note),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = AbleSpacing.sm),
                                )
                            }
                        }
                        items(filtered.size, key = { filtered[it].key }) { index ->
                            val entry = filtered[index]
                            val shape = listGroupItemShape(index, filtered.size)
                            val haptics = rememberAbleHaptics()
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .focusRing(shape)
                                    .clickable { haptics.click(); actions.onPickTarget(entry.target, entry.label) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                leadingContent = { TargetIcon(entry.target, loader) },
                                headlineContent = { Text(highlightMatches(entry.title, q), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = entry.subtitle?.let { { Text(highlightMatches(it, q), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebsitePicker(state: ShortcutEditorUiState, contentPadding: PaddingValues, actions: EditorActions) {
    ReadableWidth {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
        ) {
            OutlinedTextField(
                value = state.urlInput,
                onValueChange = actions.onUrlInput,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.shortcuts_url_label)) },
                placeholder = { Text("example.com") },
                leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
                isError = state.urlError,
                supportingText = { Text(stringResource(if (state.urlError) R.string.shortcuts_url_invalid else R.string.shortcuts_url_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, showKeyboardOnFocus = false),
                keyboardActions = KeyboardActions(onDone = { actions.onSubmitUrl() }),
            )
            AbleButton(onClick = actions.onSubmitUrl, enabled = state.urlInput.isNotBlank(), modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.shortcuts_url_continue))
            }
        }
    }
}

@Composable
private fun DocumentPicker(folder: Boolean, contentPadding: PaddingValues, actions: EditorActions) {
    ReadableWidth {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
        ) {
            AbleCard(
                title = stringResource(if (folder) R.string.shortcuts_kind_folder else R.string.shortcuts_kind_file),
                icon = if (folder) Icons.Rounded.Folder else Icons.Rounded.Description,
                style = AbleCardStyle.Tonal,
            ) {
                Text(stringResource(if (folder) R.string.shortcuts_folder_explain else R.string.shortcuts_file_explain), style = MaterialTheme.typography.bodyMedium)
            }
            AbleButton(onClick = { actions.onPickFile(folder) }, modifier = Modifier.focusRing()) {
                Text(stringResource(if (folder) R.string.shortcuts_pick_folder else R.string.shortcuts_pick_file))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Step 3: customize, preview, pin
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomizeStep(
    state: ShortcutEditorUiState,
    contentPadding: PaddingValues,
    loader: TargetDrawableLoader,
    iconDir: File,
    actions: EditorActions,
) {
    val scheme = MaterialTheme.colorScheme
    val palette = shortcutPalette(scheme)
    val needsBackground = state.meta.source != IconSource.TARGET || !state.hasTargetDrawable
    // Sources without their own background get a theme color by default.
    LaunchedEffect(needsBackground, state.backgroundArgb) {
        if (needsBackground && state.backgroundArgb == null) actions.onBackground(scheme.primaryContainer.toArgb())
    }
    val density = LocalDensity.current
    val pinSizePx = with(density) { 108.dp.roundToPx() }
    val pinBitmap = rememberShortcutIconBitmap(state.iconSpec, iconDir, loader, pinSizePx)
    val haptics = rememberAbleHaptics()

    ReadableWidth {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
        ) {
            PreviewCard(state, pinBitmap)
            PinResultBanner(state, actions)
            state.targetProblem?.let { problem ->
                StatusBanner(
                    level = StatusLevel.WARNING,
                    title = stringResource(R.string.shortcuts_problem_title),
                    message = stringResource(problem.messageRes()),
                    actionLabel = stringResource(R.string.shortcuts_change_target),
                    onAction = actions.onChangeTarget,
                )
            }
            TargetSummary(state, loader, actions)

            OutlinedTextField(
                value = state.label,
                onValueChange = actions.onLabel,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.shortcuts_label)) },
                isError = state.label.isBlank(),
                supportingText = {
                    Text(
                        if (state.label.isBlank()) {
                            stringResource(R.string.shortcuts_label_required)
                        } else {
                            stringResource(R.string.shortcuts_label_counter, state.label.length, ShortcutLauncher.MAX_SHORT_LABEL)
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, showKeyboardOnFocus = false),
            )

            // Icon source
            OptionHeader(stringResource(R.string.shortcuts_icon_source))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                IconSource.entries.forEach { source ->
                    OptionChip(
                        selected = state.meta.source == source,
                        label = stringResource(source.labelRes()),
                        icon = source.icon(),
                        onClick = { haptics.tick(); actions.onSource(source) },
                    )
                }
            }
            when (state.meta.source) {
                IconSource.MATERIAL -> MaterialIconGrid(state.meta.value, actions.onMaterialIcon)
                IconSource.TEXT -> OutlinedTextField(
                    value = state.meta.value.orEmpty(),
                    onValueChange = actions.onIconText,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.shortcuts_icon_text_label)) },
                    supportingText = { Text(stringResource(R.string.shortcuts_icon_text_hint)) },
                    keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
                )
                IconSource.IMAGE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
                    AbleOutlinedButton(onClick = actions.onPickImage, enabled = !state.busy, modifier = Modifier.focusRing()) {
                        Icon(Icons.Rounded.Image, contentDescription = null)
                        Text(
                            stringResource(if (state.meta.value == null) R.string.shortcuts_icon_pick_image else R.string.shortcuts_icon_replace_image),
                            modifier = Modifier.padding(start = AbleSpacing.sm),
                        )
                    }
                    if (state.busy) LoadingIndicator(Modifier.size(32.dp))
                }
                IconSource.TARGET -> Text(
                    stringResource(if (state.hasTargetDrawable) R.string.shortcuts_icon_target_app else R.string.shortcuts_icon_target_symbol),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }

            // Background
            OptionHeader(stringResource(R.string.shortcuts_background))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                if (!needsBackground) {
                    OptionChip(
                        selected = state.backgroundArgb == null,
                        label = stringResource(R.string.shortcuts_background_original),
                        onClick = { haptics.tick(); actions.onBackground(null) },
                    )
                }
                palette.forEachIndexed { index, color ->
                    ColorSwatch(
                        color = color,
                        selected = state.backgroundArgb == color.toArgb(),
                        description = stringResource(R.string.shortcuts_background_color, index + 1),
                        onClick = { haptics.tick(); actions.onBackground(color.toArgb()) },
                    )
                }
            }

            // Shape (advanced, explained)
            OptionHeader(
                stringResource(R.string.shortcuts_shape),
                help = stringResource(R.string.shortcuts_shape_help_title) to stringResource(R.string.shortcuts_shape_help),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                IconShape.entries.forEach { shape ->
                    OptionChip(
                        selected = state.meta.shape == shape,
                        label = stringResource(shape.labelRes()),
                        onClick = { haptics.tick(); actions.onShape(shape) },
                    )
                }
            }

            if (state.meta.source != IconSource.TARGET || !state.hasTargetDrawable || state.backgroundArgb != null) {
                OptionHeader(stringResource(R.string.shortcuts_glyph_size))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                    GlyphSize.entries.forEach { size ->
                        OptionChip(
                            selected = state.meta.glyphSize == size,
                            label = stringResource(size.labelRes()),
                            onClick = { haptics.tick(); actions.onGlyphSize(size) },
                        )
                    }
                }
            }

            // Organization
            OptionHeader(stringResource(R.string.shortcuts_collection))
            CollectionChooser(state, actions)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SwitchRow(
                        title = stringResource(R.string.shortcuts_show_label),
                        supportingText = stringResource(R.string.shortcuts_show_label_summary),
                        checked = state.meta.showLabel,
                        onCheckedChange = actions.onShowLabel,
                        shape = listGroupItemShape(0, 2),
                        modifier = Modifier.weight(1f),
                    )
                    HelpButton(title = stringResource(R.string.shortcuts_show_label), text = stringResource(R.string.shortcuts_show_label_help))
                }
                SwitchRow(
                    title = stringResource(R.string.shortcuts_favorite),
                    checked = state.meta.favorite,
                    onCheckedChange = actions.onFavorite,
                    shape = listGroupItemShape(1, 2),
                    icon = Icons.Rounded.Star,
                    modifier = Modifier.padding(end = 48.dp),
                )
            }

            // Actions
            if (!state.pinSupported) {
                StatusBanner(level = StatusLevel.WARNING, title = stringResource(R.string.shortcuts_pin_unsupported_title), message = stringResource(R.string.shortcuts_pin_unsupported))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.shortcuts_launcher_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                HelpButton(title = stringResource(R.string.shortcuts_limits_title), text = stringResource(R.string.shortcuts_limits_help))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                AbleButton(
                    onClick = { actions.onPin(pinBitmap) },
                    enabled = state.canFinish && state.pinSupported && pinBitmap != null && state.targetProblem == null,
                    modifier = Modifier.focusRing(),
                    haptic = AbleHaptic.Confirm,
                ) {
                    Icon(Icons.Rounded.AddToHomeScreen, contentDescription = null)
                    Text(stringResource(R.string.shortcuts_pin), modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
                }
                AbleOutlinedButton(onClick = { actions.onSave() }, enabled = state.canFinish, modifier = Modifier.focusRing(), haptic = AbleHaptic.Confirm) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Text(stringResource(R.string.shortcuts_save_only), modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
                }
                AbleTextButton(onClick = { actions.onTry() }, enabled = state.target != null && state.targetProblem == null, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.shortcuts_try), modifier = Modifier.padding(start = AbleSpacing.sm), maxLines = 1)
                }
            }
            if (state.saved) {
                AbleTextButton(onClick = actions.onClose, modifier = Modifier.focusRing()) { Text(stringResource(R.string.shortcuts_done)) }
            }
        }
    }
}

@Composable
private fun PreviewCard(state: ShortcutEditorUiState, bitmap: ImageBitmap?) {
    val scheme = MaterialTheme.colorScheme
    val label = if (state.meta.showLabel) state.label.ifBlank { " " }.take(ShortcutLauncher.MAX_SHORT_LABEL) else ""
    val previewDescription = stringResource(R.string.shortcuts_preview_description, state.label)
    Surface(shape = MaterialTheme.shapes.extraLarge, color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AbleSpacing.lg), verticalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
            Text(stringResource(R.string.shortcuts_preview), style = MaterialTheme.typography.titleMedium)
            // A "wallpaper" strip so light and dark icons are both visible.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(Brush.linearGradient(listOf(scheme.tertiaryContainer, scheme.primary, scheme.inverseSurface)))
                    .padding(vertical = AbleSpacing.lg)
                    .semantics { contentDescription = previewDescription },
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    PreviewMask.entries.forEach { mask ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(width = 88.dp, height = 104.dp)) {
                            LaunchedIcon(bitmap, 60.dp, mask.shape())
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(top = AbleSpacing.xs)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
            Text(stringResource(R.string.shortcuts_preview_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PinResultBanner(state: ShortcutEditorUiState, actions: EditorActions) {
    when (state.pinState) {
        PinState.IDLE -> Unit
        PinState.WAITING -> StatusBanner(
            level = StatusLevel.INFO,
            title = stringResource(R.string.shortcuts_pin_waiting_title),
            message = stringResource(R.string.shortcuts_pin_waiting),
        )
        PinState.CONFIRMED -> StatusBanner(
            level = StatusLevel.SUCCESS,
            title = stringResource(R.string.shortcuts_pin_confirmed_title),
            message = stringResource(R.string.shortcuts_pin_confirmed),
            actionLabel = stringResource(R.string.shortcuts_done),
            onAction = actions.onClose,
        )
        PinState.UPDATED -> StatusBanner(
            level = StatusLevel.SUCCESS,
            title = stringResource(R.string.shortcuts_pin_updated_title),
            message = stringResource(R.string.shortcuts_pin_updated),
            actionLabel = stringResource(R.string.shortcuts_done),
            onAction = actions.onClose,
        )
        PinState.NOT_CONFIRMED -> StatusBanner(
            level = StatusLevel.WARNING,
            title = stringResource(R.string.shortcuts_pin_unconfirmed_title),
            message = stringResource(R.string.shortcuts_pin_unconfirmed),
            actionLabel = stringResource(R.string.shortcuts_done),
            onAction = actions.onClose,
            secondaryActionLabel = stringResource(R.string.shortcuts_dismiss),
            onSecondaryAction = actions.onDismissPinResult,
        )
    }
}

@Composable
private fun TargetSummary(state: ShortcutEditorUiState, loader: TargetDrawableLoader, actions: EditorActions) {
    val target = state.target ?: return
    val kind = target.kind.presentation()
    val haptics = rememberAbleHaptics()
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .focusRing()
            .clickable(onClickLabel = stringResource(R.string.shortcuts_change_target)) { haptics.click(); actions.onChangeTarget() },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { TargetIcon(target, loader) },
        overlineContent = { Text(stringResource(kind.labelRes)) },
        headlineContent = { Text(target.describe(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Text(stringResource(R.string.shortcuts_change), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) },
    )
}

/** Human-readable technical description of a target (package, URL, route...). */
@Composable
internal fun ShortcutTarget.describe(): String = when (this) {
    is ShortcutTarget.App -> packageName
    is ShortcutTarget.Activity -> "$packageName/${className.removePrefix(packageName)}"
    is ShortcutTarget.SettingsPage -> stringResource(settingsPresentation(action).labelRes)
    is ShortcutTarget.Document -> Uri.parse(uri).lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: uri
    is ShortcutTarget.Website -> url
    is ShortcutTarget.AbleAction -> ableActionOption(route)?.let { stringResource(it.labelRes) } ?: route
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionChooser(state: ShortcutEditorUiState, actions: EditorActions) {
    var creating by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberAbleHaptics()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        OptionChip(selected = state.collectionId == null, label = stringResource(R.string.shortcuts_unsorted), onClick = { haptics.tick(); actions.onCollection(null) })
        state.collections.forEach { c ->
            OptionChip(selected = state.collectionId == c.id, label = c.name, onClick = { haptics.tick(); actions.onCollection(c.id) })
        }
        OptionChip(selected = false, label = stringResource(R.string.shortcuts_collection_new), onClick = { haptics.click(); creating = true })
    }
    if (creating) {
        CollectionNameDialog(
            title = stringResource(R.string.shortcuts_collection_new),
            initial = "",
            onConfirm = { name -> creating = false; actions.onCreateCollection(name) },
            onDismiss = { creating = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaterialIconGrid(selectedKey: String?, onSelect: (String) -> Unit) {
    val haptics = rememberAbleHaptics()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        materialIconCatalog.forEach { (key, icon) ->
            val selected = key == selectedKey
            val scheme = MaterialTheme.colorScheme
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh)
                    .then(if (selected) Modifier.border(2.dp, scheme.primary, MaterialTheme.shapes.medium) else Modifier)
                    .focusRing(MaterialTheme.shapes.medium)
                    .selectable(selected = selected, role = Role.RadioButton) { haptics.tick(); onSelect(key) }
                    .semantics { contentDescription = key },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) scheme.onPrimaryContainer else scheme.onSurface)
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, description: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, CircleShape)
            .focusRing(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Color(ShortcutIconRenderer.contentColorFor(color.toArgb())),
            )
        }
    }
}

@Composable
internal fun OptionChip(selected: Boolean, label: String, onClick: () -> Unit, icon: ImageVector? = null) {
    AbleFilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        // Callers perform the haptic (tick for choices, click for "new collection").
        haptic = AbleHaptic.None,
        leadingIcon = when {
            selected -> { { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } }
            icon != null -> { { Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } }
            else -> null
        },
        modifier = Modifier.focusRing(MaterialTheme.shapes.small),
    )
}

@Composable
private fun OptionHeader(text: String, help: Pair<String, String>? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text, modifier = Modifier.weight(1f).padding(top = AbleSpacing.sm))
        if (help != null) HelpButton(title = help.first, text = help.second)
    }
}

private fun IconSource.labelRes(): Int = when (this) {
    IconSource.TARGET -> R.string.shortcuts_icon_source_target
    IconSource.MATERIAL -> R.string.shortcuts_icon_source_symbol
    IconSource.TEXT -> R.string.shortcuts_icon_source_text
    IconSource.IMAGE -> R.string.shortcuts_icon_source_image
}

private fun IconSource.icon(): ImageVector = when (this) {
    IconSource.TARGET -> Icons.Rounded.Apps
    IconSource.MATERIAL -> Icons.Rounded.Star
    IconSource.TEXT -> Icons.Rounded.TextFields
    IconSource.IMAGE -> Icons.Rounded.Image
}

private fun IconShape.labelRes(): Int = when (this) {
    IconShape.LAUNCHER -> R.string.shortcuts_shape_launcher
    IconShape.CIRCLE -> R.string.shortcuts_shape_circle
    IconShape.SQUIRCLE -> R.string.shortcuts_shape_squircle
    IconShape.ROUNDED_SQUARE -> R.string.shortcuts_shape_rounded
}

private fun GlyphSize.labelRes(): Int = when (this) {
    GlyphSize.SMALL -> R.string.shortcuts_size_small
    GlyphSize.MEDIUM -> R.string.shortcuts_size_medium
    GlyphSize.LARGE -> R.string.shortcuts_size_large
}
