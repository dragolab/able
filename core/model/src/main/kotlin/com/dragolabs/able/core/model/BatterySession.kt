package com.dragolabs.able.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class BatteryState {
    DISCHARGING,
    CHARGING,
    FULL,
    NOT_CHARGING,
    UNKNOWN
}

/**
 * Historical battery measurement interval (MON-001 - MON-009, BAT-CHG-001).
 */
@Serializable
data class BatterySession(
    val id: Long = 0,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val isCharging: Boolean,
    val startLevelPercent: Int,
    val endLevelPercent: Int,
    val avgTemperatureCelsius: Float,
    val maxTemperatureCelsius: Float,
    val avgCurrentMilliAmperes: Float? = null,
    val avgVoltageMillivolts: Float? = null,
    val avgPowerMilliWatts: Float? = null,
    val screenOnDurationMs: Long = 0,
    val screenOffDurationMs: Long = 0,
    val deepSleepDurationMs: Long = 0,
    val hasThermalAlert: Boolean = false,
    val isPartial: Boolean = false
)

/**
 * User notes or tags attached to a specific charging/discharging session (BAT-NOTE-001).
 */
@Serializable
data class BatterySessionNote(
    val sessionId: Long,
    val noteText: String,
    val tags: List<String> = emptyList(),
    val createdAtEpochMs: Long
)

/**
 * Statistical baseline for idle drain (BASE-001, BASE-002).
 */
@Serializable
data class IdleDrainBaseline(
    val baselinePercentPerHour: Float,
    val validSampleCount: Int,
    val calculationWindowDays: Int = 7,
    val lastCalculatedEpochMs: Long,
    val alertThresholdPercentPerHour: Float = 3.0f,
    val alertEnabled: Boolean = false
)
