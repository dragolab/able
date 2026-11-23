package com.dragolabs.able.feature.tools.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.capability.LocationAdapter
import com.dragolabs.able.core.common.AbleError
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.core.database.dao.GpsDao
import com.dragolabs.able.core.database.entity.GpsPointEntity
import com.dragolabs.able.core.model.GpsPoint
import com.dragolabs.able.feature.tools.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class LocationPermissionStatus { Granted, ApproximateOnly, NotRequested, Denied }

enum class CaptureError { Permission, LocationOff, NoFix, Timeout, Generic }

sealed interface CaptureState {
    data object Idle : CaptureState
    data object Acquiring : CaptureState
    data class Captured(val point: GpsPoint) : CaptureState
    data class Failed(val error: CaptureError) : CaptureState
}

/** Editor for a new (captured) or existing point. `pointId == 0` means a new point. */
data class PointEditorState(
    val base: GpsPoint,
    val label: String,
    val tags: List<String>,
    val customTag: String = "",
    val notes: String,
    val labelError: Boolean = false,
) {
    val isNew: Boolean get() = base.id == 0L
}

sealed interface GpsSheetTarget {
    data object Captured : GpsSheetTarget
    data class Saved(val id: Long) : GpsSheetTarget
}

enum class GpsMessage { Saved, Updated, Deleted, SaveFailed }

data class GpsUiState(
    val permission: LocationPermissionStatus = LocationPermissionStatus.NotRequested,
    val locationEnabled: Boolean = true,
    val capture: CaptureState = CaptureState.Idle,
    val catalogLoaded: Boolean = false,
    val totalCount: Int = 0,
    val points: List<GpsPoint> = emptyList(),
    /** Preset tags plus every custom tag already used in the catalog. */
    val availableTags: List<String> = GpsTags.presets,
    val query: String = "",
    val tagFilter: String? = null,
    val editor: PointEditorState? = null,
    val pendingDelete: GpsPoint? = null,
    val sheetTarget: GpsSheetTarget? = null,
    val sheetPoint: GpsPoint? = null,
    val message: GpsMessage? = null,
)

private data class LocalState(
    val permission: LocationPermissionStatus,
    val locationEnabled: Boolean,
    val capture: CaptureState = CaptureState.Idle,
    val query: String = "",
    val tagFilter: String? = null,
    val editor: PointEditorState? = null,
    val pendingDelete: GpsPoint? = null,
    val sheetTarget: GpsSheetTarget? = null,
    val message: GpsMessage? = null,
)

/**
 * GPS coordinate saver and catalog (GPS-001, GPS-002). Location is read only on explicit user
 * request through [LocationAdapter] (single fix, no background updates); points are stored in
 * the local Room database and searched offline.
 */
internal class GpsViewModel(
    private val appContext: Context,
    private val location: LocationAdapter,
    private val dao: GpsDao,
    /** Localized labels of the preset tags, so search matches what the user sees. */
    private val presetLabels: Map<String, String>,
) : ViewModel() {

    private var permissionDenied = false
    private var captureJob: Job? = null

    private val local = MutableStateFlow(
        LocalState(permission = readPermission(), locationEnabled = isLocationEnabled()),
    )

    private val allPoints = dao.getAllPoints()
        .map<List<GpsPointEntity>, List<GpsPoint>?> { list -> list.map { it.toDomain() } }
        .onStart { emit(null) }

    val uiState: StateFlow<GpsUiState> = combine(allPoints, local) { points, s ->
        val all = points.orEmpty()
        val usedTags = all.flatMap { it.tags }.filter { it.isNotBlank() }
        val available = (GpsTags.presets + usedTags.sortedBy { it.lowercase() }).distinct()
        val filter = s.tagFilter?.takeIf { it in available }
        val filtered = all.filter { point ->
            (filter == null || filter in point.tags) && matchesQuery(point, s.query)
        }
        val sheetPoint = when (val target = s.sheetTarget) {
            GpsSheetTarget.Captured -> (s.capture as? CaptureState.Captured)?.point
            is GpsSheetTarget.Saved -> all.firstOrNull { it.id == target.id }
            null -> null
        }
        GpsUiState(
            permission = s.permission,
            locationEnabled = s.locationEnabled,
            capture = s.capture,
            catalogLoaded = points != null,
            totalCount = all.size,
            points = filtered,
            availableTags = available,
            query = s.query,
            tagFilter = filter,
            editor = s.editor,
            pendingDelete = s.pendingDelete,
            sheetTarget = if (sheetPoint == null) null else s.sheetTarget,
            sheetPoint = sheetPoint,
            message = s.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GpsUiState(permission = local.value.permission, locationEnabled = local.value.locationEnabled),
    )

    /** Re-reads permission and location-services state (on resume: they may change in Settings). */
    fun refreshEnvironment() {
        local.update { it.copy(permission = readPermission(), locationEnabled = isLocationEnabled()) }
    }

    fun onPermissionResult(fineGranted: Boolean, coarseGranted: Boolean) {
        permissionDenied = !fineGranted && !coarseGranted
        refreshEnvironment()
        if (fineGranted) capture()
    }

    fun capture() {
        if (captureJob?.isActive == true) return
        refreshEnvironment()
        val state = local.value
        if (state.permission != LocationPermissionStatus.Granted) {
            local.update { it.copy(capture = CaptureState.Failed(CaptureError.Permission)) }
            return
        }
        if (!state.locationEnabled) {
            local.update { it.copy(capture = CaptureState.Failed(CaptureError.LocationOff)) }
            return
        }
        captureJob = viewModelScope.launch {
            local.update { it.copy(capture = CaptureState.Acquiring) }
            val result = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { location.getCurrentGpsPoint(label = "") }
            val next = when (result) {
                null -> CaptureState.Failed(CaptureError.Timeout)
                is AbleResult.Success -> CaptureState.Captured(result.data)
                is AbleResult.Failure -> CaptureState.Failed(
                    when (result.error) {
                        is AbleError.PermissionMissing -> CaptureError.Permission
                        is AbleError.Storage -> CaptureError.NoFix
                        else -> CaptureError.Generic
                    },
                )
                AbleResult.Loading -> CaptureState.Failed(CaptureError.Generic)
            }
            local.update { it.copy(capture = next) }
        }
    }

    fun cancelCapture() {
        captureJob?.cancel()
        captureJob = null
        local.update { it.copy(capture = CaptureState.Idle) }
    }

    fun dismissCaptureError() {
        local.update { if (it.capture is CaptureState.Failed) it.copy(capture = CaptureState.Idle) else it }
    }

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun onTagFilterChange(tag: String?) = local.update { it.copy(tagFilter = tag) }

    fun openSheet(target: GpsSheetTarget) = local.update { it.copy(sheetTarget = target) }

    fun closeSheet() = local.update { it.copy(sheetTarget = null) }

    fun openEditorForCaptured() {
        val point = (local.value.capture as? CaptureState.Captured)?.point ?: return
        val label = appContext.getString(R.string.gps_editor_label_default, formatTime(point.timestampEpochMs))
        local.update {
            it.copy(sheetTarget = null, editor = PointEditorState(base = point, label = label, tags = emptyList(), notes = ""))
        }
    }

    fun openEditor(point: GpsPoint) = local.update {
        it.copy(sheetTarget = null, editor = PointEditorState(base = point, label = point.label, tags = point.tags, notes = point.notes))
    }

    fun dismissEditor() = local.update { it.copy(editor = null) }

    fun onEditorLabelChange(value: String) = updateEditor { it.copy(label = value, labelError = false) }

    fun onEditorNotesChange(value: String) = updateEditor { it.copy(notes = value) }

    fun onEditorCustomTagChange(value: String) = updateEditor { it.copy(customTag = value) }

    fun onEditorToggleTag(tag: String) = updateEditor {
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }

    fun onEditorAddCustomTag() = updateEditor { editor ->
        val tag = GpsTags.sanitize(editor.customTag) ?: return@updateEditor editor.copy(customTag = "")
        editor.copy(tags = (editor.tags + tag).distinct(), customTag = "")
    }

    fun saveEditor() {
        val editor = local.value.editor ?: return
        val label = editor.label.trim()
        if (label.isEmpty()) {
            updateEditor { it.copy(labelError = true) }
            return
        }
        val pendingCustom = GpsTags.sanitize(editor.customTag)
        val tags = (editor.tags + listOfNotNull(pendingCustom)).mapNotNull { GpsTags.sanitize(it) }.distinct()
        val point = editor.base.copy(label = label, tags = tags, notes = editor.notes.trim())
        viewModelScope.launch {
            val ok = runCatching { dao.insertPoint(GpsPointEntity.fromDomain(point)) }.isSuccess
            local.update {
                if (!ok) {
                    it.copy(message = GpsMessage.SaveFailed)
                } else {
                    it.copy(
                        editor = null,
                        // A captured point is cleared once saved to avoid saving duplicates.
                        capture = if (editor.isNew) CaptureState.Idle else it.capture,
                        message = if (editor.isNew) GpsMessage.Saved else GpsMessage.Updated,
                    )
                }
            }
        }
    }

    fun requestDelete(point: GpsPoint) = local.update { it.copy(pendingDelete = point) }

    fun dismissDelete() = local.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val point = local.value.pendingDelete ?: return
        viewModelScope.launch {
            val ok = runCatching { dao.deletePointById(point.id) }.getOrDefault(0) > 0
            local.update {
                it.copy(
                    pendingDelete = null,
                    sheetTarget = if (ok) null else it.sheetTarget,
                    message = if (ok) GpsMessage.Deleted else GpsMessage.SaveFailed,
                )
            }
        }
    }

    fun consumeMessage() = local.update { it.copy(message = null) }

    private inline fun updateEditor(crossinline transform: (PointEditorState) -> PointEditorState) {
        local.update { s -> s.editor?.let { s.copy(editor = transform(it)) } ?: s }
    }

    private fun matchesQuery(point: GpsPoint, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return point.label.contains(q, ignoreCase = true) ||
            point.notes.contains(q, ignoreCase = true) ||
            point.reverseGeocodedAddress?.contains(q, ignoreCase = true) == true ||
            point.tags.any { tag ->
                tag.contains(q, ignoreCase = true) || presetLabels[tag]?.contains(q, ignoreCase = true) == true
            } ||
            GpsFormat.decimal(point).contains(q)
    }

    private fun readPermission(): LocationPermissionStatus {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return when {
            fine -> LocationPermissionStatus.Granted.also { permissionDenied = false }
            coarse -> LocationPermissionStatus.ApproximateOnly
            permissionDenied -> LocationPermissionStatus.Denied
            else -> LocationPermissionStatus.NotRequested
        }
    }

    private fun isLocationEnabled(): Boolean =
        appContext.getSystemService(LocationManager::class.java)?.isLocationEnabled ?: false

    private fun formatTime(epochMs: Long): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(epochMs))

    private companion object {
        const val CAPTURE_TIMEOUT_MS = 30_000L
    }
}
