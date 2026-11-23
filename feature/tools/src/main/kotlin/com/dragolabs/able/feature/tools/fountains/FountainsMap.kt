package com.dragolabs.able.feature.tools.fountains

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dragolabs.able.feature.tools.R
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/** Imperative zoom handle for the toolbar (keyboard / D-pad alternative to pinch). */
internal class FountainsMapHandle {
    var view: MapView? = null
    fun zoomIn() { view?.controller?.zoomIn() }
    fun zoomOut() { view?.controller?.zoomOut() }
}

/**
 * OpenStreetMap view (osmdroid) with drinking-water markers and the user's position. Same tile
 * source, user agent, private tile cache and lifecycle handling as the Fake GPS map. Attribution
 * is drawn by the caller in Compose.
 */
@Composable
internal fun FountainsMap(
    startCenter: GeoPos,
    startZoom: Double,
    fountains: List<Fountain>,
    selectedKey: String?,
    user: GeoPos?,
    camera: FountainsCamera?,
    darkTiles: Boolean,
    handle: FountainsMapHandle,
    onViewport: (BoundingBox, Double) -> Unit,
    onSelect: (Fountain) -> Unit,
    onTapEmpty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnViewport by rememberUpdatedState(onViewport)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnTapEmpty by rememberUpdatedState(onTapEmpty)
    val holder = remember { FountainOverlays() }

    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
            controller.setZoom(startZoom)
            controller.setCenter(GeoPoint(startCenter.latitude, startCenter.longitude))
            overlays.add(
                MapEventsOverlay(
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            currentOnTapEmpty()
                            return false
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean = false
                    },
                ),
            )
            overlays.add(holder.folder)
            fun report() {
                val box = boundingBox ?: return
                if (width == 0 || height == 0) return
                currentOnViewport(
                    BoundingBox(
                        south = box.latSouth.coerceIn(-90.0, 90.0),
                        west = box.lonWest.coerceIn(-180.0, 180.0),
                        north = box.latNorth.coerceIn(-90.0, 90.0),
                        east = box.lonEast.coerceIn(-180.0, 180.0),
                    ),
                    zoomLevelDouble,
                )
            }
            addMapListener(
                object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean { report(); return false }
                    override fun onZoom(event: ZoomEvent?): Boolean { report(); return false }
                },
            )
            addOnFirstLayoutListener { _, _, _, _, _ -> report() }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        handle.view = mapView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
            handle.view = null
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlayManager.tilesOverlay.setColorFilter(if (darkTiles) DarkTilesFilter else null)
            holder.sync(map, context, fountains, selectedKey, user) { currentOnSelect(it) }
            if (camera != null && camera.id != holder.lastCameraId) {
                holder.lastCameraId = camera.id
                val point = GeoPoint(camera.target.latitude, camera.target.longitude)
                if (camera.zoom != null) {
                    map.controller.animateTo(point, maxOf(camera.zoom, map.zoomLevelDouble), CAMERA_ANIMATION_MS)
                } else {
                    map.controller.animateTo(point)
                }
            }
        },
    )
}

/** Keeps the markers so updates only rebuild what changed. */
private class FountainOverlays {
    val folder = FolderOverlay()
    var lastCameraId: Long = -1
    private var lastFountains: List<Fountain>? = null
    private var lastSelected: String? = null
    private val markers = HashMap<String, Marker>()
    private var userMarker: Marker? = null

    fun sync(
        map: MapView,
        context: Context,
        fountains: List<Fountain>,
        selectedKey: String?,
        user: GeoPos?,
        onSelect: (Fountain) -> Unit,
    ) {
        val normal = ContextCompat.getDrawable(context, R.drawable.fountains_marker)
        val selectedIcon = ContextCompat.getDrawable(context, R.drawable.fountains_marker_selected)
        if (fountains != lastFountains) {
            lastFountains = fountains
            folder.items.clear()
            markers.clear()
            fountains.forEach { f ->
                val marker = Marker(map).apply {
                    position = GeoPoint(f.position.latitude, f.position.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = if (f.key == selectedKey) selectedIcon else normal
                    title = f.name
                    setInfoWindow(null)
                    setOnMarkerClickListener { _, _ -> onSelect(f); true }
                }
                markers[f.key] = marker
                folder.add(marker)
            }
            lastSelected = selectedKey
            bringToFront(selectedKey)
        } else if (selectedKey != lastSelected) {
            lastSelected?.let { markers[it]?.icon = normal }
            selectedKey?.let { markers[it]?.icon = selectedIcon }
            lastSelected = selectedKey
            bringToFront(selectedKey)
        }

        if (user != null) {
            val marker = userMarker ?: Marker(map).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = ContextCompat.getDrawable(context, R.drawable.fountains_user_dot)
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> false }
                userMarker = this
            }
            marker.position = GeoPoint(user.latitude, user.longitude)
            if (marker !in map.overlays) map.overlays.add(marker)
        } else {
            userMarker?.let { map.overlays.remove(it) }
        }
        map.invalidate()
    }

    private fun bringToFront(key: String?) {
        val marker = key?.let { markers[it] } ?: return
        folder.remove(marker)
        folder.add(marker)
    }
}

/** OSM tile usage policy: identify the app; keep the cache private to the app (no storage permission). */
private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    config.load(context, context.getSharedPreferences(OSMDROID_PREFS, Context.MODE_PRIVATE))
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    config.userAgentValue = if (version != null) "${context.packageName}/$version" else context.packageName
    val base = File(context.cacheDir, "osmdroid")
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tiles")
}

/** Inverted, slightly dimmed tiles for dark theme (same filter as the Fake GPS map). */
private val DarkTilesFilter = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            -0.85f, 0f, 0f, 0f, 230f,
            0f, -0.85f, 0f, 0f, 230f,
            0f, 0f, -0.85f, 0f, 235f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

private const val OSMDROID_PREFS = "able_osmdroid"
private const val CAMERA_ANIMATION_MS = 600L
