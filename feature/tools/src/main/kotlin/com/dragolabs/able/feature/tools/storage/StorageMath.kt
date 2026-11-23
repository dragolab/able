package com.dragolabs.able.feature.tools.storage

/** Categories of the internal storage breakdown (STO-003). Order is the display order. */
enum class StorageKind { APPS, IMAGES, VIDEO, AUDIO, DOCUMENTS, DOWNLOADS, OTHER }

/** Why a category could not be measured. */
enum class MissingAccess { USAGE_ACCESS, MEDIA_ACCESS }

/**
 * One segment of the breakdown. [bytes] is null when Able could not measure the category; its
 * space is then part of [StorageKind.OTHER] and [missing] says which access would measure it.
 */
data class StorageSegment(
    val kind: StorageKind,
    val bytes: Long?,
    val percent: Int,
    val missing: MissingAccess? = null,
)

data class StorageBreakdown(
    val totalBytes: Long,
    val freeBytes: Long,
    val segments: List<StorageSegment>,
) {
    val usedBytes: Long get() = usedBytes(totalBytes, freeBytes)
    val usedPercent: Int get() = percentOf(usedBytes, totalBytes)
    val usedFraction: Float get() = fractionOf(usedBytes, totalBytes)
    val hasUnmeasured: Boolean get() = segments.any { it.bytes == null }
}

/** Measured category sizes; a null value means "not measured" (missing access). */
data class MeasuredCategories(
    val apps: Long? = null,
    val images: Long? = null,
    val video: Long? = null,
    val audio: Long? = null,
    val documents: Long? = null,
    val downloads: Long? = null,
    val appsMissing: MissingAccess = MissingAccess.USAGE_ACCESS,
    val mediaMissing: MissingAccess = MissingAccess.MEDIA_ACCESS,
)

fun usedBytes(totalBytes: Long, freeBytes: Long): Long =
    (totalBytes - freeBytes).coerceIn(0L, totalBytes.coerceAtLeast(0L))

/** Rounded percentage of [part] in [total], clamped to 0..100. Returns 0 when [total] is not positive. */
fun percentOf(part: Long, total: Long): Int {
    if (total <= 0L || part <= 0L) return 0
    val p = Math.round(part.toDouble() * 100.0 / total.toDouble())
    return p.toInt().coerceIn(0, 100)
}

fun fractionOf(part: Long, total: Long): Float {
    if (total <= 0L || part <= 0L) return 0f
    return (part.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
}

/**
 * Builds a breakdown of used space. Measured categories keep their measured value; whatever is
 * left of the used space is "System and other" (never negative, so overlapping measurements
 * cannot produce an impossible total).
 */
fun buildBreakdown(totalBytes: Long, freeBytes: Long, measured: MeasuredCategories): StorageBreakdown {
    val used = usedBytes(totalBytes, freeBytes)
    val known = listOf(
        Triple(StorageKind.APPS, measured.apps, measured.appsMissing),
        Triple(StorageKind.IMAGES, measured.images, measured.mediaMissing),
        Triple(StorageKind.VIDEO, measured.video, measured.mediaMissing),
        Triple(StorageKind.AUDIO, measured.audio, measured.mediaMissing),
        Triple(StorageKind.DOCUMENTS, measured.documents, MissingAccess.MEDIA_ACCESS),
        Triple(StorageKind.DOWNLOADS, measured.downloads, MissingAccess.MEDIA_ACCESS),
    )
    val measuredSum = known.sumOf { (it.second ?: 0L).coerceAtLeast(0L) }
    val other = (used - measuredSum).coerceAtLeast(0L)
    val segments = known.map { (kind, bytes, missing) ->
        val value = bytes?.coerceAtLeast(0L)
        StorageSegment(kind, value, if (value == null) 0 else percentOf(value, totalBytes), if (value == null) missing else null)
    } + StorageSegment(StorageKind.OTHER, other, percentOf(other, totalBytes))
    return StorageBreakdown(totalBytes, freeBytes, segments)
}

/** Freed space measured as an increase of free bytes; a decrease or no change is 0 (honest result). */
fun measuredFreedBytes(freeBefore: Long, freeAfter: Long): Long = (freeAfter - freeBefore).coerceAtLeast(0L)

/** Sum of cache decreases across packages measured before and after; missing "after" values are ignored. */
fun measuredCacheDecrease(before: Map<String, Long>, after: Map<String, Long>): Long =
    before.entries.sumOf { (pkg, b) -> after[pkg]?.let { a -> (b - a).coerceAtLeast(0L) } ?: 0L }

/** Strict Android package-name check used before a package name reaches a shell command. */
private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

fun isValidPackageName(name: String): Boolean = name.length <= 255 && PACKAGE_NAME.matches(name)
