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
 * visibility so reordering or hiding cards can never hide them.
 */
sealed interface HomeSafetyAlert {
    data object ShizukuWaitingForPermission : HomeSafetyAlert
    data object ShizukuPermissionDenied : HomeSafetyAlert
    data class RecentFailures(val failures: List<ActionAuditRecord>) : HomeSafetyAlert
}

data class HomeUiState(
    val cards: List<HomeCardConfig> = HomeCardConfig.defaultCards(),
    val battery: BatteryStatus? = null,
    val hasBattery: Boolean = true,
    /** Battery profiles active now; empty when none is active. */
    val activeProfiles: List<HomeActiveProfile> = emptyList(),
    val access: AccessSnapshot = AccessSnapshot(),
    val recentActions: List<ActionAuditRecord> = emptyList(),
    val storage: StorageCardState = StorageCardState.Loading,
    val alerts: List<HomeSafetyAlert> = emptyList(),
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

internal fun alertsFor(shizuku: ShizukuConnectionState, failures: List<ActionAuditRecord>): List<HomeSafetyAlert> =
    buildList {
        when (shizuku) {
            ShizukuConnectionState.WAITING_FOR_PERMISSION -> add(HomeSafetyAlert.ShizukuWaitingForPermission)
            ShizukuConnectionState.PERMISSION_DENIED -> add(HomeSafetyAlert.ShizukuPermissionDenied)
            // Not installed / not running is informational only (capability card), never a nag.
            ShizukuConnectionState.READY, ShizukuConnectionState.NOT_AVAILABLE -> Unit
        }
        if (failures.isNotEmpty()) add(HomeSafetyAlert.RecentFailures(failures))
    }

/** An active battery profile as shown on Home: built-ins use the localized name. */
data class HomeActiveProfile(val builtInType: ProfileType?, val name: String)
