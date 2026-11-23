package com.dragolabs.able.feature.tools.storage

import android.app.PendingIntent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.data.ActionLogRepository
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.core.model.StorageCategory
import com.dragolabs.able.core.model.StorageReviewItem
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.UiMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Size filter chips (STO-001): 50 MB / 100 MB (default) / 500 MB / 1 GB. */
enum class SizeFilter(val bytes: Long, @StringRes val labelRes: Int) {
    MB_50(50L * MB, R.string.tools_storage_size_50mb),
    MB_100(100L * MB, R.string.tools_storage_size_100mb),
    MB_500(500L * MB, R.string.tools_storage_size_500mb),
    GB_1(1024L * MB, R.string.tools_storage_size_1gb),
}

/** Age filter chips (STO-001): 7 / 30 (default) / 90 days. */
enum class AgeFilter(val days: Int) { D7(7), D30(30), D90(90) }

private const val MB = 1024L * 1024L
private const val TOP_APPS = 10

/** Result of the last cache cleanup, shown in the Clean up section and announced with haptics. */
@Immutable
data class CleanupReport(val message: UiMessage, val success: Boolean, val id: Long = System.nanoTime())

@Immutable
data class StorageUiState(
    // Overview (STO-003)
    val overviewLoading: Boolean = true,
    val overviewFailed: Boolean = false,
    val breakdown: StorageBreakdown? = null,
    val hasUsageAccess: Boolean = false,
    val largestApps: List<AppStorage> = emptyList(),
    // System-wide cleanup (STO-002)
    val shizukuState: ShizukuConnectionState = ShizukuConnectionState.NOT_AVAILABLE,
    val selectedApps: Set<String> = emptySet(),
    val confirmTrim: Boolean = false,
    val confirmAppClear: Boolean = false,
    val trimming: Boolean = false,
    val clearingApps: Boolean = false,
    val cleanupReport: CleanupReport? = null,
    // Review files (STO-001)
    val source: StorageSource = StorageSource.MediaLibrary,
    val hasMediaPermission: Boolean = false,
    val partialMediaAccess: Boolean = false,
    val category: StorageCategory = StorageCategory.LARGE_FILES,
    val sizeFilter: SizeFilter = SizeFilter.MB_100,
    val ageFilter: AgeFilter = AgeFilter.D30,
    val scanning: Boolean = false,
    val scanned: Boolean = false,
    val scanFailed: Boolean = false,
    val items: List<StorageReviewItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val confirmFolderDelete: Boolean = false,
    /** Android's MediaStore delete confirmation, launched by the route. */
    val pendingDeleteRequest: PendingIntent? = null,
    val deleting: Boolean = false,
    // Able's own cache
    val cacheBytes: Long = 0,
    val cleaningCache: Boolean = false,
    val message: UiMessage? = null,
) {
    val canScan: Boolean get() = source is StorageSource.Folder || hasMediaPermission
    val selectedItems: List<StorageReviewItem> get() = items.filter { it.uriString in selected }
    val selectedBytes: Long get() = selectedItems.sumOf { it.sizeBytes }
    val shizukuReady: Boolean get() = shizukuState == ShizukuConnectionState.READY
    val cleanupBusy: Boolean get() = trimming || clearingApps
    val selectedAppsCacheBytes: Long get() = largestApps.filter { it.packageName in selectedApps }.sumOf { it.cacheBytes }
}

class StorageViewModel(
    private val scanner: StorageScanner,
    private val stats: StorageStatsSource,
    private val cleaner: SystemCacheCleaner,
    private val shizuku: ShizukuAdapter,
    private val actionLog: ActionLogRepository,
    private val logScope: CoroutineScope,
    private val formatBytes: (Long) -> String,
    private val describe: (UiMessage) -> String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StorageUiState(
            hasMediaPermission = scanner.hasAnyMediaPermission(),
            partialMediaAccess = scanner.hasPartialMediaAccess(),
            hasUsageAccess = stats.hasUsageAccess(),
            shizukuState = shizuku.connectionState.value,
            cacheBytes = scanner.ableCacheBytes(),
        ),
    )
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()
    private var scanJob: Job? = null
    private var overviewJob: Job? = null

    val mediaPermissions: Array<String> get() = scanner.mediaPermissions()

    init {
        viewModelScope.launch {
            shizuku.connectionState.collect { state -> _uiState.update { it.copy(shizukuState = state) } }
        }
    }

    /** Permission grants can change in Android settings; re-read on resume. */
    fun refreshAccess() {
        val granted = scanner.hasAnyMediaPermission()
        val changed = granted != _uiState.value.hasMediaPermission
        _uiState.update {
            it.copy(
                hasMediaPermission = granted,
                partialMediaAccess = scanner.hasPartialMediaAccess(),
                hasUsageAccess = stats.hasUsageAccess(),
                cacheBytes = scanner.ableCacheBytes(),
            )
        }
        shizuku.updateState()
        loadOverview()
        if (changed || (!_uiState.value.scanned && _uiState.value.canScan)) scan()
    }

    // --- Overview (STO-003) ---

    fun loadOverview() {
        overviewJob?.cancel()
        _uiState.update { it.copy(overviewLoading = it.breakdown == null, overviewFailed = false) }
        overviewJob = viewModelScope.launch {
            val volume = stats.readVolume()
            if (volume == null) {
                _uiState.update { it.copy(overviewLoading = false, overviewFailed = true) }
                return@launch
            }
            val apps = stats.readApps()
            val external = stats.readExternalMedia()
            val media = if (scanner.hasAnyMediaPermission()) runCatching { scanner.mediaSums() }.getOrNull() else null
            val measured = MeasuredCategories(
                apps = apps?.sumOf { it.totalBytes },
                // Android's per-user totals are complete; MediaStore sums cover only visible files.
                images = external?.imageBytes ?: media?.images,
                video = external?.videoBytes ?: media?.video,
                audio = external?.audioBytes ?: media?.audio,
                documents = media?.documents,
                downloads = media?.downloads,
            )
            val breakdown = buildBreakdown(volume.totalBytes, volume.freeBytes, measured)
            _uiState.update { current ->
                val top = apps.orEmpty().take(TOP_APPS)
                current.copy(
                    overviewLoading = false,
                    overviewFailed = false,
                    breakdown = breakdown,
                    hasUsageAccess = apps != null,
                    largestApps = top,
                    selectedApps = current.selectedApps intersect top.map { it.packageName }.toSet(),
                )
            }
        }
    }

    // --- System-wide cache cleanup (STO-002) ---

    fun requestShizukuPermission() {
        shizuku.requestPermission()
    }

    fun toggleApp(packageName: String) = _uiState.update {
        it.copy(selectedApps = if (packageName in it.selectedApps) it.selectedApps - packageName else it.selectedApps + packageName)
    }

    fun askTrimCaches() {
        if (_uiState.value.shizukuReady && !_uiState.value.cleanupBusy) _uiState.update { it.copy(confirmTrim = true) }
    }

    fun askClearSelectedApps() {
        val state = _uiState.value
        if (state.shizukuReady && !state.cleanupBusy && state.selectedApps.isNotEmpty()) _uiState.update { it.copy(confirmAppClear = true) }
    }

    fun dismissCleanupDialogs() = _uiState.update { it.copy(confirmTrim = false, confirmAppClear = false) }

    fun trimAllCaches() {
        _uiState.update { it.copy(confirmTrim = false) }
        if (!cleaner.isReady() || _uiState.value.cleanupBusy) return
        _uiState.update { it.copy(trimming = true) }
        viewModelScope.launch {
            val result = runCatching { cleaner.trimAllCaches() }.getOrNull()
            val report = when {
                result == null || !result.accepted -> CleanupReport(UiMessage(R.string.storage_trim_failed), success = false)
                !result.measured -> CleanupReport(UiMessage(R.string.storage_trim_unmeasured), success = true)
                result.freedBytes == 0L -> CleanupReport(UiMessage(R.string.storage_freed_none), success = true)
                else -> CleanupReport(UiMessage(R.string.storage_trim_done, formatBytes(result.freedBytes)), success = true)
            }
            _uiState.update { it.copy(trimming = false, cleanupReport = report) }
            log("tools.storage.trim_caches", if (report.success) ActionExecutionStatus.SUCCESS else ActionExecutionStatus.FAILED, report.message)
            loadOverview()
        }
    }

    fun clearSelectedAppCaches() {
        val packages = _uiState.value.selectedApps.toList()
        _uiState.update { it.copy(confirmAppClear = false) }
        if (!cleaner.isReady() || packages.isEmpty() || _uiState.value.cleanupBusy) return
        _uiState.update { it.copy(clearingApps = true) }
        viewModelScope.launch {
            val result = runCatching { cleaner.clearAppCaches(packages) }.getOrNull()
            val report = when {
                result == null || result.cleared.isEmpty() -> CleanupReport(UiMessage(R.string.storage_apps_failed), success = false)
                result.failed.isEmpty() -> CleanupReport(
                    UiMessage(if (result.freedBytes == 0L) R.string.storage_apps_done_none else R.string.storage_apps_done, result.cleared.size, formatBytes(result.freedBytes)),
                    success = true,
                )
                else -> CleanupReport(
                    UiMessage(R.string.storage_apps_partial, result.cleared.size, result.requested.size, formatBytes(result.freedBytes)),
                    success = false,
                )
            }
            _uiState.update {
                it.copy(clearingApps = false, cleanupReport = report, selectedApps = it.selectedApps - result?.cleared.orEmpty().toSet())
            }
            val detail = describe(report.message)
            logScope.launch {
                packages.forEach { pkg ->
                    val ok = result?.cleared?.contains(pkg) == true
                    actionLog.log("tools.storage.clear_app_cache", if (ok) ActionExecutionStatus.SUCCESS else ActionExecutionStatus.FAILED, detail, targetPackage = pkg)
                }
            }
            loadOverview()
        }
    }

    fun consumeCleanupReport() = _uiState.update { it.copy(cleanupReport = null) }

    fun onMediaPermissionResult() = refreshAccess()

    fun useMediaLibrary() {
        _uiState.update { it.copy(source = StorageSource.MediaLibrary, items = emptyList(), selected = emptySet(), scanned = false) }
        if (_uiState.value.canScan) scan()
    }

    fun onFolderPicked(treeUri: Uri?) {
        if (treeUri == null) return
        val folder = StorageSource.Folder(treeUri.toString(), scanner.folderName(treeUri))
        _uiState.update { it.copy(source = folder, items = emptyList(), selected = emptySet(), scanned = false) }
        scan()
    }

    fun setCategory(category: StorageCategory) {
        if (category == _uiState.value.category) return
        _uiState.update { it.copy(category = category) }
        scan()
    }

    fun setSizeFilter(filter: SizeFilter) {
        _uiState.update { it.copy(sizeFilter = filter) }
        scan()
    }

    fun setAgeFilter(filter: AgeFilter) {
        _uiState.update { it.copy(ageFilter = filter) }
        scan()
    }

    fun scan() {
        val state = _uiState.value
        if (!state.canScan) return
        scanJob?.cancel()
        _uiState.update { it.copy(scanning = true, scanFailed = false) }
        scanJob = viewModelScope.launch {
            val result = runCatching { scanner.scan(state.source, state.category, state.sizeFilter.bytes, state.ageFilter.days) }
            _uiState.update { current ->
                val items = result.getOrDefault(emptyList())
                val stillPresent = items.map { it.uriString }.toSet()
                current.copy(
                    scanning = false,
                    scanned = true,
                    scanFailed = result.isFailure,
                    items = items,
                    selected = current.selected intersect stillPresent,
                )
            }
        }
    }

    fun toggleSelection(uri: String) = _uiState.update {
        it.copy(selected = if (uri in it.selected) it.selected - uri else it.selected + uri)
    }

    fun selectAll() = _uiState.update { it.copy(selected = it.items.map { item -> item.uriString }.toSet()) }

    fun clearSelection() = _uiState.update { it.copy(selected = emptySet()) }

    /** Review-before-delete: nothing is removed until the user confirms (STO-001). */
    fun requestDelete() {
        val state = _uiState.value
        if (state.selected.isEmpty()) return
        when (state.source) {
            StorageSource.MediaLibrary -> {
                val request = runCatching { scanner.createMediaDeleteRequest(state.selected.toList()) }.getOrNull()
                if (request == null) {
                    _uiState.update { it.copy(message = UiMessage(R.string.tools_storage_delete_request_failed)) }
                } else {
                    _uiState.update { it.copy(pendingDeleteRequest = request) }
                }
            }
            is StorageSource.Folder -> _uiState.update { it.copy(confirmFolderDelete = true) }
        }
    }

    fun consumeDeleteRequest() = _uiState.update { it.copy(pendingDeleteRequest = null) }

    /** Called with Android's answer to the MediaStore confirmation. */
    fun onMediaDeleteResult(approved: Boolean) {
        val requested = _uiState.value.selectedItems
        if (!approved) {
            _uiState.update { it.copy(message = UiMessage(R.string.tools_storage_delete_cancelled)) }
            return
        }
        verifyDeletion(requested)
    }

    fun dismissFolderDelete() = _uiState.update { it.copy(confirmFolderDelete = false) }

    fun confirmFolderDelete() {
        val requested = _uiState.value.selectedItems
        _uiState.update { it.copy(confirmFolderDelete = false, deleting = true) }
        viewModelScope.launch {
            scanner.deleteDocuments(requested.map { it.uriString })
            verifyDeletion(requested)
        }
    }

    private fun verifyDeletion(requested: List<StorageReviewItem>) {
        _uiState.update { it.copy(deleting = true) }
        viewModelScope.launch {
            val remaining = scanner.stillExisting(requested.map { it.uriString })
            val deleted = requested.filter { it.uriString !in remaining }
            val freed = deleted.sumOf { it.sizeBytes }
            val message = when {
                deleted.isEmpty() -> UiMessage(R.string.tools_storage_delete_none)
                remaining.isEmpty() -> UiMessage(R.string.tools_storage_delete_done, deleted.size, formatBytes(freed))
                else -> UiMessage(R.string.tools_storage_delete_partial, deleted.size, requested.size, formatBytes(freed))
            }
            _uiState.update { it.copy(deleting = false, selected = it.selected - deleted.map { d -> d.uriString }.toSet(), message = message) }
            log(
                actionId = "tools.storage.delete",
                status = when {
                    deleted.isEmpty() -> ActionExecutionStatus.FAILED
                    remaining.isEmpty() -> ActionExecutionStatus.SUCCESS
                    else -> ActionExecutionStatus.FAILED
                },
                message = message,
            )
            scan()
        }
    }

    /** Clears only Able's own cache (STO-002, available without Shizuku) and reports what was actually freed. */
    fun cleanAbleCache() {
        if (_uiState.value.cleaningCache) return
        _uiState.update { it.copy(cleaningCache = true) }
        viewModelScope.launch {
            val result = runCatching { scanner.cleanAbleCache() }.getOrNull()
            val message = if (result == null) {
                UiMessage(R.string.tools_storage_cache_failed)
            } else {
                UiMessage(R.string.tools_storage_cache_done, formatBytes(result.freedBytes))
            }
            _uiState.update { it.copy(cleaningCache = false, cacheBytes = result?.remainingBytes ?: scanner.ableCacheBytes(), message = message) }
            loadOverview()
            log("tools.storage.cache", if (result != null) ActionExecutionStatus.SUCCESS else ActionExecutionStatus.FAILED, message)
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun log(actionId: String, status: ActionExecutionStatus, message: UiMessage) {
        val detail = describe(message)
        logScope.launch { actionLog.log(actionId, status, detail) }
    }
}
