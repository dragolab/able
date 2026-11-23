package com.dragolabs.able.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.data.AbleContainer
import com.dragolabs.able.core.data.AccessSnapshot
import com.dragolabs.able.core.data.CapabilityStatusRepository
import com.dragolabs.able.core.data.HomeLayoutRepository
import com.dragolabs.able.core.database.UserPreferencesRepository
import com.dragolabs.able.core.model.HapticsIntensity
import com.dragolabs.able.core.model.ThemeMode
import com.dragolabs.able.feature.settings.backup.BackupError
import com.dragolabs.able.feature.settings.backup.BackupRepository
import com.dragolabs.able.feature.settings.backup.BackupSection
import com.dragolabs.able.feature.settings.backup.ConflictPolicy
import com.dragolabs.able.feature.settings.backup.ExportResult
import com.dragolabs.able.feature.settings.backup.HistorySummary
import com.dragolabs.able.feature.settings.backup.ImportMode
import com.dragolabs.able.feature.settings.backup.ImportPreview
import com.dragolabs.able.feature.settings.backup.ImportReadResult
import com.dragolabs.able.feature.settings.backup.ImportReport
import com.dragolabs.able.feature.settings.backup.ParsedBackup
import com.dragolabs.able.feature.settings.update.AppUpdater
import com.dragolabs.able.feature.settings.update.AvailableUpdate
import com.dragolabs.able.feature.settings.update.UpdaterState
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Export draft: the section checklist starts empty every time (CFG-001); the optional battery
 * history is never preselected either (CFG-003).
 */
data class ExportDraft(
    val selected: Set<BackupSection> = emptySet(),
    /** True after "Choose destination"; the route then launches the Android Save dialog. */
    val awaitingDestination: Boolean = false,
    /** Count and size of the optional battery history; null while loading. */
    val history: HistorySummary? = null,
)

sealed interface ImportStage {
    data object Reading : ImportStage
    data class Preview(
        val backup: ParsedBackup,
        val preview: ImportPreview,
        val mode: ImportMode? = null,
        val policy: ConflictPolicy? = null,
    ) : ImportStage {
        /** Merge with conflicts needs an explicit conflict choice; neither choice is preselected. */
        val canApply: Boolean
            get() = mode == ImportMode.REPLACE ||
                (mode == ImportMode.MERGE && (preview.totalConflicts == 0 || policy != null))
    }
    data object Applying : ImportStage
    data class Done(val report: ImportReport, val restore: Boolean) : ImportStage
    data class Failed(val error: BackupError) : ImportStage
    /** Rollback snapshot could not be written, so nothing was changed. */
    data object RollbackFailed : ImportStage
}

sealed interface SettingsMessage {
    data class Exported(val fileName: String) : SettingsMessage
    data object ExportFailed : SettingsMessage
    data object HomeLayoutReset : SettingsMessage
    data object Copied : SettingsMessage
    data object NoHandler : SettingsMessage
    data object ShizukuUnavailable : SettingsMessage
    data object LanguageNotApplied : SettingsMessage
    data object RestoreFailed : SettingsMessage
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val languageTag: String? = null,
    val access: AccessSnapshot = AccessSnapshot(),
    val telemetryOptIn: Boolean = false,
    /** Wallpaper-based dynamic color; off uses the Able brand palette. */
    val useDynamicColor: Boolean = true,
    /** Able haptic feedback (UX-003), on by default. */
    val hapticsEnabled: Boolean = true,
    /** Haptic strength (UX-003); OFF exactly when [hapticsEnabled] is false. */
    val hapticsIntensity: HapticsIntensity = HapticsIntensity.NORMAL,
    val appVersion: String = "",
    val rollbackAvailable: Boolean = false,
    val exportDraft: ExportDraft? = null,
    val exporting: Boolean = false,
    val importStage: ImportStage? = null,
    val message: SettingsMessage? = null,
    /** UPD-001: GitHub Releases check and install progress. */
    val updater: UpdaterState = UpdaterState(),
)

private data class LocalState(
    val exportDraft: ExportDraft? = null,
    val exporting: Boolean = false,
    val importStage: ImportStage? = null,
    val message: SettingsMessage? = null,
)

class SettingsViewModel(
    private val preferences: UserPreferencesRepository,
    private val capabilityStatus: CapabilityStatusRepository,
    private val homeLayout: HomeLayoutRepository,
    private val shizuku: ShizukuAdapter,
    private val language: AppLanguageController,
    private val backup: BackupRepository,
    private val appVersion: String,
    private val appContext: Context,
    private val updater: AppUpdater,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.userPreferencesFlow,
        capabilityStatus.access,
        backup.rollbackAvailable,
        local,
        updater.state,
    ) { prefs, access, rollback, l, update ->
        SettingsUiState(
            themeMode = prefs.themeMode,
            languageTag = prefs.languageOverride,
            access = access,
            telemetryOptIn = prefs.telemetryOptIn,
            useDynamicColor = prefs.useDynamicColor,
            hapticsEnabled = prefs.hapticsEnabled,
            hapticsIntensity = prefs.hapticsIntensity,
            appVersion = appVersion,
            rollbackAvailable = rollback,
            exportDraft = l.exportDraft,
            exporting = l.exporting,
            importStage = l.importStage,
            message = l.message,
            updater = update,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(appVersion = appVersion))

    init {
        syncLanguageFromSystem()
    }

    /** Called on resume: Android does not broadcast most grant changes. */
    fun refresh() {
        capabilityStatus.refresh()
        updater.onResume()
        syncLanguageFromSystem()
    }

    /**
     * The system "App languages" page can change Able's language without going through this
     * screen; the platform value wins so both selectors stay consistent.
     */
    private fun syncLanguageFromSystem() {
        viewModelScope.launch {
            val system = language.current()
            if (preferences.userPreferencesFlow.first().languageOverride != system) {
                preferences.setLanguageOverride(system)
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setLanguage(tag: String?) {
        viewModelScope.launch {
            preferences.setLanguageOverride(tag)
            if (!language.apply(tag)) message(SettingsMessage.LanguageNotApplied)
        }
    }

    /** Crash reports (PRIV-001, ADR-015): governs whether the ACRA dialog is offered after a crash. */
    fun setTelemetry(enabled: Boolean) {
        viewModelScope.launch { preferences.setTelemetryOptIn(enabled) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setUseDynamicColor(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setHapticsEnabled(enabled) }
    }

    fun setHapticsIntensity(intensity: HapticsIntensity) {
        viewModelScope.launch { preferences.setHapticsIntensity(intensity) }
    }

    /** UX-001: marks the tour as not completed so it can be replayed; the route then navigates. */
    fun replayTour(then: () -> Unit) {
        viewModelScope.launch {
            preferences.setTourCompleted(false)
            then()
        }
    }

    fun requestShizukuPermission() {
        if (!shizuku.requestPermission()) message(SettingsMessage.ShizukuUnavailable)
        capabilityStatus.refresh()
    }

    /** Opens the Shizuku app so the user can start the service or review Able's authorization. */
    fun openShizukuApp() {
        if (!shizuku.openShizukuApp(appContext)) message(SettingsMessage.ShizukuUnavailable)
    }

    fun resetHomeLayout() {
        viewModelScope.launch {
            homeLayout.resetToDefault()
            message(SettingsMessage.HomeLayoutReset)
        }
    }

    // --- Export ----------------------------------------------------------------------------

    fun startExport() {
        local.update { it.copy(exportDraft = ExportDraft()) }
        viewModelScope.launch {
            val summary = runCatching { backup.historySummary() }.getOrNull()
                ?: HistorySummary(sessions = 0, notes = 0, approxBytes = 0, oldestEpochMs = null, newestEpochMs = null)
            local.update { s -> s.copy(exportDraft = s.exportDraft?.copy(history = summary)) }
        }
    }

    fun toggleExportSection(section: BackupSection) = local.update { s ->
        val draft = s.exportDraft ?: return@update s
        val selected = if (section in draft.selected) draft.selected - section else draft.selected + section
        s.copy(exportDraft = draft.copy(selected = selected))
    }

    fun chooseExportDestination() = local.update { s ->
        val draft = s.exportDraft ?: return@update s
        if (draft.selected.isEmpty()) s else s.copy(exportDraft = draft.copy(awaitingDestination = true))
    }

    fun suggestedExportName(): String = backup.suggestedFileName()

    fun cancelExport() = local.update { it.copy(exportDraft = null) }

    fun onExportDestination(uri: Uri?) {
        val draft = local.value.exportDraft ?: return
        if (uri == null) {
            // Picker cancelled: return to the checklist so the user can retry or cancel.
            local.update { it.copy(exportDraft = draft.copy(awaitingDestination = false)) }
            return
        }
        local.update { it.copy(exportDraft = null, exporting = true) }
        viewModelScope.launch {
            val msg = when (val result = backup.export(uri, draft.selected)) {
                is ExportResult.Success -> SettingsMessage.Exported(result.fileName)
                ExportResult.WriteFailed -> SettingsMessage.ExportFailed
            }
            local.update { it.copy(exporting = false, message = msg) }
        }
    }

    // --- Import ----------------------------------------------------------------------------

    fun onImportFile(uri: Uri?) {
        if (uri == null) return
        local.update { it.copy(importStage = ImportStage.Reading) }
        viewModelScope.launch {
            val stage = when (val result = backup.read(uri)) {
                is ImportReadResult.Ready -> ImportStage.Preview(result.backup, result.preview)
                is ImportReadResult.Invalid -> ImportStage.Failed(result.error)
            }
            local.update { it.copy(importStage = stage) }
        }
    }

    fun selectImportMode(mode: ImportMode) = updatePreview { it.copy(mode = mode) }

    fun selectConflictPolicy(policy: ConflictPolicy) = updatePreview { it.copy(policy = policy) }

    private fun updatePreview(transform: (ImportStage.Preview) -> ImportStage.Preview) = local.update { s ->
        val stage = s.importStage as? ImportStage.Preview ?: return@update s
        s.copy(importStage = transform(stage))
    }

    fun confirmImport() {
        val stage = local.value.importStage as? ImportStage.Preview ?: return
        if (!stage.canApply) return
        val mode = stage.mode ?: return
        val policy = stage.policy ?: ConflictPolicy.KEEP_EXISTING
        local.update { it.copy(importStage = ImportStage.Applying) }
        viewModelScope.launch {
            val report = backup.apply(stage.backup, mode, policy)
            local.update {
                it.copy(importStage = if (report == null) ImportStage.RollbackFailed else ImportStage.Done(report, restore = false))
            }
        }
    }

    fun restorePrevious() {
        local.update { it.copy(importStage = ImportStage.Applying) }
        viewModelScope.launch {
            val report = backup.restoreRollback()
            local.update {
                if (report == null) {
                    it.copy(importStage = null, message = SettingsMessage.RestoreFailed)
                } else {
                    it.copy(importStage = ImportStage.Done(report, restore = true))
                }
            }
        }
    }

    fun dismissImport() = local.update {
        if (it.importStage is ImportStage.Applying || it.importStage is ImportStage.Reading) it else it.copy(importStage = null)
    }

    // --- Updates (UPD-001) -------------------------------------------------------------------

    fun checkForUpdates() = updater.check()

    fun setUpdateAutoCheck(enabled: Boolean) = updater.setAutoCheck(enabled)

    fun downloadUpdate(update: AvailableUpdate) = updater.downloadAndInstall(update)

    fun cancelUpdateDownload() = updater.cancelDownload()

    fun dismissUpdateInstall() = updater.dismissInstallResult()

    // --- Messages --------------------------------------------------------------------------

    fun message(message: SettingsMessage) = local.update { it.copy(message = message) }

    fun consumeMessage() = local.update { it.copy(message = null) }

    companion object {
        fun create(container: AbleContainer): SettingsViewModel {
            val context = container.appContext
            val language = AppLanguageController(context)
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            }.getOrNull().orEmpty()
            return SettingsViewModel(
                preferences = container.preferences,
                capabilityStatus = container.capabilityStatus,
                homeLayout = container.homeLayout,
                shizuku = container.shizuku,
                language = language,
                backup = BackupRepository(
                    context,
                    container.preferences,
                    container.database,
                    container.batteryProfiles,
                    container.actionLog,
                    language,
                ),
                appVersion = version,
                appContext = context,
                updater = AppUpdater.get(container),
            )
        }
    }
}
