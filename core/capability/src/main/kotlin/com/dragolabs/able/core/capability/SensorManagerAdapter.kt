package com.dragolabs.able.core.capability

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class InclinometerData(
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val isLevel: Boolean = false // Within +/- 0.5 degrees of zero
)

data class CompassData(
    val azimuthDegrees: Float = 0f,
    val cardinalDirection: String = "N",
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
)

class SensorManagerAdapter(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _inclinometerFlow = MutableStateFlow(InclinometerData())
    val inclinometerFlow: StateFlow<InclinometerData> = _inclinometerFlow.asStateFlow()

    private val _compassFlow = MutableStateFlow(CompassData())
    val compassFlow: StateFlow<CompassData> = _compassFlow.asStateFlow()

    private val gravityReadings = FloatArray(3)
    private val geomagneticReadings = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    private var isInclinometerActive = false
    private var isCompassActive = false

    fun startInclinometer() {
        if (isInclinometerActive) return
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            isInclinometerActive = true
        }
    }

    fun stopInclinometer() {
        if (!isInclinometerActive) return
        isInclinometerActive = false
        if (!isCompassActive) {
            sensorManager?.unregisterListener(this)
        }
    }

    fun startCompass() {
        if (isCompassActive) return
        sensorManager?.let { sm ->
            accelerometer?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magnetometer?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            isCompassActive = true
        }
    }

    fun stopCompass() {
        if (!isCompassActive) return
        isCompassActive = false
        if (!isInclinometerActive) {
            sensorManager?.unregisterListener(this)
        } else {
            // Keep accelerometer if inclinometer is still active
            magnetometer?.let { sensorManager?.unregisterListener(this, it) }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityReadings, 0, 3)
                hasGravity = true

                if (isInclinometerActive) {
                    processInclinometer(gravityReadings[0], gravityReadings[1], gravityReadings[2])
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagneticReadings, 0, 3)
                hasGeomagnetic = true
            }
        }

        if (isCompassActive && hasGravity && hasGeomagnetic) {
            processCompass()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            _compassFlow.value = _compassFlow.value.copy(accuracy = accuracy)
        }
    }

    private fun processInclinometer(ax: Float, ay: Float, az: Float) {
        // Calculate pitch and roll in degrees
        val pitchRad = atan2(ay.toDouble(), sqrt((ax * ax + az * az).toDouble()))
        val rollRad = atan2(-ax.toDouble(), az.toDouble())

        val pitchDeg = Math.toDegrees(pitchRad).toFloat()
        val rollDeg = Math.toDegrees(rollRad).toFloat()

        val isLevel = kotlin.math.abs(pitchDeg) < 0.5f && kotlin.math.abs(rollDeg) < 0.5f
        _inclinometerFlow.value = InclinometerData(
            pitchDegrees = pitchDeg,
            rollDegrees = rollDeg,
            isLevel = isLevel
        )
    }

    private fun processCompass() {
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)

        if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, gravityReadings, geomagneticReadings)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuthDeg < 0) azimuthDeg += 360f

            val cardinal = getCardinal(azimuthDeg)
            _compassFlow.value = _compassFlow.value.copy(
                azimuthDegrees = azimuthDeg,
                cardinalDirection = cardinal
            )
        }
    }

    private fun getCardinal(azimuth: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
        val index = ((azimuth + 22.5f) / 45f).toInt()
        return directions[index % 8]
    }
}
