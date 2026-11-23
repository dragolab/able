package com.dragolabs.able.feature.tools.fountains

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS84 coordinate (degrees). */
data class GeoPos(val latitude: Double, val longitude: Double)

/** What kind of OSM feature the drinking-water point is. */
enum class FountainKind { DRINKING_WATER, WATER_TAP, SPRING, FOUNTAIN }

/** Three-state OSM answer (`yes` / `limited` / `no`); [UNKNOWN] when the tag is missing. */
enum class TriState { YES, LIMITED, NO, UNKNOWN }

/** OSM element type, needed for the `openstreetmap.org/<type>/<id>` link. */
enum class OsmType(val path: String) { NODE("node"), WAY("way") }

/**
 * A public drinking-water point from OpenStreetMap (ODbL, © OpenStreetMap contributors).
 * Tags are kept as the raw OSM values where the UI shows them verbatim (opening hours, dates).
 */
data class Fountain(
    val osmType: OsmType,
    val osmId: Long,
    val position: GeoPos,
    val kind: FountainKind,
    val name: String?,
    val bottle: TriState,
    val wheelchair: TriState,
    val fee: TriState,
    /** null when not tagged; "yes"/"no" or a season list such as "summer". */
    val seasonal: String?,
    val openingHours: String?,
    /** `check_date` (or `survey:date`), as tagged (usually YYYY-MM-DD). */
    val lastChecked: String?,
    val operator: String?,
    val description: String?,
) {
    val key: String get() = "${osmType.path}/$osmId"
}

/** Map bounding box in degrees. West may be greater than east only across the antimeridian (not supported: clamped). */
data class BoundingBox(val south: Double, val west: Double, val north: Double, val east: Double) {
    val latSpan: Double get() = north - south
    val lonSpan: Double get() = east - west
    val center: GeoPos get() = GeoPos((south + north) / 2, (west + east) / 2)

    fun contains(point: GeoPos): Boolean =
        point.latitude in south..north && point.longitude in west..east

    fun contains(other: BoundingBox): Boolean =
        other.south >= south && other.north <= north && other.west >= west && other.east <= east
}

/** User-selected list/map filters. */
data class FountainFilters(val bottleOnly: Boolean = false, val wheelchairOnly: Boolean = false) {
    fun matches(f: Fountain): Boolean =
        (!bottleOnly || f.bottle == TriState.YES) &&
            (!wheelchairOnly || f.wheelchair == TriState.YES || f.wheelchair == TriState.LIMITED)

    val active: Boolean get() = bottleOnly || wheelchairOnly
}

/** Eight-point compass direction for "120 m · NE". */
enum class Compass { N, NE, E, SE, S, SW, W, NW }

/** Pure geodesy helpers (spherical Earth, good to a few metres at walking distances). */
object FountainGeo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in metres (haversine). */
    fun distanceMeters(a: GeoPos, b: GeoPos): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Initial bearing from [from] to [to], degrees clockwise from north in [0, 360). */
    fun bearingDegrees(from: GeoPos, to: GeoPos): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return (deg + 360.0) % 360.0
    }

    fun compass(bearing: Double): Compass {
        val normalized = ((bearing % 360.0) + 360.0) % 360.0
        val index = floor((normalized + 22.5) / 45.0).toInt() % 8
        return Compass.entries[index]
    }

    /** Fountains with their distance from [origin], nearest first (ties broken by id for stable order). */
    fun nearest(origin: GeoPos, fountains: List<Fountain>, limit: Int = Int.MAX_VALUE): List<NearbyFountain> =
        fountains.asSequence()
            .map { f ->
                NearbyFountain(
                    fountain = f,
                    distanceMeters = distanceMeters(origin, f.position),
                    bearingDegrees = bearingDegrees(origin, f.position),
                )
            }
            .sortedWith(compareBy<NearbyFountain> { it.distanceMeters }.thenBy { it.fountain.osmId })
            .take(limit)
            .toList()
}

data class NearbyFountain(val fountain: Fountain, val distanceMeters: Double, val bearingDegrees: Double) {
    val compass: Compass get() = FountainGeo.compass(bearingDegrees)
}

/**
 * Overpass area policy: the visible box is snapped outward to a coarse grid (so small pans reuse
 * the same cached request) and refused when it is too large for a polite, fast query.
 */
object FountainArea {
    /** Grid step for snapping, degrees (~2.2 km of latitude). */
    const val GRID_DEG = 0.02

    /** Largest visible span accepted before asking the user to zoom in (~ 22 km × 22 km in Italy). */
    const val MAX_LAT_SPAN = 0.20
    const val MAX_LON_SPAN = 0.28

    fun isTooLarge(box: BoundingBox): Boolean = box.latSpan > MAX_LAT_SPAN || box.lonSpan > MAX_LON_SPAN

    /** Snaps outward to the grid and clamps to valid coordinates. */
    fun snap(box: BoundingBox): BoundingBox = BoundingBox(
        south = (floor(box.south / GRID_DEG) * GRID_DEG).coerceIn(-90.0, 90.0).round6(),
        west = (floor(box.west / GRID_DEG) * GRID_DEG).coerceIn(-180.0, 180.0).round6(),
        north = (ceil(box.north / GRID_DEG) * GRID_DEG).coerceIn(-90.0, 90.0).round6(),
        east = (ceil(box.east / GRID_DEG) * GRID_DEG).coerceIn(-180.0, 180.0).round6(),
    )

    /** Stable cache key of a snapped box. */
    fun key(snapped: BoundingBox): String =
        listOf(snapped.south, snapped.west, snapped.north, snapped.east).joinToString("_") { "%.4f".format(java.util.Locale.ROOT, it) }

    private fun Double.round6(): Double = Math.round(this * 1e6) / 1e6
}
