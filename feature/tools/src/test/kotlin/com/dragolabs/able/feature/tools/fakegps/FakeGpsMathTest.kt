package com.dragolabs.able.feature.tools.fakegps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class CoordinateParserTest {

    private fun assertParsed(input: String, lat: Double, lon: Double, tolerance: Double = 1e-5) {
        val result = CoordinateParser.parse(input)
        assertNotNull("'$input' should parse", result)
        assertEquals("lat of '$input'", lat, result!!.latitude, tolerance)
        assertEquals("lon of '$input'", lon, result.longitude, tolerance)
    }

    @Test
    fun decimalPairs() {
        assertParsed("45.464203, 9.189982", 45.464203, 9.189982)
        assertParsed("45.464203,9.189982", 45.464203, 9.189982)
        assertParsed("  -33.8688 151.2093 ", -33.8688, 151.2093)
        assertParsed("40.7128; -74.0060", 40.7128, -74.006)
        assertParsed("45,464203 9,189982", 45.464203, 9.189982)
        assertParsed("45, 9", 45.0, 9.0)
    }

    @Test
    fun dms() {
        assertParsed("45°27'51.2\"N 9°11'29.0\"E", 45.464222, 9.191389)
        assertParsed("33°52'07.7\"S 151°12'33.5\"E", -33.868806, 151.209306)
        assertParsed("40°42′46″N, 74°00′22″W", 40.712778, -74.006111)
        assertParsed("N 45°27'51.2\" E 9°11'29.0\"", 45.464222, 9.191389)
        // Longitude first, assigned by hemisphere letter.
        assertParsed("9°11'29.0\"E 45°27'51.2\"N", 45.464222, 9.191389)
        // Degrees and decimal minutes.
        assertParsed("45°27.853'N 9°11.483'E", 45.464217, 9.191383)
        // Decimal comma inside DMS.
        assertParsed("45°27'51,2\"N 9°11'29,0\"E", 45.464222, 9.191389)
    }

    @Test
    fun hemisphereDecimal() {
        assertParsed("45.4642N 9.1900E", 45.4642, 9.19)
        assertParsed("33.8688 S, 151.2093 E", -33.8688, 151.2093)
        assertParsed("N 45.4642 W 9.19", 45.4642, -9.19)
    }

    @Test
    fun uris() {
        assertParsed("geo:45.4642,9.19?q=45.4642,9.19(Duomo)", 45.4642, 9.19)
        assertParsed("https://www.google.com/maps/@45.4642035,9.189982,17z", 45.4642035, 9.189982)
        assertParsed("https://maps.google.com/?q=41.9028,12.4964", 41.9028, 12.4964)
        assertParsed("https://www.openstreetmap.org/?mlat=1&ll=48.8584,2.2945", 48.8584, 2.2945)
    }

    @Test
    fun rejectsInvalid() {
        assertNull(CoordinateParser.parse(""))
        assertNull(CoordinateParser.parse("hello"))
        assertNull(CoordinateParser.parse("91.0, 10.0"))
        assertNull(CoordinateParser.parse("45.0, 181.0"))
        assertNull(CoordinateParser.parse("45.0"))
        assertNull(CoordinateParser.parse("45°61'00\"N 9°00'00\"E"))
        assertNull(CoordinateParser.parse("45°27'51\"N 9°11'29\"N"))
        assertNull(CoordinateParser.parse("45.1 9.2 3.3"))
        assertNull(CoordinateParser.parse("45.4642N 9.19E extra"))
    }
}

class GeoMathTest {

    @Test
    fun distanceAndBearing() {
        val milan = LatLon(45.4642, 9.19)
        val rome = LatLon(41.9028, 12.4964)
        assertEquals(477_000.0, GeoMath.distanceMeters(milan, rome), 3_000.0)
        assertEquals(0.0, GeoMath.bearingDegrees(LatLon(0.0, 0.0), LatLon(1.0, 0.0)), 1e-9)
        assertEquals(90.0, GeoMath.bearingDegrees(LatLon(0.0, 0.0), LatLon(0.0, 1.0)), 1e-9)
    }

    @Test
    fun destinationRoundTrip() {
        val start = LatLon(45.0, 9.0)
        val end = GeoMath.destination(start, 37.0, 1_234.0)
        assertEquals(1_234.0, GeoMath.distanceMeters(start, end), 0.01)
        assertEquals(37.0, GeoMath.bearingDegrees(start, end), 0.01)
    }

    @Test
    fun longitudeWraps() {
        assertEquals(-179.0, GeoMath.normalizeLongitude(181.0), 1e-9)
        assertEquals(179.0, GeoMath.normalizeLongitude(-181.0), 1e-9)
        assertEquals(180.0, GeoMath.normalizeLongitude(180.0), 1e-9)
        val east = GeoMath.destination(LatLon(0.0, 179.9999), 90.0, 100.0)
        assertTrue(east.isValid)
    }

    @Test
    fun joystickBearing() {
        assertNull(GeoMath.joystickBearing(0f, 0f))
        assertEquals(0.0, GeoMath.joystickBearing(0f, -1f)!!, 1e-6) // up = north
        assertEquals(90.0, GeoMath.joystickBearing(1f, 0f)!!, 1e-6) // right = east
        assertEquals(180.0, GeoMath.joystickBearing(0f, 1f)!!, 1e-6)
        assertEquals(270.0, GeoMath.joystickBearing(-1f, 0f)!!, 1e-6)
    }
}

class RoutePathTest {
    private val a = LatLon(45.0, 9.0)
    private val b = GeoMath.destination(a, 90.0, 1_000.0)
    private val c = GeoMath.destination(b, 0.0, 1_000.0)

    @Test
    fun onceInterpolatesAndFinishes() {
        val mid = RoutePath.sample(listOf(a, b, c), RouteRepeat.ONCE, 500.0)!!
        assertEquals(500.0, GeoMath.distanceMeters(a, mid.position), 0.5)
        assertEquals(90.0, mid.bearingDegrees, 0.1)
        assertFalse(mid.finished)
        assertEquals(0.25, mid.progress, 1e-3)

        val second = RoutePath.sample(listOf(a, b, c), RouteRepeat.ONCE, 1_500.0)!!
        assertEquals(500.0, GeoMath.distanceMeters(b, second.position), 0.5)
        assertEquals(0.0, second.bearingDegrees, 0.1)

        val end = RoutePath.sample(listOf(a, b, c), RouteRepeat.ONCE, 5_000.0)!!
        assertTrue(end.finished)
        assertEquals(0.0, GeoMath.distanceMeters(c, end.position), 0.5)
    }

    @Test
    fun loopClosesBackToStart() {
        val total = RoutePath(listOf(a, b, c), closed = true).totalLength
        assertEquals(2_000.0 + GeoMath.distanceMeters(c, a), total, 0.5)
        val lap = RoutePath.sample(listOf(a, b, c), RouteRepeat.LOOP, total + 500.0)!!
        assertEquals(500.0, GeoMath.distanceMeters(a, lap.position), 0.5)
        assertFalse(lap.finished)
        // On the closing segment the heading points back to the start.
        val closing = RoutePath.sample(listOf(a, b, c), RouteRepeat.LOOP, total - 10.0)!!
        assertTrue(GeoMath.distanceMeters(a, closing.position) < 11.0)
    }

    @Test
    fun backAndForthReverses() {
        val waypoints = listOf(a, b)
        val out = RoutePath.sample(waypoints, RouteRepeat.BACK_AND_FORTH, 250.0)!!
        assertEquals(90.0, out.bearingDegrees, 0.1)
        val back = RoutePath.sample(waypoints, RouteRepeat.BACK_AND_FORTH, 1_250.0)!!
        assertEquals(750.0, GeoMath.distanceMeters(a, back.position), 0.5)
        assertEquals(270.0, back.bearingDegrees, 0.1)
        val again = RoutePath.sample(waypoints, RouteRepeat.BACK_AND_FORTH, 2_250.0)!!
        assertEquals(250.0, GeoMath.distanceMeters(a, again.position), 0.5)
        assertFalse(back.finished)
    }

    @Test
    fun degenerateRoutes() {
        assertNull(RoutePath.sample(emptyList(), RouteRepeat.LOOP, 10.0))
        val single = RoutePath.sample(listOf(a), RouteRepeat.LOOP, 10.0)!!
        assertEquals(a, single.position)
        assertTrue(single.finished)
        val same = RoutePath.sample(listOf(a, a), RouteRepeat.BACK_AND_FORTH, 10.0)!!
        assertEquals(a, same.position)
    }
}

class JitterModelTest {

    @Test
    fun offsetStaysWithinRadius() {
        val jitter = JitterModel(Random(42))
        for (radius in listOf(0.5, 2.5, 10.0)) {
            repeat(5_000) {
                val (north, east) = jitter.next(radius)
                assertTrue("offset ${hypot(north, east)} > $radius", hypot(north, east) <= radius + 1e-9)
            }
        }
    }

    @Test
    fun jitterActuallyMoves() {
        val jitter = JitterModel(Random(7))
        val samples = (0 until 200).map { jitter.next(5.0) }.toSet()
        assertTrue(samples.size > 150)
    }

    @Test
    fun zeroRadiusIsStatic() {
        val jitter = JitterModel(Random(1))
        jitter.next(5.0)
        assertEquals(0.0 to 0.0, jitter.next(0.0))
    }

    @Test
    fun accuracyAndAltitudeBounds() {
        val jitter = JitterModel(Random(3))
        repeat(5_000) {
            val accuracy = jitter.accuracy(10f)
            assertTrue(accuracy in 8f..12f)
            assertTrue(jitter.accuracy(1f) >= 1f)
            val altitude = jitter.altitude(100.0)
            assertTrue(altitude in (100.0 - JitterModel.ALTITUDE_NOISE_M)..(100.0 + JitterModel.ALTITUDE_NOISE_M))
        }
        assertEquals(0.5, JitterModel.radiusFor(0.2f), 1e-9)
        assertEquals(2.5, JitterModel.radiusFor(5f), 1e-9)
        assertEquals(10.0, JitterModel.radiusFor(50f), 1e-9)
    }

    @Test
    fun offsetAppliedOnMap() {
        val base = LatLon(45.0, 9.0)
        val moved = GeoMath.offset(base, 3.0, 4.0)
        assertEquals(5.0, GeoMath.distanceMeters(base, moved), 1e-3)
    }
}
