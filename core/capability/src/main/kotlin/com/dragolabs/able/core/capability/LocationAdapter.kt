package com.dragolabs.able.core.capability

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.dragolabs.able.core.common.AbleError
import com.dragolabs.able.core.common.AbleResult
import com.dragolabs.able.core.model.GpsPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class LocationAdapter(
    private val context: Context,
    private val permissionCoordinator: PermissionCoordinator
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentGpsPoint(
        label: String = "GPS Point",
        notes: String = "",
        tags: List<String> = emptyList()
    ): AbleResult<GpsPoint> = withContext(Dispatchers.IO) {
        if (!permissionCoordinator.hasFineLocationPermission()) {
            return@withContext AbleResult.Failure(
                AbleError.PermissionMissing("ACCESS_FINE_LOCATION")
            )
        }

        try {
            val location: Location? = suspendCancellableCoroutine { continuation ->
                val cts = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc -> continuation.resume(loc) }
                    .addOnFailureListener { continuation.resume(null) }
                    .addOnCanceledListener { continuation.resume(null) }

                continuation.invokeOnCancellation { cts.cancel() }
            }

            if (location == null) {
                return@withContext AbleResult.Failure(
                    AbleError.Storage("Unable to acquire GPS fix. Please ensure location services are enabled.")
                )
            }

            val address = tryReverseGeocode(location.latitude, location.longitude)

            val point = GpsPoint(
                id = 0,
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = if (location.hasAltitude()) location.getAltitude() else null,
                accuracyMeters = location.getAccuracy(),
                timestampEpochMs = location.getTime(),
                label = label,
                notes = notes,
                tags = tags,
                reverseGeocodedAddress = address
            )

            AbleResult.Success(point)
        } catch (e: Exception) {
            AbleResult.Failure(AbleError.Unknown("Error retrieving location: ${e.message}", e))
        }
    }

    private fun tryReverseGeocode(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                addr.getAddressLine(0)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
