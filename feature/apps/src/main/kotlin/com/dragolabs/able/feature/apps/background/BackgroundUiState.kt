package com.dragolabs.able.feature.apps.background

import androidx.compose.runtime.Immutable
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.feature.apps.AppTypeFilter
import com.dragolabs.able.feature.apps.BlockReason
import com.dragolabs.able.feature.apps.data.PackageActionOutcome
import com.dragolabs.able.feature.apps.data.RemovalLevel

/** Top-level views of the Apps list. */
enum class AppsView { ALL, BACKGROUND }

@Immutable
data class BackgroundFilters(
    val query: String = "",
    val type: AppTypeFilter = AppTypeFilter.ALL,
    val restrictedOnly: Boolean = false,
    /** Only apps whose background mobile data is blocked. */
    val backgroundDataBlockedOnly: Boolean = false,
    /** Hides reviewed apps that still match their review (changed ones stay visible). */
    val hideReviewed: Boolean = false,
) {
    val isDefault: Boolean get() = type == AppTypeFilter.ALL && !restrictedOnly && !backgroundDataBlockedOnly && !hideReviewed
}

@Immutable
data class BackgroundRow(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val status: BackgroundStatus,
    val bucket: StandbyBucket?,
    val batteryOptimizationExempt: Boolean?,
    val isProtected: Boolean,
    val removal: RemovalLevel?,
    val blockReason: BlockReason?,
    /** Expert/Unsafe (or unlisted system) package: changing it needs an explicit acknowledgement. */
    val risky: Boolean,
    val mode: BackgroundMode? = null,
    /** On the system battery-optimization allowlist: cannot be made Optimised or Restricted. */
    val systemAllowlisted: Boolean = false,
    val backgroundDataBlocked: Boolean? = null,
    val review: ReviewState = ReviewState.NONE,
)

sealed interface OverviewLoad {
    /** Shizuku is not ready: the state of other apps cannot be read. */
    data object Unavailable : OverviewLoad
    data object Loading : OverviewLoad
    data object Failed : OverviewLoad
    data class Loaded(val overview: BackgroundOverview) : OverviewLoad
}

/** What a bulk run does. */
enum class BulkKind { RESTRICT, ALLOW, RESTORE_REVIEWED }

sealed interface BulkUi {
    data object Idle : BulkUi
    data class Running(val kind: BulkKind, val done: Int, val total: Int) : BulkUi
    data class Done(
        val kind: BulkKind,
        val applied: Int,
        val rejected: Int,
        val notConfirmed: Int,
        /** Packages Able never changes (itself, Shizuku, core Android) or no longer installed. */
        val skipped: Int,
    ) : BulkUi
}

data class BackgroundListUiState(
    val load: OverviewLoad = OverviewLoad.Unavailable,
    val filters: BackgroundFilters = BackgroundFilters(),
    val rows: List<BackgroundRow> = emptyList(),
    val totalRows: Int = 0,
    val restrictedCount: Int = 0,
    /** Reviewed apps whose live settings differ from the review. */
    val changedCount: Int = 0,
    val selectionMode: Boolean = false,
    val selected: Set<String> = emptySet(),
    val bulk: BulkUi = BulkUi.Idle,
    val shizuku: ShizukuConnectionState = ShizukuConnectionState.NOT_AVAILABLE,
)

/** Counts shown in the bulk confirmation. */
data class BulkSummary(val total: Int, val changeable: Int, val skipped: Int, val protectedCount: Int, val riskyCount: Int)

sealed interface DetailReading {
    data object Loading : DetailReading
    /** Read through Shizuku. */
    data class Loaded(val snapshot: BackgroundSnapshot) : DetailReading
    /** Without Shizuku only the battery-optimization exemption is readable (public API). */
    data class Limited(val batteryOptimizationExempt: Boolean?) : DetailReading
    data object Failed : DetailReading
}

sealed interface BackgroundOperationUi {
    data object Idle : BackgroundOperationUi
    data class Running(val change: BackgroundChange) : BackgroundOperationUi
    data class Done(val change: BackgroundChange, val outcome: PackageActionOutcome, val detail: String?) : BackgroundOperationUi
}

data class BackgroundDetailUi(
    val packageName: String,
    val label: String,
    val installed: Boolean,
    val isSystem: Boolean,
    val reading: DetailReading,
    val operation: BackgroundOperationUi,
    val blockReason: BlockReason?,
    val isProtected: Boolean,
    val removal: RemovalLevel?,
    val risky: Boolean,
    val review: ReviewedSettings? = null,
    /** Fields that differ from [review] in the current reading. */
    val reviewDiff: Set<ReviewField> = emptySet(),
    /** Last review save/forget failed (file write error). */
    val reviewSaveFailed: Boolean = false,
) {
    val reviewState: ReviewState
        get() = when {
            review == null -> ReviewState.NONE
            reviewDiff.isNotEmpty() -> ReviewState.CHANGED
            else -> ReviewState.REVIEWED
        }
}
