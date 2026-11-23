package com.dragolabs.able.feature.apps

import androidx.compose.runtime.Immutable
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.core.model.ProfileType
import com.dragolabs.able.feature.apps.data.AppArchiveRecord
import com.dragolabs.able.feature.apps.data.AppInstallState
import com.dragolabs.able.feature.apps.data.AppUsageSummary
import com.dragolabs.able.feature.apps.data.BackupProblem
import com.dragolabs.able.feature.apps.data.InstalledApp
import com.dragolabs.able.feature.apps.data.PackageAction
import com.dragolabs.able.feature.apps.data.PackageActionOutcome
import com.dragolabs.able.feature.apps.data.PackageKnowledge
import com.dragolabs.able.feature.apps.data.RemovalLevel
import com.dragolabs.able.feature.apps.data.UadList

enum class AppTypeFilter { ALL, USER, SYSTEM }

/** State filter. [ABLE_ARCHIVE] lists packages Able disabled or uninstalled. */
enum class AppStateFilter { ALL, INSTALLED, DISABLED, UNINSTALLED, ARCHIVED_BY_SYSTEM, ABLE_ARCHIVE }

/** Removal-level filter; [NOT_LISTED] matches packages without UAD information. */
enum class RemovalFilter { RECOMMENDED, ADVANCED, EXPERT, UNSAFE, NOT_LISTED }

@Immutable
data class AppFilters(
    val query: String = "",
    val state: AppStateFilter = AppStateFilter.ALL,
    val type: AppTypeFilter = AppTypeFilter.ALL,
    val protectedOnly: Boolean = false,
    val lists: Set<UadList> = emptySet(),
    val removals: Set<RemovalFilter> = emptySet(),
) {
    /** Filters inside the "More filters" sheet. */
    val sheetFilterCount: Int get() = lists.size + removals.size

    val isDefault: Boolean
        get() = state == AppStateFilter.ALL && type == AppTypeFilter.ALL && !protectedOnly &&
            lists.isEmpty() && removals.isEmpty()
}

/** One list row. Precomputed so composition does no lookups. */
@Immutable
data class AppListItem(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val state: AppInstallState,
    val removal: RemovalLevel?,
    val list: UadList?,
    val isProtected: Boolean,
    val inAbleArchive: Boolean,
    /** Lower-cased description used only for search. */
    val searchDescription: String,
)

sealed interface CatalogState {
    data object Loading : CatalogState
    data object Failed : CatalogState
    data class Loaded(val apps: List<InstalledApp>) : CatalogState
}

/** Usage evidence for APP-002. Drain is never inferred; only Android-reported usage is shown. */
sealed interface UsageEvidence {
    data object AccessMissing : UsageEvidence
    data object Loading : UsageEvidence
    data object NotReported : UsageEvidence
    data class Reported(val summary: AppUsageSummary) : UsageEvidence
}

enum class ForceStopOutcome {
    /** Command accepted and no process found afterwards. */
    STOPPED,
    /** Command accepted but a process is still (or again) running. */
    STILL_RUNNING,
    /** Command accepted but the result could not be read back. */
    NOT_CONFIRMED,
    /** Command rejected or Shizuku failed. */
    FAILED,
}

sealed interface ForceStopUi {
    data object Idle : ForceStopUi
    data object Running : ForceStopUi
    data class Done(val outcome: ForceStopOutcome, val detail: String? = null) : ForceStopUi
}

/** User-facing package operations (APP-004). [RESTORE] reinstalls and/or re-enables as needed. */
enum class PackageOperation { DISABLE, ENABLE, UNINSTALL, RESTORE }

sealed interface PackageOperationUi {
    data object Idle : PackageOperationUi
    data class Running(val operation: PackageOperation) : PackageOperationUi
    data class Done(
        val operation: PackageOperation,
        val outcome: PackageActionOutcome,
        val detail: String? = null,
        /** Restore impossible on the device: Android deleted the APK and Able has no backup. */
        val storeRequired: Boolean = false,
        /** Uninstall was not started because the APK backup failed. */
        val backupFailed: BackupProblem? = null,
    ) : PackageOperationUi
}

/** How Able can restore a package that is not installed for the current user. */
sealed interface RestoreRoute {
    /** The APK is still on the device (system apps, other users): `install-existing`. */
    data object OnDevice : RestoreRoute
    /** Android deleted the APK; Able reinstalls it from its own backup. */
    data class FromBackup(val sizeBytes: Long, val versionName: String?) : RestoreRoute
    /** Android deleted the APK and there is no backup: only the store can reinstall it. */
    data class FromStore(val installer: String?) : RestoreRoute
}

/** APK backup preview shown in the uninstall confirmation. */
sealed interface UninstallPlanUi {
    data object Idle : UninstallPlanUi
    data object Loading : UninstallPlanUi
    /** System app: the APK stays on the device, no backup needed. */
    data object NotNeeded : UninstallPlanUi
    data class Ready(val sizeBytes: Long, val alreadyBackedUp: Boolean, val problem: BackupProblem?) : UninstallPlanUi
}

/** Why Able refuses to change a package regardless of Shizuku. */
enum class BlockReason { SELF, SHIZUKU, CORE_SYSTEM }

data class AppDetailState(
    val packageName: String,
    /** Null when the package is no longer reported by PackageManager. */
    val app: InstalledApp?,
    val label: String,
    val state: AppInstallState,
    val knowledge: PackageKnowledge?,
    val knowledgeLoaded: Boolean,
    val archiveRecord: AppArchiveRecord?,
    val blockReason: BlockReason?,
    val isProtected: Boolean,
    /** Every battery profile with this app's level in it (owner decision 2026-11-22: user profiles). */
    val profileRules: List<AppProfileRule>,
    /** Action that would apply now from the active profiles (most restrictive wins); null when none. */
    val effectiveAction: AppEffectiveAction? = null,
    val usage: UsageEvidence,
    val forceStop: ForceStopUi,
    val operation: PackageOperationUi,
    /** Null while unknown or when the package is installed for the user. */
    val restoreRoute: RestoreRoute? = null,
    /** Size of Able's APK backup for this package, if one exists. */
    val backupBytes: Long? = null,
    val uninstallPlan: UninstallPlanUi = UninstallPlanUi.Idle,
) {
    fun levelFor(profileId: String): AppInterventionLevel =
        profileRules.firstOrNull { it.profileId == profileId }?.level ?: AppInterventionLevel.OSSERVA
}

/** One battery profile as seen from an app's detail pane. [name] is blank for an unrenamed built-in. */
data class AppProfileRule(
    val profileId: String,
    val name: String,
    val builtInType: ProfileType?,
    val level: AppInterventionLevel,
    val active: Boolean,
    val enabled: Boolean,
)

/** Effective action from the active profiles and the profiles that set it. */
data class AppEffectiveAction(val level: AppInterventionLevel, val sourceProfileIds: List<String>)

data class AppsUiState(
    val catalog: CatalogState = CatalogState.Loading,
    val filters: AppFilters = AppFilters(),
    val items: List<AppListItem> = emptyList(),
    val totalCount: Int = 0,
    /** All rows before filtering (packages plus archive-only records). */
    val totalRows: Int = 0,
    val archiveCount: Int = 0,
    val systemArchivedCount: Int = 0,
    val shizuku: ShizukuConnectionState = ShizukuConnectionState.NOT_AVAILABLE,
    val usageAccess: Boolean = false,
    val detail: AppDetailState? = null,
)

internal fun PackageOperation.toAction(): PackageAction = when (this) {
    PackageOperation.DISABLE -> PackageAction.DISABLE
    PackageOperation.ENABLE -> PackageAction.ENABLE
    PackageOperation.UNINSTALL -> PackageAction.UNINSTALL_FOR_USER
    PackageOperation.RESTORE -> PackageAction.REINSTALL_FOR_USER
}
