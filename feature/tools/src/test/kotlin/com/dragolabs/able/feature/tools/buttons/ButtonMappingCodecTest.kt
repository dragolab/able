package com.dragolabs.able.feature.tools.buttons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonMappingCodecTest {
    @Test
    fun roundTrip_allTargetTypes() {
        val mappings = mapOf(
            ButtonGesture.VOLUME_BOTH to ButtonTarget.Camera,
            ButtonGesture.DOUBLE_UP to ButtonTarget.Catalog("torch"),
            ButtonGesture.LONG_DOWN to ButtonTarget.App("com.example.app"),
            ButtonGesture.DOUBLE_DOWN to ButtonTarget.Activity("com.example.app", "com.example.app.SettingsActivity"),
        )
        assertEquals(mappings, ButtonMappingCodec.decode(ButtonMappingCodec.encode(mappings)))
    }

    @Test
    fun emptyOrCorrupt_decodesToEmpty() {
        assertTrue(ButtonMappingCodec.decode(null).isEmpty())
        assertTrue(ButtonMappingCodec.decode("").isEmpty())
        assertTrue(ButtonMappingCodec.decode("{not json").isEmpty())
        assertTrue(ButtonMappingCodec.decode("[]").isEmpty())
    }

    @Test
    fun unknownEntries_areSkipped() {
        val raw = """{"version":2,"mappings":[
            {"gesture":"triple_power","type":"camera"},
            {"gesture":"double_volume_up","type":"teleport"},
            {"gesture":"long_volume_up","type":"app"},
            {"gesture":"double_volume_down","type":"catalog","action":"nfc","extra":true}
        ]}"""
        assertEquals(mapOf(ButtonGesture.DOUBLE_DOWN to ButtonTarget.Catalog("nfc")), ButtonMappingCodec.decode(raw))
    }

    @Test
    fun encodedFormat_isStable() {
        val json = ButtonMappingCodec.encode(mapOf(ButtonGesture.DOUBLE_UP to ButtonTarget.Catalog("torch")))
        assertEquals("""{"version":1,"mappings":[{"gesture":"double_volume_up","type":"catalog","action":"torch"}]}""", json)
    }
}
