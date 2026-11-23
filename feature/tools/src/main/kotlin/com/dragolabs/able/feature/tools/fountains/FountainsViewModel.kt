package com.dragolabs.able.feature.tools.fountains

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.capability.LocationAdapter
import com.dragolabs.able.core.common.AbleResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Where the area lookup stands. */
enum class AreaStatus { WAITING, LOADING, LOADED, ZOOM_IN, OFFLINE, BUSY, FAILED }

/** One-shot snackbar messages. */
enum class FountainsMessage { PERMISSION_DENIED, LOCATION_OFF, LOCATION_FAILED, RETRY_LATER }

data class FountainsCamera(val target: GeoPos, val zoom: Double?, val id: Long)

data class FountainsUiState(
    val startCenter: GeoPos,
    val startZoom: Double,
    val status: AreaStatus = AreaStatus.WAITING,
    /** All drinking-water points of the loaded area (unfiltered). */
    val fountains: List<Fountain> = emptyList(),
    val filters: FountainFilters = FountainFilters(),
    /** Filtered points, nearest to [origin] first. */
    val nearest: List<NearbyFountain> = emptyList(),
    /** The user's position when known, otherwise the map centre. */
    val origin: GeoPos? = null,
    val originIsUser: Boolean = false,
    val user: GeoPos? = null,
    val locating: Boolean = false,
    val locationPermission: Boolean = false,
    val selectedKey: String? = null,
    val fetchedAtMs: Long? = null,
    val fromCache: Boolean = false,
    val camera: FountainsCamera? = null,
    val message: FountainsMessage? = null,
) {
    val visible: List<Fountain> get() = fountains.filter(filters::matches)
    val selected: NearbyFountain? get() = selectedKey?.let { key -> nearest.firstOrNull { it.fountain.key == key } }
        ?: selectedKey?.let { key ->
            fountains.firstOrNull { it.key == key }?.let { f ->
                origin?.let { FountainGeo.nearest(it, listOf(f)).first() } ?: NearbyFountain(f, Double.NaN, Double.NaN)
            }
        }
}

/**
 * Fountains tool (MAP-001). Debounces map movements, throttles Overpass requests (at most one
 * every [MIN_REQUEST_INTERVAL_MS], longer back-off after a busy answer) and reuses cached areas.
 */
internal class FountainsViewModel(
    private val appContext: Context,
    private val repository: FountainsRepository,
    private val location: LocationAdapter,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(initialState())
    val uiState: StateFlow<FountainsUiState> = _state.asStateFlow()

    private var viewport: BoundingBox? = null
    private var loadedArea: BoundingBox? = null
    private var debounceJob: Job? = null
    private var lastRequestAtMs = 0L
    private var busyUntilMs = 0L
    private var cameraId = 0L
    private var autoLocated = false

    private fun initialState(): FountainsUiState {
        val lat = prefs.getFloat(KEY_LAT, Float.NaN)
        val lon = prefs.getFloat(KEY_LON, Float.NaN)
        val zoom = prefs.getFloat(KEY_ZOOM, Float.NaN)
        val saved = if (!lat.isNaN() && !lon.isNaN()) GeoPos(lat.toDouble(), lon.toDouble()) else null
        return FountainsUiState(
            startCenter = saved ?: ITALY_CENTER,
            startZoom = if (saved != null && !zoom.isNaN()) zoom.toDouble() else ITALY_ZOOM,
            locationPermission = hasPermission(),
        )
    }

    /** Called on resume: refresh the permission and, the first time it is granted, centre on the user. */
    fun refreshPermission() {
        val granted = hasPermission()
        _state.update { it.copy(locationPermission = granted) }
        if (granted && !autoLocated) {
            autoLocated = true
            locate(showErrors = false)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(locationPermission = granted) }
        if (granted) {
            autoLocated = true
            locate(showErrors = true)
        } else {
            _state.update { it.copy(message = FountainsMessage.PERMISSION_DENIED) }
        }
    }

    fun locateMe() = locate(showErrors = true)

    private fun locate(showErrors: Boolean) {
        if (!hasPermission()) {
            if (showErrors) _state.update { it.copy(message = FountainsMessage.PERMISSION_DENIED) }
            return
        }
        val enabled = appContext.getSystemService(LocationManager::class.java)?.isLocationEnabled ?: false
        if (!enabled) {
            if (showErrors) _state.update { it.copy(message = FountainsMessage.LOCATION_OFF) }
            return
        }
        // Already have a fix: centre immediately, refresh in the background.
        _state.value.user?.let { requestCamera(it, USER_ZOOM) }
        if (_state.value.locating) return
        viewModelScope.launch {
            _state.update { it.copy(locating = true) }
            val result = withTimeoutOrNull(LOCATE_TIMEOUT_MS) { location.getCurrentGpsPoint(label = "") }
            if (result is AbleResult.Success) {
                val user = GeoPos(result.data.latitude, result.data.longitude)
                _state.update { it.copy(locating = false, user = user) }
                recomputeNearest()
                requestCamera(user, USER_ZOOM)
            } else {
                _state.update { it.copy(locating = false) }
                if (showErrors) _state.update { it.copy(message = FountainsMessage.LOCATION_FAILED) }
            }
        }
    }

    /** Map moved or zoomed (every frame of a gesture); the lookup is debounced. */
    fun onViewport(box: BoundingBox, zoom: Double) {
        viewport = box
        recomputeNearest()
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            prefs.edit()
                .putFloat(KEY_LAT, box.center.latitude.toFloat())
                .putFloat(KEY_LON, box.center.longitude.toFloat())
                .putFloat(KEY_ZOOM, zoom.toFloat())
                .apply()
            loadArea(force = false, manual = false)
        }
    }

    fun refresh() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch { loadArea(force = true, manual = true) }
    }

    private suspend fun loadArea(force: Boolean, manual: Boolean) {
        val box = viewport ?: return
        if (FountainArea.isTooLarge(box)) {
            _state.update { it.copy(status = AreaStatus.ZOOM_IN) }
            return
        }
        val snapped = FountainArea.snap(box)
        val current = loadedArea
        if (!force && current != null && current.contains(snapped) && _state.value.status == AreaStatus.LOADED) return

        if (!force) {
            repository.cached(snapped)?.let { applySuccess(snapped, it); return }
        }
        val now = clock()
        if (!manual && now < busyUntilMs) {
            _state.update { it.copy(status = AreaStatus.BUSY) }
            return
        }
        val wait = lastRequestAtMs + MIN_REQUEST_INTERVAL_MS - now
        if (wait > 0) {
            if (manual) {
                _state.update { it.copy(message = FountainsMessage.RETRY_LATER) }
            }
            delay(wait)
        }
        _state.update { it.copy(status = AreaStatus.LOADING) }
        lastRequestAtMs = clock()
        when (val result = repository.load(snapped, force = force)) {
            is FountainsResult.Success -> applySuccess(snapped, result)
            FountainsResult.Busy -> {
                busyUntilMs = clock() + BUSY_BACKOFF_MS
                _state.update { it.copy(status = AreaStatus.BUSY) }
            }
            FountainsResult.Offline -> _state.update { it.copy(status = AreaStatus.OFFLINE) }
            FountainsResult.Failed -> _state.update { it.copy(status = AreaStatus.FAILED) }
        }
    }

    private fun applySuccess(area: BoundingBox, result: FountainsResult.Success) {
        loadedArea = area
        _state.update { s ->
            s.copy(
                status = AreaStatus.LOADED,
                fountains = result.fountains,
                fetchedAtMs = result.fetchedAtMs,
                fromCache = result.fromCache,
                selectedKey = s.selectedKey?.takeIf { key -> result.fountains.any { it.key == key } },
            )
        }
        recomputeNearest()
    }

    fun setBottleOnly(on: Boolean) {
        _state.update { it.copy(filters = it.filters.copy(bottleOnly = on)) }
        recomputeNearest()
    }

    fun setWheelchairOnly(on: Boolean) {
        _state.update { it.copy(filters = it.filters.copy(wheelchairOnly = on)) }
        recomputeNearest()
    }

    fun select(fountain: Fountain) {
        _state.update { it.copy(selectedKey = fountain.key) }
        requestCamera(fountain.position, zoom = null)
    }

    fun clearSelection() = _state.update { it.copy(selectedKey = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun recomputeNearest() {
        _state.update { s ->
            val origin = s.user ?: viewport?.center
            val filtered = s.fountains.filter(s.filters::matches)
            s.copy(
                origin = origin,
                originIsUser = s.user != null,
                nearest = origin?.let { FountainGeo.nearest(it, filtered, NEAREST_LIMIT) } ?: emptyList(),
            )
        }
    }

    private fun requestCamera(target: GeoPos, zoom: Double?) {
        cameraId++
        _state.update { it.copy(camera = FountainsCamera(target, zoom, cameraId)) }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        val ITALY_CENTER = GeoPos(42.5, 12.5)
        const val ITALY_ZOOM = 6.0
        const val USER_ZOOM = 16.0

        /** Zoom from which the area is usually small enough to query. */
        const val MIN_QUERY_ZOOM = 12.0
        const val DEBOUNCE_MS = 900L
        const val MIN_REQUEST_INTERVAL_MS = 4_000L
        const val BUSY_BACKOFF_MS = 60_000L
        private const val LOCATE_TIMEOUT_MS = 20_000L
        private const val NEAREST_LIMIT = 50
        private const val PREFS = "able_fountains"
        private const val KEY_LAT = "last_lat"
        private const val KEY_LON = "last_lon"
        private const val KEY_ZOOM = "last_zoom"
    }
}
