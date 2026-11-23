package com.dragolabs.able.feature.tools.fakegps

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** WGS84 coordinate in decimal degrees. Pure Kotlin so the math is JVM-testable. */
data class LatLon(val latitude: Double, val longitude: Double) {
    val isValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun format(): String = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
}

/** Spherical-earth helpers; accurate to well under a metre for the distances used here. */
internal object GeoMath {
    const val EARTH_RADIUS_M = 6_371_008.8

    private fun Double.rad() = this * PI / 180.0
    private fun Double.deg() = this * 180.0 / PI

    fun distanceMeters(a: LatLon, b: LatLon): Double {
        val dLat = (b.latitude - a.latitude).rad()
        val dLon = (b.longitude - a.longitude).rad()
        val h = sin(dLat / 2).let { it * it } +
            cos(a.latitude.rad()) * cos(b.latitude.rad()) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** Initial great-circle bearing from [a] to [b], 0..360 degrees clockwise from north. */
    fun bearingDegrees(a: LatLon, b: LatLon): Double {
        val lat1 = a.latitude.rad()
        val lat2 = b.latitude.rad()
        val dLon = (b.longitude - a.longitude).rad()
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalizeBearing(atan2(y, x).deg())
    }

    /** Point reached travelling [distanceM] metres from [start] along [bearingDeg]. */
    fun destination(start: LatLon, bearingDeg: Double, distanceM: Double): LatLon {
        if (distanceM == 0.0) return start
        val delta = distanceM / EARTH_RADIUS_M
        val theta = bearingDeg.rad()
        val lat1 = start.latitude.rad()
        val lon1 = start.longitude.rad()
        val lat2 = asin(sin(lat1) * cos(delta) + cos(lat1) * sin(delta) * cos(theta))
        val lon2 = lon1 + atan2(sin(theta) * sin(delta) * cos(lat1), cos(delta) - sin(lat1) * sin(lat2))
        return LatLon(lat2.deg().coerceIn(-90.0, 90.0), normalizeLongitude(lon2.deg()))
    }

    /** Small local offset in metres (north/east), used for jitter. */
    fun offset(start: LatLon, northM: Double, eastM: Double): LatLon {
        val distance = hypot(northM, eastM)
        if (distance == 0.0) return start
        return destination(start, atan2(eastM, northM).deg(), distance)
    }

    fun normalizeBearing(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    fun normalizeLongitude(lon: Double): Double {
        var value = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        if (value == -180.0 && lon > 0) value = 180.0
        return value
    }

    /**
     * Joystick deflection (screen axes: x right, y down, each -1..1) to a compass bearing.
     * Returns null when the stick is centred.
     */
    fun joystickBearing(x: Float, y: Float): Double? {
        if (hypot(x.toDouble(), y.toDouble()) < 0.05) return null
        return normalizeBearing(atan2(x.toDouble(), -y.toDouble()).deg())
    }
}

/** How a route repeats once the last waypoint is reached. */
enum class RouteRepeat { ONCE, LOOP, BACK_AND_FORTH }

data class RouteSample(
    val position: LatLon,
    val bearingDegrees: Double,
    /** True once a [RouteRepeat.ONCE] route has reached its last waypoint. */
    val finished: Boolean,
    /** 0..1 progress through the current pass (for a loop, one lap). */
    val progress: Double,
)

/**
 * Polyline through two or more waypoints. Positions are interpolated along each great-circle
 * segment by distance travelled, so a constant speed gives constant ground speed.
 */
internal class RoutePath(private val waypoints: List<LatLon>, private val closed: Boolean) {

    private val segments: List<Pair<LatLon, LatLon>> = buildList {
        for (i in 0 until waypoints.size - 1) add(waypoints[i] to waypoints[i + 1])
        if (closed && waypoints.size > 2) add(waypoints.last() to waypoints.first())
    }
    private val lengths = segments.map { GeoMath.distanceMeters(it.first, it.second) }
    val totalLength: Double = lengths.sum()

    /** Point at [distance] metres from the first waypoint, without repetition (clamped). */
    fun pointAt(distance: Double): Pair<LatLon, Double> {
        if (segments.isEmpty()) return (waypoints.firstOrNull() ?: LatLon(0.0, 0.0)) to 0.0
        var remaining = distance.coerceIn(0.0, totalLength)
        for (i in segments.indices) {
            val len = lengths[i]
            val (a, b) = segments[i]
            if (remaining <= len || i == segments.lastIndex) {
                val bearing = if (len > 0) GeoMath.bearingDegrees(a, b) else 0.0
                val along = min(remaining, len)
                return GeoMath.destination(a, bearing, along) to bearing
            }
            remaining -= len
        }
        return waypoints.last() to 0.0
    }

    companion object {
        /** Samples a route of [waypoints] after travelling [travelled] metres with [repeat]. */
        fun sample(waypoints: List<LatLon>, repeat: RouteRepeat, travelled: Double): RouteSample? {
            if (waypoints.isEmpty()) return null
            val path = RoutePath(waypoints, closed = repeat == RouteRepeat.LOOP)
            val total = path.totalLength
            if (waypoints.size < 2 || total <= 0.0) {
                return RouteSample(waypoints.first(), 0.0, finished = true, progress = 1.0)
            }
            val d = max(0.0, travelled)
            return when (repeat) {
                RouteRepeat.ONCE -> {
                    val (p, b) = path.pointAt(d)
                    RouteSample(p, b, finished = d >= total, progress = min(1.0, d / total))
                }
                RouteRepeat.LOOP -> {
                    val lap = d % total
                    val (p, b) = path.pointAt(lap)
                    RouteSample(p, b, finished = false, progress = lap / total)
                }
                RouteRepeat.BACK_AND_FORTH -> {
                    val cycle = d % (2 * total)
                    if (cycle <= total) {
                        val (p, b) = path.pointAt(cycle)
                        RouteSample(p, b, finished = false, progress = cycle / total)
                    } else {
                        val back = 2 * total - cycle
                        val (p, b) = path.pointAt(back)
                        RouteSample(p, GeoMath.normalizeBearing(b + 180.0), finished = false, progress = back / total)
                    }
                }
            }
        }
    }
}

/**
 * Bounded random walk that makes a static fake position look like a real fix (small wander,
 * varying accuracy). The offset never leaves [radiusMeters] around the anchor.
 */
internal class JitterModel(private val random: Random = Random.Default) {
    var northMeters = 0.0
        private set
    var eastMeters = 0.0
        private set

    /** Advances the walk and returns the (north, east) offset in metres, within [radiusMeters]. */
    fun next(radiusMeters: Double): Pair<Double, Double> {
        val radius = max(0.0, radiusMeters)
        if (radius == 0.0) {
            northMeters = 0.0
            eastMeters = 0.0
            return 0.0 to 0.0
        }
        val step = radius * STEP_FRACTION
        northMeters += (random.nextDouble() * 2 - 1) * step
        eastMeters += (random.nextDouble() * 2 - 1) * step
        // Gentle pull back to the anchor so the walk does not stick to the edge.
        northMeters *= DAMPING
        eastMeters *= DAMPING
        val distance = hypot(northMeters, eastMeters)
        if (distance > radius) {
            val scale = radius / distance
            northMeters *= scale
            eastMeters *= scale
        }
        return northMeters to eastMeters
    }

    /** Reported accuracy varying around [baseMeters] by at most +/-20 %, never below 1 m. */
    fun accuracy(baseMeters: Float): Float {
        val factor = 1.0 + (random.nextDouble() * 2 - 1) * ACCURACY_VARIATION
        return max(1.0, baseMeters * factor).toFloat()
    }

    /** Altitude with at most +/-[ALTITUDE_NOISE_M] metres of noise. */
    fun altitude(baseMeters: Double): Double = baseMeters + (random.nextDouble() * 2 - 1) * ALTITUDE_NOISE_M

    companion object {
        const val STEP_FRACTION = 0.35
        const val DAMPING = 0.92
        const val ACCURACY_VARIATION = 0.2
        const val ALTITUDE_NOISE_M = 1.5

        /** Jitter radius for a given reported accuracy: half of it, 0.5..10 m. */
        fun radiusFor(accuracyMeters: Float): Double = (accuracyMeters * 0.5).coerceIn(0.5, 10.0)
    }
}

/**
 * Parses pasted coordinates: decimal pairs (`45.4642, 9.19`, `45,4642 9,19`), hemisphere
 * letters (`45.4642N 9.19E`), degrees-minutes-seconds (`45°27'51.2"N 9°11'29.0"E`), degrees and
 * decimal minutes (`45°27.853'N`), `geo:` URIs and map links containing `@lat,lon`, `q=lat,lon`
 * or `ll=lat,lon`. Returns null when the text is not a valid coordinate.
 */
internal object CoordinateParser {

    private const val NUM = "[+-]?\\d{1,3}(?:[.,]\\d+)?"
    private val decimalPair = Regex("^\\s*($NUM)\\s*(?:[,;]\\s*|\\s+)($NUM)\\s*$")
    private val urlPair = Regex("(?:@|[?&](?:q|ll|query|center)=|^geo:)\\s*([+-]?\\d{1,3}\\.\\d+)\\s*,\\s*([+-]?\\d{1,3}\\.\\d+)")
    private const val DMS_BODY =
        "([+-]?\\d+(?:\\.\\d+)?)\\s*°\\s*(?:(\\d+(?:\\.\\d+)?)\\s*'\\s*)?(?:(\\d+(?:\\.\\d+)?)\\s*\"\\s*)?"
    private val dmsPrefix = Regex("([NSEW])\\s*$DMS_BODY", RegexOption.IGNORE_CASE)
    private val dmsSuffix = Regex("$DMS_BODY([NSEW])?", RegexOption.IGNORE_CASE)
    private const val DEC = "(\\d{1,3}(?:\\.\\d+)?)"
    private val hemiSuffix = Regex("^$DEC\\s*([NSEW])\\s*$DEC\\s*([NSEW])$", RegexOption.IGNORE_CASE)
    private val hemiPrefix = Regex("^([NSEW])\\s*$DEC\\s*([NSEW])\\s*$DEC$", RegexOption.IGNORE_CASE)

    fun parse(input: String): LatLon? {
        val text = input.trim()
        if (text.isEmpty()) return null
        urlPair.find(text)?.let { m ->
            return build(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
        }
        decimalPair.matchEntire(text)?.let { m ->
            val lat = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            val lon = m.groupValues[2].replace(',', '.').toDoubleOrNull()
            if (lat != null && lon != null) return build(lat, lon)
        }
        val normalized = normalizeSymbols(text)
        return if (normalized.contains('°')) parseDms(normalized) else parseHemisphereDecimal(normalized)
    }

    private fun normalizeSymbols(text: String): String = text
        .replace('º', '°').replace('˚', '°')
        .replace('′', '\'').replace('’', '\'').replace('‘', '\'')
        .replace('″', '"').replace('”', '"').replace('“', '"')
        .replace("''", "\"")
        .replace(Regex("(\\d),(\\d)"), "$1.$2")

    private data class Component(val value: Double, val hemisphere: Char?)

    private fun parseDms(text: String): LatLon? {
        val prefixStyle = text.trimStart().firstOrNull()?.uppercaseChar() in HEMISPHERES
        val regex = if (prefixStyle) dmsPrefix else dmsSuffix
        val matches = regex.findAll(text).toList()
        val parts = matches.map { m ->
            val g = m.groupValues
            // Prefix regex: 1=hemisphere, 2..4=d/m/s. Suffix regex: 1..3=d/m/s, 4=hemisphere.
            val (degText, minText, secText, hemiText) =
                if (prefixStyle) listOf(g[2], g[3], g[4], g[1]) else listOf(g[1], g[2], g[3], g[4])
            val degrees = degText.toDoubleOrNull() ?: return null
            val minutes = minText.takeIf { it.isNotEmpty() }?.toDouble() ?: 0.0
            val seconds = secText.takeIf { it.isNotEmpty() }?.toDouble() ?: 0.0
            if (minutes >= 60.0 || seconds >= 60.0) return null
            if (minText.isEmpty() && secText.isNotEmpty()) return null
            if (hemiText.isNotEmpty() && degrees < 0) return null
            val sign = if (degrees < 0) -1.0 else 1.0
            Component(sign * (abs(degrees) + minutes / 60.0 + seconds / 3600.0), hemiText.firstOrNull()?.uppercaseChar())
        }
        // Everything except separators must belong to the two components.
        val leftover = regex.replace(text, "").replace(Regex("[\\s,;]"), "")
        if (leftover.isNotEmpty()) return null
        return assign(parts)
    }

    private fun parseHemisphereDecimal(text: String): LatLon? {
        val cleaned = text.replace(Regex("[,;]"), " ").trim()
        hemiSuffix.matchEntire(cleaned)?.let { m ->
            val g = m.groupValues
            return assign(
                listOf(
                    Component(g[1].toDouble(), g[2].first().uppercaseChar()),
                    Component(g[3].toDouble(), g[4].first().uppercaseChar()),
                ),
            )
        }
        hemiPrefix.matchEntire(cleaned)?.let { m ->
            val g = m.groupValues
            return assign(
                listOf(
                    Component(g[2].toDouble(), g[1].first().uppercaseChar()),
                    Component(g[4].toDouble(), g[3].first().uppercaseChar()),
                ),
            )
        }
        return null
    }

    private fun assign(parts: List<Component>): LatLon? {
        if (parts.size != 2) return null
        val (first, second) = parts
        val (latC, lonC) = when {
            first.hemisphere == null && second.hemisphere == null -> first to second
            first.hemisphere in LAT && (second.hemisphere == null || second.hemisphere in LON) -> first to second
            first.hemisphere in LON && (second.hemisphere == null || second.hemisphere in LAT) -> second to first
            first.hemisphere == null && second.hemisphere in LON -> first to second
            first.hemisphere == null && second.hemisphere in LAT -> second to first
            else -> return null
        }
        val lat = if (latC.hemisphere == 'S') -abs(latC.value) else latC.value
        val lon = if (lonC.hemisphere == 'W') -abs(lonC.value) else lonC.value
        return build(lat, lon)
    }

    private fun build(lat: Double, lon: Double): LatLon? = LatLon(lat, lon).takeIf { it.isValid }

    private val HEMISPHERES = setOf('N', 'S', 'E', 'W')
    private val LAT = setOf('N', 'S')
    private val LON = setOf('E', 'W')
}
