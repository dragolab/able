package com.dragolabs.able.core.model

/**
 * Point-in-time battery reading as reported by Android (BatteryManager). Every hardware-dependent
 * field is nullable: null means Android/the device did not report it (MON-006, BAT-HLT-001).
 */
data class BatteryStatus(
    val levelPercent: Int?,
    val state: BatteryState,
    val plugType: PlugType,
    val temperatureCelsius: Float?,
    val voltageMillivolts: Int?,
    /** Instantaneous current in microamperes; sign convention depends on the device. */
    val currentMicroAmperes: Int?,
    val cycleCount: Int?,
    val health: BatteryHealth,
    val technology: String?,
    val chargeTimeRemainingMs: Long?,
    val timestampEpochMs: Long,
) {
    val isPluggedIn: Boolean get() = plugType != PlugType.NONE
    val isCharging: Boolean get() = state == BatteryState.CHARGING || state == BatteryState.FULL

    /** Estimated power in milliwatts when both voltage and current are reported. */
    val powerMilliWatts: Float?
        get() {
            val v = voltageMillivolts ?: return null
            val i = currentMicroAmperes ?: return null
            return kotlin.math.abs(v.toFloat() * i.toFloat() / 1_000_000f)
        }

    companion object {
        val Unavailable = BatteryStatus(
            levelPercent = null,
            state = BatteryState.UNKNOWN,
            plugType = PlugType.NONE,
            temperatureCelsius = null,
            voltageMillivolts = null,
            currentMicroAmperes = null,
            cycleCount = null,
            health = BatteryHealth.UNKNOWN,
            technology = null,
            chargeTimeRemainingMs = null,
            timestampEpochMs = 0L,
        )
    }
}

enum class PlugType { NONE, AC, USB, WIRELESS, DOCK }

enum class BatteryHealth { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, COLD, FAILURE, UNKNOWN }

/** Internal storage usage summary for the Home card (STO-001). */
data class StorageSummary(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}
