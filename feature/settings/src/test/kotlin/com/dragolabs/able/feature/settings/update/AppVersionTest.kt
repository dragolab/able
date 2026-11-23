package com.dragolabs.able.feature.settings.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    private fun v(value: String) = requireNotNull(AppVersion.parse(value)) { "unparsed: $value" }

    @Test
    fun parsesReleaseTagsAndVersionNames() {
        assertEquals(AppVersion(0, 9, 0, listOf("beta")), v("able-0.9-beta"))
        assertEquals(AppVersion(0, 9, 1, listOf("beta")), v("able-0.9.1-beta"))
        assertEquals(AppVersion(0, 9, 0, listOf("beta")), v("v0.9.0-beta"))
        assertEquals(AppVersion(0, 9, 0, listOf("beta")), v("0.9.0-beta"))
        assertEquals(AppVersion(1, 0, 0), v("able-1.0"))
        assertEquals(AppVersion(1, 2, 3, listOf("rc", "2")), v("V1.2.3-RC.2"))
        assertEquals("0.9.0-beta", v("able-0.9-beta").toString())
    }

    @Test
    fun rejectsUnrelatedTags() {
        listOf(null, "", "latest", "able", "able-beta", "0", "nightly-2026", "1.2.3.4").forEach {
            assertNull("should not parse: $it", AppVersion.parse(it))
        }
    }

    @Test
    fun missingPatchEqualsZero() {
        assertEquals(0, v("able-0.9-beta").compareTo(v("0.9.0-beta")))
        assertEquals(v("0.9"), v("0.9.0"))
    }

    @Test
    fun ordersByNumbersThenPreRelease() {
        assertTrue(v("able-0.10-beta") > v("able-0.9-beta"))
        assertTrue(v("able-0.9.1-beta") > v("able-0.9-beta"))
        assertTrue(v("able-1.0-beta") > v("able-0.99-beta"))
        assertTrue(v("1.0.0") > v("1.0.0-beta"))
        assertTrue(v("1.0.0-rc") > v("1.0.0-beta"))
        assertTrue(v("1.0.0-beta.2") > v("1.0.0-beta.1"))
        assertTrue(v("1.0.0-beta.10") > v("1.0.0-beta.2"))
        assertTrue(v("1.0.0-beta.1") > v("1.0.0-beta"))
        assertTrue(v("able-0.8-beta") < v("0.9.0-beta"))
    }

    @Test
    fun ignoresBuildMetadataAndDebugSuffix() {
        assertEquals(0, v("0.9.0-beta+42").compareTo(v("able-0.9-beta")))
        assertEquals(0, v("0.9.0-beta-debug").compareTo(v("able-0.9-beta")))
        assertEquals(AppVersion(1, 0, 0), v("1.0.0-debug"))
    }
}
