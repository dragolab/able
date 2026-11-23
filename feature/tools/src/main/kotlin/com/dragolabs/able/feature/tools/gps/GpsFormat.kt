package com.dragolabs.able.feature.tools.gps

import android.net.Uri
import com.dragolabs.able.core.model.GpsPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Coordinate formatting for copy/share (GPS-002). Coordinates always use `.` as the decimal
 * separator so they can be pasted into map apps and search fields regardless of locale.
 */
internal object GpsFormat {

    fun decimal(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

    fun decimal(point: GpsPoint): String = decimal(point.latitude, point.longitude)

    /** Degrees, minutes, seconds with 0.1" resolution, e.g. `45°27'51.2"N 9°11'29.0"E`. */
    fun dms(latitude: Double, longitude: Double): String =
        "${dmsComponent(latitude, 'N', 'S')} ${dmsComponent(longitude, 'E', 'W')}"

    fun dms(point: GpsPoint): String = dms(point.latitude, point.longitude)

    fun coordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun dmsComponent(value: Double, positive: Char, negative: Char): String {
        val absolute = abs(value)
        var degrees = floor(absolute).toLong()
        val minutesFull = (absolute - degrees) * 60.0
        var minutes = floor(minutesFull).toLong()
        var tenthsOfSeconds = ((minutesFull - minutes) * 600.0).roundToLong()
        if (tenthsOfSeconds >= 600) {
            tenthsOfSeconds -= 600
            minutes += 1
        }
        if (minutes >= 60) {
            minutes -= 60
            degrees += 1
        }
        val hemisphere = if (value >= 0) positive else negative
        return String.format(
            Locale.US,
            "%d°%02d'%02d.%d\"%c",
            degrees,
            minutes,
            tenthsOfSeconds / 10,
            tenthsOfSeconds % 10,
            hemisphere,
        )
    }

    /** Standard Android geo URI with a labelled query, understood by most map apps. */
    fun geoUri(point: GpsPoint, label: String = point.label): String {
        val lat = coordinate(point.latitude)
        val lon = coordinate(point.longitude)
        val query = if (label.isBlank()) "$lat,$lon" else "$lat,$lon(${Uri.encode(label)})"
        return "geo:$lat,$lon?q=$query"
    }

    /** Vendor-neutral web map link (OpenStreetMap) that opens in any browser. */
    fun webMapLink(point: GpsPoint): String {
        val lat = coordinate(point.latitude)
        val lon = coordinate(point.longitude)
        return "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=17/$lat/$lon"
    }

    fun shareText(point: GpsPoint, label: String = point.label): String = buildString {
        if (label.isNotBlank()) appendLine(label)
        appendLine(decimal(point))
        appendLine(dms(point))
        append(webMapLink(point))
    }
}

/** Tag handling. Preset tags are stored with stable English keys and localized for display. */
internal object GpsTags {
    const val PARKING = "Parking"
    const val TREKKING = "Trekking"
    const val WORK = "Work"
    const val PHOTOGRAPHY = "Photography"

    val presets = listOf(PARKING, TREKKING, WORK, PHOTOGRAPHY)

    private const val MAX_TAG_LENGTH = 32

    /** Tags are persisted comma-separated, so commas are not allowed inside a tag. */
    fun sanitize(input: String): String? =
        input.replace(',', ' ').replace(Regex("\\s+"), " ").trim().take(MAX_TAG_LENGTH).ifBlank { null }
}
