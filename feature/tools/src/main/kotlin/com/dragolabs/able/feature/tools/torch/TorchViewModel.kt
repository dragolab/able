package com.dragolabs.able.feature.tools.torch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.UiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TorchMode { STEADY, SOS, MORSE, INTERVAL }

@Immutable
data class TorchUiState(
    val available: Boolean,
    val isOn: Boolean = false,
    val cameraBusy: Boolean = false,
    val mode: TorchMode = TorchMode.STEADY,
    val patternRunning: Boolean = false,
    val strengthSupported: Boolean = false,
    val maxStrength: Int = 1,
    val strength: Int = 1,
    val morseText: String = "SOS",
    val intervalMs: Long = TorchPatterns.DEFAULT_INTERVAL_MS,
    val message: UiMessage? = null,
)

/** A torch step: [on] for [durationMs]. */
data class TorchStep(val on: Boolean, val durationMs: Long)

/** Flash pattern encoding (TORCH-002). International Morse timing, 1 unit = [UNIT_MS]. */
object TorchPatterns {
    const val UNIT_MS = 200L
    const val DEFAULT_INTERVAL_MS = 500L
    val INTERVAL_OPTIONS_MS = listOf(250L, 500L, 1_000L)

    private val MORSE = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
        'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
        'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
        'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---",
        '3' to "...--", '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
        '8' to "---..", '9' to "----.",
    )

    fun isEncodable(text: String): Boolean = text.uppercase().any { it in MORSE }

    /** Encodes [text]; characters without a Morse code are skipped. Ends with a word gap. */
    fun morse(text: String): List<TorchStep> {
        val steps = mutableListOf<TorchStep>()
        text.uppercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
            word.mapNotNull { MORSE[it] }.forEachIndexed { charIndex, code ->
                if (charIndex > 0) steps += TorchStep(false, 3 * UNIT_MS)
                code.forEachIndexed { symbolIndex, symbol ->
                    if (symbolIndex > 0) steps += TorchStep(false, UNIT_MS)
                    steps += TorchStep(true, if (symbol == '.') UNIT_MS else 3 * UNIT_MS)
                }
            }
            steps += TorchStep(false, 7 * UNIT_MS)
        }
        return steps
    }

    val SOS: List<TorchStep> = morse("SOS")

    fun interval(periodMs: Long): List<TorchStep> = listOf(TorchStep(true, periodMs), TorchStep(false, periodMs))
}

class TorchViewModel(private val controller: TorchController) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TorchUiState(
            available = controller.isAvailable,
            strengthSupported = controller.supportsStrength,
            maxStrength = controller.maxStrength,
            strength = controller.currentStrength() ?: controller.defaultStrength,
        ),
    )
    val uiState: StateFlow<TorchUiState> = _uiState.asStateFlow()

    private var patternJob: Job? = null

    init {
        if (controller.isAvailable) {
            viewModelScope.launch {
                controller.events().collect { event ->
                    when (event) {
                        is TorchEvent.Mode -> _uiState.update { it.copy(isOn = event.enabled, cameraBusy = false) }
                        is TorchEvent.Strength -> _uiState.update { it.copy(strength = event.level) }
                        TorchEvent.Unavailable -> onCameraConflict()
                    }
                }
            }
        }
    }

    fun selectMode(mode: TorchMode) {
        if (mode == _uiState.value.mode) return
        stopPattern(turnOff = _uiState.value.patternRunning)
        _uiState.update { it.copy(mode = mode) }
    }

    fun toggleSteady() {
        stopPattern(turnOff = false)
        val state = _uiState.value
        val target = !state.isOn
        val accepted = controller.setTorch(target, state.strength.takeIf { state.strengthSupported })
        if (!accepted) _uiState.update { it.copy(message = UiMessage(R.string.tools_torch_rejected)) }
    }

    fun setStrength(level: Int) {
        val state = _uiState.value
        val clamped = level.coerceIn(1, state.maxStrength)
        _uiState.update { it.copy(strength = clamped) }
        if (state.isOn && !state.patternRunning) {
            if (!controller.setTorch(true, clamped)) {
                _uiState.update { it.copy(message = UiMessage(R.string.tools_torch_rejected)) }
            }
        }
    }

    fun setMorseText(text: String) {
        _uiState.update { it.copy(morseText = text.take(MAX_MORSE_LENGTH)) }
    }

    fun setInterval(periodMs: Long) {
        val wasRunning = _uiState.value.patternRunning
        _uiState.update { it.copy(intervalMs = periodMs) }
        if (wasRunning && _uiState.value.mode == TorchMode.INTERVAL) startPattern()
    }

    fun startPattern() {
        val state = _uiState.value
        val steps = when (state.mode) {
            TorchMode.STEADY -> return
            TorchMode.SOS -> TorchPatterns.SOS
            TorchMode.MORSE -> {
                if (!TorchPatterns.isEncodable(state.morseText)) {
                    _uiState.update { it.copy(message = UiMessage(R.string.tools_torch_morse_invalid)) }
                    return
                }
                TorchPatterns.morse(state.morseText)
            }
            TorchMode.INTERVAL -> TorchPatterns.interval(state.intervalMs)
        }
        patternJob?.cancel()
        _uiState.update { it.copy(patternRunning = true) }
        patternJob = viewModelScope.launch {
            val strength = state.strength.takeIf { state.strengthSupported }
            while (isActive) {
                for (step in steps) {
                    if (!controller.setTorch(step.on, strength)) {
                        // Android refused (camera in use or flash lost): stop instead of pretending.
                        stopAfterFailure(UiMessage(R.string.tools_torch_pattern_stopped_conflict))
                        return@launch
                    }
                    delay(step.durationMs)
                }
            }
        }
    }

    /** Stops any flashing pattern. Called from the stop button, on dispose and when cleared. */
    fun stopPattern(turnOff: Boolean = true) {
        val wasRunning = patternJob?.isActive == true || _uiState.value.patternRunning
        patternJob?.cancel()
        patternJob = null
        if (wasRunning) {
            _uiState.update { it.copy(patternRunning = false) }
            if (turnOff) controller.setTorch(false)
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun onCameraConflict() {
        val wasRunning = _uiState.value.patternRunning
        patternJob?.cancel()
        patternJob = null
        _uiState.update {
            it.copy(
                isOn = false,
                cameraBusy = true,
                patternRunning = false,
                message = if (wasRunning) UiMessage(R.string.tools_torch_pattern_stopped_conflict) else it.message,
            )
        }
    }

    private fun stopAfterFailure(message: UiMessage) {
        patternJob = null
        controller.setTorch(false)
        _uiState.update { it.copy(patternRunning = false, message = message) }
    }

    override fun onCleared() {
        stopPattern(turnOff = true)
    }

    private companion object {
        const val MAX_MORSE_LENGTH = 40
    }
}
