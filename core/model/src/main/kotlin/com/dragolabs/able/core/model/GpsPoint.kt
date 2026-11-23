package com.dragolabs.able.core.model

import kotlinx.serialization.Serializable

/**
 * Saved GPS location point with custom metadata (GPS-001, GPS-002, ADR-021).
 */
@Serializable
data class GpsPoint(
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float,
    val timestampEpochMs: Long,
    val label: String,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val reverseGeocodedAddress: String? = null
) {
    /**
     * Formats location as standard decimal string: "lat, lon"
     */
    fun toCoordinateString(): String = String.format(java.util.Locale.ROOT, "%.6f, %.6f", latitude, longitude)

    /**
     * Creates standard Android geo URI: "geo:lat,lon?q=lat,lon(label)"
     */
    fun toGeoUriString(): String {
        // Percent-encode (spaces as %20, not '+') and avoid exponent notation in coordinates.
        val encodedLabel = java.net.URLEncoder.encode(label, "UTF-8").replace("+", "%20")
        val lat = String.format(java.util.Locale.ROOT, "%.6f", latitude)
        val lon = String.format(java.util.Locale.ROOT, "%.6f", longitude)
        return "geo:$lat,$lon?q=$lat,$lon($encodedLabel)"
    }
}
