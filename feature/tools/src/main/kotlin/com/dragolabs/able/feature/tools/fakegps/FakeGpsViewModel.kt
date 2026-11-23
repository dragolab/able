package com.dragolabs.able.feature.tools.fakegps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.capability.LocationAdapter
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.core.data.ActionLogRepository
import com.dragolabs.able.core.database.dao.GpsDao
import com.dragolabs.able.core.database.entity.GpsPointEntity
import com.dragolabs.able.core.model.ActionExecutionStatus
import com.dragolabs.able.core.model.GpsPoint
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.gps.GpsTags
import kotlinx.coroutines.CoroutineScope
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

enum class FakeGpsMessage {
    NEED_TARGET,
    NEED_ROUTE,
    NOT_MOCK_APP,
    STOPPED,
    MOCK_APP_SET,
    MOCK_APP_REJECTED,
    MOCK_APP_NOT_CONFIRMED,
    SHIZUKU_UNAVAILABLE,
    POINT_SAVED,
    SAVE_FAILED,
    ROUTE_FULL,
    LOCATION_FAILED,
    LOCATION_WHILE_RUNNING,
    PERMISSION_DENIED,
    LOCATION_OFF,
}

/** One-off camera move; [id] changes for every request so the same point can be re-centred. */
data class CameraRequest(val target: LatLon, val zoom: Double?, val id: Long)

data class SavePointDraft(
    val position: LatLon,
    val label: String,
    val tags: List<String> = emptyList(),
    val customTag: String = "",
    val labelError: Boolean = false,
)

data class FakeGpsUiState(
    val config: FakeGpsConfig = FakeGpsConfig(),
    val engine: EngineState = EngineState(),
    val setup: MockSetupState = MockSetupState(),
    val savedPoints: List<GpsPoint> = emptyList(),
    val savedLoaded: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    val camera: CameraRequest? = null,
    val searchOpen: Boolean = false,
    val searchText: String = "",
    val searchResult: LatLon? = null,
    val savePoint: SavePointDraft? = null,
    val availableTags: List<String> = GpsTags.presets,
    val locating: Boolean = false,
    val settingUpShizuku: Boolean = false,
    val message: FakeGpsMessage? = null,
)

private data class Local(
    val setup: MockSetupState,
    val camera: CameraRequest? = null,
    val searchOpen: Boolean = false,
    val searchText: String = "",
    val savePoint: SavePointDraft? = null,
    val locating: Boolean = false,
    val settingUpShizuku: Boolean = false,
    val message: FakeGpsMessage? = null,
)

/**
 * Fake GPS screen state (FGPS). The configuration lives in [FakeGpsSession] so the foreground
 * service can read it; saved points come from the GPS tool's catalog ([GpsDao]).
 */
internal class FakeGpsViewModel(
    private val appContext: Context,
    private val access: MockLocationAccess,
    private val location: LocationAdapter,
    private val dao: GpsDao,
    private val store: FakeGpsStore,
    private val actionLog: ActionLogRepository,
    private val logScope: CoroutineScope,
) : ViewModel() {

    private val local = MutableStateFlow(Local(setup = access.read()))
    private var cameraId = 0L

    private val savedPoints = dao.getAllPoints()
        .map<List<GpsPointEntity>, List<GpsPoint>?> { list -> list.map { it.toDomain() } }
        .onStart { emit(null) }

    init {
        FakeGpsSession.restoreOnce(store)
        // Test providers outlive a killed process; remove leftovers so the real location returns.
        FakeGpsService.clearStaleProviders(appContext)
        FakeGpsSession.config.value.target?.let { requestCamera(it, zoom = DEFAULT_ZOOM) }
        // Report the end of a session the user started (notification Stop included).
        viewModelScope.launch {
            var previous: EngineStatus = FakeGpsSession.engine.value.status
            FakeGpsSession.engine.collect { engine ->
                if (previous == EngineStatus.Running && engine.status == EngineStatus.Idle) {
                    local.update { it.copy(message = FakeGpsMessage.STOPPED) }
                }
                previous = engine.status
            }
        }
    }

    val uiState: StateFlow<FakeGpsUiState> = combine(
        FakeGpsSession.config,
        FakeGpsSession.engine,
        savedPoints,
        store.history,
        local,
    ) { config, engine, points, history, s ->
        val all = points.orEmpty()
        val usedTags = all.flatMap { it.tags }.filter { it.isNotBlank() }
        FakeGpsUiState(
            config = config,
            engine = engine,
            setup = s.setup,
            savedPoints = all,
            savedLoaded = points != null,
            history = history,
            camera = s.camera,
            searchOpen = s.searchOpen,
            searchText = s.searchText,
            searchResult = CoordinateParser.parse(s.searchText),
            savePoint = s.savePoint,
            availableTags = (GpsTags.presets + usedTags.sortedBy { it.lowercase() }).distinct(),
            locating = s.locating,
            settingUpShizuku = s.settingUpShizuku,
            message = s.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FakeGpsUiState(
            config = FakeGpsSession.config.value,
            engine = FakeGpsSession.engine.value,
            setup = local.value.setup,
            camera = local.value.camera,
        ),
    )

    /** Re-reads mock app, Developer options, Shizuku and permission state (on resume). */
    fun refreshSetup() = local.update { it.copy(setup = access.read()) }

    // region Position and map

    fun onMapTap(point: LatLon) {
        if (FakeGpsSession.config.value.mode == FakeMode.ROUTE) addWaypoint(point) else setTarget(point)
    }

    fun onMapLongPress(point: LatLon) = setTarget(point)

    fun setTarget(point: LatLon, center: Boolean = false) {
        updateConfig { it.copy(target = point) }
        if (FakeGpsSession.engine.value.status == EngineStatus.Running) store.addHistory(point)
        if (center) requestCamera(point, zoom = null)
    }

    /** Applies a saved point or history entry: route mode appends a waypoint, other modes move there. */
    fun applyPoint(point: LatLon) {
        if (FakeGpsSession.config.value.mode == FakeMode.ROUTE) {
            addWaypoint(point)
            requestCamera(point, zoom = null)
        } else {
            setTarget(point, center = true)
        }
    }

    fun centerOnFake() {
        val position = FakeGpsSession.engine.value.emitted ?: FakeGpsSession.config.value.target ?: return
        requestCamera(position, zoom = DEFAULT_ZOOM)
    }

    private fun requestCamera(point: LatLon, zoom: Double?) {
        cameraId++
        local.update { it.copy(camera = CameraRequest(point, zoom, cameraId)) }
    }

    /** Centres on the real location (single fix). While faking, Android would report the fake one. */
    fun locateMe() {
        refreshSetup()
        val setup = local.value.setup
        when {
            FakeGpsSession.engine.value.isActive -> {
                local.update { it.copy(message = FakeGpsMessage.LOCATION_WHILE_RUNNING) }
                return
            }
            !setup.fineLocationGranted -> {
                local.update { it.copy(message = FakeGpsMessage.PERMISSION_DENIED) }
                return
            }
            !setup.locationServicesOn -> {
                local.update { it.copy(message = FakeGpsMessage.LOCATION_OFF) }
                return
            }
        }
        if (local.value.locating) return
        viewModelScope.launch {
            local.update { it.copy(locating = true) }
            val result = withTimeoutOrNull(LOCATE_TIMEOUT_MS) { location.getCurrentGpsPoint(label = "") }
            local.update { it.copy(locating = false) }
            if (result is AbleResult.Success) {
                val real = LatLon(result.data.latitude, result.data.longitude)
                if (FakeGpsSession.config.value.target == null) updateConfig { it.copy(target = real) }
                requestCamera(real, zoom = DEFAULT_ZOOM)
            } else {
                local.update { it.copy(message = FakeGpsMessage.LOCATION_FAILED) }
            }
        }
    }

    // endregion

    // region Modes and settings

    fun setMode(mode: FakeMode) {
        FakeGpsSession.setJoystick(0f, 0f)
        updateConfig { config ->
            // Entering route mode with a position but no waypoints starts the route there.
            if (mode == FakeMode.ROUTE && config.waypoints.isEmpty() && config.target != null) {
                config.copy(mode = mode, waypoints = listOf(config.target))
            } else {
                config.copy(mode = mode)
            }
        }
    }

    fun setSpeed(speed: SpeedPreset) = updateConfig { it.copy(speed = speed) }

    fun setRepeat(repeat: RouteRepeat) = updateConfig { it.copy(repeat = repeat) }

    fun setJitter(enabled: Boolean) = updateConfig { it.copy(jitter = enabled) }

    fun setAltitude(meters: Double) = updateConfig {
        it.copy(altitudeMeters = meters.coerceIn(FakeGpsConfig.MIN_ALTITUDE_M, FakeGpsConfig.MAX_ALTITUDE_M))
    }

    fun setAccuracy(meters: Float) = updateConfig {
        it.copy(accuracyMeters = meters.coerceIn(FakeGpsConfig.MIN_ACCURACY_M, FakeGpsConfig.MAX_ACCURACY_M))
    }

    private fun addWaypoint(point: LatLon) {
        val config = FakeGpsSession.config.value
        if (config.waypoints.size >= FakeGpsConfig.MAX_WAYPOINTS) {
            local.update { it.copy(message = FakeGpsMessage.ROUTE_FULL) }
            return
        }
        updateConfig { it.copy(waypoints = it.waypoints + point) }
    }

    fun removeLastWaypoint() = updateConfig { it.copy(waypoints = it.waypoints.dropLast(1)) }

    fun clearWaypoints() = updateConfig { it.copy(waypoints = emptyList()) }

    fun restartRoute() = FakeGpsSession.restartRoute()

    fun onJoystick(x: Float, y: Float) = FakeGpsSession.setJoystick(x, y)

    /** Accessible joystick alternative: moves the position a few seconds' worth of travel. */
    fun nudge(bearingDegrees: Double) {
        val config = FakeGpsSession.config.value
        val from = config.target ?: return
        val distance = config.speed.metersPerSecond * NUDGE_SECONDS
        updateConfig { it.copy(target = GeoMath.destination(from, bearingDegrees, distance)) }
    }

    // endregion

    // region Engine

    fun start() {
        refreshSetup()
        val config = FakeGpsSession.config.value
        val setup = local.value.setup
        val message = when {
            config.mode == FakeMode.ROUTE && config.waypoints.size < 2 -> FakeGpsMessage.NEED_ROUTE
            config.mode != FakeMode.ROUTE && config.target == null -> FakeGpsMessage.NEED_TARGET
            !setup.fineLocationGranted -> FakeGpsMessage.PERMISSION_DENIED
            !setup.mockAppSelected -> FakeGpsMessage.NOT_MOCK_APP
            else -> null
        }
        if (message != null) {
            local.update { it.copy(message = message) }
            return
        }
        if (config.mode == FakeMode.ROUTE) FakeGpsSession.restartRoute()
        store.saveConfig(config)
        FakeGpsService.start(appContext)
    }

    fun stop() = FakeGpsService.stop(appContext)

    fun dismissFailure() {
        if (FakeGpsSession.engine.value.status is EngineStatus.Failed) {
            FakeGpsSession.updateEngine { EngineState() }
        }
    }

    fun allowWithShizuku() {
        if (local.value.settingUpShizuku) return
        viewModelScope.launch {
            local.update { it.copy(settingUpShizuku = true) }
            val result = access.allowWithShizuku()
            val message = when (result) {
                ShizukuMockResult.APPLIED -> FakeGpsMessage.MOCK_APP_SET
                ShizukuMockResult.REJECTED -> FakeGpsMessage.MOCK_APP_REJECTED
                ShizukuMockResult.NOT_CONFIRMED -> FakeGpsMessage.MOCK_APP_NOT_CONFIRMED
                ShizukuMockResult.UNAVAILABLE -> FakeGpsMessage.SHIZUKU_UNAVAILABLE
            }
            local.update { it.copy(settingUpShizuku = false, setup = access.read(), message = message) }
            if (result != ShizukuMockResult.UNAVAILABLE) {
                val status = when (result) {
                    ShizukuMockResult.APPLIED -> ActionExecutionStatus.SUCCESS
                    ShizukuMockResult.NOT_CONFIRMED -> ActionExecutionStatus.ATTEMPTED
                    else -> ActionExecutionStatus.FAILED
                }
                logScope.launch {
                    runCatching {
                        actionLog.log(
                            FakeGpsService.ACTION_ID_MOCK_APP,
                            status,
                            appContext.getString(R.string.fakegps_log_mock_app, result.name),
                            reversible = true,
                        )
                    }
                }
            }
        }
    }

    // endregion

    // region Search

    fun openSearch() = local.update { it.copy(searchOpen = true) }

    fun closeSearch() = local.update { it.copy(searchOpen = false) }

    fun onSearchText(text: String) = local.update { it.copy(searchText = text) }

    fun confirmSearch() {
        val point = CoordinateParser.parse(local.value.searchText) ?: return
        local.update { it.copy(searchOpen = false) }
        applyPoint(point)
        requestCamera(point, zoom = DEFAULT_ZOOM)
    }

    // endregion

    // region Saved points and history

    fun openSavePoint() {
        val position = FakeGpsSession.config.value.target ?: run {
            local.update { it.copy(message = FakeGpsMessage.NEED_TARGET) }
            return
        }
        val label = appContext.getString(R.string.fakegps_save_default_label, formatTime(System.currentTimeMillis()))
        local.update { it.copy(savePoint = SavePointDraft(position = position, label = label)) }
    }

    fun dismissSavePoint() = local.update { it.copy(savePoint = null) }

    fun onSaveLabel(value: String) = updateDraft { it.copy(label = value, labelError = false) }

    fun onSaveCustomTag(value: String) = updateDraft { it.copy(customTag = value) }

    fun onSaveToggleTag(tag: String) = updateDraft { it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag) }

    fun onSaveAddCustomTag() = updateDraft { draft ->
        val tag = GpsTags.sanitize(draft.customTag) ?: return@updateDraft draft.copy(customTag = "")
        draft.copy(tags = (draft.tags + tag).distinct(), customTag = "")
    }

    fun confirmSavePoint() {
        val draft = local.value.savePoint ?: return
        val label = draft.label.trim()
        if (label.isEmpty()) {
            updateDraft { it.copy(labelError = true) }
            return
        }
        val config = FakeGpsSession.config.value
        val tags = (draft.tags + listOfNotNull(GpsTags.sanitize(draft.customTag))).mapNotNull { GpsTags.sanitize(it) }.distinct()
        val point = GpsPoint(
            latitude = draft.position.latitude,
            longitude = draft.position.longitude,
            altitudeMeters = config.altitudeMeters,
            accuracyMeters = config.accuracyMeters,
            timestampEpochMs = System.currentTimeMillis(),
            label = label,
            // Make clear in the GPS catalog that this is not a measured fix.
            notes = appContext.getString(R.string.fakegps_saved_note),
            tags = tags,
        )
        viewModelScope.launch {
            val ok = runCatching { dao.insertPoint(GpsPointEntity.fromDomain(point)) }.isSuccess
            local.update {
                if (ok) it.copy(savePoint = null, message = FakeGpsMessage.POINT_SAVED) else it.copy(message = FakeGpsMessage.SAVE_FAILED)
            }
        }
    }

    fun clearHistory() = store.clearHistory()

    // endregion

    fun consumeMessage() = local.update { it.copy(message = null) }

    fun onPermissionDenied() = local.update { it.copy(setup = access.read(), message = FakeGpsMessage.PERMISSION_DENIED) }

    private fun updateConfig(transform: (FakeGpsConfig) -> FakeGpsConfig) {
        FakeGpsSession.updateConfig(transform)
        store.saveConfig(FakeGpsSession.config.value)
    }

    private inline fun updateDraft(crossinline transform: (SavePointDraft) -> SavePointDraft) {
        local.update { s -> s.savePoint?.let { s.copy(savePoint = transform(it)) } ?: s }
    }

    private fun formatTime(epochMs: Long): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(epochMs))

    companion object {
        const val DEFAULT_ZOOM = 16.0
        private const val LOCATE_TIMEOUT_MS = 30_000L
        private const val NUDGE_SECONDS = 5.0
    }
}
