package com.dragolabs.able.feature.tools.toggles

import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.model.PrivateDnsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleStateMappingTest {

    private fun snapshot(
        nfcSupported: Boolean = true,
        nfc: Boolean = false,
        usb: Boolean = false,
        wireless: Boolean = false,
        wss: Boolean = false,
    ) = SystemSnapshot(
        nfcSupported = nfcSupported,
        nfcEnabled = nfc,
        writeSettingsGranted = false,
        autoBrightness = false,
        developerOptionsEnabled = true,
        usbDebugging = usb,
        wirelessDebugging = wireless,
        wifiIpAddress = null,
        dnsMode = PrivateDnsMode.OFF,
        dnsHostname = "",
        writeSecureSettings = wss,
    )

    @Test
    fun noPendingRequestShowsRealValue() {
        assertEquals(ToggleDisplay(checked = true, transitioning = false), toggleDisplay(actual = true, target = null))
        assertEquals(ToggleDisplay(checked = false, transitioning = false), toggleDisplay(actual = false, target = null))
    }

    @Test
    fun pendingRequestShowsTargetAsTransitionUntilReadBackMatches() {
        assertEquals(ToggleDisplay(checked = true, transitioning = true), toggleDisplay(actual = false, target = true))
        assertEquals(ToggleDisplay(checked = false, transitioning = true), toggleDisplay(actual = true, target = false))
        assertEquals(ToggleDisplay(checked = true, transitioning = false), toggleDisplay(actual = true, target = true))
    }

    @Test
    fun displayFollowsLiveSnapshotForDebugToggles() {
        val state = SystemTogglesUiState(snapshot = snapshot(usb = true, wireless = false))
        assertEquals(ToggleDisplay(true, false), state.display(ToggleKey.USB_DEBUGGING))
        assertEquals(ToggleDisplay(false, false), state.display(ToggleKey.WIRELESS_DEBUGGING))
        // External change (Android settings, or Wi-Fi loss) arrives as a new snapshot.
        val changed = state.copy(snapshot = snapshot(usb = true, wireless = true))
        assertEquals(ToggleDisplay(true, false), changed.display(ToggleKey.WIRELESS_DEBUGGING))
    }

    @Test
    fun displayShowsTransitionForPendingTarget() {
        val state = SystemTogglesUiState(
            snapshot = snapshot(wireless = false),
            targets = mapOf(ToggleKey.WIRELESS_DEBUGGING to true),
        )
        assertEquals(ToggleDisplay(true, true), state.display(ToggleKey.WIRELESS_DEBUGGING))
        assertNull(state.display(ToggleKey.PRIVATE_DNS))
        assertNull(SystemTogglesUiState(snapshot = null).display(ToggleKey.NFC))
    }

    @Test
    fun directNfcNeedsAdapterAndPermissionOrShizuku() {
        assertFalse(SystemTogglesUiState(snapshot = snapshot()).directNfc)
        assertTrue(SystemTogglesUiState(snapshot = snapshot(wss = true)).directNfc)
        assertTrue(SystemTogglesUiState(snapshot = snapshot(), shizuku = ShizukuConnectionState.READY).directNfc)
        assertFalse(SystemTogglesUiState(snapshot = snapshot(nfcSupported = false, wss = true)).directNfc)
    }
}
