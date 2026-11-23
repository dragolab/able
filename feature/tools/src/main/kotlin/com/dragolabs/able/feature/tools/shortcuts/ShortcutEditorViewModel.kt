package com.dragolabs.able.feature.tools.shortcuts

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.data.shortcut.GlyphSize
import com.dragolabs.able.core.data.shortcut.IconShape
import com.dragolabs.able.core.data.shortcut.IconSource
import com.dragolabs.able.core.data.shortcut.ShortcutMeta
import com.dragolabs.able.core.data.shortcut.ShortcutTarget
import com.dragolabs.able.core.data.shortcut.encode
import com.dragolabs.able.core.data.shortcut.meta
import com.dragolabs.able.core.data.shortcut.normalizeWebUrl
import com.dragolabs.able.core.data.shortcut.target
import com.dragolabs.able.core.model.ShortcutCollection
import com.dragolabs.able.core.model.ShortcutIconConfig
import com.dragolabs.able.core.model.ShortcutItem
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.activities.ActivityCatalog
import com.dragolabs.able.feature.tools.activities.AppActivities
import com.dragolabs.able.feature.tools.common.UiMessage
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EditorStep { TYPE, TARGET, CUSTOMIZE }

/** Launcher pin flow state shown in the editor (US-SHORT-01: never claim an icon exists without evidence). */
enum class PinState {
    IDLE,

    /** Android forwarded the request; waiting for the launcher's dialog. */
    WAITING,

    /** The launcher reported the shortcut as pinned (callback or read-back). */
    CONFIRMED,

    /** An already pinned shortcut was updated in place. */
    UPDATED,

    /** Back in Able without confirmation: cancelled, or the launcher does not report. */
    NOT_CONFIRMED,
}

@Immutable
data class ShortcutEditorUiState(
    val loading: Boolean = false,
    val isExisting: Boolean = false,
    val definitionId: String = UUID.randomUUID().toString(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val step: EditorStep = EditorStep.TYPE,
    val kind: TargetKind? = null,
    val target: ShortcutTarget? = null,
    val label: String = "",
    val meta: ShortcutMeta = ShortcutMeta(),
    val backgroundArgb: Int? = null,
    val collectionId: String? = null,
    val collections: List<ShortcutCollection> = emptyList(),
    val sortOrder: Int = 0,
    /** True while the user re-picks the target from the customize step; Back returns there. */
    val changingTarget: Boolean = false,
    // Pickers
    val apps: List<InstalledApp>? = null,
    val activities: List<AppActivities>? = null,
    val settings: List<String>? = null,
    val pickerQuery: String = "",
    val urlInput: String = "",
    val urlError: Boolean = false,
    // Validation and pinning
    val targetProblem: TargetProblem? = null,
    val pinSupported: Boolean = true,
    val pinState: PinState = PinState.IDLE,
    val saved: Boolean = false,
    val busy: Boolean = false,
    val message: UiMessage? = null,
) {
    val hasTargetDrawable: Boolean get() = target is ShortcutTarget.App || target is ShortcutTarget.Activity
    val canFinish: Boolean get() = target != null && label.isNotBlank() && !busy
    val iconSpec: IconSpec get() = IconSpec(target, meta, backgroundArgb)
}

class ShortcutEditorViewModel(
    private val repository: ShortcutRepository,
    private val launcher: ShortcutLauncher,
    private val catalog: ActivityCatalog,
    private val cleanupScope: CoroutineScope,
    editId: String?,
    initialTarget: ShortcutTarget?,
    initialLabel: String?,
    initialCollectionId: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShortcutEditorUiState(pinSupported = launcher.isPinSupported(), collectionId = initialCollectionId))
    val uiState: StateFlow<ShortcutEditorUiState> = _uiState.asStateFlow()

    /** Image assets imported in this session; removed on close unless the saved definition uses them. */
    private val importedAssets = mutableSetOf<String>()
    private var originalAsset: String? = null
    private var readBackJob: Job? = null

    init {
        viewModelScope.launch { repository.collections.collect { list -> _uiState.update { it.copy(collections = list.sortedBy { c -> c.sortOrder }) } } }
        viewModelScope.launch {
            launcher.pinCallbacks().collect { id ->
                if (id == launcherShortcutId(_uiState.value.definitionId)) markConfirmed()
            }
        }
        when {
            editId != null -> loadExisting(editId)
            initialTarget != null -> pickTarget(initialTarget, initialLabel, jumpToCustomize = true)
        }
    }

    private fun loadExisting(id: String) {
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val item = repository.get(id)
            val target = item?.target()
            if (item == null) {
                _uiState.update { it.copy(loading = false, message = UiMessage(R.string.shortcuts_editor_missing)) }
                return@launch
            }
            originalAsset = item.customIconFile()
            _uiState.update {
                it.copy(
                    loading = false,
                    isExisting = true,
                    definitionId = item.id,
                    createdAtMs = item.createdAtEpochMs,
                    step = EditorStep.CUSTOMIZE,
                    kind = target?.kind,
                    target = target,
                    label = item.label,
                    meta = item.meta,
                    backgroundArgb = parseArgbHex(item.iconConfig.backgroundColorHex),
                    collectionId = item.collectionId,
                    sortOrder = item.sortOrder,
                    targetProblem = if (target == null) TargetProblem.INVALID else null,
                    saved = true,
                )
            }
            target?.let { checkTarget(it) }
        }
    }

    // Steps -----------------------------------------------------------------------------------

    fun chooseKind(kind: TargetKind) {
        _uiState.update { it.copy(kind = kind, step = EditorStep.TARGET, pickerQuery = "", urlError = false) }
        viewModelScope.launch {
            when (kind) {
                TargetKind.APP -> if (_uiState.value.apps == null) {
                    val apps = runCatching { launcher.launcherApps() }.getOrDefault(emptyList())
                    _uiState.update { it.copy(apps = apps) }
                }
                TargetKind.ACTIVITY -> if (_uiState.value.activities == null) {
                    val activities = runCatching { catalog.load() }.getOrDefault(emptyList())
                    _uiState.update { it.copy(activities = activities) }
                }
                TargetKind.SETTINGS -> if (_uiState.value.settings == null) {
                    val settings = runCatching { launcher.availableSettings() }.getOrDefault(emptyList())
                    _uiState.update { it.copy(settings = settings) }
                }
                else -> Unit
            }
        }
    }

    /** From the customize step: choose a different target type/target. */
    fun changeTarget() = _uiState.update { it.copy(step = EditorStep.TYPE, changingTarget = true, pinState = PinState.IDLE) }

    /** Returns false when there is nothing to go back to (the caller closes the editor). */
    fun back(): Boolean {
        val state = _uiState.value
        val previous = when {
            state.pinState == PinState.WAITING -> {
                _uiState.update { it.copy(pinState = PinState.NOT_CONFIRMED) }
                return true
            }
            state.changingTarget && state.step != EditorStep.CUSTOMIZE && state.target != null -> {
                _uiState.update { it.copy(step = EditorStep.CUSTOMIZE, changingTarget = false, kind = state.target.kind) }
                return true
            }
            state.step == EditorStep.CUSTOMIZE && !state.isExisting -> EditorStep.TARGET
            state.step == EditorStep.TARGET -> EditorStep.TYPE
            else -> return false
        }
        _uiState.update { it.copy(step = previous, pinState = PinState.IDLE) }
        return true
    }

    fun setPickerQuery(query: String) = _uiState.update { it.copy(pickerQuery = query) }

    fun setUrlInput(input: String) = _uiState.update { it.copy(urlInput = input, urlError = false) }

    fun submitUrl() {
        val url = normalizeWebUrl(_uiState.value.urlInput)
        if (url == null) {
            _uiState.update { it.copy(urlError = true) }
            return
        }
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.")
        pickTarget(ShortcutTarget.Website(url), host)
    }

    /** SAF result: keeps a persistable read grant so the home-screen shortcut keeps working. */
    fun onDocumentPicked(uri: Uri?, isFolder: Boolean, mimeType: String?) {
        if (uri == null) return
        if (!launcher.persistReadPermission(uri)) {
            _uiState.update { it.copy(message = UiMessage(R.string.shortcuts_problem_permission)) }
            return
        }
        pickTarget(ShortcutTarget.Document(uri.toString(), mimeType, isFolder), null)
    }

    /** Selects a target. [suggestedLabel] null → Able asks Android for the target's own label. */
    fun pickTarget(target: ShortcutTarget, suggestedLabel: String?, jumpToCustomize: Boolean = true) {
        _uiState.update {
            it.copy(
                target = target,
                kind = target.kind,
                label = suggestedLabel?.take(ShortcutLauncher.MAX_LONG_LABEL) ?: it.label,
                step = if (jumpToCustomize) EditorStep.CUSTOMIZE else it.step,
                changingTarget = false,
                meta = it.meta.copy(source = if (it.meta.source == IconSource.IMAGE || it.meta.source == IconSource.TEXT) it.meta.source else IconSource.TARGET),
                pinState = PinState.IDLE,
                targetProblem = null,
            )
        }
        viewModelScope.launch {
            if (suggestedLabel == null) {
                launcher.systemLabel(target)?.let { label -> _uiState.update { it.copy(label = label.take(ShortcutLauncher.MAX_LONG_LABEL)) } }
            }
            checkTarget(target)
        }
    }

    private suspend fun checkTarget(target: ShortcutTarget) {
        val problem = launcher.problemWith(target)
        _uiState.update { if (it.target == target) it.copy(targetProblem = problem) else it }
    }

    // Customization ---------------------------------------------------------------------------

    fun setLabel(label: String) = _uiState.update { it.copy(label = label.take(ShortcutLauncher.MAX_LONG_LABEL)) }

    fun setSource(source: IconSource) = _uiState.update {
        val value = when (source) {
            IconSource.MATERIAL -> it.meta.value.takeIf { v -> it.meta.source == IconSource.MATERIAL && v != null } ?: materialIconCatalog.first().first
            IconSource.TEXT -> it.meta.value.takeIf { _ -> it.meta.source == IconSource.TEXT } ?: clampIconText(it.label.take(1).uppercase())
            IconSource.IMAGE -> it.meta.value.takeIf { _ -> it.meta.source == IconSource.IMAGE }
            IconSource.TARGET -> null
        }
        it.copy(meta = it.meta.copy(source = source, value = value))
    }

    fun setMaterialIcon(key: String) = _uiState.update { it.copy(meta = it.meta.copy(source = IconSource.MATERIAL, value = key)) }

    fun setIconText(text: String) = _uiState.update { it.copy(meta = it.meta.copy(source = IconSource.TEXT, value = clampIconText(text))) }

    fun onImagePicked(uri: Uri?) {
        if (uri == null) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val name = repository.importIconImage(uri)
            if (name == null) {
                _uiState.update { it.copy(busy = false, message = UiMessage(R.string.shortcuts_image_failed)) }
            } else {
                importedAssets += name
                _uiState.update { it.copy(busy = false, meta = it.meta.copy(source = IconSource.IMAGE, value = name)) }
            }
        }
    }

    fun setBackground(argb: Int?) = _uiState.update { it.copy(backgroundArgb = argb) }
    fun setShape(shape: IconShape) = _uiState.update { it.copy(meta = it.meta.copy(shape = shape)) }
    fun setGlyphSize(size: GlyphSize) = _uiState.update { it.copy(meta = it.meta.copy(glyphSize = size)) }
    fun setShowLabel(show: Boolean) = _uiState.update { it.copy(meta = it.meta.copy(showLabel = show)) }
    fun setFavorite(favorite: Boolean) = _uiState.update { it.copy(meta = it.meta.copy(favorite = favorite)) }
    fun setCollection(id: String?) = _uiState.update { it.copy(collectionId = id) }

    fun createCollection(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val created = repository.createCollection(name)
            _uiState.update { it.copy(collectionId = created.id) }
        }
    }

    // Save and pin ----------------------------------------------------------------------------

    private fun buildItem(state: ShortcutEditorUiState, pinRequestAt: Long?): ShortcutItem? {
        val target = state.target ?: return null
        val encoded = target.encode()
        val meta = state.meta.copy(lastPinRequestAtMs = pinRequestAt ?: state.meta.lastPinRequestAtMs)
        return ShortcutItem(
            id = state.definitionId,
            label = state.label.trim(),
            targetType = encoded.type,
            targetData = encoded.data,
            targetPackage = encoded.packageName,
            collectionId = state.collectionId,
            iconConfig = ShortcutIconConfig(meta.iconType, meta.encode(), state.backgroundArgb?.let(::argbToHex)),
            sortOrder = state.sortOrder,
            isPinnedToLauncher = state.pinState == PinState.CONFIRMED || state.pinState == PinState.UPDATED ||
                launcherShortcutId(state.definitionId) in launcher.pinnedIds(),
            createdAtEpochMs = state.createdAtMs,
        )
    }

    private suspend fun persist(pinRequestAt: Long?): Boolean {
        val state = _uiState.value
        val item = buildItem(state, pinRequestAt) ?: return false
        runCatching { repository.save(item) }.onFailure {
            _uiState.update { s -> s.copy(message = UiMessage(R.string.shortcuts_save_failed)) }
            return false
        }
        val previousAsset = originalAsset
        originalAsset = item.customIconFile()
        if (previousAsset != null && previousAsset != originalAsset) repository.deleteIconAssetIfUnused(previousAsset)
        _uiState.update { it.copy(saved = true, isExisting = true, meta = item.meta) }
        return true
    }

    /** Saves the definition in Able without contacting the launcher. */
    fun saveOnly() {
        val state = _uiState.value
        if (!state.canFinish) return
        viewModelScope.launch {
            if (persist(null)) _uiState.update { it.copy(message = UiMessage(R.string.shortcuts_saved)) }
        }
    }

    /**
     * Saves the definition and asks the launcher to pin [icon] (the previewed bitmap).
     * Unsupported/broken targets are rejected before pinning with a reason (SHORT-002).
     */
    fun pin(icon: Bitmap?) {
        val state = _uiState.value
        val target = state.target ?: return
        if (!state.canFinish || icon == null) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val problem = launcher.problemWith(target)
            if (problem != null) {
                _uiState.update { it.copy(busy = false, targetProblem = problem, message = UiMessage(problem.messageRes())) }
                return@launch
            }
            if (!launcher.isPinSupported()) {
                persist(null)
                _uiState.update { it.copy(busy = false, pinSupported = false, message = UiMessage(R.string.shortcuts_pin_unsupported_saved)) }
                return@launch
            }
            if (!persist(System.currentTimeMillis())) {
                _uiState.update { it.copy(busy = false) }
                return@launch
            }
            val shortLabel = if (state.meta.showLabel) state.label.trim() else HIDDEN_LABEL
            val result = launcher.requestPin(state.definitionId, target, shortLabel, state.label.trim(), icon)
            when (result) {
                PinRequestResult.REQUESTED -> _uiState.update { it.copy(busy = false, pinState = PinState.WAITING) }
                PinRequestResult.UPDATED -> {
                    _uiState.update { it.copy(busy = false, pinState = PinState.UPDATED) }
                    persist(null)
                }
                PinRequestResult.UNSUPPORTED -> _uiState.update {
                    it.copy(busy = false, pinSupported = false, message = UiMessage(R.string.shortcuts_pin_unsupported_saved))
                }
                PinRequestResult.REJECTED -> _uiState.update { it.copy(busy = false, message = UiMessage(R.string.shortcuts_pin_rejected)) }
            }
        }
    }

    /** Called when Able returns to the foreground; reads back the launcher's pinned list. */
    fun onResumed() {
        if (_uiState.value.pinState != PinState.WAITING) return
        readBackJob?.cancel()
        readBackJob = viewModelScope.launch {
            // Give the launcher a moment to deliver its callback / persist the pin.
            repeat(READ_BACK_ATTEMPTS) {
                delay(READ_BACK_INTERVAL_MS)
                if (_uiState.value.pinState != PinState.WAITING) return@launch
                if (launcherShortcutId(_uiState.value.definitionId) in launcher.pinnedIds()) {
                    markConfirmed()
                    return@launch
                }
            }
            _uiState.update { if (it.pinState == PinState.WAITING) it.copy(pinState = PinState.NOT_CONFIRMED) else it }
        }
    }

    private fun markConfirmed() {
        if (_uiState.value.pinState == PinState.CONFIRMED) return
        _uiState.update { it.copy(pinState = PinState.CONFIRMED) }
        viewModelScope.launch { persist(null) }
    }

    fun dismissPinResult() = _uiState.update { it.copy(pinState = PinState.IDLE) }

    fun tryTarget() {
        val target = _uiState.value.target ?: return
        if (!launcher.open(target)) _uiState.update { it.copy(message = UiMessage(R.string.shortcuts_open_failed)) }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** Removes imported images that the saved definition does not use. */
    fun discardUnsavedAssets() {
        val keep = originalAsset
        val toCheck = importedAssets.filter { it != keep }
        importedAssets.clear()
        if (toCheck.isEmpty()) return
        cleanupScope.launch { toCheck.forEach { repository.deleteIconAssetIfUnused(it) } }
    }

    override fun onCleared() {
        discardUnsavedAssets()
    }

    private companion object {
        /** Launchers require a non-empty short label; a no-break space shows as no text on most launchers. */
        const val HIDDEN_LABEL = " "
        const val READ_BACK_ATTEMPTS = 4
        const val READ_BACK_INTERVAL_MS = 500L
    }
}

internal fun TargetProblem.messageRes(): Int = when (this) {
    TargetProblem.APP_MISSING -> R.string.shortcuts_problem_app
    TargetProblem.ACTIVITY_MISSING -> R.string.shortcuts_problem_activity
    TargetProblem.SETTINGS_MISSING -> R.string.shortcuts_problem_settings
    TargetProblem.NO_VIEWER -> R.string.shortcuts_problem_viewer
    TargetProblem.NO_BROWSER -> R.string.shortcuts_problem_browser
    TargetProblem.PERMISSION_LOST -> R.string.shortcuts_problem_permission
    TargetProblem.INVALID -> R.string.shortcuts_problem_invalid
}
