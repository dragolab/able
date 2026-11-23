package com.dragolabs.able.feature.tools.measure

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.hypot

/** UI state of the screen ruler (SENS-002). Marker positions are in pixels of the ruler canvas. */
data class ScreenRulerUiState(
    val supported: Boolean = true,
    val imperial: Boolean = false,
    /** User calibration factor applied on top of the DPI reported by Android. */
    val scale: Float = 1f,
    val xdpi: Float = 0f,
    val ydpi: Float = 0f,
    val reportedXdpi: Float = 0f,
    val reportedYdpi: Float = 0f,
    val dpiSuspicious: Boolean = false,
    val showCardGuide: Boolean = false,
    val markerA: Offset? = null,
    val markerB: Offset? = null,
) {
    /** Pixels per millimeter on each axis after calibration. */
    val pxPerMmX: Float get() = xdpi / MM_PER_INCH * scale
    val pxPerMmY: Float get() = ydpi / MM_PER_INCH * scale

    /** Distance between the two markers in millimeters, or null when fewer than two markers exist. */
    val distanceMm: Float?
        get() {
            val a = markerA ?: return null
            val b = markerB ?: return null
            if (pxPerMmX <= 0f || pxPerMmY <= 0f) return null
            return hypot((b.x - a.x) / pxPerMmX, (b.y - a.y) / pxPerMmY)
        }

    companion object {
        const val MM_PER_INCH = 25.4f
    }
}

internal class ScreenRulerViewModel(
    hasTouchscreen: Boolean,
    private val prefs: MeasurePreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ScreenRulerUiState(
            supported = hasTouchscreen,
            imperial = prefs.rulerImperial,
            scale = prefs.rulerScale.coerceIn(MIN_SCALE, MAX_SCALE),
        ),
    )
    val uiState: StateFlow<ScreenRulerUiState> = _uiState.asStateFlow()

    /**
     * Called with the metrics of the window that hosts the ruler. Some devices report bogus
     * `xdpi`/`ydpi`; in that case fall back to the generalized density and flag the estimate.
     */
    fun onDisplayMetrics(xdpi: Float, ydpi: Float, densityDpi: Int) {
        val suspicious = isSuspicious(xdpi, densityDpi) || isSuspicious(ydpi, densityDpi)
        val usedX = if (suspicious) densityDpi.toFloat() else xdpi
        val usedY = if (suspicious) densityDpi.toFloat() else ydpi
        _uiState.update {
            if (it.reportedXdpi == xdpi && it.reportedYdpi == ydpi && it.xdpi == usedX && it.ydpi == usedY) {
                it
            } else {
                it.copy(
                    xdpi = usedX,
                    ydpi = usedY,
                    reportedXdpi = xdpi,
                    reportedYdpi = ydpi,
                    dpiSuspicious = suspicious,
                    markerA = null,
                    markerB = null,
                )
            }
        }
    }

    fun setImperial(imperial: Boolean) {
        prefs.rulerImperial = imperial
        _uiState.update { it.copy(imperial = imperial) }
    }

    fun setScale(scale: Float) {
        _uiState.update { it.copy(scale = scale.coerceIn(MIN_SCALE, MAX_SCALE)) }
    }

    fun nudgeScale(steps: Int) {
        setScale(_uiState.value.scale + steps * FINE_STEP)
        persistScale()
    }

    fun persistScale() {
        prefs.rulerScale = _uiState.value.scale
    }

    fun resetScale() {
        setScale(1f)
        persistScale()
    }

    fun setCardGuide(visible: Boolean) {
        _uiState.update { it.copy(showCardGuide = visible) }
    }

    /** Tap: place marker A, then B; afterwards move whichever marker is closer. */
    fun onTap(position: Offset) {
        _uiState.update {
            when {
                it.markerA == null -> it.copy(markerA = position)
                it.markerB == null -> it.copy(markerB = position)
                else -> moveNearest(it, position)
            }
        }
    }

    fun onDrag(position: Offset) {
        _uiState.update {
            if (it.markerA == null || it.markerB == null) onTapState(it, position) else moveNearest(it, position)
        }
    }

    fun clearMarkers() {
        _uiState.update { it.copy(markerA = null, markerB = null) }
    }

    /** The canvas size changed (rotation, window resize): pixel markers are no longer meaningful. */
    fun onCanvasResized() = clearMarkers()

    private fun onTapState(state: ScreenRulerUiState, position: Offset): ScreenRulerUiState = when {
        state.markerA == null -> state.copy(markerA = position)
        else -> state.copy(markerB = position)
    }

    private fun moveNearest(state: ScreenRulerUiState, position: Offset): ScreenRulerUiState {
        val a = state.markerA ?: return state.copy(markerA = position)
        val b = state.markerB ?: return state.copy(markerB = position)
        return if ((a - position).getDistance() <= (b - position).getDistance()) {
            state.copy(markerA = position)
        } else {
            state.copy(markerB = position)
        }
    }

    private fun isSuspicious(dpi: Float, densityDpi: Int): Boolean =
        dpi < 72f || dpi > 1200f || (densityDpi > 0 && abs(dpi - densityDpi) / densityDpi > 0.5f)

    internal companion object {
        const val MIN_SCALE = 0.85f
        const val MAX_SCALE = 1.15f
        const val FINE_STEP = 0.001f
    }
}
