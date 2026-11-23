package com.dragolabs.able.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.core.database.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    /** Telemetry stays off unless the user explicitly picks "Yes" (PRIV-001). */
    val telemetryOptIn: Boolean = false,
    val finishing: Boolean = false,
    val finished: Boolean = false,
)

/**
 * First-launch flow 1: welcome, telemetry consent, capability overview. No permission is
 * requested here; accesses are only explained.
 */
class OnboardingViewModel(private val preferences: UserPreferencesRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        // Keep a previously given answer instead of silently resetting it.
        viewModelScope.launch {
            val prefs = preferences.userPreferencesFlow.first()
            if (prefs.telemetryPromptAnswered) _uiState.update { it.copy(telemetryOptIn = prefs.telemetryOptIn) }
        }
    }

    fun setTelemetryChoice(optIn: Boolean) = _uiState.update { it.copy(telemetryOptIn = optIn) }

    /**
     * Completes (or skips) onboarding. The telemetry answer is stored so the question is never
     * repeated; skipping before choosing records "No".
     */
    fun finish() {
        if (_uiState.value.finishing) return
        _uiState.update { it.copy(finishing = true) }
        viewModelScope.launch {
            val prefs = preferences.userPreferencesFlow.first()
            val choice = _uiState.value.telemetryOptIn
            if (!prefs.telemetryPromptAnswered || prefs.telemetryOptIn != choice) {
                preferences.setTelemetryOptIn(choice)
            }
            preferences.setOnboardingCompleted(true)
            _uiState.update { it.copy(finishing = false, finished = true) }
        }
    }
}
