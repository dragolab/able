package com.dragolabs.able.feature.home

import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.data.AccessSnapshot
import com.dragolabs.able.core.model.ActionAuditRecord
import com.dragolabs.able.core.model.BatteryStatus
import com.dragolabs.able.core.model.HomeCardConfig
import com.dragolabs.able.core.model.ProfileType
import com.dragolabs.able.core.model.StorageSummary

/** Storage card content. [Unavailable] means Android did not report storage figures. */
sealed interface StorageCardState {
    data object Loading : StorageCardState
    data object Unavailable : StorageCardState
    data class Available(val summary: StorageSummary) : StorageCardState
}

/**
 * Safety banners (HOME-003, ADR-016). They are computed independently from card order and
 * visibility so reordering or hiding cards can never hide them; they stay at the top of Home.
 */
sealed interface HomeSafetyAlert {
    data object ShizukuWaitingForPermission : HomeSafetyAlert
    data object ShizukuPermissionDenied : HomeSafetyAlert
}

/**
 * Failed-actions notice (owner request 2026-11-23): shown at the bottom of the Home feed,
 * independent of card visibility. The user can hide it; it reappears when a newer failure occurs.
 */
data class FailedActionsNotice(val failures: List<ActionAuditRecord>) {
    /** Timestamp of the newest failure; hiding the notice records it as "dismissed through". */
    val latestEpochMs: Long get() = failures.maxOf { it.timestampEpochMs }
}

data class HomeUiState(
    val cards: List<HomeCardConfig> = HomeCardConfig.defaultCards(),
    val battery: BatteryStatus? = null,
    val hasBattery: Boolean = true,
    /** Battery profiles active now; empty when none is active. */
    val activeProfiles: List<HomeActiveProfile> = emptyList(),
    val access: AccessSnapshot = AccessSnapshot(),
    /** Latest actions, newest first (up to [RECENT_HISTORY_COUNT]); the card shows the first few. */
    val recentActions: List<ActionAuditRecord> = emptyList(),
    val storage: StorageCardState = StorageCardState.Loading,
    val alerts: List<HomeSafetyAlert> = emptyList(),
    /** Failed actions in the last 24 hours not yet hidden by the user; null when none. */
    val failedActions: FailedActionsNotice? = null,
    val hasAccelerometer: Boolean = true,
    val hasMagnetometer: Boolean = true,
    /** Favorite shortcuts from Shortcut Maker (see [favoriteShortcuts]). */
    val shortcuts: List<HomeShortcut> = emptyList(),
    /** One-off message (cleared via [HomeViewModel.consumeMessage]). */
    val message: HomeMessage? = null,
) {
    val visibleCards: List<HomeCardConfig> get() = cards.filter { it.isVisible }
}

enum class HomeMessage {
    LAYOUT_RESET,
    SHIZUKU_REQUEST_FAILED,
    SHORTCUT_FAILED,
    SHORTCUT_UNSUPPORTED,
    SHORTCUT_APP_MISSING,
    SHORTCUT_BLOCKED,
}

/** Number of recent actions shown on the Home card; the rest are in the "Show all" sheet. */
internal const val RECENT_CARD_COUNT = 5

/** Number of actions in the full "Recent actions" sheet. */
internal const val RECENT_HISTORY_COUNT = 50

internal fun alertsFor(shizuku: ShizukuConnectionState): List<HomeSafetyAlert> =
    buildList {
        when (shizuku) {
            ShizukuConnectionState.WAITING_FOR_PERMISSION -> add(HomeSafetyAlert.ShizukuWaitingForPermission)
            ShizukuConnectionState.PERMISSION_DENIED -> add(HomeSafetyAlert.ShizukuPermissionDenied)
            // Not installed / not running is informational only (capability card), never a nag.
            ShizukuConnectionState.READY, ShizukuConnectionState.NOT_AVAILABLE -> Unit
        }
    }

/**
 * Failures within [windowMs] of [nowEpochMs] that are newer than [dismissedThroughEpochMs]
 * (the newest failure the user had seen when hiding the notice). Once a newer failure occurs,
 * the notice shows every recent failure again, so the context is not lost.
 */
internal fun failedActionsNoticeFor(
    failures: List<ActionAuditRecord>,
    nowEpochMs: Long,
    dismissedThroughEpochMs: Long,
    windowMs: Long = RECENT_FAILURE_WINDOW_MS,
): FailedActionsNotice? {
    val recent = failures.filter { nowEpochMs - it.timestampEpochMs in 0..windowMs }
    if (recent.isEmpty()) return null
    if (recent.none { it.timestampEpochMs > dismissedThroughEpochMs }) return null
    return FailedActionsNotice(recent)
}

/** An active battery profile as shown on Home: built-ins use the localized name. */
data class HomeActiveProfile(val builtInType: ProfileType?, val name: String)
