package com.dragolabs.able.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class StorageCategory {
    LARGE_FILES,
    OLD_DOWNLOADS,
    ABLE_CACHE
}

/**
 * Item identified during storage review (STO-001, STO-002, ADR-019).
 */
@Serializable
data class StorageReviewItem(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long,
    val category: StorageCategory,
    val mimeType: String? = null
)
