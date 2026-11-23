package com.dragolabs.able.feature.apps.background

import kotlinx.serialization.Serializable

/**
 * Background settings the user marked as reviewed (APP-007 "Reviewed apps"). Stored on the device
 * only (see [BackgroundReviewStore]); the standby bucket is not part of it because Android moves
 * apps between buckets on its own.
 */
@Serializable
data class ReviewedSettings(
    val runAnyInBackground: AppOpMode,
    val runInBackground: AppOpMode,
    /** In the user battery-optimization allowlist ("Unrestricted"). System entries are not stored. */
    val userAllowlisted: Boolean,
    /** Null when the background-data state could not be read at review time; then it is not compared. */
    val backgroundDataBlocked: Boolean? = null,
    val reviewedAtMs: Long,
)

/** A reviewed field that can differ from the live state. */
enum class ReviewField { RUN_ANY_IN_BACKGROUND, RUN_IN_BACKGROUND, ALLOWLIST, BACKGROUND_DATA }

/** Whether a package was reviewed and still matches its review. */
enum class ReviewState { NONE, REVIEWED, CHANGED }

/** Review of [snapshot] at [nowMs]; null when the ops or the allowlist are unknown. */
fun reviewOf(snapshot: BackgroundSnapshot, nowMs: Long): ReviewedSettings? {
    val any = snapshot.runAnyInBackground ?: return null
    val run = snapshot.runInBackground ?: return null
    val allowlisted = snapshot.userAllowlisted ?: return null
    return ReviewedSettings(any, run, allowlisted, snapshot.backgroundDataBlocked, nowMs)
}

/**
 * Fields whose live value differs from [review]. Ops are compared by effect (`allow` and `default`
 * both permit background work) so an equivalent mode is not reported as a change. Unknown live
 * values are never reported as changed.
 */
fun reviewDiff(review: ReviewedSettings, live: BackgroundSnapshot): Set<ReviewField> {
    val diff = LinkedHashSet<ReviewField>()
    live.runAnyInBackground?.let { if (it.restricts != review.runAnyInBackground.restricts) diff += ReviewField.RUN_ANY_IN_BACKGROUND }
    live.runInBackground?.let { if (it.restricts != review.runInBackground.restricts) diff += ReviewField.RUN_IN_BACKGROUND }
    live.userAllowlisted?.let { if (it != review.userAllowlisted) diff += ReviewField.ALLOWLIST }
    val reviewedData = review.backgroundDataBlocked
    val liveData = live.backgroundDataBlocked
    if (reviewedData != null && liveData != null && reviewedData != liveData) diff += ReviewField.BACKGROUND_DATA
    return diff
}

/** True when every reviewed field is known in [live] and matches. Used to verify a restore. */
fun isReviewMatched(review: ReviewedSettings, live: BackgroundSnapshot): Boolean {
    if (live.runAnyInBackground == null || live.runInBackground == null || live.userAllowlisted == null) return false
    if (review.backgroundDataBlocked != null && live.backgroundDataBlocked == null) return false
    return reviewDiff(review, live).isEmpty()
}

fun reviewStateOf(review: ReviewedSettings?, live: BackgroundSnapshot?): ReviewState = when {
    review == null -> ReviewState.NONE
    live != null && reviewDiff(review, live).isNotEmpty() -> ReviewState.CHANGED
    else -> ReviewState.REVIEWED
}
