package com.dragolabs.able.feature.tools.storage

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.dragolabs.able.feature.tools.R
import java.util.Locale

/** File type filter of the review list (STO-001). */
enum class FileKind(@StringRes val labelRes: Int) {
    IMAGE(R.string.storage_filekind_images),
    VIDEO(R.string.storage_filekind_videos),
    AUDIO(R.string.storage_filekind_audio),
    DOCUMENT(R.string.storage_filekind_documents),
    ARCHIVE(R.string.storage_filekind_archives),
    OTHER(R.string.storage_filekind_other),
}

/** Minimum size chips (STO-001). [CUSTOM] uses [FileFilters.customSizeMb]. */
enum class SizeThreshold(val bytes: Long) {
    ANY(0),
    MB_10(10 * MB),
    MB_50(50 * MB),
    MB_100(100 * MB),
    MB_500(500 * MB),
    GB_1(1024 * MB),
    CUSTOM(-1),
}

/** "Modified before" chips (STO-001). */
enum class AgeThreshold(val days: Int?) { ANY(null), D7(7), D30(30), D90(90), D365(365) }

/**
 * Shared-storage location (MediaStore `RELATIVE_PATH` prefixes). Only meaningful for the media
 * library source; a picked folder is already a location.
 */
enum class FileLocation(@StringRes val labelRes: Int, val pathPrefixes: List<String>) {
    ALL(R.string.storage_location_all, emptyList()),
    DOWNLOADS(R.string.storage_location_downloads, listOf("Download/")),
    PICTURES(R.string.storage_location_pictures, listOf("DCIM/", "Pictures/")),
    MOVIES(R.string.storage_location_movies, listOf("Movies/")),
    MUSIC(R.string.storage_location_music, listOf("Music/")),
    DOCUMENTS(R.string.storage_location_documents, listOf("Documents/")),
}

enum class FileSort(@StringRes val labelRes: Int) {
    SIZE(R.string.storage_sort_size),
    DATE(R.string.storage_sort_date),
    NAME(R.string.storage_sort_name),
}

/**
 * Review filters. Size, age and location are applied by the scan query; type, name search and
 * sort are applied in memory to the scan result, so they respond instantly.
 */
@Immutable
data class FileFilters(
    /** Empty means every type. */
    val kinds: Set<FileKind> = emptySet(),
    val size: SizeThreshold = SizeThreshold.MB_100,
    val customSizeMb: Long = DEFAULT_CUSTOM_MB,
    val age: AgeThreshold = AgeThreshold.ANY,
    val location: FileLocation = FileLocation.ALL,
    val sort: FileSort = FileSort.SIZE,
    val query: String = "",
) {
    val minSizeBytes: Long get() = if (size == SizeThreshold.CUSTOM) customSizeMb.coerceAtLeast(0) * MB else size.bytes

    /** True when the query-side filters (size, age, location) equal [other]'s. */
    fun sameScan(other: FileFilters): Boolean =
        minSizeBytes == other.minSizeBytes && age == other.age && location == other.location

    companion object {
        const val DEFAULT_CUSTOM_MB = 250L
        const val MAX_CUSTOM_MB = 1024L * 1024L
    }
}

/** One file found by the review scan. */
@Immutable
data class ReviewFile(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long,
    val mimeType: String?,
    val kind: FileKind,
    /** MediaStore relative path such as `Download/`, or null for folder (SAF) results. */
    val relativePath: String? = null,
) {
    val hasThumbnail: Boolean get() = kind == FileKind.IMAGE || kind == FileKind.VIDEO
}

/** Scan output; [truncated] is true when the result limit was reached. */
data class ScanResult(val items: List<ReviewFile>, val truncated: Boolean)

internal const val MB = 1024L * 1024L

private val DOCUMENT_EXTENSIONS = setOf(
    "pdf", "doc", "docx", "odt", "rtf", "txt", "md", "csv", "xls", "xlsx", "ods", "ppt", "pptx", "odp", "epub", "mobi", "json", "xml", "html", "htm",
)
private val ARCHIVE_EXTENSIONS = setOf(
    "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "iso", "apk", "apks", "xapk", "apkm", "obb", "img",
)
private val ARCHIVE_MIME_HINTS = listOf("zip", "rar", "7z", "tar", "gzip", "x-bzip", "x-xz", "compressed", "android.package-archive", "iso9660")

/** Classifies a file by MIME type first, then by extension. Pure so it can be unit-tested. */
internal fun classifyFile(mimeType: String?, displayName: String): FileKind {
    val mime = mimeType?.lowercase(Locale.ROOT).orEmpty()
    val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        mime.startsWith("image/") -> FileKind.IMAGE
        mime.startsWith("video/") -> FileKind.VIDEO
        mime.startsWith("audio/") -> FileKind.AUDIO
        ARCHIVE_MIME_HINTS.any { it in mime } || ext in ARCHIVE_EXTENSIONS -> FileKind.ARCHIVE
        mime.startsWith("text/") || mime == "application/pdf" || "document" in mime || "msword" in mime ||
            "spreadsheet" in mime || "presentation" in mime || "epub" in mime || ext in DOCUMENT_EXTENSIONS -> FileKind.DOCUMENT
        else -> FileKind.OTHER
    }
}

/** Applies the in-memory filters (type, name) and the sort to a scan result. */
internal fun applyFileFilters(items: List<ReviewFile>, filters: FileFilters): List<ReviewFile> {
    val q = filters.query.trim()
    val filtered = items.filter { item ->
        (filters.kinds.isEmpty() || item.kind in filters.kinds) &&
            (q.isEmpty() || item.displayName.contains(q, ignoreCase = true))
    }
    return when (filters.sort) {
        FileSort.SIZE -> filtered.sortedWith(compareByDescending<ReviewFile> { it.sizeBytes }.thenBy { it.displayName.lowercase(Locale.ROOT) })
        FileSort.DATE -> filtered.sortedWith(compareBy<ReviewFile> { it.lastModifiedEpochMs.takeIf { ms -> ms > 0 } ?: Long.MAX_VALUE }.thenByDescending { it.sizeBytes })
        FileSort.NAME -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
    }
}
