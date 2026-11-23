package com.dragolabs.able.feature.tools.speedtest

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTestMathTest {

    @Test
    fun mbpsIsDecimalMegabits() {
        // 12.5 MB in 1 s = 100 Mbps.
        assertEquals(100.0, SpeedTestMath.mbps(12_500_000, 1_000_000_000), 1e-9)
        // 1 MB in 0.5 s = 16 Mbps.
        assertEquals(16.0, SpeedTestMath.mbps(1_000_000, 500_000_000), 1e-9)
        assertEquals(0.0, SpeedTestMath.mbps(1_000, 0), 0.0)
        assertEquals(0.0, SpeedTestMath.mbps(0, 1_000), 0.0)
    }

    @Test
    fun medianHandlesOddEvenAndEmpty() {
        assertEquals(3.0, SpeedTestMath.median(listOf(5.0, 1.0, 3.0))!!, 0.0)
        assertEquals(2.5, SpeedTestMath.median(listOf(4.0, 1.0, 2.0, 3.0))!!, 0.0)
        assertNull(SpeedTestMath.median(emptyList()))
    }

    @Test
    fun jitterIsMeanAbsoluteConsecutiveDifference() {
        // |12-10| + |11-12| + |15-11| = 2 + 1 + 4 = 7 -> 7/3
        assertEquals(7.0 / 3.0, SpeedTestMath.jitter(listOf(10.0, 12.0, 11.0, 15.0))!!, 1e-9)
        assertEquals(0.0, SpeedTestMath.jitter(listOf(20.0, 20.0, 20.0))!!, 0.0)
        assertNull(SpeedTestMath.jitter(listOf(10.0)))
    }

    @Test
    fun throughputExcludesWarmup() {
        // Slow first second (1 MB), then 12.5 MB per second for 2 s -> 100 Mbps after warm-up.
        val samples = listOf(
            ByteSample(0, 0),
            ByteSample(1_000_000_000, 1_000_000),
            ByteSample(2_000_000_000, 13_500_000),
            ByteSample(3_000_000_000, 26_000_000),
        )
        assertEquals(100.0, SpeedTestMath.throughputAfterWarmup(samples, 1_000_000_000), 1e-9)
    }

    @Test
    fun throughputFallsBackToWholePhaseWhenTooShort() {
        val samples = listOf(ByteSample(0, 0), ByteSample(500_000_000, 1_000_000))
        assertEquals(16.0, SpeedTestMath.throughputAfterWarmup(samples, 1_000_000_000), 1e-9)
        assertEquals(0.0, SpeedTestMath.throughputAfterWarmup(listOf(ByteSample(0, 0)), 0), 0.0)
    }

    @Test
    fun rollingUsesLastWindow() {
        val samples = (0..8).map { ByteSample(it * 250_000_000L, it * 3_125_000L) } // 100 Mbps steady
        assertEquals(100.0, SpeedTestMath.rollingMbps(samples, 1_000_000_000), 1e-9)
    }

    @Test
    fun formatsOneDecimalPerLocale() {
        assertEquals("93.5", SpeedTestMath.formatMbps(93.46, Locale.US))
        assertEquals("93,5", SpeedTestMath.formatMbps(93.46, Locale.ITALY))
        assertEquals("12", SpeedTestMath.formatMs(12.4, Locale.US))
        assertEquals("1.5", SpeedTestMath.formatMegabytes(1_500_000, Locale.US))
    }

    @Test
    fun gaugeIsLogarithmicAndClamped() {
        assertEquals(0f, SpeedTestMath.gaugeFraction(0.0), 0f)
        assertEquals(1f, SpeedTestMath.gaugeFraction(5_000.0), 0f)
        val f10 = SpeedTestMath.gaugeFraction(10.0)
        val f100 = SpeedTestMath.gaugeFraction(100.0)
        assertTrue(f10 > 0.3f && f10 < f100 && f100 < 1f)
    }

    @Test
    fun sizeRampGrowsOnlyOnFastTransfers() {
        assertEquals(1, SpeedTestMath.nextSize(0, 200_000_000))
        assertEquals(0, SpeedTestMath.nextSize(0, 3_000_000_000))
        assertEquals(SpeedTestConfig.SIZE_RAMP.lastIndex, SpeedTestMath.nextSize(SpeedTestConfig.SIZE_RAMP.lastIndex, 1))
    }

    @Test
    fun parsesServerTimingAndSubtractsIt() {
        assertEquals(1.25, SpeedTestMath.parseServerTimingMs("cfRequestDuration;dur=1.25")!!, 1e-9)
        assertEquals(3.0, SpeedTestMath.parseServerTimingMs("cfL4;desc=\"x\", cfRequestDuration;dur=3")!!, 1e-9)
        assertNull(SpeedTestMath.parseServerTimingMs(null))
        assertNull(SpeedTestMath.parseServerTimingMs("other;dur=2"))
        assertEquals(18.75, SpeedTestMath.latencyMs(20_000_000, 1.25), 1e-9)
        assertEquals(0.0, SpeedTestMath.latencyMs(1_000_000, 5.0), 0.0)
    }

    @Test
    fun parsesTrace() {
        val map = SpeedTestMath.parseTrace("fl=1\nh=speed.cloudflare.com\ncolo=MXP\nloc=IT\nbroken\n")
        assertEquals("MXP", map["colo"])
        assertEquals("IT", map["loc"])
        assertFalse(map.containsKey("broken"))
    }

    @Test
    fun stopRuleStopsAtMaxOrCap() {
        val rule = PhaseStopRule(byteCap = 1_000)
        assertTrue(rule.shouldStop(10_000_000_000, 0, emptyList()))
        assertTrue(rule.shouldStop(1_000_000_000, 1_000, emptyList()))
        assertFalse(rule.shouldStop(1_000_000_000, 10, emptyList()))
    }

    @Test
    fun stopRuleStopsEarlyOnlyWhenStableAfterMinimum() {
        val rule = PhaseStopRule()
        val stable = List(8) { 100.0 + (it % 2) }
        val unstable = listOf(50.0, 80.0, 100.0, 120.0, 90.0, 110.0, 70.0, 130.0)
        assertFalse(rule.shouldStop(3_000_000_000, 1, stable)) // before minimum
        assertTrue(rule.shouldStop(5_000_000_000, 1, stable))
        assertFalse(rule.shouldStop(5_000_000_000, 1, unstable))
        assertFalse(rule.shouldStop(5_000_000_000, 1, stable.take(4))) // not enough samples
        assertFalse(rule.isStable(List(8) { 0.0 }))
    }

    @Test
    fun meteredBudgetIsSmaller() {
        assertTrue(SpeedTestConfig.maxTestBytes(true) < SpeedTestConfig.maxTestBytes(false))
        assertEquals(SpeedTestConfig.PHASE_BYTE_CAP_METERED, SpeedTestConfig.stopRule(true).byteCap)
    }
}
