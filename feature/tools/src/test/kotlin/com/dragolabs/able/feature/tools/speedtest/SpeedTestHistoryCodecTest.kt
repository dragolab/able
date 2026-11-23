package com.dragolabs.able.feature.tools.speedtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTestHistoryCodecTest {

    private fun record(ts: Long, down: Double? = 93.4) = SpeedTestRecord(
        timestampMs = ts,
        type = ConnectionType.WIFI,
        pingMs = 12.5,
        jitterMs = null,
        downMbps = down,
        upMbps = 20.1,
        bytesUsed = 123_456,
        colo = "MXP",
    )

    @Test
    fun roundTrips() {
        val records = listOf(record(2), record(1, down = null))
        assertEquals(records, SpeedTestHistoryCodec.decode(SpeedTestHistoryCodec.encode(records)))
    }

    @Test
    fun skipsBrokenLinesAndUnknownVersions() {
        val text = SpeedTestHistoryCodec.encode(listOf(record(1))) + "\ngarbage\nv9|1|WIFI||||||\n"
        assertEquals(1, SpeedTestHistoryCodec.decode(text).size)
        assertTrue(SpeedTestHistoryCodec.decode("").isEmpty())
    }

    @Test
    fun coloCannotBreakTheFormat() {
        val tricky = record(1).copy(colo = "MX|P\n")
        assertEquals("MXP", SpeedTestHistoryCodec.decode(SpeedTestHistoryCodec.encode(listOf(tricky))).single().colo)
    }

    @Test
    fun addKeepsNewestFirstAndCaps() {
        var history = emptyList<SpeedTestRecord>()
        repeat(25) { history = SpeedTestHistoryCodec.add(history, record(it.toLong())) }
        assertEquals(SpeedTestHistoryCodec.MAX_ENTRIES, history.size)
        assertEquals(24L, history.first().timestampMs)
        assertEquals(5L, history.last().timestampMs)
    }
}
