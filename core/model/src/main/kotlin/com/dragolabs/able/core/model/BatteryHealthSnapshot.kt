package com.dragolabs.able.core.model

import kotlinx.serialization.Serializable

/**
 * Deep hardware battery health insights (BAT-HLT-001, BAT-HLT-002, ADR-018).
 */
@Serializable
data class BatteryHealthSnapshot(
    val timestampEpochMs: Long,
    val cycleCount: Int? = null,
    val stateOfHealthPercent: Int? = null,
    val nominalCapacityMilliAmpereHours: Int? = null,
    val designCapacityMilliAmpereHours: Int? = null,
    val manufactureDate: String? = null,
    val firstUsageDate: String? = null,
    val chargingPolicy: String? = null,
    val isShizukuPrivileged: Boolean = false
)

/**
 * Identified battery anomaly checklist item (BAT-CHK-001).
 */
@Serializable
data class BatteryAnomaly(
    val id: String,
    val title: String,
    val description: String,
    val severity: AnomalySeverity,
    val detectedAtEpochMs: Long,
    val systemSettingsIntentAction: String? = null
)

@Serializable
enum class AnomalySeverity {
    INFO,
    WARNING,
    CRITICAL
}
