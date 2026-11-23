package com.dragolabs.able.feature.battery.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class KernelBatteryValuesTest {

    private val today = LocalDate.of(2026, 11, 22)

    @Test
    fun parsesEpochSecondsMillisDaysAndCalendarForms() {
        val expected = LocalDate.of(2023, 8, 19)
        assertEquals(expected, KernelBatteryValues.parseDate("1692403200", today))
        assertEquals(expected, KernelBatteryValues.parseDate("1692403200000", today))
        assertEquals(expected, KernelBatteryValues.parseDate(expected.toEpochDay().toString(), today))
        assertEquals(expected, KernelBatteryValues.parseDate("20230819", today))
        assertEquals(expected, KernelBatteryValues.parseDate(" 2023-08-19 ", today))
    }

    @Test
    fun rejectsImplausibleOrMissingDates() {
        assertNull(KernelBatteryValues.parseDate(null, today))
        assertNull(KernelBatteryValues.parseDate("", today))
        assertNull(KernelBatteryValues.parseDate("0", today))
        assertNull(KernelBatteryValues.parseDate("-5", today))
        assertNull(KernelBatteryValues.parseDate("abc", today))
        assertNull(KernelBatteryValues.parseDate("20231345", today))
        assertNull(KernelBatteryValues.parseDate("4102444800", today)) // 2100-01-01
        assertNull(KernelBatteryValues.parseDate("12", today)) // 1970-01-13
    }

    @Test
    fun mapsChargingPolicyConstants() {
        assertEquals(KernelBatteryValues.ChargingPolicy.DEFAULT, KernelBatteryValues.parseChargingPolicy("1"))
        assertEquals(KernelBatteryValues.ChargingPolicy.ADAPTIVE_AON, KernelBatteryValues.parseChargingPolicy("2"))
        assertEquals(KernelBatteryValues.ChargingPolicy.ADAPTIVE_AC, KernelBatteryValues.parseChargingPolicy("3"))
        assertEquals(KernelBatteryValues.ChargingPolicy.ADAPTIVE_LONGLIFE, KernelBatteryValues.parseChargingPolicy(" 4\n"))
        assertNull(KernelBatteryValues.parseChargingPolicy("0"))
        assertNull(KernelBatteryValues.parseChargingPolicy("7"))
        assertNull(KernelBatteryValues.parseChargingPolicy(null))
    }
}
