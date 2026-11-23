package com.dragolabs.able.feature.battery.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenTimeTest {
    private val hour = 60L * 60 * 1000
    private val midnight = 100 * hour
    private val now = midnight + 10 * hour

    @Test
    fun nothingRecordedTodayIsNull() {
        val yesterday = ScreenInterval(midnight - 5 * hour, midnight - hour, hour, 3 * hour)
        assertNull(ScreenTimeMath.today(listOf(yesterday), midnight, now))
    }

    @Test
    fun sessionsInsideTodayCountFully() {
        val a = ScreenInterval(midnight + hour, midnight + 3 * hour, hour, hour)
        val b = ScreenInterval(midnight + 4 * hour, midnight + 9 * hour, 2 * hour, 3 * hour)
        val today = ScreenTimeMath.today(listOf(a, b), midnight, now)!!
        assertEquals(3 * hour, today.screenOnMs)
        assertEquals(4 * hour, today.screenOffMs)
    }

    @Test
    fun sessionAcrossMidnightIsProrated() {
        // 8 hours, half of them today: half of the recorded screen time is attributed to today.
        val overnight = ScreenInterval(midnight - 4 * hour, midnight + 4 * hour, 2 * hour, 6 * hour)
        val today = ScreenTimeMath.today(listOf(overnight), midnight, now)!!
        assertEquals(hour, today.screenOnMs)
        assertEquals(3 * hour, today.screenOffMs)
    }
}
