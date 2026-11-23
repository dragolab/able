package com.dragolabs.able.feature.apps.background

import android.content.Context
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.data.ActionLogRepository
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.feature.apps.AppTypeFilter
import com.dragolabs.able.feature.apps.R
import com.dragolabs.able.feature.apps.blockReasonFor
import com.dragolabs.able.feature.apps.data.InstalledApp
import com.dragolabs.able.feature.apps.data.PackageActionOutcome
import com.dragolabs.able.feature.apps.data.PackageCommands
import com.dragolabs.able.feature.apps.data.PackageKnowledge
import com.dragolabs.able.feature.apps.requiresAcknowledgement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the Background view and the "Background activity" detail section. Owned by
 * [com.dragolabs.able.feature.apps.AppsViewModel]; changes run in [applicationScope] so a started
 * change is always verified and logged even if the screen closes.
 */
class BackgroundController(
    private val appContext: Context,
    private val repository: BackgroundRepository,
    private val actionLog: ActionLogRepository,
    private val ownPackage: String,
    private val userId: () -> Int,
    shizukuState: Flow<ShizukuConnectionState>,
    apps: Flow<List<InstalledApp>>,
    knowledge: Flow<Map<String, PackageKnowledge>?>,
    protectedSet: Flow<Set<String>>,
    private val scope: CoroutineScope,
    private val applicationScope: CoroutineScope,
) {
    private data class Inputs(
        val apps: Map<String, InstalledApp>,
        val knowledge: Map<String, PackageKnowledge>,
        val protectedSet: Set<String>,
    )

    private data class Selection(val selectionMode: Boolean = false, val selected: Set<String> = emptySet())

    private data class DetailSelection(
        val packageName: String? = null,
        val reading: DetailReading = DetailReading.Loading,
        val operation: BackgroundOperationUi = BackgroundOperationUi.Idle,
    )

    private val load = MutableStateFlow<OverviewLoad>(OverviewLoad.Unavailable)
    private val filters = MutableStateFlow(BackgroundFilters())
    private val selection = MutableStateFlow(Selection())
    private val bulk = MutableStateFlow<BulkUi>(BulkUi.Idle)
    private val detail = MutableStateFlow(DetailSelection())
    private val shizuku = MutableStateFlow(ShizukuConnectionState.NOT_AVAILABLE)
    private var visible = false

    private val context = combine(apps, knowledge, protectedSet) { list, info, protected ->
        Inputs(list.filter { it.isInstalledForUser }.associateBy { it.packageName }, info.orEmpty(), protected)
    }

    private val allRows = combine(context, load) { ctx, loaded -> buildRows(ctx, (loaded as? OverviewLoad.Loaded)?.overview) }
        .flowOn(Dispatchers.Default)

    val listState: StateFlow<BackgroundListUiState> = combine(allRows, load, filters, selection, combine(bulk, shizuku, ::Pair)) { rows, loaded, filter, sel, (bulkUi, state) ->
        BackgroundListUiState(
            load = loaded,
            filters = filter,
            rows = filterBackgroundRows(rows, filter),
            totalRows = rows.size,
            restrictedCount = rows.count { it.status == BackgroundStatus.RESTRICTED },
            selectionMode = sel.selectionMode,
            selected = sel.selected,
            bulk = bulkUi,
            shizuku = state,
        )
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5_000), BackgroundListUiState())

    val detailState: StateFlow<BackgroundDetailUi?> = combine(detail, context) { sel, ctx ->
        val pkg = sel.packageName ?: return@combine null
        val app = ctx.apps[pkg]
        val removal = ctx.knowledge[pkg]?.removal
        BackgroundDetailUi(
            packageName = pkg,
            label = app?.label ?: pkg,
            installed = app != null,
            isSystem = app?.isSystem ?: false,
            reading = sel.reading,
            operation = sel.operation,
            blockReason = blockReasonFor(pkg, ownPackage),
            isProtected = pkg in ctx.protectedSet,
            removal = removal,
            risky = requiresAcknowledgement(removal, app?.isSystem ?: false),
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        scope.launch {
            shizukuState.distinctUntilChanged().collect { state ->
                shizuku.value = state
                if (state == ShizukuConnectionState.READY) {
                    if (visible) refreshOverview()
                } else {
                    load.value = OverviewLoad.Unavailable
                }
                detail.value.packageName?.let(::loadDetail)
            }
        }
    }

    // region Intents

    /** The Background view became visible (or hidden). The overview is read only while visible. */
    fun setVisible(value: Boolean) {
        visible = value
        if (value && load.value !is OverviewLoad.Loaded) refreshOverview()
    }

    fun onResume() {
        if (visible) refreshOverview()
        detail.value.packageName?.let(::loadDetail)
    }

    fun refreshOverview() {
        if (shizuku.value != ShizukuConnectionState.READY || !repository.isReady) {
            load.value = OverviewLoad.Unavailable
            return
        }
        if (load.value !is OverviewLoad.Loaded) load.value = OverviewLoad.Loading
        scope.launch {
            val overview = runCatching { repository.readOverview(userId()) }.getOrNull()
            load.update { previous ->
                when {
                    overview != null && overview.runAnyRestricted != null -> OverviewLoad.Loaded(overview)
                    overview == null && !repository.isReady -> OverviewLoad.Unavailable
                    previous is OverviewLoad.Loaded -> previous
                    else -> OverviewLoad.Failed
                }
            }
        }
    }

    fun setQuery(query: String) = filters.update { it.copy(query = query) }
    fun setType(type: AppTypeFilter) = filters.update { it.copy(type = type) }
    fun setRestrictedOnly(value: Boolean) = filters.update { it.copy(restrictedOnly = value) }
    fun clearFilters() = filters.update { BackgroundFilters(query = it.query) }

    fun setSelectionMode(enabled: Boolean) = selection.update {
        if (enabled) it.copy(selectionMode = true) else Selection()
    }

    fun toggleSelected(packageName: String) = selection.update {
        val next = if (packageName in it.selected) it.selected - packageName else it.selected + packageName
        it.copy(selectionMode = true, selected = next)
    }

    /** Selects every changeable row currently shown (search and filters applied). */
    fun selectAllShown() = selection.update { sel ->
        sel.copy(selectionMode = true, selected = sel.selected + listState.value.rows.filter { it.blockReason == null }.map { it.packageName })
    }

    fun clearSelection() = selection.update { it.copy(selected = emptySet()) }

    fun dismissBulkResult() {
        bulk.value = BulkUi.Idle
    }

    fun bulkSummary(): BulkSummary {
        val rows = listState.value
        val byName = rowsByName()
        val chosen = rows.selected.mapNotNull { byName[it] }
        val changeable = chosen.filter { it.blockReason == null }
        return BulkSummary(
            total = rows.selected.size,
            changeable = changeable.size,
            skipped = rows.selected.size - changeable.size,
            protectedCount = changeable.count { it.isProtected },
            riskyCount = changeable.count { it.risky },
        )
    }

    private var lastRows: List<BackgroundRow> = emptyList()
    private fun rowsByName(): Map<String, BackgroundRow> = lastRows.associateBy { it.packageName }

    /** Restricts (or allows) every selected package, one at a time, reading each one back. */
    fun runBulk(restrict: Boolean) {
        if (bulk.value is BulkUi.Running) return
        val byName = rowsByName()
        val chosen = selection.value.selected.toList()
        val targets = chosen.filter { pkg ->
            val row = byName[pkg]
            row != null && row.blockReason == null && PackageCommands.isValidPackageName(pkg)
        }
        val skipped = chosen.size - targets.size
        if (targets.isEmpty()) {
            bulk.value = BulkUi.Done(restrict, 0, 0, 0, skipped)
            return
        }
        bulk.value = BulkUi.Running(restrict, 0, targets.size)
        applicationScope.launch {
            var applied = 0
            var rejected = 0
            var notConfirmed = 0
            val change = BackgroundChange.SetBackground(allowed = !restrict)
            targets.forEachIndexed { index, pkg ->
                val result = runCatching { repository.apply(userId(), pkg, change) }
                    .getOrElse { BackgroundChangeResult(PackageActionOutcome.NOT_CONFIRMED, null, null, it.message) }
                when (result.outcome) {
                    PackageActionOutcome.APPLIED -> applied++
                    PackageActionOutcome.REJECTED -> rejected++
                    PackageActionOutcome.NOT_CONFIRMED -> notConfirmed++
                }
                log(pkg, byName[pkg]?.label ?: pkg, change, result)
                bulk.value = BulkUi.Running(restrict, index + 1, targets.size)
            }
            bulk.value = BulkUi.Done(restrict, applied, rejected, notConfirmed, skipped)
            selection.value = Selection()
            refreshOverviewNow()
            detail.value.packageName?.let(::loadDetail)
        }
    }

    /** Synced with the list-detail navigator. */
    fun select(packageName: String?) {
        if (detail.value.packageName == packageName) return
        detail.value = DetailSelection(packageName = packageName)
        packageName?.let(::loadDetail)
    }

    private fun loadDetail(packageName: String) {
        scope.launch {
            val reading = if (repository.isReady) {
                runCatching { repository.read(userId(), packageName) }.getOrNull()
                    ?.let { DetailReading.Loaded(it) } ?: DetailReading.Failed
            } else {
                DetailReading.Limited(repository.isBatteryOptimizationExempt(packageName))
            }
            detail.update { if (it.packageName == packageName) it.copy(reading = reading) else it }
        }
    }

    fun dismissOperationResult() = detail.update { it.copy(operation = BackgroundOperationUi.Idle) }

    /** Applies [change] to the selected package (detail section) and reads it back. */
    fun apply(packageName: String, label: String, change: BackgroundChange) {
        if (!PackageCommands.isValidPackageName(packageName)) return
        if (blockReasonFor(packageName, ownPackage) != null) return
        if (detail.value.operation is BackgroundOperationUi.Running) return
        detail.update { if (it.packageName == packageName) it.copy(operation = BackgroundOperationUi.Running(change)) else it }
        applicationScope.launch {
            val result = runCatching { repository.apply(userId(), packageName, change) }
                .getOrElse { BackgroundChangeResult(PackageActionOutcome.NOT_CONFIRMED, null, null, it.message) }
            log(packageName, label, change, result)
            detail.update {
                if (it.packageName != packageName) return@update it
                it.copy(
                    operation = BackgroundOperationUi.Done(change, result.outcome, result.detail),
                    reading = result.after?.let(DetailReading::Loaded) ?: it.reading,
                )
            }
            if (load.value is OverviewLoad.Loaded) refreshOverviewNow()
        }
    }

    // endregion

    private suspend fun refreshOverviewNow() {
        val overview = runCatching { repository.readOverview(userId()) }.getOrNull() ?: return
        if (overview.runAnyRestricted != null) load.value = OverviewLoad.Loaded(overview)
    }

    private suspend fun log(packageName: String, label: String, change: BackgroundChange, result: BackgroundChangeResult) {
        val status = when (result.outcome) {
            PackageActionOutcome.APPLIED -> ActionExecutionStatus.SUCCESS
            PackageActionOutcome.NOT_CONFIRMED -> ActionExecutionStatus.ATTEMPTED
            PackageActionOutcome.REJECTED -> ActionExecutionStatus.FAILED
        }
        val action = when (change) {
            is BackgroundChange.SetBackground -> appContext.getString(
                if (change.allowed) R.string.apps_log_background_allow else R.string.apps_log_background_restrict,
                label,
            )
            is BackgroundChange.SetBucket -> appContext.getString(
                R.string.apps_log_standby_bucket,
                label,
                appContext.getString(change.bucket.labelRes()),
            )
        }
        val outcome = appContext.getString(
            when (result.outcome) {
                PackageActionOutcome.APPLIED -> R.string.apps_log_outcome_applied
                PackageActionOutcome.NOT_CONFIRMED -> R.string.apps_log_outcome_not_confirmed
                PackageActionOutcome.REJECTED -> R.string.apps_log_outcome_rejected
            },
        )
        runCatching {
            actionLog.log(
                actionId = when (change) {
                    is BackgroundChange.SetBackground -> if (change.allowed) ACTION_ALLOW else ACTION_RESTRICT
                    is BackgroundChange.SetBucket -> ACTION_BUCKET
                },
                status = status,
                detail = "$action – $outcome",
                targetPackage = packageName,
                reversible = true,
            )
        }
    }

    private fun buildRows(ctx: Inputs, overview: BackgroundOverview?): List<BackgroundRow> =
        ctx.apps.values.map { app ->
            val snapshot = overview?.snapshotOf(app.packageName)
            val removal = ctx.knowledge[app.packageName]?.removal
            BackgroundRow(
                packageName = app.packageName,
                label = app.label,
                isSystem = app.isSystem,
                status = snapshot?.status ?: BackgroundStatus.UNKNOWN,
                bucket = snapshot?.bucket,
                batteryOptimizationExempt = snapshot?.batteryOptimizationExempt,
                isProtected = app.packageName in ctx.protectedSet,
                removal = removal,
                blockReason = blockReasonFor(app.packageName, ownPackage),
                risky = requiresAcknowledgement(removal, app.isSystem),
            )
        }.also { lastRows = it }

    private companion object {
        const val ACTION_RESTRICT = "app_background_restrict"
        const val ACTION_ALLOW = "app_background_allow"
        const val ACTION_BUCKET = "app_standby_bucket"
    }
}

/** Search (label, package) and filters of the Background view. */
internal fun filterBackgroundRows(rows: List<BackgroundRow>, filters: BackgroundFilters): List<BackgroundRow> {
    val query = filters.query.trim().lowercase()
    return rows.filter { row ->
        when (filters.type) {
            AppTypeFilter.ALL -> true
            AppTypeFilter.USER -> !row.isSystem
            AppTypeFilter.SYSTEM -> row.isSystem
        } &&
            (!filters.restrictedOnly || row.status == BackgroundStatus.RESTRICTED || row.status == BackgroundStatus.PARTIAL) &&
            (query.isEmpty() || row.label.lowercase().contains(query) || row.packageName.lowercase().contains(query))
    }
}
