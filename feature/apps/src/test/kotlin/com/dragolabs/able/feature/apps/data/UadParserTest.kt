package com.dragolabs.able.feature.apps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class UadParserTest {

    @Test
    fun parsesAllFields() {
        val json = """
            {
              "com.example.bloat": {
                "list": "Oem",
                "description": "Bloat app.\nSee https://example.com/info.",
                "dependencies": ["com.example.core"],
                "neededBy": ["com.example.other"],
                "labels": ["ads"],
                "removal": "Recommended"
              }
            }
        """.trimIndent()
        val entry = UadParser.parse(json).getValue("com.example.bloat")
        assertEquals(UadList.OEM, entry.list)
        assertEquals(RemovalLevel.RECOMMENDED, entry.removal)
        assertEquals("Bloat app.\nSee https://example.com/info.", entry.description)
        assertEquals(listOf("com.example.core"), entry.dependencies)
        assertEquals(listOf("com.example.other"), entry.neededBy)
        assertEquals(listOf("ads"), entry.labels)
    }

    @Test
    fun toleratesUnknownMissingAndNullValues() {
        val json = """
            {
              "a.one": { "list": "Satellite", "removal": "Maybe", "futureKey": {"x": 1} },
              "a.two": { "description": null, "dependencies": null, "removal": "unsafe", "list": "GOOGLE" },
              "a.bad": "not an object",
              "a.three": {}
            }
        """.trimIndent()
        val result = UadParser.parse(json)
        val one = result.getValue("a.one")
        assertNull(one.list)
        assertNull(one.removal)
        assertEquals("", one.description)
        val two = result.getValue("a.two")
        assertEquals(RemovalLevel.UNSAFE, two.removal)
        assertEquals(UadList.GOOGLE, two.list)
        assertTrue(two.dependencies.isEmpty())
        assertTrue("a.three" in result)
        assertTrue("a.bad" !in result)
    }

    @Test
    fun parsesBundledSnapshot() {
        val file = File("src/main/assets/uad/uad_lists.json")
        assumeTrue("snapshot not found from working directory", file.exists())
        val result = UadParser.parse(file.readText())
        assertTrue("expected thousands of entries, got ${result.size}", result.size > 1000)
        val withRemoval = result.values.count { it.removal != null }
        assertTrue("most entries must have a known removal level", withRemoval > result.size * 0.95)
        assertEquals(RemovalLevel.UNSAFE, result["android.ext.shared"]?.removal)
    }

    @Test
    fun translations_replaceDescriptionsOnlyWhereAvailable() {
        val base = UadParser.parse("""{"a.b":{"list":"Oem","description":"English A"},"c.d":{"list":"Misc","description":"English C"}}""")
        val it = UadParser.parseTranslations("""{"a.b":"Italiano A","x.y":"ignorato","c.d":""}""")
        val merged = UadParser.applyTranslations(base, it)
        org.junit.Assert.assertEquals("Italiano A", merged.getValue("a.b").description)
        org.junit.Assert.assertEquals("English C", merged.getValue("c.d").description)
        org.junit.Assert.assertEquals(2, merged.size)
    }
}
