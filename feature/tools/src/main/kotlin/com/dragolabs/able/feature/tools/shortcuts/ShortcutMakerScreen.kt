package com.dragolabs.able.feature.tools.shortcuts

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.hapticClickable
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ClearRestoredFocusOnTouch
import com.dragolabs.able.core.designsystem.component.ConfirmationDialog
import com.dragolabs.able.core.designsystem.component.EmptyState
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.LoadingState
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.listGroupItemShape
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.CollectionDeletionPolicy
import com.dragolabs.able.core.model.ShortcutCollection
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.MessageEffect
import java.io.File
import java.util.UUID
import com.dragolabs.able.core.designsystem.R as DsR

/** Editor session requested from the home list. */
private data class EditorRequest(val key: String, val editId: String?, val collectionId: String?)

/** Shortcut Maker tool (SHORT-001..SHORT-003): saved shortcuts, collections and the shared editor. */
@Composable
fun ShortcutMakerRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: ShortcutMakerViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            ShortcutMakerViewModel(
                repository = ShortcutRepository(container.appContext, container.database.shortcutDao()),
                launcher = ShortcutLauncher(container.appContext),
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editorKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editorEditId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorCollection by rememberSaveable { mutableStateOf<String?>(null) }
    val openEditor: (EditorRequest) -> Unit = {
        editorKey = it.key
        editorEditId = it.editId
        editorCollection = it.collectionId
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPinned() }

    val key = editorKey
    if (key != null) {
        ShortcutEditorRoute(
            sessionKey = key,
            editId = editorEditId,
            initialCollectionId = editorCollection,
            onClose = {
                editorKey = null
                viewModel.refreshPinned()
            },
            modifier = modifier,
        )
        return
    }

    val launcher = remember(context) { ShortcutLauncher(context.applicationContext) }
    val loader = remember(launcher) { TargetDrawableLoader { launcher.targetDrawable(it) } }
    val iconDir = remember(context) { File(context.filesDir, ShortcutRepository.ICON_DIR) }
    ShortcutMakerScreen(
        state = state,
        loader = loader,
        iconDir = iconDir,
        onBack = onBack,
        onNew = {
            val collection = (state.filter as? ShortcutFilter.InCollection)?.id
            openEditor(EditorRequest(UUID.randomUUID().toString(), null, collection))
        },
        onEdit = { row -> openEditor(EditorRequest(UUID.randomUUID().toString(), row.item.id, null)) },
        onFilter = viewModel::setFilter,
        onOpen = viewModel::open,
        onToggleFavorite = viewModel::toggleFavorite,
        onMove = viewModel::moveTo,
        onDelete = viewModel::delete,
        onCreateCollection = viewModel::createCollection,
        onRenameCollection = viewModel::renameCollection,
        onMoveCollection = viewModel::moveCollection,
        onDeleteCollection = viewModel::deleteCollection,
        onMessageConsumed = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@Composable
internal fun ShortcutMakerScreen(
    state: ShortcutHomeUiState,
    loader: TargetDrawableLoader,
    iconDir: File,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (ShortcutRow) -> Unit,
    onFilter: (ShortcutFilter) -> Unit,
    onOpen: (ShortcutRow) -> Unit,
    onToggleFavorite: (ShortcutRow) -> Unit,
    onMove: (ShortcutRow, String?) -> Unit,
    onDelete: (ShortcutRow) -> Unit,
    onCreateCollection: (String) -> Unit,
    onRenameCollection: (ShortcutCollection, String) -> Unit,
    onMoveCollection: (ShortcutCollection, Int) -> Unit,
    onDeleteCollection: (ShortcutCollection, CollectionDeletionPolicy) -> Unit,
    onMessageConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, onMessageConsumed)
    ClearRestoredFocusOnTouch()
    val haptics = rememberAbleHaptics()

    var manageCollections by rememberSaveable { mutableStateOf(false) }
    var moving by remember { mutableStateOf<ShortcutRow?>(null) }
    var deleting by remember { mutableStateOf<ShortcutRow?>(null) }

    val rowActions = RowActions(
        onEdit = onEdit,
        onOpen = onOpen,
        onToggleFavorite = { haptics.toggle(!it.meta.favorite); onToggleFavorite(it) },
        onMove = { moving = it },
        onDelete = { deleting = it },
    )

    AbleDetailScaffold(
        title = stringResource(R.string.shortcuts_title),
        subtitle = if (state.loading) null else stringResource(R.string.shortcuts_subtitle_count, state.rows.size, state.collections.size),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            HelpButton(title = stringResource(R.string.shortcuts_limits_title), text = stringResource(R.string.shortcuts_limits_help))
            AbleIconButton(onClick = { manageCollections = true }) {
                Icon(Icons.Rounded.FolderSpecial, contentDescription = stringResource(R.string.shortcuts_collections_manage))
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { haptics.click(); onNew() },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.shortcuts_new)) },
                modifier = Modifier.focusRing(MaterialTheme.shapes.large),
            )
        },
        modifier = modifier,
    ) { padding ->
        val contentPadding = screenContentPadding(padding)
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@AbleDetailScaffold
        }
        val favoritesTitle = stringResource(R.string.shortcuts_section_favorites)
        val recentTitle = stringResource(R.string.shortcuts_section_recent)
        val unsortedTitle = stringResource(R.string.shortcuts_unsorted)
        ReadableWidth {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = contentPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = contentPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 72.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "filters") { FilterRow(state, onFilter) }
                if (!state.pinSupported) {
                    item(key = "unsupported") {
                        StatusBanner(
                            level = StatusLevel.WARNING,
                            title = stringResource(R.string.shortcuts_pin_unsupported_title),
                            message = stringResource(R.string.shortcuts_pin_unsupported),
                            modifier = Modifier.padding(vertical = AbleSpacing.sm),
                        )
                    }
                }
                if (state.rows.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = stringResource(R.string.shortcuts_empty_title),
                            message = stringResource(R.string.shortcuts_empty_message),
                            icon = Icons.Rounded.AddToHomeScreen,
                            actionLabel = stringResource(R.string.shortcuts_new),
                            onAction = onNew,
                        )
                    }
                    return@LazyColumn
                }
                when (val filter = state.filter) {
                    ShortcutFilter.All -> {
                        section("favorites", favoritesTitle, state.favorites, loader, iconDir, rowActions)
                        section("recent", recentTitle, state.recent, loader, iconDir, rowActions)
                        state.collections.forEach { c ->
                            section("c-${c.id}", c.name, state.inCollection(c.id), loader, iconDir, rowActions, showEmpty = true)
                        }
                        section("unsorted", unsortedTitle, state.unsorted, loader, iconDir, rowActions)
                    }
                    else -> {
                        val rows = state.filtered()
                        if (rows.isEmpty()) {
                            item(key = "filter-empty") {
                                EmptyState(
                                    title = stringResource(if (filter == ShortcutFilter.Favorites) R.string.shortcuts_no_favorites else R.string.shortcuts_collection_empty),
                                    icon = if (filter == ShortcutFilter.Favorites) Icons.Rounded.StarOutline else Icons.Rounded.FolderSpecial,
                                )
                            }
                        } else {
                            section("filtered", null, rows, loader, iconDir, rowActions)
                        }
                    }
                }
            }
        }
    }

    if (manageCollections) {
        CollectionsSheet(
            state = state,
            onDismiss = { manageCollections = false },
            onCreate = onCreateCollection,
            onRename = onRenameCollection,
            onMove = onMoveCollection,
            onDelete = onDeleteCollection,
        )
    }
    moving?.let { row ->
        MoveDialog(
            row = row,
            collections = state.collections,
            onMove = { id -> moving = null; onMove(row, id) },
            onDismiss = { moving = null },
        )
    }
    deleting?.let { row ->
        ConfirmationDialog(
            title = stringResource(R.string.shortcuts_delete_title, row.item.label),
            message = stringResource(if (row.onHomeScreen) R.string.shortcuts_delete_message_pinned else R.string.shortcuts_delete_message),
            confirmLabel = stringResource(DsR.string.ds_delete),
            destructive = true,
            icon = Icons.Rounded.Delete,
            onConfirm = { deleting = null; onDelete(row) },
            onDismiss = { deleting = null },
        )
    }
}

private class RowActions(
    val onEdit: (ShortcutRow) -> Unit,
    val onOpen: (ShortcutRow) -> Unit,
    val onToggleFavorite: (ShortcutRow) -> Unit,
    val onMove: (ShortcutRow) -> Unit,
    val onDelete: (ShortcutRow) -> Unit,
)

@Composable
private fun FilterRow(state: ShortcutHomeUiState, onFilter: (ShortcutFilter) -> Unit) {
    val haptics = rememberAbleHaptics()
    val filters = buildList {
        add(ShortcutFilter.All to stringResource(R.string.shortcuts_filter_all))
        add(ShortcutFilter.Favorites to stringResource(R.string.shortcuts_section_favorites))
        add(ShortcutFilter.Unsorted to stringResource(R.string.shortcuts_unsorted))
        state.collections.forEach { add(ShortcutFilter.InCollection(it.id) to it.name) }
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
        modifier = Modifier.fillMaxWidth().padding(bottom = AbleSpacing.sm),
    ) {
        items(filters, key = { it.first.toString() }) { (filter, label) ->
            OptionChip(selected = state.filter == filter, label = label, onClick = { haptics.tick(); onFilter(filter) })
        }
    }
}

private fun LazyListScope.section(
    key: String,
    title: String?,
    rows: List<ShortcutRow>,
    loader: TargetDrawableLoader,
    iconDir: File,
    actions: RowActions,
    showEmpty: Boolean = false,
) {
    if (rows.isEmpty() && !showEmpty) return
    if (title != null) {
        item(key = "h-$key") {
            SectionHeader(title, modifier = Modifier.padding(top = AbleSpacing.lg, bottom = AbleSpacing.sm))
        }
    }
    if (rows.isEmpty()) {
        item(key = "e-$key") {
            Text(
                stringResource(R.string.shortcuts_collection_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    items(rows.size, key = { "$key-${rows[it].item.id}" }) { index ->
        ShortcutRowItem(rows[index], listGroupItemShape(index, rows.size), loader, iconDir, actions)
    }
}

@Composable
private fun ShortcutRowItem(row: ShortcutRow, shape: androidx.compose.ui.graphics.Shape, loader: TargetDrawableLoader, iconDir: File, actions: RowActions) {
    var menu by remember { mutableStateOf(false) }
    val haptics = rememberAbleHaptics()
    val target = row.target
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .focusRing(shape)
            .clickable(onClickLabel = stringResource(R.string.shortcuts_edit_and_pin)) { haptics.click(); actions.onEdit(row) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { ShortcutIconView(row.spec, iconDir, loader, 48.dp) },
        headlineContent = {
            Text(row.item.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    target?.let { stringResource(it.kind.presentation().labelRes) + " · " + it.describe() }
                        ?: stringResource(R.string.shortcuts_problem_invalid),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.onHomeScreen) StatusPill(text = stringResource(R.string.shortcuts_on_home_screen), level = StatusLevel.SUCCESS)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbleIconButton(onClick = { actions.onToggleFavorite(row) }, haptic = AbleHaptic.None) {
                    Icon(
                        if (row.meta.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = stringResource(if (row.meta.favorite) R.string.shortcuts_unfavorite else R.string.shortcuts_make_favorite),
                        tint = if (row.meta.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AbleIconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.shortcuts_more_actions, row.item.label))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shortcuts_edit_and_pin)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { haptics.click(); menu = false; actions.onEdit(row) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shortcuts_try)) },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                        enabled = target != null,
                        onClick = { haptics.click(); menu = false; actions.onOpen(row) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shortcuts_move)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                        onClick = { haptics.click(); menu = false; actions.onMove(row) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(DsR.string.ds_delete)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = { haptics.click(); menu = false; actions.onDelete(row) },
                    )
                }
            }
        },
    )
}

@Composable
private fun MoveDialog(row: ShortcutRow, collections: List<ShortcutCollection>, onMove: (String?) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(row.item.collectionId) }
    val options = listOf<Pair<String?, String>>(null to stringResource(R.string.shortcuts_unsorted)) + collections.map { it.id to it.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
        title = { Text(stringResource(R.string.shortcuts_move_title, row.item.label)) },
        text = {
            Column {
                options.forEach { (id, name) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .focusRing(MaterialTheme.shapes.medium)
                            .hapticClickable(role = Role.RadioButton, haptic = AbleHaptic.Tick) { selected = id }
                            .padding(vertical = AbleSpacing.xs),
                    ) {
                        RadioButton(selected = selected == id, onClick = null)
                        Text(name, modifier = Modifier.padding(start = AbleSpacing.sm))
                    }
                }
            }
        },
        confirmButton = { AbleTextButton(onClick = { onMove(selected) }) { Text(stringResource(R.string.shortcuts_move)) } },
        dismissButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(DsR.string.ds_cancel)) } },
    )
}

@Composable
private fun CollectionsSheet(
    state: ShortcutHomeUiState,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (ShortcutCollection, String) -> Unit,
    onMove: (ShortcutCollection, Int) -> Unit,
    onDelete: (ShortcutCollection, CollectionDeletionPolicy) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ShortcutCollection?>(null) }
    var deleting by remember { mutableStateOf<ShortcutCollection?>(null) }
    val haptics = rememberAbleHaptics()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = AbleSpacing.screenGutter).padding(bottom = AbleSpacing.xl), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.shortcuts_collections_manage), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                HelpButton(title = stringResource(R.string.shortcuts_collections_manage), text = stringResource(R.string.shortcuts_collections_help))
            }
            Text(
                stringResource(R.string.shortcuts_collections_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = AbleSpacing.md),
            )
            state.collections.forEachIndexed { index, c ->
                val count = state.inCollection(c.id).size
                val shape = listGroupItemShape(index, state.collections.size)
                ListItem(
                    modifier = Modifier.fillMaxWidth().clip(shape),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(stringResource(R.string.shortcuts_collection_count, count)) },
                    trailingContent = {
                        Row {
                            AbleIconButton(onClick = { onMove(c, -1) }, enabled = index > 0, haptic = AbleHaptic.Tick) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.shortcuts_collection_move_up, c.name))
                            }
                            AbleIconButton(onClick = { onMove(c, 1) }, enabled = index < state.collections.lastIndex, haptic = AbleHaptic.Tick) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = stringResource(R.string.shortcuts_collection_move_down, c.name))
                            }
                            AbleIconButton(onClick = { renaming = c }) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.shortcuts_collection_rename, c.name))
                            }
                            AbleIconButton(onClick = { deleting = c }) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.shortcuts_collection_delete, c.name))
                            }
                        }
                    },
                )
            }
            if (state.collections.isEmpty()) {
                Text(stringResource(R.string.shortcuts_collections_none), style = MaterialTheme.typography.bodyMedium)
            }
            AbleTextButton(onClick = { creating = true }, modifier = Modifier.padding(top = AbleSpacing.sm).focusRing()) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.shortcuts_collection_new), modifier = Modifier.padding(start = AbleSpacing.sm))
            }
        }
    }

    if (creating) {
        CollectionNameDialog(
            title = stringResource(R.string.shortcuts_collection_new),
            initial = "",
            onConfirm = { creating = false; onCreate(it) },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { c ->
        CollectionNameDialog(
            title = stringResource(R.string.shortcuts_collection_rename, c.name),
            initial = c.name,
            onConfirm = { renaming = null; onRename(c, it) },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { c ->
        val count = state.inCollection(c.id).size
        if (count == 0) {
            ConfirmationDialog(
                title = stringResource(R.string.shortcuts_collection_delete_title, c.name),
                message = stringResource(R.string.shortcuts_collection_delete_empty),
                confirmLabel = stringResource(DsR.string.ds_delete),
                destructive = true,
                onConfirm = { deleting = null; onDelete(c, CollectionDeletionPolicy.MOVE_TO_UNSORTED) },
                onDismiss = { deleting = null },
            )
        } else {
            DeleteNonEmptyCollectionDialog(
                collection = c,
                count = count,
                onChoose = { policy -> deleting = null; onDelete(c, policy) },
                onDismiss = { deleting = null },
            )
        }
    }
}

/** ADR-020: explicit choice between keeping the shortcuts (Unsorted) and deleting them. Cancel changes nothing. */
@Composable
private fun DeleteNonEmptyCollectionDialog(
    collection: ShortcutCollection,
    count: Int,
    onChoose: (CollectionDeletionPolicy) -> Unit,
    onDismiss: () -> Unit,
) {
    var policy by remember { mutableStateOf(CollectionDeletionPolicy.MOVE_TO_UNSORTED) }
    val haptics = rememberAbleHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.shortcuts_collection_delete_title, collection.name)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                Text(stringResource(R.string.shortcuts_collection_delete_nonempty, count))
                listOf(
                    CollectionDeletionPolicy.MOVE_TO_UNSORTED to (R.string.shortcuts_collection_delete_move to R.string.shortcuts_collection_delete_move_summary),
                    CollectionDeletionPolicy.DELETE_CONTAINED_SHORTCUTS to (R.string.shortcuts_collection_delete_all to R.string.shortcuts_collection_delete_all_summary),
                ).forEach { (option, labels) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .focusRing(MaterialTheme.shapes.medium)
                            .clickable(role = Role.RadioButton) { haptics.tick(); policy = option }
                            .padding(vertical = AbleSpacing.xs),
                    ) {
                        RadioButton(selected = policy == option, onClick = null)
                        Column(Modifier.padding(start = AbleSpacing.sm)) {
                            Text(stringResource(labels.first), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(labels.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            AbleTextButton(onClick = {
                if (policy == CollectionDeletionPolicy.DELETE_CONTAINED_SHORTCUTS) haptics.reject() else haptics.confirm()
                onChoose(policy)
            }, haptic = AbleHaptic.None) {
                Text(
                    stringResource(
                        if (policy == CollectionDeletionPolicy.MOVE_TO_UNSORTED) R.string.shortcuts_collection_delete_move else R.string.shortcuts_collection_delete_all,
                    ),
                    color = if (policy == CollectionDeletionPolicy.DELETE_CONTAINED_SHORTCUTS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(DsR.string.ds_cancel)) } },
    )
}

@Composable
internal fun CollectionNameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                label = { Text(stringResource(R.string.shortcuts_collection_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { AbleTextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(DsR.string.ds_save)) } },
        dismissButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(DsR.string.ds_cancel)) } },
    )
}

@Suppress("unused")
private val pinnedIcon = Icons.Rounded.CheckCircle
