package com.dragolabs.able.core.database.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.dragolabs.able.core.model.BatterySession
import com.dragolabs.able.core.model.BatterySessionNote

@Entity(tableName = "battery_sessions")
data class BatterySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val isCharging: Boolean,
    val startLevelPercent: Int,
    val endLevelPercent: Int,
    val avgTemperatureCelsius: Float,
    val maxTemperatureCelsius: Float,
    val avgCurrentMilliAmperes: Float?,
    val avgVoltageMillivolts: Float? = null,
    val avgPowerMilliWatts: Float? = null,
    val screenOnDurationMs: Long,
    val screenOffDurationMs: Long,
    val deepSleepDurationMs: Long,
    val hasThermalAlert: Boolean,
    @ColumnInfo(defaultValue = "0")
    val isPartial: Boolean = false,
    /** Whole-percent level drop observed while the screen was on (MON-001); null for older rows. */
    val screenOnDropPercent: Int? = null,
    /** Whole-percent level drop observed while the screen was off (MON-001); null for older rows. */
    val screenOffDropPercent: Int? = null
) {
    fun toDomain(): BatterySession = BatterySession(
        id = id,
        startTimeEpochMs = startTimeEpochMs,
        endTimeEpochMs = endTimeEpochMs,
        isCharging = isCharging,
        startLevelPercent = startLevelPercent,
        endLevelPercent = endLevelPercent,
        avgTemperatureCelsius = avgTemperatureCelsius,
        maxTemperatureCelsius = maxTemperatureCelsius,
        avgCurrentMilliAmperes = avgCurrentMilliAmperes,
        avgVoltageMillivolts = avgVoltageMillivolts,
        avgPowerMilliWatts = avgPowerMilliWatts,
        screenOnDurationMs = screenOnDurationMs,
        screenOffDurationMs = screenOffDurationMs,
        deepSleepDurationMs = deepSleepDurationMs,
        hasThermalAlert = hasThermalAlert,
        isPartial = isPartial
    )

    companion object {
        fun fromDomain(domain: BatterySession): BatterySessionEntity = BatterySessionEntity(
            id = domain.id,
            startTimeEpochMs = domain.startTimeEpochMs,
            endTimeEpochMs = domain.endTimeEpochMs,
            isCharging = domain.isCharging,
            startLevelPercent = domain.startLevelPercent,
            endLevelPercent = domain.endLevelPercent,
            avgTemperatureCelsius = domain.avgTemperatureCelsius,
            maxTemperatureCelsius = domain.maxTemperatureCelsius,
            avgCurrentMilliAmperes = domain.avgCurrentMilliAmperes,
            avgVoltageMillivolts = domain.avgVoltageMillivolts,
            avgPowerMilliWatts = domain.avgPowerMilliWatts,
            screenOnDurationMs = domain.screenOnDurationMs,
            screenOffDurationMs = domain.screenOffDurationMs,
            deepSleepDurationMs = domain.deepSleepDurationMs,
            hasThermalAlert = domain.hasThermalAlert,
            isPartial = domain.isPartial
        )
    }
}

@Entity(tableName = "battery_notes")
data class BatteryNoteEntity(
    @PrimaryKey
    val sessionId: Long,
    val noteText: String,
    val tagsJson: String,
    val createdAtEpochMs: Long
)
