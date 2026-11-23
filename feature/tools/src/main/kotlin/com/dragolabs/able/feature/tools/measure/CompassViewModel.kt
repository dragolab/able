package com.dragolabs.able.feature.tools.measure

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.view.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Magnetometer accuracy as reported by Android (`SensorManager.SENSOR_STATUS_*`). */
enum class CompassAccuracy { High, Medium, Low, Unreliable, Unknown }

/** Why true north is or is not available (SENS-003: declination needs a location). */
enum class TrueNorthStatus { Available, PermissionNeeded, PermissionDenied, NoLocation }

data class CompassUiState(
    val supported: Boolean = true,
    val hasReading: Boolean = false,
    /** Heading shown to the user, relative to the effective north reference, in [0, 360). */
    val headingDegrees: Float = 0f,
    val magneticHeadingDegrees: Float = 0f,
    /** 0 = N, 1 = NE, ... 7 = NW. */
    val cardinalIndex: Int = 0,
    val trueNorthPreferred: Boolean = false,
    val trueNorthStatus: TrueNorthStatus = TrueNorthStatus.PermissionNeeded,
    val declinationDegrees: Float? = null,
    val accuracy: CompassAccuracy = CompassAccuracy.Unknown,
) {
    /** True north is used only when the user prefers it and the declination is known. */
    val usingTrueNorth: Boolean get() = trueNorthPreferred && trueNorthStatus == TrueNorthStatus.Available
    val needsCalibration: Boolean get() = accuracy == CompassAccuracy.Low || accuracy == CompassAccuracy.Unreliable
}

private data class HeadingSample(val magneticDegrees: Float, val accuracy: CompassAccuracy)

private data class Declination(val status: TrueNorthStatus, val degrees: Float?)

internal class CompassViewModel(
    private val appContext: Context,
    sensorManager: SensorManager?,
    private val prefs: MeasurePreferences,
) : ViewModel() {

    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** The fused rotation vector (gyro + accel + mag) is steadier; it exists only with a magnetometer. */
    private val rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val supported = magnetometer != null && (rotationVector != null || accelerometer != null)

    private val displayRotation = MutableStateFlow(Surface.ROTATION_0)
    private val trueNorthPreferred = MutableStateFlow(prefs.compassTrueNorth)
    private val declination = MutableStateFlow(Declination(TrueNorthStatus.PermissionNeeded, null))
    private var permissionDenied = false

    private val headings: Flow<HeadingSample?> =
        if (sensorManager == null || !supported) flowOf(null) else headingFlow(sensorManager)

    val uiState: StateFlow<CompassUiState> =
        combine(headings, trueNorthPreferred, declination) { heading, preferTrue, decl ->
            val base = CompassUiState(
                supported = supported,
                trueNorthPreferred = preferTrue,
                trueNorthStatus = decl.status,
                declinationDegrees = decl.degrees,
            )
            if (heading == null) {
                base
            } else {
                val useTrue = preferTrue && decl.status == TrueNorthStatus.Available && decl.degrees != null
                val shown = normalize(if (useTrue) heading.magneticDegrees + decl.degrees else heading.magneticDegrees)
                base.copy(
                    hasReading = true,
                    headingDegrees = shown,
                    magneticHeadingDegrees = heading.magneticDegrees,
                    cardinalIndex = cardinalIndex(shown),
                    accuracy = heading.accuracy,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            // Stop immediately so the sensors are unregistered while the screen is paused.
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
            initialValue = CompassUiState(supported = supported, trueNorthPreferred = trueNorthPreferred.value),
        )

    init {
        refreshDeclination()
    }

    fun onDisplayRotationChanged(rotation: Int) {
        displayRotation.value = rotation
    }

    fun setTrueNorthPreferred(preferred: Boolean) {
        prefs.compassTrueNorth = preferred
        trueNorthPreferred.value = preferred
    }

    fun onLocationPermissionResult(granted: Boolean) {
        permissionDenied = !granted
        if (granted) setTrueNorthPreferred(true)
        refreshDeclination()
    }

    /** Re-reads permission and the last known location (called on resume and on user request). */
    fun refreshDeclination() {
        declination.value = computeDeclination()
    }

    @SuppressLint("MissingPermission") // Checked by hasLocationPermission().
    private fun computeDeclination(): Declination {
        if (!hasLocationPermission()) {
            return Declination(if (permissionDenied) TrueNorthStatus.PermissionDenied else TrueNorthStatus.PermissionNeeded, null)
        }
        permissionDenied = false
        val locationManager = appContext.getSystemService(LocationManager::class.java)
            ?: return Declination(TrueNorthStatus.NoLocation, null)
        val last: Location? = runCatching {
            locationManager.getProviders(true)
                .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()
        last ?: return Declination(TrueNorthStatus.NoLocation, null)
        val field = GeomagneticField(
            last.latitude.toFloat(),
            last.longitude.toFloat(),
            if (last.hasAltitude()) last.altitude.toFloat() else 0f,
            System.currentTimeMillis(),
        )
        return Declination(TrueNorthStatus.Available, field.declination)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun headingFlow(sensorManager: SensorManager): Flow<HeadingSample?> = flow {
        emit(null)
        val useRotationVector = rotationVector != null
        val sensors = listOfNotNull(if (useRotationVector) rotationVector else accelerometer, magnetometer)
        val rotationMatrix = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        var gravity: FloatArray? = null
        var geomagnetic: FloatArray? = null
        var accuracy = CompassAccuracy.Unknown
        var smoothed: Float? = null

        sensorManager.samples(sensors, SensorManager.SENSOR_DELAY_GAME).collect { sample ->
            var matrixReady = false
            when (sample) {
                is SensorSample.Accuracy -> if (sample.sensorType == Sensor.TYPE_MAGNETIC_FIELD) {
                    accuracy = accuracyOf(sample.accuracy)
                }

                is SensorSample.Values -> when (sample.sensorType) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, sample.values)
                        matrixReady = true
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        gravity = lowPass(sample.values, gravity, 0.15f)
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        accuracy = accuracyOf(sample.accuracy)
                        geomagnetic = lowPass(sample.values, geomagnetic, 0.2f)
                    }
                }
            }
            if (!useRotationVector && sample is SensorSample.Values) {
                val g = gravity
                val m = geomagnetic
                matrixReady = g != null && m != null && SensorManager.getRotationMatrix(rotationMatrix, null, g, m)
            }
            if (matrixReady) {
                remapForDisplay(rotationMatrix, remapped, displayRotation.value)
                SensorManager.getOrientation(remapped, orientation)
                val azimuth = normalize(Math.toDegrees(orientation[0].toDouble()).toFloat())
                val previous = smoothed
                smoothed = if (previous == null) azimuth else normalize(previous + SMOOTHING * shortestDelta(previous, azimuth))
                emit(HeadingSample(smoothed, accuracy))
            } else if (sample is SensorSample.Accuracy && smoothed != null) {
                emit(HeadingSample(smoothed, accuracy))
            }
        }
    }

    internal companion object {
        private const val SMOOTHING = 0.2f

        fun normalize(degrees: Float): Float {
            val d = degrees % 360f
            return if (d < 0f) d + 360f else d
        }

        /** Signed shortest rotation from [from] to [to], in (-180, 180]. */
        fun shortestDelta(from: Float, to: Float): Float {
            var delta = (to - from) % 360f
            if (delta > 180f) delta -= 360f
            if (delta <= -180f) delta += 360f
            return delta
        }

        fun cardinalIndex(degrees: Float): Int = ((normalize(degrees) + 22.5f) / 45f).toInt() % 8

        private fun accuracyOf(status: Int): CompassAccuracy = when (status) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.High
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.Medium
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.Low
            SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassAccuracy.Unreliable
            else -> CompassAccuracy.Unknown
        }

        private fun lowPass(input: FloatArray, previous: FloatArray?, alpha: Float): FloatArray =
            if (previous == null) {
                floatArrayOf(input[0], input[1], input[2])
            } else {
                FloatArray(3) { i -> previous[i] + alpha * (input[i] - previous[i]) }
            }

        private fun remapForDisplay(input: FloatArray, output: FloatArray, rotation: Int) {
            val (x, y) = when (rotation) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            SensorManager.remapCoordinateSystem(input, x, y, output)
        }
    }
}
