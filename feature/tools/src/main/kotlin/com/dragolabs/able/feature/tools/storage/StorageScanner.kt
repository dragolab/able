package com.dragolabs.able.feature.tools.storage

import android.Manifest
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.dragolabs.able.core.model.StorageCategory
import com.dragolabs.able.core.model.StorageReviewItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Where the review reads files from. */
sealed interface StorageSource {
    /** Shared media indexed by Android (MediaStore); requires media read permissions. */
    data object MediaLibrary : StorageSource

    /** A folder the user picked through the Storage Access Framework. */
    data class Folder(val treeUri: String, val name: String) : StorageSource
}

data class CacheCleanResult(val freedBytes: Long, val remainingBytes: Long)

/** MediaStore size sums for files visible to Able. */
data class MediaSums(val images: Long, val video: Long, val audio: Long, val documents: Long, val downloads: Long)

/**
 * Feature-local storage scanner (STO-001, STO-002, ADR-019).
 *
 * Able never deletes files directly from MediaStore: deletion goes through
 * [MediaStore.createDeleteRequest], which shows Android's own confirmation. Files in a folder the
 * user granted through SAF are deleted with [DocumentsContract.deleteDocument] after an in-app
 * confirmation, then the scan is repeated so the UI shows the real outcome.
 */
class StorageScanner(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    fun mediaPermissions(): Array<String> = buildList {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.READ_MEDIA_VIDEO)
        add(Manifest.permission.READ_MEDIA_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }.toTypedArray()

    fun hasAnyMediaPermission(): Boolean = mediaPermissions().any {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    /** True when only "selected photos" access was granted (Android 14+), so results are partial. */
    fun hasPartialMediaAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED

    suspend fun scan(
        source: StorageSource,
        category: StorageCategory,
        minSizeBytes: Long,
        olderThanDays: Int,
    ): List<StorageReviewItem> = withContext(Dispatchers.IO) {
        val cutoffMs = System.currentTimeMillis() - olderThanDays * DAY_MS
        when (source) {
            StorageSource.MediaLibrary -> when (category) {
                StorageCategory.LARGE_FILES -> queryMediaLargeFiles(minSizeBytes)
                StorageCategory.OLD_DOWNLOADS -> queryMediaOldDownloads(cutoffMs)
                StorageCategory.ABLE_CACHE -> emptyList()
            }
            is StorageSource.Folder -> scanTree(Uri.parse(source.treeUri), category, minSizeBytes, cutoffMs)
        }.sortedByDescending { it.sizeBytes }
    }

    private fun queryMediaLargeFiles(minSizeBytes: Long): List<StorageReviewItem> = queryFiles(
        selection = "${MediaStore.MediaColumns.SIZE} >= ?",
        args = arrayOf(minSizeBytes.toString()),
        category = StorageCategory.LARGE_FILES,
    )

    private fun queryMediaOldDownloads(cutoffMs: Long): List<StorageReviewItem> = queryFiles(
        selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DATE_MODIFIED} < ?",
        args = arrayOf("Download/%", (cutoffMs / 1000).toString()),
        category = StorageCategory.OLD_DOWNLOADS,
    )

    private fun queryFiles(selection: String, args: Array<String>, category: StorageCategory): List<StorageReviewItem> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val items = mutableListOf<StorageReviewItem>()
        resolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.SIZE} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (cursor.moveToNext() && items.size < MAX_RESULTS) {
                val id = cursor.getLong(idCol)
                items += StorageReviewItem(
                    uriString = mediaUri(id, cursor.getInt(typeCol)).toString(),
                    displayName = cursor.getString(nameCol) ?: id.toString(),
                    sizeBytes = cursor.getLong(sizeCol),
                    lastModifiedEpochMs = cursor.getLong(dateCol) * 1000,
                    category = category,
                    mimeType = cursor.getString(mimeCol),
                )
            }
        }
        return items
    }

    private fun mediaUri(id: Long, mediaType: Int): Uri {
        val base = when (mediaType) {
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        return ContentUris.withAppendedId(base, id)
    }

    private fun scanTree(treeUri: Uri, category: StorageCategory, minSizeBytes: Long, cutoffMs: Long): List<StorageReviewItem> {
        val items = mutableListOf<StorageReviewItem>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val pending = ArrayDeque<Pair<String, Int>>().apply { add(rootId to 0) }
        var visited = 0
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
        while (pending.isNotEmpty() && visited < MAX_VISITED && items.size < MAX_RESULTS) {
            val (parentId, depth) = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            runCatching {
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        visited++
                        val docId = cursor.getString(0)
                        val mime = cursor.getString(2)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < MAX_DEPTH) pending.add(docId to depth + 1)
                            continue
                        }
                        val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                        val modified = if (cursor.isNull(4)) 0L else cursor.getLong(4)
                        val flags = cursor.getInt(5)
                        val matches = when (category) {
                            StorageCategory.LARGE_FILES -> size >= minSizeBytes
                            StorageCategory.OLD_DOWNLOADS -> modified in 1 until cutoffMs
                            StorageCategory.ABLE_CACHE -> false
                        }
                        // Only list files the provider says can be deleted, so selection is honest.
                        if (matches && flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) {
                            items += StorageReviewItem(
                                uriString = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString(),
                                displayName = cursor.getString(1) ?: docId,
                                sizeBytes = size,
                                lastModifiedEpochMs = modified,
                                category = category,
                                mimeType = mime,
                            )
                        }
                    }
                }
            }
        }
        return items
    }

    /** Builds Android's own delete confirmation for MediaStore items. */
    fun createMediaDeleteRequest(uris: List<String>): PendingIntent =
        MediaStore.createDeleteRequest(resolver, uris.map(Uri::parse))

    /** Deletes SAF documents the user confirmed. Returns the URIs Android reported as deleted. */
    suspend fun deleteDocuments(uris: List<String>): Set<String> = withContext(Dispatchers.IO) {
        uris.filter { uri ->
            runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(uri)) }.getOrDefault(false)
        }.toSet()
    }

    /** Re-queries each URI so the reported outcome reflects what actually still exists. */
    suspend fun stillExisting(uris: Collection<String>): Set<String> = withContext(Dispatchers.IO) {
        uris.filter { uri ->
            runCatching {
                resolver.query(Uri.parse(uri), arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { it.moveToFirst() } ?: false
            }.getOrDefault(false)
        }.toSet()
    }

    fun folderName(treeUri: Uri): String = runCatching {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        resolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: treeUri.lastPathSegment.orEmpty()

    /**
     * Sums the sizes of files visible to Able in MediaStore, by type (STO-003). Categories are
     * disjoint: images/video/audio by media type, then documents, then anything else under
     * Download/. Files Android does not show to Able are not counted (they stay in "other").
     */
    suspend fun mediaSums(): MediaSums = withContext(Dispatchers.IO) {
        var images = 0L
        var video = 0L
        var audio = 0L
        var documents = 0L
        var downloads = 0L
        val projection = arrayOf(
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        resolver.query(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val size = if (cursor.isNull(0)) 0L else cursor.getLong(0).coerceAtLeast(0L)
                when (cursor.getInt(1)) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> images += size
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> video += size
                    MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> audio += size
                    MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT -> documents += size
                    else -> if (cursor.getString(2)?.startsWith("Download/") == true) downloads += size
                }
            }
        }
        MediaSums(images, video, audio, documents, downloads)
    }

    // --- Able's own cache (STO-002) ---

    fun ableCacheBytes(): Long = cacheDirs().sumOf { it.sizeRecursive() }

    suspend fun cleanAbleCache(): CacheCleanResult = withContext(Dispatchers.IO) {
        val before = ableCacheBytes()
        cacheDirs().forEach { dir -> dir.listFiles()?.forEach { runCatching { it.deleteRecursively() } } }
        val after = ableCacheBytes()
        CacheCleanResult(freedBytes = (before - after).coerceAtLeast(0), remainingBytes = after)
    }

    private fun cacheDirs(): List<File> = listOfNotNull(context.cacheDir, context.externalCacheDir)

    private fun File.sizeRecursive(): Long = runCatching { walkBottomUp().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_RESULTS = 500
        const val MAX_VISITED = 20_000
        const val MAX_DEPTH = 8
    }
}
