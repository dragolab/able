package com.dragolabs.able.feature.tools.shortcuts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.dragolabs.able.core.data.shortcut.IconSource
import com.dragolabs.able.core.data.shortcut.ShortcutMeta
import com.dragolabs.able.core.data.shortcut.meta
import com.dragolabs.able.core.database.dao.ShortcutDao
import com.dragolabs.able.core.database.entity.ShortcutCollectionEntity
import com.dragolabs.able.core.database.entity.ShortcutDefinitionEntity
import com.dragolabs.able.core.model.CollectionDeletionPolicy
import com.dragolabs.able.core.model.ShortcutCollection
import com.dragolabs.able.core.model.ShortcutItem
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Feature-local persistence for shortcut definitions, collections and custom icon assets
 * (SHORT-003, ADR-020). Definitions live in Room through [ShortcutDao]; gallery images are copied
 * into `files/shortcut_icons/` so they survive the source photo being deleted.
 */
class ShortcutRepository(
    private val context: Context,
    private val dao: ShortcutDao,
) {
    private val iconDir: File get() = File(context.filesDir, ICON_DIR).apply { mkdirs() }

    val shortcuts: Flow<List<ShortcutItem>> = dao.getAllShortcuts().map { list -> list.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }
    val collections: Flow<List<ShortcutCollection>> = dao.getAllCollections().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): ShortcutItem? = shortcuts.first().firstOrNull { it.id == id }

    suspend fun save(item: ShortcutItem) = dao.insertShortcut(ShortcutDefinitionEntity.fromDomain(item))

    suspend fun setFavorite(item: ShortcutItem, favorite: Boolean) = save(item.withMeta(item.meta.copy(favorite = favorite)))

    suspend fun moveTo(item: ShortcutItem, collectionId: String?) = save(item.copy(collectionId = collectionId))

    /** Deletes the definition and its custom icon asset. Pinned launcher icons are not removable by Able. */
    suspend fun delete(item: ShortcutItem) {
        dao.deleteShortcutById(item.id)
        deleteIconAssetIfUnused(item.customIconFile())
    }

    // Collections ---------------------------------------------------------------------------

    suspend fun createCollection(name: String): ShortcutCollection {
        val existing = collections.first()
        val collection = ShortcutCollection(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        dao.insertCollection(ShortcutCollectionEntity.fromDomain(collection))
        return collection
    }

    suspend fun renameCollection(collection: ShortcutCollection, name: String) =
        dao.insertCollection(ShortcutCollectionEntity.fromDomain(collection.copy(name = name.trim())))

    /** Moves [collection] one position up/down and rewrites a dense sort order. */
    suspend fun moveCollection(collection: ShortcutCollection, delta: Int) {
        val ordered = collections.first().sortedBy { it.sortOrder }.toMutableList()
        val from = ordered.indexOfFirst { it.id == collection.id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, ordered.lastIndex)
        if (to == from) return
        ordered.add(to, ordered.removeAt(from))
        ordered.forEachIndexed { index, c ->
            if (c.sortOrder != index) dao.insertCollection(ShortcutCollectionEntity.fromDomain(c.copy(sortOrder = index)))
        }
    }

    /** ADR-020: the caller must have asked the user which [policy] to apply for a non-empty collection. */
    suspend fun deleteCollection(collection: ShortcutCollection, policy: CollectionDeletionPolicy) {
        when (policy) {
            CollectionDeletionPolicy.MOVE_TO_UNSORTED -> dao.deleteCollectionAndMoveShortcuts(collection.id)
            CollectionDeletionPolicy.DELETE_CONTAINED_SHORTCUTS -> {
                val contained = dao.getShortcutsByCollection(collection.id).first().map { it.toDomain() }
                dao.deleteCollectionAndShortcuts(collection.id)
                contained.forEach { deleteIconAssetIfUnused(it.customIconFile()) }
            }
        }
    }

    // Custom icon assets ---------------------------------------------------------------------

    /** Copies a picked image into Able's files, downscaled to [ICON_SOURCE_PX]. Returns the file name. */
    suspend fun importIconImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = ICON_SOURCE_PX.toFloat() / maxOf(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val name = "icon_${UUID.randomUUID()}.png"
            File(iconDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            name
        }.getOrNull()
    }

    fun iconFile(name: String): File? = File(iconDir, name).takeIf { it.isFile && it.parentFile == iconDir }

    /** Removes an asset no saved definition references (e.g. a picked image the user discarded). */
    suspend fun deleteIconAssetIfUnused(name: String?) {
        if (name == null) return
        val stillUsed = shortcuts.first().any { it.customIconFile() == name }
        if (!stillUsed) withContext(Dispatchers.IO) { iconFile(name)?.delete() }
    }

    companion object {
        const val ICON_DIR = "shortcut_icons"
        const val ICON_SOURCE_PX = 384
    }
}

internal fun ShortcutItem.withMeta(meta: ShortcutMeta): ShortcutItem =
    copy(iconConfig = iconConfig.copy(iconType = meta.iconType, iconValue = meta.encode()))

internal fun ShortcutItem.customIconFile(): String? = meta.takeIf { it.source == IconSource.IMAGE }?.value
