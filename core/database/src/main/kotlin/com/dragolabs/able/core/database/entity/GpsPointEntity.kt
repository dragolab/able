package com.dragolabs.able.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dragolabs.able.core.model.GpsPoint

@Entity(tableName = "gps_points")
data class GpsPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float,
    val timestampEpochMs: Long,
    val label: String,
    val notes: String,
    val tagsJson: String,
    val reverseGeocodedAddress: String?
) {
    fun toDomain(): GpsPoint = GpsPoint(
        id = id,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
        accuracyMeters = accuracyMeters,
        timestampEpochMs = timestampEpochMs,
        label = label,
        notes = notes,
        tags = if (tagsJson.isBlank()) emptyList() else tagsJson.split(",").map { it.trim() },
        reverseGeocodedAddress = reverseGeocodedAddress
    )

    companion object {
        fun fromDomain(domain: GpsPoint): GpsPointEntity = GpsPointEntity(
            id = domain.id,
            latitude = domain.latitude,
            longitude = domain.longitude,
            altitudeMeters = domain.altitudeMeters,
            accuracyMeters = domain.accuracyMeters,
            timestampEpochMs = domain.timestampEpochMs,
            label = domain.label,
            notes = domain.notes,
            tagsJson = domain.tags.joinToString(","),
            reverseGeocodedAddress = domain.reverseGeocodedAddress
        )
    }
}
