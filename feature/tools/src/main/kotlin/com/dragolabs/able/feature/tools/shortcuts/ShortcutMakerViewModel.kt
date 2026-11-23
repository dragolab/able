package com.dragolabs.able.feature.tools.shortcuts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.data.shortcut.ShortcutMeta
import com.dragolabs.able.core.data.shortcut.ShortcutTarget
import com.dragolabs.able.core.data.shortcut.meta
import com.dragolabs.able.core.data.shortcut.target
import com.dragolabs.able.core.model.CollectionDeletionPolicy
import com.dragolabs.able.core.model.ShortcutCollection
import com.dragolabs.able.core.model.ShortcutItem
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Home list filter (SHORT-003). */
@Immutable
sealed interface ShortcutFilter {
    data object All : ShortcutFilter
    data object Favorites : ShortcutFilter
    data object Unsorted : ShortcutFilter
    data class InCollection(val id: String) : ShortcutFilter
}

@Immutable
data class ShortcutRow(
    val item: ShortcutItem,
    val target: ShortcutTarget?,
    val meta: ShortcutMeta,
    /** Read back from the launcher's pinned list; not an assumption. */
    val onHomeScreen: Boolean,
) {
    val spec: IconSpec get() = IconSpec(target, meta, parseArgbHex(item.iconConfig.backgroundColorHex))
}

@Immutable
data class ShortcutHomeUiState(
    val loading: Boolean = true,
    val rows: List<ShortcutRow> = emptyList(),
    val collections: List<ShortcutCollection> = emptyList(),
    val filter: ShortcutFilter = ShortcutFilter.All,
    val pinSupported: Boolean = true,
    val message: UiMessage? = null,
) {
    val favorites: List<ShortcutRow> get() = rows.filter { it.meta.favorite }
    val recent: List<ShortcutRow> get() = rows.sortedByDescending { it.item.lastActivityAtMs }.take(RECENT_COUNT)
    val unsorted: List<ShortcutRow> get() = rows.filter { it.item.collectionId == null || collections.none { c -> c.id == it.item.collectionId } }
    fun inCollection(id: String): List<ShortcutRow> = rows.filter { it.item.collectionId == id }

    /** Rows for a single-list filter (everything except [ShortcutFilter.All]). */
    fun filtered(): List<ShortcutRow> = when (val f = filter) {
        ShortcutFilter.All -> rows
        ShortcutFilter.Favorites -> favorites
        ShortcutFilter.Unsorted -> unsorted
        is ShortcutFilter.InCollection -> inCollection(f.id)
    }

    companion object {
        const val RECENT_COUNT = 5
    }
}

class ShortcutMakerViewModel(
    private val repository: ShortcutRepository,
    private val launcher: ShortcutLauncher,
) : ViewModel() {

    private val pinnedIds = MutableStateFlow(launcher.pinnedIds())
    private val local = MutableStateFlow(ShortcutHomeUiState(pinSupported = launcher.isPinSupported()))

    val uiState: StateFlow<ShortcutHomeUiState> = combine(
        repository.shortcuts,
        repository.collections,
        pinnedIds,
        local,
    ) { shortcuts, collections, pinned, ui ->
        val sortedCollections = collections.sortedBy { it.sortOrder }
        val filter = ui.filter.takeIf { f -> f !is ShortcutFilter.InCollection || sortedCollections.any { it.id == f.id } } ?: ShortcutFilter.All
        ui.copy(
            loading = false,
            collections = sortedCollections,
            filter = filter,
            rows = shortcuts
                .sortedWith(compareBy<ShortcutItem> { it.sortOrder }.thenBy { it.label.lowercase() })
                .map { ShortcutRow(it, it.target(), it.meta, launcherShortcutId(it.id) in pinned) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShortcutHomeUiState())

    /** Refreshes the launcher read-back when the screen becomes visible. */
    fun refreshPinned() {
        pinnedIds.value = launcher.pinnedIds()
        local.update { it.copy(pinSupported = launcher.isPinSupported()) }
    }

    fun setFilter(filter: ShortcutFilter) = local.update { it.copy(filter = filter) }

    fun toggleFavorite(row: ShortcutRow) {
        viewModelScope.launch { repository.setFavorite(row.item, !row.meta.favorite) }
    }

    fun moveTo(row: ShortcutRow, collectionId: String?) {
        viewModelScope.launch {
            repository.moveTo(row.item, collectionId)
            local.update { it.copy(message = UiMessage(R.string.shortcuts_moved)) }
        }
    }

    fun open(row: ShortcutRow) {
        val target = row.target
        if (target == null || !launcher.open(target)) local.update { it.copy(message = UiMessage(R.string.shortcuts_open_failed)) }
    }

    fun delete(row: ShortcutRow) {
        viewModelScope.launch {
            repository.delete(row.item)
            local.update {
                it.copy(message = UiMessage(if (row.onHomeScreen) R.string.shortcuts_deleted_pinned else R.string.shortcuts_deleted, row.item.label))
            }
        }
    }

    // Collections ---------------------------------------------------------------------------

    fun createCollection(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createCollection(name) }
    }

    fun renameCollection(collection: ShortcutCollection, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renameCollection(collection, name) }
    }

    fun moveCollection(collection: ShortcutCollection, delta: Int) {
        viewModelScope.launch { repository.moveCollection(collection, delta) }
    }

    /** ADR-020: [policy] comes from the user's explicit choice for non-empty collections. */
    fun deleteCollection(collection: ShortcutCollection, policy: CollectionDeletionPolicy) {
        viewModelScope.launch {
            repository.deleteCollection(collection, policy)
            local.update {
                it.copy(
                    message = UiMessage(
                        if (policy == CollectionDeletionPolicy.MOVE_TO_UNSORTED) R.string.shortcuts_collection_deleted_moved else R.string.shortcuts_collection_deleted_all,
                        collection.name,
                    ),
                )
            }
        }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }
}
