package com.dragolabs.able.feature.tools.fakegps

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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dragolabs.able.feature.tools.R
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** Imperative handle so the screen's toolbar can zoom the map (keyboard / D-pad alternative to pinch). */
internal class MapHandle {
    var view: MapView? = null
    fun zoomIn() { view?.controller?.zoomIn() }
    fun zoomOut() { view?.controller?.zoomOut() }
}

/**
 * OpenStreetMap view (osmdroid) wrapped for Compose. Tiles come from the OSM standard tile
 * server, identified by Able's package name as user agent (OSM tile usage policy) and cached in
 * the app cache directory. Attribution is drawn by the caller in Compose.
 */
@Composable
internal fun FakeGpsMap(
    target: LatLon?,
    emitted: LatLon?,
    waypoints: List<LatLon>,
    closedRoute: Boolean,
    showRoute: Boolean,
    camera: CameraRequest?,
    darkTiles: Boolean,
    handle: MapHandle,
    onTap: (LatLon) -> Unit,
    onLongPress: (LatLon) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val holder = remember { MapOverlays() }

    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            isHorizontalMapRepetitionEnabled = true
            isVerticalMapRepetitionEnabled = false
            setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
            val start = target ?: emitted
            controller.setZoom(if (start != null) FakeGpsViewModel.DEFAULT_ZOOM else 4.0)
            controller.setCenter(start?.toGeoPoint() ?: GeoPoint(DEFAULT_LAT, DEFAULT_LON))
            overlays.add(
                MapEventsOverlay(
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            currentOnTap(LatLon(p.latitude, GeoMath.normalizeLongitude(p.longitude)))
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean {
                            currentOnLongPress(LatLon(p.latitude, GeoMath.normalizeLongitude(p.longitude)))
                            return true
                        }
                    },
                ),
            )
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
            holder.sync(map, context, target, emitted, waypoints, closedRoute, showRoute)
            if (camera != null && camera.id != holder.lastCameraId) {
                holder.lastCameraId = camera.id
                if (camera.zoom != null) {
                    map.controller.animateTo(camera.target.toGeoPoint(), camera.zoom, CAMERA_ANIMATION_MS)
                } else {
                    map.controller.animateTo(camera.target.toGeoPoint())
                }
            }
        },
    )
}

/** Keeps references to the overlays so updates replace them instead of stacking new ones. */
private class MapOverlays {
    var lastCameraId: Long = -1
    private var targetMarker: Marker? = null
    private var liveMarker: Marker? = null
    private var routeLine: Polyline? = null
    private val waypointMarkers = mutableListOf<Marker>()
    private var lastWaypoints: List<LatLon>? = null
    private var lastClosed: Boolean? = null
    private var lastShowRoute: Boolean? = null

    fun sync(
        map: MapView,
        context: Context,
        target: LatLon?,
        emitted: LatLon?,
        waypoints: List<LatLon>,
        closed: Boolean,
        showRoute: Boolean,
    ) {
        if (waypoints != lastWaypoints || closed != lastClosed || showRoute != lastShowRoute) {
            lastWaypoints = waypoints
            lastClosed = closed
            lastShowRoute = showRoute
            routeLine?.let { map.overlays.remove(it) }
            waypointMarkers.forEach { map.overlays.remove(it) }
            waypointMarkers.clear()
            routeLine = null
            if (showRoute && waypoints.isNotEmpty()) {
                val points = waypoints.map { it.toGeoPoint() }.toMutableList()
                if (closed && waypoints.size > 2) points += waypoints.first().toGeoPoint()
                if (points.size >= 2) {
                    routeLine = Polyline(map).apply {
                        setPoints(points)
                        outlinePaint.color = ROUTE_COLOR
                        outlinePaint.strokeWidth = 10f
                        setOnClickListener { _, _, _ -> false }
                    }.also { map.overlays.add(1, it) }
                }
                val icon = ContextCompat.getDrawable(context, R.drawable.fakegps_waypoint)
                waypoints.forEachIndexed { index, point ->
                    waypointMarkers += Marker(map).apply {
                        position = point.toGeoPoint()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        this.icon = icon
                        title = context.getString(R.string.fakegps_waypoint_n, index + 1)
                        setInfoWindow(null)
                        setOnMarkerClickListener { _, _ -> false }
                    }.also { map.overlays.add(it) }
                }
            }
        }

        if (target != null) {
            val marker = targetMarker ?: Marker(map).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(context, R.drawable.fakegps_marker)
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> false }
                targetMarker = this
            }
            marker.position = target.toGeoPoint()
            if (marker !in map.overlays) map.overlays.add(marker)
        } else {
            targetMarker?.let { map.overlays.remove(it) }
        }

        if (emitted != null) {
            val marker = liveMarker ?: Marker(map).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = ContextCompat.getDrawable(context, R.drawable.fakegps_live_dot)
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> false }
                liveMarker = this
            }
            marker.position = emitted.toGeoPoint()
            // Keep the live dot on top of the pin.
            map.overlays.remove(marker)
            map.overlays.add(marker)
        } else {
            liveMarker?.let { map.overlays.remove(it) }
        }
        map.invalidate()
    }
}

private fun LatLon.toGeoPoint() = GeoPoint(latitude, longitude)

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

/** Inverted, slightly dimmed tiles for dark theme (colours shift, labels stay readable). */
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
private const val DEFAULT_LAT = 41.9028
private const val DEFAULT_LON = 12.4964
private const val CAMERA_ANIMATION_MS = 600L
private const val ROUTE_COLOR = 0xFF6750A4.toInt()
