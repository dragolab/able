package com.dragolabs.able.feature.tools

import com.dragolabs.able.core.capability.DeviceCapabilities
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.model.ToolId
import com.dragolabs.able.feature.tools.activities.AppActivities
import com.dragolabs.able.feature.tools.activities.LaunchableActivity
import com.dragolabs.able.feature.tools.activities.filterActivities
import com.dragolabs.able.feature.tools.toggles.isValidDnsHostname
import com.dragolabs.able.feature.tools.torch.TorchPatterns
import com.dragolabs.able.feature.tools.torch.TorchStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsLogicTest {

    @Test
    fun morseEncodesSosWithStandardTiming() {
        val u = TorchPatterns.UNIT_MS
        val dot = TorchStep(true, u)
        val dash = TorchStep(true, 3 * u)
        val gap = TorchStep(false, u)
        val letterGap = TorchStep(false, 3 * u)
        val expected = listOf(dot, gap, dot, gap, dot, letterGap, dash, gap, dash, gap, dash, letterGap, dot, gap, dot, gap, dot, TorchStep(false, 7 * u))
        assertEquals(expected, TorchPatterns.SOS)
    }

    @Test
    fun morseSkipsUnsupportedCharacters() {
        assertFalse(TorchPatterns.isEncodable("!?"))
        assertEquals(TorchPatterns.morse("E"), TorchPatterns.morse("E!"))
    }

    @Test
    fun hostnameValidationRejectsShellAndMalformedInput() {
        assertTrue(isValidDnsHostname("dns.quad9.net"))
        assertTrue(isValidDnsHostname("one.one.one.one"))
        assertFalse(isValidDnsHostname("localhost"))
        assertFalse(isValidDnsHostname("dns.google; reboot"))
        assertFalse(isValidDnsHostname("-bad.example.com"))
        assertFalse(isValidDnsHostname("a..b"))
    }

    @Test
    fun activitySearchMatchesAppOrActivity() {
        val settings = AppActivities(
            "com.android.settings", "Settings",
            listOf(
                LaunchableActivity("com.android.settings", "com.android.settings.Settings", "Settings", "Settings", true),
                LaunchableActivity("com.android.settings", "com.android.settings.RadioInfo", "Phone info", "Settings", false),
            ),
        )
        val camera = AppActivities("com.example.camera", "Camera", listOf(LaunchableActivity("com.example.camera", "com.example.camera.Main", "Camera", "Camera", true)))
        val apps = listOf(camera, settings)
        assertEquals(apps, filterActivities(apps, "  "))
        assertEquals(listOf(camera), filterActivities(apps, "camera"))
        val radio = filterActivities(apps, "radioinfo")
        assertEquals(1, radio.single().activities.size)
    }

    @Test
    fun hubDisablesToolsWithoutHardware() {
        val device = DeviceCapabilities(
            hasFlashTorch = false, hasNfc = false, hasAccelerometer = true, hasMagnetometer = false,
            hasTelephony = false, hasTouchscreen = false, hasBattery = true,
        )
        val entries = buildToolEntries(device, hasLocation = false, shizuku = ShizukuConnectionState.NOT_AVAILABLE).associateBy { it.id }
        assertEquals(ToolId.entries.toSet(), entries.keys)
        assertNotNull(entries.getValue(ToolId.TORCH).unavailableReason)
        assertNotNull(entries.getValue(ToolId.GPS).unavailableReason)
        assertNotNull(entries.getValue(ToolId.RULER).unavailableReason)
        assertNotNull(entries.getValue(ToolId.COMPASS).unavailableReason)
        assertNull(entries.getValue(ToolId.LEVEL).unavailableReason)
        assertNull(entries.getValue(ToolId.STORAGE).unavailableReason)
    }
}
