package com.dragolabs.able.feature.tools.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageMathTest {
    private val gb = 1024L * 1024L * 1024L

    @Test
    fun percentRoundsAndClamps() {
        assertEquals(38, percentOf(48 * gb, 128 * gb))
        assertEquals(0, percentOf(5, 0))
        assertEquals(0, percentOf(-1, 100))
        assertEquals(100, percentOf(200, 100))
        assertEquals(1, percentOf(5, 1000))
    }

    @Test
    fun usedBytesNeverNegativeOrAboveTotal() {
        assertEquals(0L, usedBytes(100, 150))
        assertEquals(100L, usedBytes(100, -5))
        assertEquals(60L, usedBytes(100, 40))
    }

    @Test
    fun breakdownOtherIsRemainderOfUsed() {
        val b = buildBreakdown(
            totalBytes = 128 * gb,
            freeBytes = 80 * gb,
            measured = MeasuredCategories(apps = 20 * gb, images = 10 * gb, video = 5 * gb, audio = 1 * gb, documents = 1 * gb, downloads = 1 * gb),
        )
        val other = b.segments.single { it.kind == StorageKind.OTHER }
        assertEquals(10 * gb, other.bytes)
        assertEquals(48 * gb, b.usedBytes)
        assertEquals(38, b.usedPercent)
        assertFalse(b.hasUnmeasured)
    }

    @Test
    fun unmeasuredCategoriesReportMissingAccessAndFoldIntoOther() {
        val b = buildBreakdown(100, 40, MeasuredCategories(apps = null, images = 10))
        val apps = b.segments.single { it.kind == StorageKind.APPS }
        assertNull(apps.bytes)
        assertEquals(MissingAccess.USAGE_ACCESS, apps.missing)
        assertEquals(50L, b.segments.single { it.kind == StorageKind.OTHER }.bytes)
        assertTrue(b.hasUnmeasured)
    }

    @Test
    fun overlappingMeasurementsNeverProduceNegativeOther() {
        val b = buildBreakdown(100, 50, MeasuredCategories(apps = 60, images = 20))
        assertEquals(0L, b.segments.single { it.kind == StorageKind.OTHER }.bytes)
    }

    @Test
    fun freedBytesAreOnlyMeasuredIncreases() {
        assertEquals(300L, measuredFreedBytes(1_000, 1_300))
        assertEquals(0L, measuredFreedBytes(1_000, 900))
        assertEquals(150L, measuredCacheDecrease(mapOf("a.b" to 100L, "c.d" to 80L, "e.f" to 10L), mapOf("a.b" to 0L, "c.d" to 30L)))
    }

    @Test
    fun packageNameValidationIsStrict() {
        assertTrue(isValidPackageName("com.example.app"))
        assertTrue(isValidPackageName("org.a_b.C1"))
        assertFalse(isValidPackageName("single"))
        assertFalse(isValidPackageName("com.example; rm -rf /"))
        assertFalse(isValidPackageName("com..example"))
        assertFalse(isValidPackageName("1com.example"))
        assertFalse(isValidPackageName("com.example\napp"))
        assertFalse(isValidPackageName("com.example app"))
        assertFalse(isValidPackageName("com.\$(id)"))
    }
}
