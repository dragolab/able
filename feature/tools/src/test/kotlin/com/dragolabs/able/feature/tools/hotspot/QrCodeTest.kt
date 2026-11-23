package com.dragolabs.able.feature.tools.hotspot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeTest {

    @Test
    fun reedSolomonMatchesPublishedExample() {
        // ISO 18004 / Thonky tutorial example: "HELLO WORLD", version 1-M.
        val data = intArrayOf(32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17).map { it.toByte() }.toByteArray()
        val expected = intArrayOf(196, 35, 39, 119, 235, 215, 231, 226, 93, 23).map { it.toByte() }.toByteArray()
        val ecc = QrCode.reedSolomonRemainder(data, QrCode.reedSolomonDivisor(10))
        assertArrayEquals(expected, ecc)
    }

    @Test
    fun formatAndVersionBitsMatchSpecificationTables() {
        assertEquals(0b111011111000100, QrCode.formatBits(QrCode.Ecc.L, 0))
        assertEquals(0b110011000101111, QrCode.formatBits(QrCode.Ecc.L, 4))
        assertEquals(0b101010000010010, QrCode.formatBits(QrCode.Ecc.M, 0))
        assertEquals(0x07C94, QrCode.versionBits(7))
        assertEquals(0x28C69, QrCode.versionBits(40))
    }

    @Test
    fun capacityMatchesSpecification() {
        // Data codewords: 1-M = 16, 5-M = 86, 10-M = 216, 10-L = 274, 40-M = 2334.
        assertEquals(16, QrCode.numDataCodewords(1, QrCode.Ecc.M))
        assertEquals(86, QrCode.numDataCodewords(5, QrCode.Ecc.M))
        assertEquals(216, QrCode.numDataCodewords(10, QrCode.Ecc.M))
        assertEquals(274, QrCode.numDataCodewords(10, QrCode.Ecc.L))
        assertEquals(2334, QrCode.numDataCodewords(40, QrCode.Ecc.M))
    }

    @Test
    fun encodesTypicalPayloadWithFinderPatterns() {
        val payload = WifiQrPayload.build("Pixel 8 Pro", "correct horse 42", HotspotSecurity.WPA3_SAE_TRANSITION, hidden = false)!!
        val qr = QrCode.encode(payload)
        assertNotNull(qr)
        qr!!
        assertEquals(0, (qr.size - 17) % 4)
        // Finder pattern corners are dark, separators light.
        for ((x, y) in listOf(0 to 0, qr.size - 1 to 0, 0 to qr.size - 1)) assertTrue(qr.isDark(x, y))
        assertTrue(!qr.isDark(7, 7))
        // Dark module next to the bottom-left finder.
        assertTrue(qr.isDark(8, qr.size - 8))
    }

    @Test
    fun longestPossiblePayloadFits() {
        val ssid = ";".repeat(32)
        val pass = "\\".repeat(63)
        val payload = WifiQrPayload.build(ssid, pass, HotspotSecurity.WPA2_PSK, hidden = true)!!
        assertNotNull(QrCode.encode(payload))
    }

    @Test
    fun tooLongReturnsNull() {
        assertNull(QrCode.encode("x".repeat(3000)))
    }
}
