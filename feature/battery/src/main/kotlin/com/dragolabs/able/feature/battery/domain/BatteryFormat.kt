package com.dragolabs.able.feature.battery.domain

import android.content.res.Resources
import com.dragolabs.able.core.database.TemperatureUnit
import com.dragolabs.able.feature.battery.R
import java.util.Locale

/** Localized formatting shared by the screen and the monitor notification. */
object BatteryFormat {

    fun decimal(value: Float, digits: Int = 1): String =
        String.format(Locale.getDefault(), "%.${digits}f", value)

    fun toUnit(celsius: Float, unit: TemperatureUnit): Float = when (unit) {
        TemperatureUnit.CELSIUS -> celsius
        TemperatureUnit.FAHRENHEIT -> celsius * 9f / 5f + 32f
    }

    fun temperature(res: Resources, celsius: Float, unit: TemperatureUnit, digits: Int = 1): String {
        val value = decimal(toUnit(celsius, unit), digits)
        return when (unit) {
            TemperatureUnit.CELSIUS -> res.getString(R.string.battery_unit_celsius, value)
            TemperatureUnit.FAHRENHEIT -> res.getString(R.string.battery_unit_fahrenheit, value)
        }
    }

    fun duration(res: Resources, durationMs: Long): String {
        val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            res.getString(R.string.battery_duration_hm, hours.toInt(), minutes.toInt())
        } else {
            res.getString(R.string.battery_duration_m, minutes.toInt())
        }
    }

    fun ratePerHour(res: Resources, rate: Float): String =
        res.getString(R.string.battery_rate_per_hour, decimal(rate))
}
