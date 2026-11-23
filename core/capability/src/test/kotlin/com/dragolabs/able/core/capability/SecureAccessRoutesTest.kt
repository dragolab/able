package com.dragolabs.able.core.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureAccessRoutesTest {

    @Test
    fun secureRoutePrefersPermissionThenShizukuSelfGrant() {
        assertEquals(SecureAccessRoute.DIRECT, selectSecureAccessRoute(hasWriteSecureSettings = true, shizukuReady = false))
        assertEquals(SecureAccessRoute.DIRECT, selectSecureAccessRoute(hasWriteSecureSettings = true, shizukuReady = true))
        assertEquals(SecureAccessRoute.SHIZUKU_SELF_GRANT, selectSecureAccessRoute(hasWriteSecureSettings = false, shizukuReady = true))
        assertEquals(SecureAccessRoute.SETTINGS, selectSecureAccessRoute(hasWriteSecureSettings = false, shizukuReady = false))
    }

    @Test
    fun nfcRouteWithoutAdapterIsUnsupportedWhateverTheAccess() {
        for (wss in listOf(true, false)) for (shizuku in listOf(true, false)) {
            assertEquals(NfcToggleRoute.UNSUPPORTED, selectNfcRoute(nfcSupported = false, hasWriteSecureSettings = wss, shizukuReady = shizuku))
        }
    }

    @Test
    fun nfcRouteUsesInProcessAdapterWhenPermissionHeld() {
        assertEquals(NfcToggleRoute.ADVANCED, selectNfcRoute(true, hasWriteSecureSettings = true, shizukuReady = false))
        assertEquals(NfcToggleRoute.ADVANCED, selectNfcRoute(true, hasWriteSecureSettings = true, shizukuReady = true))
    }

    @Test
    fun nfcRouteSelfGrantsThroughShizukuAndFallsBackToSettings() {
        assertEquals(NfcToggleRoute.SHIZUKU_SELF_GRANT, selectNfcRoute(true, hasWriteSecureSettings = false, shizukuReady = true))
        assertEquals(NfcToggleRoute.SETTINGS, selectNfcRoute(true, hasWriteSecureSettings = false, shizukuReady = false))
    }

    @Test
    fun onlyAdvancedAndSelfGrantAreDirect() {
        assertTrue(NfcToggleRoute.ADVANCED.isDirect)
        assertTrue(NfcToggleRoute.SHIZUKU_SELF_GRANT.isDirect)
        assertFalse(NfcToggleRoute.SETTINGS.isDirect)
        assertFalse(NfcToggleRoute.UNSUPPORTED.isDirect)
    }

    @Test
    fun nfcInvokeReturnIsAcceptedOnlyForTrue() {
        assertEquals(NfcInvokeResult.ACCEPTED, mapNfcInvokeReturn(true))
        assertEquals(NfcInvokeResult.REFUSED, mapNfcInvokeReturn(false))
        assertEquals(NfcInvokeResult.REFUSED, mapNfcInvokeReturn(null))
        assertEquals(NfcInvokeResult.REFUSED, mapNfcInvokeReturn("true"))
    }

    @Test
    fun adapterStatesMapToLiveStates() {
        assertEquals(NfcLiveState.OFF, mapNfcAdapterState(1))
        assertEquals(NfcLiveState.TURNING_ON, mapNfcAdapterState(2))
        assertEquals(NfcLiveState.ON, mapNfcAdapterState(3))
        assertEquals(NfcLiveState.TURNING_OFF, mapNfcAdapterState(4))
        assertNull(mapNfcAdapterState(0))
        assertNull(mapNfcAdapterState(-1))
        assertTrue(NfcLiveState.TURNING_ON.transitioning && NfcLiveState.TURNING_ON.enabled)
        assertTrue(NfcLiveState.TURNING_OFF.transitioning && !NfcLiveState.TURNING_OFF.enabled)
        assertFalse(NfcLiveState.ON.transitioning)
    }
}
