package com.dragolabs.able.feature.apps

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.core.data.AbleContainer
import com.dragolabs.able.core.data.ActionLogRepository
import com.dragolabs.able.core.data.CapabilityStatusRepository
import com.dragolabs.able.core.data.battery.BatteryProfileRepository
import com.dragolabs.able.core.data.battery.ProfileEngine
import com.dragolabs.able.core.database.UserPreferencesRepository
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.feature.apps.data.ApkBackupStore
import com.dragolabs.able.feature.apps.data.AppArchiveRecord
import com.dragolabs.able.feature.apps.data.AppArchiveRepository
import com.dragolabs.able.feature.apps.data.AppInstallState
import com.dragolabs.able.feature.apps.data.ArchivedAction
import com.dragolabs.able.feature.apps.data.BackupProblem
import com.dragolabs.able.feature.apps.data.BackupResult
import com.dragolabs.able.feature.apps.data.InstalledApp
import com.dragolabs.able.feature.apps.data.InstalledAppCatalog
import com.dragolabs.able.feature.apps.data.PackageAction
import com.dragolabs.able.feature.apps.data.PackageActionOutcome
import com.dragolabs.able.feature.apps.data.PackageActionResult
import com.dragolabs.able.feature.apps.data.PackageActionRunner
import com.dragolabs.able.feature.apps.data.PackageCommands
import com.dragolabs.able.feature.apps.data.PackageKnowledge
import com.dragolabs.able.feature.apps.data.PackageKnowledgeRepository
import com.dragolabs.able.feature.apps.data.RemovalLevel
import com.dragolabs.able.feature.apps.data.UadList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppsViewModel(
    private val appContext: Context,
    private val catalog: InstalledAppCatalog,
    private val knowledgeRepository: PackageKnowledgeRepository,
    private val archive: AppArchiveRepository,
    private val backups: ApkBackupStore,
    private val runner: PackageActionRunner,
    private val preferences: UserPreferencesRepository,
    private val batteryProfiles: BatteryProfileRepository,
    private val capabilityStatus: CapabilityStatusRepository,
    private val shizuku: ShizukuAdapter,
    private val actionLog: ActionLogRepository,
    /** Outlives the screen so a started change is always verified and logged. */
    private val applicationScope: CoroutineScope,
) : ViewModel() {

    private constructor(container: AbleContainer, catalog: InstalledAppCatalog, backups: ApkBackupStore) : this(
        appContext = container.appContext,
        catalog = catalog,
        knowledgeRepository = PackageKnowledgeRepository(container.appContext),
        archive = AppArchiveRepository(container.database.appArchiveDao(), backups),
        backups = backups,
        runner = PackageActionRunner(catalog, container.shizuku),
        preferences = container.preferences,
        batteryProfiles = container.batteryProfiles,
        capabilityStatus = container.capabilityStatus,
        shizuku = container.shizuku,
        actionLog = container.actionLog,
        applicationScope = container.applicationScope,
    )

    constructor(container: AbleContainer) : this(
        container,
        InstalledAppCatalog(container.appContext),
        ApkBackupStore(container.appContext),
    )

    /** Restore path and backup size of the selected package (file IO, loaded asynchronously). */
    private data class RestoreInfo(val route: RestoreRoute?, val backupBytes: Long?)

    private data class Selection(
        val packageName: String? = null,
        val usage: UsageEvidence = UsageEvidence.Loading,
        val forceStop: ForceStopUi = ForceStopUi.Idle,
        val operation: PackageOperationUi = PackageOperationUi.Idle,
        val restore: RestoreInfo? = null,
        val uninstallPlan: UninstallPlanUi = UninstallPlanUi.Idle,
        /** Set when a backup failed at uninstall time, so the next confirmation shows it. */
        val backupFailure: BackupProblem? = null,
    )

    private data class Base(
        val catalog: CatalogState,
        val apps: Map<String, InstalledApp>,
        val knowledge: Map<String, PackageKnowledge>?,
        val archive: Map<String, AppArchiveRecord>,
        val protectedSet: Set<String>,
        val profiles: List<BatteryProfile>,
        val activeProfileIds: Set<String>,
        val items: List<AppListItem>,
        val systemArchivedCount: Int,
    )

    private val catalogState = MutableStateFlow<CatalogState>(CatalogState.Loading)
    private val knowledge = MutableStateFlow<Map<String, PackageKnowledge>?>(null)
    private val filters = MutableStateFlow(AppFilters())
    private val selection = MutableStateFlow(Selection())

    /** Background view and "Background activity" detail section (state lives in its own holder). */
    internal val background = com.dragolabs.able.feature.apps.background.BackgroundController(
        appContext = appContext,
        repository = com.dragolabs.able.feature.apps.background.BackgroundRepository(appContext, shizuku),
        actionLog = actionLog,
        ownPackage = catalog.ownPackageName,
        userId = { catalog.currentUserId },
        shizukuState = capabilityStatus.access.map { it.shizuku },
        apps = catalogState.map { (it as? CatalogState.Loaded)?.apps.orEmpty() },
        knowledge = knowledge,
        protectedSet = preferences.userPreferencesFlow.map { it.protectedAppPackages },
        scope = viewModelScope,
        applicationScope = applicationScope,
    )

    private val appPrefs = combine(
        preferences.userPreferencesFlow.map { it.protectedAppPackages }.distinctUntilChanged(),
        batteryProfiles.profiles,
        batteryProfiles.evaluation.map { it.activeIds }.distinctUntilChanged(),
    ) { protectedSet, profiles, activeIds -> Triple(protectedSet, profiles, activeIds) }
    private val archiveMap = archive.records.map { list -> list.associateBy { it.packageName } }

    private val base = combine(catalogState, knowledge, archiveMap, appPrefs) { catalogValue, info, archived, (protectedSet, profiles, activeIds) ->
        val apps = (catalogValue as? CatalogState.Loaded)?.apps.orEmpty()
        Base(
            catalog = catalogValue,
            apps = apps.associateBy { it.packageName },
            knowledge = info,
            archive = archived,
            protectedSet = protectedSet,
            profiles = profiles,
            activeProfileIds = activeIds,
            items = buildItems(apps, info.orEmpty(), archived, protectedSet),
            systemArchivedCount = apps.count { it.state == AppInstallState.ARCHIVED_BY_SYSTEM },
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<AppsUiState> = combine(
        base,
        filters,
        capabilityStatus.access,
        selection,
    ) { b, filter, access, selected ->
        val detail = selected.packageName?.let { pkg ->
            val app = b.apps[pkg]
            val record = b.archive[pkg]
            AppDetailState(
                packageName = pkg,
                app = app,
                label = app?.label ?: record?.label ?: pkg,
                state = app?.state ?: AppInstallState.NOT_FOUND,
                knowledge = b.knowledge?.get(pkg),
                knowledgeLoaded = b.knowledge != null,
                archiveRecord = record,
                blockReason = blockReasonFor(pkg, catalog.ownPackageName),
                isProtected = pkg in b.protectedSet,
                profileRules = b.profiles.map { profile ->
                    AppProfileRule(
                        profileId = profile.id,
                        name = profile.name,
                        builtInType = profile.builtInType,
                        level = profile.appRules[pkg] ?: AppInterventionLevel.OSSERVA,
                        active = profile.id in b.activeProfileIds,
                        enabled = profile.enabled,
                    )
                },
                effectiveAction = ProfileEngine.effectiveFor(
                    pkg,
                    b.profiles.filter { it.id in b.activeProfileIds },
                    b.protectedSet,
                )?.let { AppEffectiveAction(it.level, it.sourceProfileIds) },
                usage = if (access.usageAccess) selected.usage else UsageEvidence.AccessMissing,
                forceStop = selected.forceStop,
                operation = selected.operation,
                restoreRoute = selected.restore?.route.takeIf { app?.isInstalledForUser != true },
                backupBytes = selected.restore?.backupBytes,
                uninstallPlan = selected.uninstallPlan,
            )
        }
        AppsUiState(
            catalog = b.catalog,
            filters = filter,
            items = filterItems(b.items, filter),
            totalCount = b.apps.size,
            totalRows = b.items.size,
            archiveCount = b.archive.size,
            systemArchivedCount = b.systemArchivedCount,
            shizuku = access.shizuku,
            usageAccess = access.usageAccess,
            detail = detail,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    init {
        reloadCatalog()
        viewModelScope.launch { knowledge.value = knowledgeRepository.all() }
    }

    /** Re-reads grants and the package list (e.g. after returning from App info or Usage access settings). */
    fun onResume() {
        capabilityStatus.refresh()
        reloadCatalog()
        selection.value.packageName?.let {
            loadUsage(it)
            loadRestoreInfo(it)
        }
    }

    private fun reloadCatalog() {
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val result = runCatching { catalog.loadApps() }
            catalogState.update { previous ->
                result.fold(
                    onSuccess = { CatalogState.Loaded(it) },
                    // Keep showing a previously loaded list if a refresh fails.
                    onFailure = { if (previous is CatalogState.Loaded) previous else CatalogState.Failed },
                )
            }
            result.getOrNull()?.let { pruneArchive(it, startedAt) }
        }
    }

    /**
     * Drops archive records for packages that are installed and enabled again (restored outside
     * Able, e.g. from Android App info). Only positive PackageManager evidence read after the
     * record was written removes it.
     */
    private suspend fun pruneArchive(apps: List<InstalledApp>, loadStartedAt: Long) {
        runCatching {
            val byName = apps.associateBy { it.packageName }
            val restored = archive.records.first()
                .filter { it.timestampEpochMs < loadStartedAt && byName[it.packageName]?.state == AppInstallState.INSTALLED }
                .map { it.packageName }
            archive.removeAll(restored)
        }
    }

    fun retryLoad() {
        catalogState.value = CatalogState.Loading
        reloadCatalog()
    }

    suspend fun loadIcon(packageName: String): ImageBitmap? = catalog.loadIcon(packageName)

    fun setQuery(query: String) = filters.update { it.copy(query = query) }
    fun setStateFilter(state: AppStateFilter) = filters.update { it.copy(state = state) }
    fun setTypeFilter(type: AppTypeFilter) = filters.update { it.copy(type = type) }
    fun setProtectedOnly(enabled: Boolean) = filters.update { it.copy(protectedOnly = enabled) }
    fun toggleList(list: UadList) = filters.update { it.copy(lists = it.lists.toggle(list)) }
    fun toggleRemoval(removal: RemovalFilter) = filters.update { it.copy(removals = it.removals.toggle(removal)) }
    fun clearFilters() = filters.update { AppFilters(query = it.query) }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

    /** Synced from the list-detail navigator's current destination. */
    fun select(packageName: String?) {
        if (selection.value.packageName == packageName) return
        selection.value = Selection(packageName = packageName)
        packageName?.let {
            loadUsage(it)
            loadRestoreInfo(it)
        }
    }

    private fun loadRestoreInfo(packageName: String) {
        viewModelScope.launch {
            val info = runCatching { resolveRestore(packageName, archive.records.first().firstOrNull { it.packageName == packageName }) }
                .getOrNull()
            selection.update { if (it.packageName == packageName) it.copy(restore = info) else it }
        }
    }

    /**
     * Decides how [packageName] can be restored. A user app uninstalled for its only user loses its
     * APK (Android deletes `codePath`), so `install-existing` fails; then Able uses its backup or,
     * without one, the store.
     */
    private suspend fun resolveRestore(packageName: String, record: AppArchiveRecord?): RestoreInfo {
        val backup = backups.find(packageName)
        val apkPresent = backups.isApkPresent(packageName)
        val isSystem = backups.isSystemPackage(packageName) ?: record?.isSystem ?: false
        val state = catalog.readState(packageName)
        val route = when {
            apkPresent && state?.archivedBySystem != true -> RestoreRoute.OnDevice
            backup != null -> RestoreRoute.FromBackup(backup.manifest.totalBytes, backup.manifest.versionName)
            isSystem || state?.archivedBySystem == true -> RestoreRoute.OnDevice
            else -> RestoreRoute.FromStore(backups.installerOf(packageName))
        }
        return RestoreInfo(route, backup?.manifest?.totalBytes)
    }

    /** Computes the APK backup preview for the uninstall confirmation. */
    fun prepareUninstall(packageName: String) {
        val current = uiState.value.detail?.takeIf { it.packageName == packageName } ?: return
        if (current.app?.isSystem != false) {
            selection.update { if (it.packageName == packageName) it.copy(uninstallPlan = UninstallPlanUi.NotNeeded) else it }
            return
        }
        selection.update { if (it.packageName == packageName) it.copy(uninstallPlan = UninstallPlanUi.Loading) else it }
        viewModelScope.launch {
            val plan = runCatching { backups.plan(packageName) }.getOrNull()
            selection.update {
                if (it.packageName != packageName) return@update it
                val ui = if (plan == null) {
                    UninstallPlanUi.Ready(0, alreadyBackedUp = false, problem = it.backupFailure ?: BackupProblem.COPY_FAILED)
                } else {
                    UninstallPlanUi.Ready(plan.totalBytes, plan.alreadyBackedUp, plan.problem ?: it.backupFailure)
                }
                it.copy(uninstallPlan = ui)
            }
        }
    }

    fun cancelUninstall() {
        selection.update { it.copy(uninstallPlan = UninstallPlanUi.Idle) }
    }

    /** Deletes Able's APK backup of [packageName]; restore is then possible only from the store. */
    fun deleteBackup(packageName: String) {
        applicationScope.launch {
            runCatching { backups.delete(packageName) }
            loadRestoreInfo(packageName)
        }
    }

    private fun loadUsage(packageName: String) {
        viewModelScope.launch {
            val summary = catalog.usageSummary(packageName)
            selection.update {
                if (it.packageName != packageName) it
                else it.copy(usage = summary?.let(UsageEvidence::Reported) ?: UsageEvidence.NotReported)
            }
        }
    }

    fun setProtected(packageName: String, protected: Boolean) {
        viewModelScope.launch { preferences.setAppProtected(packageName, protected) }
    }

    /** Writes the rule into the battery profile (built-in rules are mirrored to the legacy preferences by the repository). */
    fun setInterventionLevel(packageName: String, profileId: String, level: AppInterventionLevel) {
        viewModelScope.launch { batteryProfiles.setAppRule(profileId, packageName, level) }
    }

    fun requestShizukuPermission() {
        shizuku.requestPermission()
        capabilityStatus.refresh()
    }

    fun dismissForceStopResult() {
        selection.update { it.copy(forceStop = ForceStopUi.Idle) }
    }

    fun dismissOperationResult() {
        selection.update { it.copy(operation = PackageOperationUi.Idle) }
    }

    /**
     * User-initiated force stop through Shizuku (APP-003). The process list is read back after the
     * command and the real outcome is reported and logged; Able never relaunches the app.
     */
    fun forceStop(packageName: String, label: String) {
        val command = PackageCommands.forceStop(catalog.currentUserId, packageName) ?: return
        val check = PackageCommands.pidOf(packageName) ?: return
        if (blockReasonFor(packageName, catalog.ownPackageName) != null) return
        if (selection.value.forceStop == ForceStopUi.Running) return
        selection.update { if (it.packageName == packageName) it.copy(forceStop = ForceStopUi.Running) else it }

        applicationScope.launch {
            val result = when (val stop = shizuku.executeCommand(command)) {
                is AbleResult.Failure -> ForceStopUi.Done(ForceStopOutcome.FAILED, stop.error.message)
                AbleResult.Loading -> ForceStopUi.Done(ForceStopOutcome.NOT_CONFIRMED)
                is AbleResult.Success -> {
                    delay(FORCE_STOP_VERIFY_DELAY_MS)
                    when (val pid = shizuku.executeCommand(check)) {
                        is AbleResult.Success ->
                            if (pid.data.isBlank()) ForceStopUi.Done(ForceStopOutcome.STOPPED)
                            else ForceStopUi.Done(ForceStopOutcome.STILL_RUNNING)
                        is AbleResult.Failure -> ForceStopUi.Done(ForceStopOutcome.NOT_CONFIRMED, pid.error.message)
                        AbleResult.Loading -> ForceStopUi.Done(ForceStopOutcome.NOT_CONFIRMED)
                    }
                }
            }
            val (status, detailRes) = when (result.outcome) {
                ForceStopOutcome.STOPPED -> ActionExecutionStatus.SUCCESS to R.string.apps_log_force_stop_success
                ForceStopOutcome.STILL_RUNNING -> ActionExecutionStatus.ATTEMPTED to R.string.apps_log_force_stop_still_running
                ForceStopOutcome.NOT_CONFIRMED -> ActionExecutionStatus.ATTEMPTED to R.string.apps_log_force_stop_not_confirmed
                ForceStopOutcome.FAILED -> ActionExecutionStatus.FAILED to R.string.apps_log_force_stop_failed
            }
            runCatching {
                actionLog.log(
                    actionId = ACTION_FORCE_STOP,
                    status = status,
                    detail = appContext.getString(detailRes, label),
                    targetPackage = packageName,
                    reversible = false,
                )
            }
            selection.update { if (it.packageName == packageName) it.copy(forceStop = result) else it }
        }
    }

    /**
     * Disable / enable / uninstall-for-user / restore through Shizuku (APP-004). Every command is
     * verified by re-reading PackageManager; the outcome is logged and the Able archive updated.
     */
    fun runOperation(packageName: String, operation: PackageOperation, allowWithoutBackup: Boolean = false) {
        if (!PackageCommands.isValidPackageName(packageName)) return
        if (blockReasonFor(packageName, catalog.ownPackageName) != null) return
        if (selection.value.operation is PackageOperationUi.Running) return
        val current = uiState.value.detail?.takeIf { it.packageName == packageName }
        val label = current?.label ?: packageName
        val isSystem = current?.app?.isSystem ?: current?.archiveRecord?.isSystem ?: false
        val record = current?.archiveRecord
        selection.update {
            if (it.packageName == packageName) {
                it.copy(operation = PackageOperationUi.Running(operation), uninstallPlan = UninstallPlanUi.Idle)
            } else {
                it
            }
        }

        applicationScope.launch {
            var backupFailed: BackupProblem? = null
            val result = when (operation) {
                PackageOperation.RESTORE -> restore(packageName, record)
                PackageOperation.UNINSTALL -> {
                    val outcome = uninstall(packageName, isSystem, allowWithoutBackup)
                    backupFailed = outcome.second
                    outcome.first
                }
                else -> runner.run(operation.toAction(), packageName)
            }
            updateArchive(packageName, label, isSystem, operation, result)
            logOperation(packageName, label, operation, result, backupFailed)
            reloadCatalogNow()
            val info = runCatching { resolveRestore(packageName, archive.records.first().firstOrNull { it.packageName == packageName }) }
                .getOrNull()
            selection.update {
                if (it.packageName == packageName) {
                    it.copy(
                        operation = PackageOperationUi.Done(
                            operation = operation,
                            outcome = result.outcome,
                            detail = result.detail,
                            storeRequired = result.storeRequired,
                            backupFailed = backupFailed,
                        ),
                        restore = info,
                        backupFailure = backupFailed ?: it.backupFailure,
                    )
                } else {
                    it
                }
            }
        }
    }

    /**
     * Uninstall for the user. For user apps the APK is backed up first because Android deletes it;
     * if the backup fails the uninstall only runs when the user accepted a store-only restore.
     * Returns the result and, when the backup failed, why.
     */
    private suspend fun uninstall(
        packageName: String,
        isSystem: Boolean,
        allowWithoutBackup: Boolean,
    ): Pair<PackageActionResult, BackupProblem?> {
        var problem: BackupProblem? = null
        var freshBackup = false
        if (!isSystem) {
            when (val backup = backups.backup(packageName)) {
                is BackupResult.Created -> freshBackup = !backup.reused
                is BackupResult.Failed -> problem = backup.problem
            }
            if (problem != null && !allowWithoutBackup) {
                val state = catalog.readState(packageName)
                return PackageActionResult(
                    outcome = PackageActionOutcome.REJECTED,
                    before = state,
                    after = state,
                    detail = appContext.getString(R.string.apps_backup_failed_not_uninstalled, appContext.getString(problem.labelRes())),
                ) to problem
            }
        }
        val result = runner.run(PackageAction.UNINSTALL_FOR_USER, packageName)
        // Nothing changed: drop a backup made only for this attempt.
        if (result.outcome == PackageActionOutcome.REJECTED && freshBackup) runCatching { backups.delete(packageName) }
        return result to problem
    }

    /**
     * Reinstalls for the user if needed (APK still on the device: `install-existing`; APK deleted:
     * Able's backup; neither: store required), then re-enables when the pre-Able state was enabled.
     */
    private suspend fun restore(packageName: String, record: AppArchiveRecord?): PackageActionResult {
        val initial = catalog.readState(packageName)
        var result: PackageActionResult? = null
        if (initial == null || !initial.installedForUser || initial.archivedBySystem) {
            result = when (resolveRestore(packageName, record).route) {
                is RestoreRoute.FromBackup -> {
                    val backup = backups.find(packageName)
                    if (backup == null) {
                        PackageActionResult(PackageActionOutcome.REJECTED, initial, initial, storeRequired = true)
                    } else {
                        runner.installFromBackup(packageName, backup)
                    }
                }
                is RestoreRoute.FromStore ->
                    PackageActionResult(PackageActionOutcome.REJECTED, initial, initial, storeRequired = true)
                RestoreRoute.OnDevice, null -> runner.run(PackageAction.REINSTALL_FOR_USER, packageName)
            }
            if (result.outcome != PackageActionOutcome.APPLIED) return result
        }
        val afterInstall = result?.after ?: initial
        val wantEnabled = record?.priorEnabled ?: true
        if (wantEnabled && afterInstall != null && !afterInstall.enabled) {
            val enable = runner.run(PackageAction.ENABLE, packageName)
            return enable.copy(before = initial)
        }
        return result ?: PackageActionResult(PackageActionOutcome.APPLIED, initial, initial)
    }

    private suspend fun updateArchive(
        packageName: String,
        label: String,
        isSystem: Boolean,
        operation: PackageOperation,
        result: PackageActionResult,
    ) {
        runCatching {
            when (operation) {
                PackageOperation.DISABLE, PackageOperation.UNINSTALL -> {
                    // Keep a restore path whenever the change may have happened.
                    if (result.outcome != PackageActionOutcome.REJECTED) {
                        archive.record(
                            packageName = packageName,
                            label = label,
                            action = if (operation == PackageOperation.DISABLE) ArchivedAction.DISABLED else ArchivedAction.UNINSTALLED,
                            isSystem = isSystem,
                            priorState = result.before,
                        )
                    }
                }
                PackageOperation.ENABLE, PackageOperation.RESTORE -> {
                    if (result.outcome == PackageActionOutcome.APPLIED) archive.remove(packageName)
                }
            }
        }
    }

    private suspend fun logOperation(
        packageName: String,
        label: String,
        operation: PackageOperation,
        result: PackageActionResult,
        backupFailed: BackupProblem? = null,
    ) {
        val status = when {
            result.storeRequired -> ActionExecutionStatus.SKIPPED
            result.outcome == PackageActionOutcome.APPLIED -> ActionExecutionStatus.SUCCESS
            result.outcome == PackageActionOutcome.NOT_CONFIRMED -> ActionExecutionStatus.ATTEMPTED
            else -> ActionExecutionStatus.FAILED
        }
        val res = when (operation) {
            PackageOperation.DISABLE -> R.string.apps_log_disable
            PackageOperation.ENABLE -> R.string.apps_log_enable
            PackageOperation.UNINSTALL -> R.string.apps_log_uninstall
            PackageOperation.RESTORE -> R.string.apps_log_restore
        }
        val outcomeRes = when (result.outcome) {
            PackageActionOutcome.APPLIED -> R.string.apps_log_outcome_applied
            PackageActionOutcome.NOT_CONFIRMED -> R.string.apps_log_outcome_not_confirmed
            PackageActionOutcome.REJECTED -> R.string.apps_log_outcome_rejected
        }
        runCatching {
            actionLog.log(
                actionId = when (operation) {
                    PackageOperation.DISABLE -> ACTION_DISABLE
                    PackageOperation.ENABLE -> ACTION_ENABLE
                    PackageOperation.UNINSTALL -> ACTION_UNINSTALL
                    PackageOperation.RESTORE -> ACTION_RESTORE
                },
                status = status,
                detail = buildString {
                    append(appContext.getString(res, label)).append(" – ")
                    append(appContext.getString(if (result.storeRequired) R.string.apps_log_outcome_store_required else outcomeRes))
                    if (backupFailed != null) {
                        append(" – ").append(appContext.getString(R.string.apps_log_backup_failed, appContext.getString(backupFailed.labelRes())))
                    }
                },
                targetPackage = packageName,
                reversible = operation == PackageOperation.DISABLE || operation == PackageOperation.UNINSTALL,
            )
        }
    }

    private suspend fun reloadCatalogNow() {
        runCatching { catalog.loadApps() }.onSuccess { catalogState.value = CatalogState.Loaded(it) }
    }

    private companion object {
        const val ACTION_FORCE_STOP = "app_force_stop"
        const val ACTION_DISABLE = "app_disable"
        const val ACTION_ENABLE = "app_enable"
        const val ACTION_UNINSTALL = "app_uninstall_user"
        const val ACTION_RESTORE = "app_restore"
        const val FORCE_STOP_VERIFY_DELAY_MS = 700L
    }
}

internal fun BackupProblem.labelRes(): Int = when (this) {
    BackupProblem.NOT_FOUND -> R.string.apps_backup_problem_not_found
    BackupProblem.NOT_READABLE -> R.string.apps_backup_problem_not_readable
    BackupProblem.NO_SPACE -> R.string.apps_backup_problem_no_space
    BackupProblem.COPY_FAILED -> R.string.apps_backup_problem_copy_failed
}

/** True when the removal level calls for the strong warning and explicit acknowledgement. */
internal fun requiresAcknowledgement(removal: RemovalLevel?, isSystem: Boolean): Boolean =
    removal == RemovalLevel.EXPERT || removal == RemovalLevel.UNSAFE || (removal == null && isSystem)
