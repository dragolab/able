package com.dragolabs.able.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsPointTest {

    @Test
    fun toCoordinateString_formatsCorrectly() {
        val point = GpsPoint(
            id = 1,
            latitude = 45.4642035,
            longitude = 9.189982,
            accuracyMeters = 3.5f,
            timestampEpochMs = 1700000000000L,
            label = "Duomo di Milano"
        )

        val coords = point.toCoordinateString()
        assertEquals("45.464204, 9.189982", coords)
    }

    @Test
    fun toGeoUriString_includesEncodedLabel() {
        val point = GpsPoint(
            id = 2,
            latitude = 41.9027835,
            longitude = 12.4963655,
            accuracyMeters = 5.0f,
            timestampEpochMs = 1700000000000L,
            label = "Colosseo Roma"
        )

        val geoUri = point.toGeoUriString()
        assertTrue(geoUri.startsWith("geo:41.902784,12.496366?q=41.902784,12.496366("))
        assertTrue(geoUri.contains("Colosseo%20Roma"))
    }
}
