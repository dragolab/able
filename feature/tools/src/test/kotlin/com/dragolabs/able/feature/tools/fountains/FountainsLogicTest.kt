package com.dragolabs.able.feature.tools.fountains

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLDecoder
import java.nio.file.Files

class OverpassQueryTest {

    private val box = BoundingBox(south = 45.46, west = 9.18, north = 45.48, east = 9.20)

    @Test
    fun queryHasHeaderFiltersBoxAndOutput() {
        val q = OverpassQuery.build(box)
        assertTrue(q.startsWith("[out:json][timeout:25];"))
        val bbox = "(45.460000,9.180000,45.480000,9.200000)"
        OverpassQuery.FILTERS.forEach { filter ->
            assertTrue("node $filter", q.contains("node$filter$bbox;"))
            assertTrue("way $filter", q.contains("way$filter$bbox;"))
        }
        assertTrue(q.contains("""node["amenity"="drinking_water"]"""))
        assertTrue(q.contains("""node["man_made"="water_tap"]["drinking_water"="yes"]"""))
        assertTrue(q.contains("""node["natural"="spring"]["drinking_water"="yes"]"""))
        assertTrue(q.contains("""node["amenity"="fountain"]["drinking_water"="yes"]"""))
        assertTrue(q.contains("""node["fountain"="drinking"]"""))
        assertTrue(q.trimEnd().endsWith("out center tags ${OverpassQuery.MAX_ELEMENTS};"))
    }

    @Test
    fun formBodyIsUrlEncodedDataParameter() {
        val q = OverpassQuery.build(box)
        val body = OverpassQuery.formBody(q)
        assertTrue(body.startsWith("data="))
        assertFalse(body.contains(' '))
        assertEquals(q, URLDecoder.decode(body.removePrefix("data="), "UTF-8"))
    }

    @Test
    fun localeDoesNotChangeDecimalSeparator() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.ITALY)
            assertTrue(OverpassQuery.build(box).contains("45.460000,9.180000"))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}

class OverpassParserTest {

    private val sample = """
        {
          "version": 0.6,
          "generator": "Overpass API",
          "osm3s": {"copyright": "The data included in this document is from www.openstreetmap.org."},
          "elements": [
            {"type": "node", "id": 1, "lat": 41.9, "lon": 12.5,
             "tags": {"amenity": "drinking_water", "name": "Nasone", "bottle": "yes", "wheelchair": "limited",
                      "check_date": "2024-05-01", "seasonal": "no", "operator": "Acea", "fee": "no"}},
            {"type": "node", "id": 2, "lat": 41.91, "lon": 12.51,
             "tags": {"man_made": "water_tap", "drinking_water": "yes", "survey:date": "2023-01-02"}},
            {"type": "node", "id": 3, "lat": 41.92, "lon": 12.52,
             "tags": {"man_made": "water_tap", "drinking_water": "no"}},
            {"type": "way", "id": 4, "center": {"lat": 41.93, "lon": 12.53},
             "tags": {"amenity": "fountain", "drinking_water": "yes", "opening_hours": "Mo-Su 08:00-20:00"}},
            {"type": "node", "id": 5, "lat": 41.94, "lon": 12.54,
             "tags": {"natural": "spring", "drinking_water": "yes", "seasonal": "summer"}},
            {"type": "node", "id": 6, "lat": 41.95, "lon": 12.55, "tags": {"fountain": "drinking"}},
            {"type": "node", "id": 7, "lat": 41.96, "lon": 12.56, "tags": {"amenity": "fountain"}},
            {"type": "node", "id": 8, "lat": 41.97, "lon": 12.57,
             "tags": {"amenity": "drinking_water", "drinking_water": "no"}},
            {"type": "relation", "id": 9, "tags": {"amenity": "drinking_water"}},
            {"type": "node", "id": 1, "lat": 41.9, "lon": 12.5, "tags": {"amenity": "drinking_water"}},
            {"type": "node", "id": 10, "tags": {"amenity": "drinking_water"}}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesKindsAndSkipsNonDrinkable() {
        val list = OverpassParser.parse(sample)
        assertEquals(listOf(1L, 2L, 4L, 5L, 6L), list.map { it.osmId })
        val byId = list.associateBy { it.osmId }
        assertEquals(FountainKind.DRINKING_WATER, byId.getValue(1).kind)
        assertEquals(FountainKind.WATER_TAP, byId.getValue(2).kind)
        assertEquals(FountainKind.FOUNTAIN, byId.getValue(4).kind)
        assertEquals(FountainKind.SPRING, byId.getValue(5).kind)
        assertEquals(FountainKind.DRINKING_WATER, byId.getValue(6).kind)
    }

    @Test
    fun parsesTags() {
        val f = OverpassParser.parse(sample).first { it.osmId == 1L }
        assertEquals("Nasone", f.name)
        assertEquals(TriState.YES, f.bottle)
        assertEquals(TriState.LIMITED, f.wheelchair)
        assertEquals(TriState.NO, f.fee)
        assertEquals("no", f.seasonal)
        assertEquals("2024-05-01", f.lastChecked)
        assertEquals("Acea", f.operator)
        assertEquals(GeoPos(41.9, 12.5), f.position)
        assertEquals("node/1", f.key)

        val tap = OverpassParser.parse(sample).first { it.osmId == 2L }
        assertNull(tap.name)
        assertEquals(TriState.UNKNOWN, tap.bottle)
        assertEquals("2023-01-02", tap.lastChecked)
    }

    @Test
    fun wayUsesCenter() {
        val way = OverpassParser.parse(sample).first { it.osmId == 4L }
        assertEquals(OsmType.WAY, way.osmType)
        assertEquals(GeoPos(41.93, 12.53), way.position)
        assertEquals("Mo-Su 08:00-20:00", way.openingHours)
    }

    @Test
    fun emptyElements() {
        assertTrue(OverpassParser.parse("""{"elements": []}""").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHtmlErrorPage() {
        OverpassParser.parse("<html><body>rate_limited</body></html>")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingElements() {
        OverpassParser.parse("""{"remark": "runtime error"}""")
    }

    @Test
    fun triState() {
        assertEquals(TriState.YES, OverpassParser.triState("Yes"))
        assertEquals(TriState.LIMITED, OverpassParser.triState("limited"))
        assertEquals(TriState.NO, OverpassParser.triState("no"))
        assertEquals(TriState.UNKNOWN, OverpassParser.triState(null))
        assertEquals(TriState.UNKNOWN, OverpassParser.triState("maybe"))
    }
}

class FountainGeoTest {

    @Test
    fun distanceKnownValues() {
        // Colosseum to St Peter's Square, Rome: ~3.4 km.
        val d = FountainGeo.distanceMeters(GeoPos(41.8902, 12.4922), GeoPos(41.9022, 12.4567))
        assertEquals(3_200.0, d, 150.0)
        // One degree of latitude ~ 111.2 km.
        assertEquals(111_195.0, FountainGeo.distanceMeters(GeoPos(0.0, 0.0), GeoPos(1.0, 0.0)), 50.0)
        assertEquals(0.0, FountainGeo.distanceMeters(GeoPos(45.0, 9.0), GeoPos(45.0, 9.0)), 1e-6)
    }

    @Test
    fun bearingAndCompass() {
        val o = GeoPos(45.0, 9.0)
        assertEquals(0.0, FountainGeo.bearingDegrees(o, GeoPos(45.01, 9.0)), 0.5)
        assertEquals(90.0, FountainGeo.bearingDegrees(o, GeoPos(45.0, 9.01)), 0.5)
        assertEquals(180.0, FountainGeo.bearingDegrees(o, GeoPos(44.99, 9.0)), 0.5)
        assertEquals(270.0, FountainGeo.bearingDegrees(o, GeoPos(45.0, 8.99)), 0.5)
        assertEquals(Compass.N, FountainGeo.compass(0.0))
        assertEquals(Compass.N, FountainGeo.compass(359.0))
        assertEquals(Compass.N, FountainGeo.compass(22.4))
        assertEquals(Compass.NE, FountainGeo.compass(22.6))
        assertEquals(Compass.E, FountainGeo.compass(90.0))
        assertEquals(Compass.SW, FountainGeo.compass(225.0))
        assertEquals(Compass.NW, FountainGeo.compass(-45.0))
    }

    @Test
    fun nearestSortsAndLimits() {
        val o = GeoPos(45.0, 9.0)
        val far = fountain(1, GeoPos(45.02, 9.0))
        val near = fountain(2, GeoPos(45.001, 9.0))
        val mid = fountain(3, GeoPos(45.0, 9.01))
        val result = FountainGeo.nearest(o, listOf(far, near, mid), limit = 2)
        assertEquals(listOf(2L, 3L), result.map { it.fountain.osmId })
        assertEquals(Compass.N, result[0].compass)
        assertEquals(Compass.E, result[1].compass)
    }

    @Test
    fun filters() {
        val bottle = fountain(1, GeoPos(0.0, 0.0), bottle = TriState.YES)
        val wheel = fountain(2, GeoPos(0.0, 0.0), wheelchair = TriState.LIMITED)
        val none = fountain(3, GeoPos(0.0, 0.0))
        val all = listOf(bottle, wheel, none)
        assertEquals(all, all.filter(FountainFilters()::matches))
        assertEquals(listOf(bottle), all.filter(FountainFilters(bottleOnly = true)::matches))
        assertEquals(listOf(wheel), all.filter(FountainFilters(wheelchairOnly = true)::matches))
        assertTrue(all.none(FountainFilters(bottleOnly = true, wheelchairOnly = true)::matches))
    }
}

class FountainAreaTest {

    @Test
    fun snapIsOutwardAndStable() {
        val box = BoundingBox(45.4612, 9.1811, 45.4788, 9.1999)
        val snapped = FountainArea.snap(box)
        assertTrue(snapped.contains(box))
        assertEquals(BoundingBox(45.46, 9.18, 45.48, 9.2), snapped)
        // A small pan inside the same grid cell maps to the same key.
        val panned = FountainArea.snap(BoundingBox(45.4620, 9.1820, 45.4790, 9.1990))
        assertEquals(FountainArea.key(snapped), FountainArea.key(panned))
    }

    @Test
    fun tooLarge() {
        assertFalse(FountainArea.isTooLarge(BoundingBox(45.40, 9.10, 45.55, 9.30)))
        assertTrue(FountainArea.isTooLarge(BoundingBox(45.0, 9.0, 45.5, 9.2)))
        assertTrue(FountainArea.isTooLarge(BoundingBox(45.0, 9.0, 45.1, 9.5)))
    }
}

class FountainLinksTest {

    @Test
    fun links() {
        val p = GeoPos(41.9, 12.5)
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=41.900000,12.500000&travelmode=walking",
            FountainLinks.walkingDirections(p),
        )
        assertEquals("geo:41.900000,12.500000?q=41.900000,12.500000(Fontanella%20%C3%A8)", FountainLinks.geoUri(p, "Fontanella è"))
        assertEquals("https://www.openstreetmap.org/way/4", FountainLinks.osmElement(fountain(4, p, type = OsmType.WAY)))
        assertEquals("41.900000, 12.500000", FountainLinks.coordinates(p))
    }
}

class FountainsRepositoryTest {

    private val body = """{"elements":[{"type":"node","id":1,"lat":45.47,"lon":9.19,"tags":{"amenity":"drinking_water"}}]}"""
    private val area = BoundingBox(45.46, 9.18, 45.48, 9.2)

    @Test
    fun cachesInMemoryAndOnDisk() = runBlocking {
        var calls = 0
        val dir = Files.createTempDirectory("fountains").toFile()
        try {
            val now = System.currentTimeMillis()
            val repo = FountainsRepository({ calls++; OverpassResponse.Ok(body) }, dir) { now }
            val first = repo.load(area, force = false) as FountainsResult.Success
            assertEquals(1, first.fountains.size)
            assertFalse(first.fromCache)
            repo.load(area, force = false)
            assertEquals(1, calls)
            repo.load(area, force = true)
            assertEquals(2, calls)
            assertTrue(File(dir, FountainArea.key(area) + ".json").isFile)

            // A new repository (process restart) reads the disk copy without a request.
            val again = FountainsRepository({ calls++; OverpassResponse.Ok(body) }, dir) { now }
            val cached = again.load(area, force = false) as FountainsResult.Success
            assertTrue(cached.fromCache)
            assertEquals(2, calls)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun expiredDiskCacheIsIgnored() = runBlocking {
        val dir = Files.createTempDirectory("fountains").toFile()
        try {
            val now = System.currentTimeMillis()
            FountainsRepository({ OverpassResponse.Ok(body) }, dir) { now }.load(area, force = false)
            val later = now + FountainsRepository.CACHE_TTL_MS + 60_000
            val repo = FountainsRepository({ OverpassResponse.Busy }, dir) { later }
            assertNull(repo.cached(area))
            assertEquals(FountainsResult.Busy, repo.load(area, force = false))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun mapsErrors() = runBlocking {
        assertEquals(FountainsResult.Offline, FountainsRepository({ OverpassResponse.Offline }, null).load(area, false))
        assertEquals(FountainsResult.Busy, FountainsRepository({ OverpassResponse.Busy }, null).load(area, false))
        assertEquals(FountainsResult.Failed, FountainsRepository({ OverpassResponse.Failed }, null).load(area, false))
        assertEquals(FountainsResult.Failed, FountainsRepository({ OverpassResponse.Ok("<html/>") }, null).load(area, false))
    }

    @Test
    fun userAgentIsDescriptive() {
        assertEquals("Able/0.9.3-beta (+https://github.com/dragolab/able)", FountainsRepository.userAgent("0.9.3-beta"))
    }
}

private fun fountain(
    id: Long,
    position: GeoPos,
    bottle: TriState = TriState.UNKNOWN,
    wheelchair: TriState = TriState.UNKNOWN,
    type: OsmType = OsmType.NODE,
) = Fountain(
    osmType = type,
    osmId = id,
    position = position,
    kind = FountainKind.DRINKING_WATER,
    name = null,
    bottle = bottle,
    wheelchair = wheelchair,
    fee = TriState.UNKNOWN,
    seasonal = null,
    openingHours = null,
    lastChecked = null,
    operator = null,
    description = null,
)
