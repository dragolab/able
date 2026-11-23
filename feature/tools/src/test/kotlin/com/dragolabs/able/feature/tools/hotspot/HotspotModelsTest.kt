package com.dragolabs.able.feature.tools.hotspot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotModelsTest {

    private val base = HotspotConfig(
        ssid = "Pixel",
        passphrase = "secret123",
        security = HotspotSecurity.WPA2_PSK,
        band = HotspotBand.BAND_2GHZ,
        hidden = false,
        maxClients = 0,
        autoShutdown = true,
        shutdownTimeoutMs = -1,
        blockedClients = emptyList(),
        macRandomization = HotspotMacRandomization.PERSISTENT,
    )

    @Test
    fun ssidLimitsCountUtf8Bytes() {
        assertEquals(HotspotFieldError.SSID_EMPTY, HotspotValidator.validateSsid(""))
        assertNull(HotspotValidator.validateSsid("a".repeat(32)))
        assertEquals(HotspotFieldError.SSID_TOO_LONG, HotspotValidator.validateSsid("a".repeat(33)))
        // "è" is two bytes in UTF-8: 16 characters = 32 bytes fit, 17 do not.
        assertNull(HotspotValidator.validateSsid("è".repeat(16)))
        assertEquals(HotspotFieldError.SSID_TOO_LONG, HotspotValidator.validateSsid("è".repeat(17)))
    }

    @Test
    fun passphraseRulesDependOnSecurity() {
        assertNull(HotspotValidator.validatePassphrase("", HotspotSecurity.OPEN))
        assertEquals(HotspotFieldError.PASSPHRASE_TOO_SHORT, HotspotValidator.validatePassphrase("1234567", HotspotSecurity.WPA2_PSK))
        assertNull(HotspotValidator.validatePassphrase("12345678", HotspotSecurity.WPA3_SAE))
        assertNull(HotspotValidator.validatePassphrase("x".repeat(63), HotspotSecurity.WPA3_SAE_TRANSITION))
        assertEquals(HotspotFieldError.PASSPHRASE_TOO_LONG, HotspotValidator.validatePassphrase("x".repeat(64), HotspotSecurity.WPA2_PSK))
        assertEquals(HotspotFieldError.PASSPHRASE_NOT_ASCII, HotspotValidator.validatePassphrase("password€", HotspotSecurity.WPA2_PSK))
    }

    @Test
    fun bandRulesUseCapabilitiesAndSecurity() {
        val unknown = HotspotCapabilities()
        assertNull(HotspotValidator.validateBand(HotspotBand.BAND_5GHZ, HotspotSecurity.WPA2_PSK, unknown))
        assertEquals(
            HotspotFieldError.BAND_UNSUPPORTED,
            HotspotValidator.validateBand(HotspotBand.BAND_5GHZ, HotspotSecurity.WPA2_PSK, HotspotCapabilities(supports5Ghz = false)),
        )
        assertEquals(
            HotspotFieldError.BAND_6GHZ_NEEDS_WPA3,
            HotspotValidator.validateBand(HotspotBand.BAND_6GHZ, HotspotSecurity.WPA3_SAE_TRANSITION, unknown),
        )
        assertNull(HotspotValidator.validateBand(HotspotBand.BAND_6GHZ, HotspotSecurity.WPA3_SAE, HotspotCapabilities(supports6Ghz = true)))
    }

    @Test
    fun maxClientsRespectsDeviceLimit() {
        assertNull(HotspotValidator.validateMaxClients(0, HotspotCapabilities()))
        assertNull(HotspotValidator.validateMaxClients(10, HotspotCapabilities(maxSupportedClients = 10)))
        assertEquals(HotspotFieldError.MAX_CLIENTS_INVALID, HotspotValidator.validateMaxClients(11, HotspotCapabilities(maxSupportedClients = 10)))
        assertEquals(HotspotFieldError.MAX_CLIENTS_INVALID, HotspotValidator.validateMaxClients(-1, HotspotCapabilities()))
    }

    @Test
    fun macValidation() {
        assertTrue(HotspotValidator.isValidMac("aa:bb:cc:dd:ee:0F"))
        assertEquals(listOf(HotspotFieldError.MAC_INVALID), HotspotValidator.validate(base.copy(blockedClients = listOf("aa:bb")), HotspotCapabilities()))
    }

    @Test
    fun qrPayloadEscapesSpecialCharacters() {
        assertEquals(
            "WIFI:T:WPA;S:My\\;Net\\,\\:1;P:p\\\\a\\\"ss\\;word;;",
            WifiQrPayload.build("My;Net,:1", "p\\a\"ss;word", HotspotSecurity.WPA2_PSK, hidden = false),
        )
        assertEquals("WIFI:T:SAE;S:Net;P:12345678;H:true;;", WifiQrPayload.build("Net", "12345678", HotspotSecurity.WPA3_SAE, hidden = true))
        assertEquals("WIFI:T:nopass;S:Open;;", WifiQrPayload.build("Open", null, HotspotSecurity.OPEN, hidden = false))
        assertNull(WifiQrPayload.build("Net", null, HotspotSecurity.WPA2_PSK, hidden = false))
        assertNull(WifiQrPayload.build("Net", "x", HotspotSecurity.WPA3_OWE, hidden = false))
    }

    @Test
    fun editContainsOnlyChangedFields() {
        assertTrue(HotspotEdit.between(base, base).isEmpty)
        val edit = HotspotEdit.between(base, base.copy(ssid = "New", maxClients = 4))
        assertEquals(HotspotEdit(ssid = "New", maxClients = 4), edit)
        assertTrue(edit.needsRestart)
        assertTrue(!HotspotEdit(maxClients = 4).needsRestart)
    }

    @Test
    fun passwordChangeAlsoSendsSecurity() {
        val edit = HotspotEdit.between(base, base.copy(passphrase = "another1"))
        assertEquals(HotspotSecurity.WPA2_PSK, edit.security)
        assertEquals("another1", edit.passphrase)
        val open = HotspotEdit.between(base, base.copy(security = HotspotSecurity.OPEN))
        assertEquals(HotspotSecurity.OPEN, open.security)
        assertEquals("", open.passphrase)
    }

    @Test
    fun defaultTimeoutsAreEquivalent() {
        assertTrue(HotspotEdit.between(base, base.copy(shutdownTimeoutMs = 0)).isEmpty)
        assertTrue(HotspotEdit(shutdownTimeoutMs = -1).unconfirmedFields(base.copy(shutdownTimeoutMs = 0)).isEmpty())
    }

    @Test
    fun wireRoundTripAndParsing() {
        val edit = HotspotEdit(ssid = "Café; 1", security = HotspotSecurity.WPA3_SAE, passphrase = "pa ss=word", blockedClients = emptyList())
        val args = HotspotWire.editArgs(edit)
        assertTrue(args.all { arg -> arg.none { it.isWhitespace() || it == '\'' || it == '"' || it == ';' } })
        assertEquals("Café; 1", HotspotWire.unhex(args.first { it.startsWith("ssid=") }.substringAfter('=')))
        assertTrue("blocked=-" in args)

        val output = """
            state=13
            ssid=${HotspotWire.hex("Café")}
            pass=${HotspotWire.hex("p=ss|word")}
            security=3
            band=3
            hidden=true
            maxclients=5
            autoshutdown=false
            timeout=600000
            macrandom=2
            blocked=aa:bb:cc:dd:ee:ff
            error=foo:bar
            cap5=true
            cap6=false
            capmaxclients=10
            clients=ok
            client=11:22:33:44:55:66|${HotspotWire.hex("phone")}|192.168.1.2
            client=11:22:33:44:55:77||
        """.trimIndent()
        val snapshot = HotspotWire.parse(output)
        assertEquals(HotspotState.ENABLED, snapshot.state)
        val config = snapshot.config!!
        assertEquals("Café", config.ssid)
        assertEquals("p=ss|word", config.passphrase)
        assertEquals(HotspotSecurity.WPA3_SAE, config.security)
        assertEquals(HotspotBand.BAND_5GHZ, config.band)
        assertTrue(config.hidden)
        assertEquals(5, config.maxClients)
        assertEquals(listOf("aa:bb:cc:dd:ee:ff"), config.blockedClients)
        assertEquals(HotspotMacRandomization.NON_PERSISTENT, config.macRandomization)
        assertEquals(true, snapshot.capabilities.supports5Ghz)
        assertEquals(false, snapshot.capabilities.supports6Ghz)
        assertEquals(10, snapshot.capabilities.maxSupportedClients)
        assertEquals(listOf("foo:bar"), snapshot.errors)
        assertEquals(
            listOf(HotspotClient("11:22:33:44:55:66", "phone", "192.168.1.2"), HotspotClient("11:22:33:44:55:77", null, null)),
            snapshot.clients,
        )
    }

    @Test
    fun clientsAreNullWhenNotRead() {
        assertNull(HotspotWire.parse("state=11\nerror=clients:timeout").clients)
        assertEquals(HotspotState.DISABLED, HotspotWire.parse("state=11").state)
        assertNull(HotspotWire.parse("state=11").config)
    }

    @Test
    fun bandsMapping() {
        assertEquals(HotspotBand.BAND_2GHZ, HotspotBand.fromBands(listOf(1)))
        assertEquals(HotspotBand.BAND_5GHZ, HotspotBand.fromBands(listOf(2)))
        assertEquals(HotspotBand.BAND_6GHZ, HotspotBand.fromBands(listOf(7)))
        assertEquals(HotspotBand.DUAL, HotspotBand.fromBands(listOf(1, 3)))
        assertEquals(HotspotBand.UNKNOWN, HotspotBand.fromBands(emptyList()))
    }

    @Test
    fun unconfirmedFieldsDetectsMismatch() {
        val edit = HotspotEdit(ssid = "New", maxClients = 3, blockedClients = listOf("AA:BB:CC:DD:EE:FF"))
        val readBack = base.copy(ssid = "New", maxClients = 0, blockedClients = listOf("aa:bb:cc:dd:ee:ff"))
        assertEquals(listOf("maxClients"), edit.unconfirmedFields(readBack))
    }
}
