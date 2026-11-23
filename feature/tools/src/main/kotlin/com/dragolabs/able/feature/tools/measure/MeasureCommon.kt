package com.dragolabs.able.feature.tools.measure

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.core.content.edit
import com.dragolabs.able.core.designsystem.component.AbleToggleButton
import com.dragolabs.able.core.designsystem.component.focusRing
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * Feature-local persistence for the measurement tools (ruler calibration, level zero reference,
 * compass north reference). Kept in this module on purpose: these values are device-specific
 * calibration data, not shared configuration.
 */
internal class MeasurePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("able_measure_tools", Context.MODE_PRIVATE)

    var rulerScale: Float
        get() = prefs.getFloat(KEY_RULER_SCALE, 1f)
        set(value) = prefs.edit { putFloat(KEY_RULER_SCALE, value) }

    var rulerImperial: Boolean
        get() = prefs.getBoolean(KEY_RULER_IMPERIAL, false)
        set(value) = prefs.edit { putBoolean(KEY_RULER_IMPERIAL, value) }

    var levelZeroPitch: Float
        get() = prefs.getFloat(KEY_LEVEL_ZERO_PITCH, 0f)
        set(value) = prefs.edit { putFloat(KEY_LEVEL_ZERO_PITCH, value) }

    var levelZeroRoll: Float
        get() = prefs.getFloat(KEY_LEVEL_ZERO_ROLL, 0f)
        set(value) = prefs.edit { putFloat(KEY_LEVEL_ZERO_ROLL, value) }

    var levelZeroActive: Boolean
        get() = prefs.getBoolean(KEY_LEVEL_ZERO_ACTIVE, false)
        set(value) = prefs.edit { putBoolean(KEY_LEVEL_ZERO_ACTIVE, value) }

    var levelHaptics: Boolean
        get() = prefs.getBoolean(KEY_LEVEL_HAPTICS, true)
        set(value) = prefs.edit { putBoolean(KEY_LEVEL_HAPTICS, value) }

    var compassTrueNorth: Boolean
        get() = prefs.getBoolean(KEY_COMPASS_TRUE_NORTH, false)
        set(value) = prefs.edit { putBoolean(KEY_COMPASS_TRUE_NORTH, value) }

    private companion object {
        const val KEY_RULER_SCALE = "ruler_scale"
        const val KEY_RULER_IMPERIAL = "ruler_imperial"
        const val KEY_LEVEL_ZERO_PITCH = "level_zero_pitch"
        const val KEY_LEVEL_ZERO_ROLL = "level_zero_roll"
        const val KEY_LEVEL_ZERO_ACTIVE = "level_zero_active"
        const val KEY_LEVEL_HAPTICS = "level_haptics"
        const val KEY_COMPASS_TRUE_NORTH = "compass_true_north"
    }
}

/** A raw sensor callback, forwarded as data so ViewModels can stay free of listener plumbing. */
internal sealed interface SensorSample {
    data class Values(val sensorType: Int, val values: FloatArray, val accuracy: Int) : SensorSample
    data class Accuracy(val sensorType: Int, val accuracy: Int) : SensorSample
}

/**
 * Cold flow that registers [sensors] while collected and unregisters on cancellation. Combined
 * with `collectAsStateWithLifecycle(minActiveState = RESUMED)` this keeps sensors registered only
 * while the screen is visible and resumed (SENS-001 "unregisters sensors when screen pauses").
 */
internal fun SensorManager.samples(
    sensors: List<Sensor>,
    samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI,
): Flow<SensorSample> = callbackFlow {
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            trySend(SensorSample.Values(event.sensor.type, event.values.copyOf(), event.accuracy))
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            trySend(SensorSample.Accuracy(sensor.type, accuracy))
        }
    }
    sensors.forEach { registerListener(listener, it, samplingPeriodUs) }
    awaitClose { unregisterListener(listener) }
}.buffer(capacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)

/**
 * Current display rotation (`Surface.ROTATION_*`) of the window hosting this composable, updated
 * also for 180° turns that do not trigger a configuration change.
 */
@Composable
internal fun rememberDisplayRotation(): State<Int> {
    val view = LocalView.current
    val state = remember { mutableIntStateOf(view.display?.rotation ?: Surface.ROTATION_0) }
    DisposableEffect(view) {
        val displayManager = view.context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                val display = view.display ?: return
                if (display.displayId == displayId) state.intValue = display.rotation
            }
        }
        state.intValue = view.display?.rotation ?: Surface.ROTATION_0
        displayManager?.registerDisplayListener(listener, null)
        onDispose { displayManager?.unregisterDisplayListener(listener) }
    }
    return state
}

/**
 * Connected single-choice toggle group (Material 3 Expressive button group pattern).
 */
@Composable
internal fun ConnectedChoiceGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (Int) -> Boolean = { true },
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, label ->
            AbleToggleButton(
                checked = index == selectedIndex,
                onCheckedChange = { if (it) onSelect(index) },
                enabled = enabled(index),
                modifier = Modifier
                    .weight(1f)
                    .focusRing()
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
}

/** Formats an angle with 0.1° resolution, avoiding a misleading "-0.0". */
internal fun formatTenths(value: Float): String {
    val rounded = Math.round(value * 10f) / 10f
    val safe = if (rounded == 0f) 0f else rounded
    return String.format(java.util.Locale.getDefault(), "%.1f", safe)
}
