package com.dragolabs.able.feature.battery.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Interprets raw kernel battery nodes read through Shizuku (BAT-HLT-002). Values that cannot be
 * interpreted plausibly return null so the UI keeps the raw value and never invents a date.
 */
object KernelBatteryValues {

    private val MIN_PLAUSIBLE: LocalDate = LocalDate.of(2000, 1, 1)

    /**
     * Parses `manufacture_date` / `first_usage_date`. The framework documents these properties as
     * seconds since the Unix epoch (BatteryManager.BATTERY_PROPERTY_MANUFACTURING_DATE and
     * BATTERY_PROPERTY_FIRST_USAGE_DATE, AOSP frameworks/base, checked 2026-11-22), but some
     * kernels report milliseconds, days since the epoch, or a `YYYYMMDD` / `YYYY-MM-DD` value.
     * The result must fall between 2000-01-01 and [today] + 1 year, otherwise null.
     */
    fun parseDate(raw: String?, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): LocalDate? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val maxPlausible = today.plusYears(1)
        fun LocalDate.plausible() = takeIf { !it.isBefore(MIN_PLAUSIBLE) && !it.isAfter(maxPlausible) }

        if (value.length == 10 && value[4] == '-' && value[7] == '-') {
            return try { LocalDate.parse(value).plausible() } catch (_: DateTimeParseException) { null }
        }
        val number = value.toLongOrNull() ?: return null
        if (number <= 0) return null
        // YYYYMMDD (e.g. 20230819) is checked first: as seconds it would be in 1970.
        if (value.length == 8) {
            val year = (number / 10_000).toInt()
            val month = (number / 100 % 100).toInt()
            val day = (number % 100).toInt()
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull()?.plausible()
            if (date != null) return date
        }
        val candidates = listOf(
            { Instant.ofEpochSecond(number).atOffset(ZoneOffset.UTC).toLocalDate() },
            { Instant.ofEpochMilli(number).atOffset(ZoneOffset.UTC).toLocalDate() },
            { LocalDate.ofEpochDay(number) },
        )
        val inferred = when {
            number >= 100_000_000_000L -> candidates[1]
            number >= 100_000_000L -> candidates[0]
            else -> candidates[2]
        }
        return runCatching { inferred() }.getOrNull()?.plausible()
    }

    /** Charging policy reported by the kernel / health HAL. */
    enum class ChargingPolicy { DEFAULT, ADAPTIVE_AON, ADAPTIVE_AC, ADAPTIVE_LONGLIFE }

    /**
     * Maps `charging_policy` using the framework constants BatteryManager.CHARGING_POLICY_DEFAULT = 1,
     * CHARGING_POLICY_ADAPTIVE_AON = 2, CHARGING_POLICY_ADAPTIVE_AC = 3,
     * CHARGING_POLICY_ADAPTIVE_LONGLIFE = 4 (@SystemApi, AOSP frameworks/base
     * core/java/android/os/BatteryManager.java, checked 2026-11-22; not listed in the public SDK
     * reference). Unknown values return null and the UI shows the raw value only.
     */
    fun parseChargingPolicy(raw: String?): ChargingPolicy? = when (raw?.trim()?.toIntOrNull()) {
        1 -> ChargingPolicy.DEFAULT
        2 -> ChargingPolicy.ADAPTIVE_AON
        3 -> ChargingPolicy.ADAPTIVE_AC
        4 -> ChargingPolicy.ADAPTIVE_LONGLIFE
        else -> null
    }
}
