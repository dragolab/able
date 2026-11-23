package com.dragolabs.able.feature.tools.measure

import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** UI state of the spirit level (SENS-001). Angles are relative to the zero reference. */
data class SpiritLevelUiState(
    val supported: Boolean = true,
    val hasReading: Boolean = false,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    /** Bubble offset in the unit circle, screen oriented (x right, y down). */
    val bubbleX: Float = 0f,
    val bubbleY: Float = 0f,
    val isLevel: Boolean = false,
    val zeroActive: Boolean = false,
    val hapticsEnabled: Boolean = true,
)

internal data class TiltAngles(val pitch: Float, val roll: Float)

private data class LevelSettings(
    val zeroPitch: Float,
    val zeroRoll: Float,
    val zeroActive: Boolean,
    val haptics: Boolean,
)

internal class SpiritLevelViewModel(
    sensorManager: SensorManager?,
    private val prefs: MeasurePreferences,
) : ViewModel() {

    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Prefer the fused gravity sensor (already filtered); fall back to the raw accelerometer. */
    private val tiltSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: accelerometer
    private val supported = accelerometer != null

    private val displayRotation = MutableStateFlow(Surface.ROTATION_0)
    private val settings = MutableStateFlow(
        LevelSettings(prefs.levelZeroPitch, prefs.levelZeroRoll, prefs.levelZeroActive, prefs.levelHaptics),
    )

    private val _levelReached = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once each time the device enters the ±0.5° window (used for the haptic pulse). */
    val levelReached: SharedFlow<Unit> = _levelReached.asSharedFlow()

    private var lastRaw: TiltAngles? = null
    private var wasLevel = false

    private val rawAngles: Flow<TiltAngles?> =
        if (sensorManager == null || tiltSensor == null) {
            flowOf(null)
        } else {
            val alpha = if (tiltSensor.type == Sensor.TYPE_GRAVITY) 0.5f else 0.15f
            sensorManager.samples(listOf(tiltSensor), SensorManager.SENSOR_DELAY_GAME)
                .filterIsInstance<SensorSample.Values>()
                .runningFold<SensorSample.Values, FloatArray?>(null) { previous, sample ->
                    val v = sample.values
                    if (previous == null) {
                        floatArrayOf(v[0], v[1], v[2])
                    } else {
                        FloatArray(3) { i -> previous[i] + alpha * (v[i] - previous[i]) }
                    }
                }
                .filterNotNull()
                .combine(displayRotation) { gravity, rotation -> tiltFrom(gravity, rotation) }
                .map<TiltAngles, TiltAngles?> { it }
                .onStart { emit(null) }
        }

    val uiState: StateFlow<SpiritLevelUiState> =
        combine(rawAngles, settings) { raw, s -> buildState(raw, s) }
            .stateIn(
                scope = viewModelScope,
                // Stop immediately: sensors must not stay registered after the screen pauses.
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
                initialValue = SpiritLevelUiState(
                    supported = supported,
                    zeroActive = settings.value.zeroActive,
                    hapticsEnabled = settings.value.haptics,
                ),
            )

    fun onDisplayRotationChanged(rotation: Int) {
        displayRotation.value = rotation
    }

    fun setZero() {
        val raw = lastRaw ?: return
        prefs.levelZeroPitch = raw.pitch
        prefs.levelZeroRoll = raw.roll
        prefs.levelZeroActive = true
        settings.update { it.copy(zeroPitch = raw.pitch, zeroRoll = raw.roll, zeroActive = true) }
    }

    fun resetZero() {
        prefs.levelZeroPitch = 0f
        prefs.levelZeroRoll = 0f
        prefs.levelZeroActive = false
        settings.update { it.copy(zeroPitch = 0f, zeroRoll = 0f, zeroActive = false) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.levelHaptics = enabled
        settings.update { it.copy(haptics = enabled) }
    }

    private fun buildState(raw: TiltAngles?, s: LevelSettings): SpiritLevelUiState {
        if (raw == null) {
            return SpiritLevelUiState(supported = supported, zeroActive = s.zeroActive, hapticsEnabled = s.haptics)
        }
        lastRaw = raw
        val pitch = if (s.zeroActive) raw.pitch - s.zeroPitch else raw.pitch
        val roll = if (s.zeroActive) raw.roll - s.zeroRoll else raw.roll

        // Hysteresis avoids repeated pulses while hovering around the threshold.
        val threshold = if (wasLevel) LEVEL_EXIT_DEGREES else LEVEL_ENTER_DEGREES
        val isLevel = abs(pitch) <= threshold && abs(roll) <= threshold
        if (isLevel && !wasLevel && s.haptics) _levelReached.tryEmit(Unit)
        wasLevel = isLevel

        // The bubble floats towards the higher side: opposite to the tilt direction.
        var bx = -roll / BUBBLE_FULL_SCALE_DEGREES
        var by = -pitch / BUBBLE_FULL_SCALE_DEGREES
        val magnitude = hypot(bx, by)
        if (magnitude > 1f) {
            bx /= magnitude
            by /= magnitude
        }
        return SpiritLevelUiState(
            supported = supported,
            hasReading = true,
            pitchDegrees = pitch,
            rollDegrees = roll,
            bubbleX = bx,
            bubbleY = by,
            isLevel = isLevel,
            zeroActive = s.zeroActive,
            hapticsEnabled = s.haptics,
        )
    }

    internal companion object {
        const val LEVEL_ENTER_DEGREES = 0.5f
        const val LEVEL_EXIT_DEGREES = 0.75f
        const val BUBBLE_FULL_SCALE_DEGREES = 10f

        /**
         * Pitch and roll from a gravity vector in device coordinates, remapped to the current
         * screen orientation. Both are in [-90°, 90°] and 0 when the screen axis is horizontal.
         */
        fun tiltFrom(g: FloatArray, rotation: Int): TiltAngles {
            val (sx, sy) = when (rotation) {
                Surface.ROTATION_90 -> -g[1] to g[0]
                Surface.ROTATION_180 -> -g[0] to -g[1]
                Surface.ROTATION_270 -> g[1] to -g[0]
                else -> g[0] to g[1]
            }
            val z = g[2]
            val pitch = Math.toDegrees(atan2(sy.toDouble(), sqrt((sx * sx + z * z).toDouble()))).toFloat()
            val roll = Math.toDegrees(atan2(-sx.toDouble(), sqrt((sy * sy + z * z).toDouble()))).toFloat()
            return TiltAngles(pitch, roll)
        }
    }
}
