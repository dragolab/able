package com.dragolabs.able.feature.tools.speedtest

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/**
 * Pure speed-test arithmetic (NET-001). No Android or network types so it is unit tested on the
 * JVM. Throughput is decimal: 1 Mbps = 1 000 000 bits per second.
 */
object SpeedTestMath {

    /** Megabits per second for [bytes] moved in [nanos]. Zero when no time elapsed. */
    fun mbps(bytes: Long, nanos: Long): Double {
        if (nanos <= 0L || bytes <= 0L) return 0.0
        return bytes * 8.0 / (nanos / 1_000_000_000.0) / 1_000_000.0
    }

    /** Median of [values]; null for an empty list. */
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /**
     * Jitter as the mean absolute difference between consecutive latency samples (the RFC 3550
     * idea without smoothing, as most browser speed tests report it). Null with fewer than 2.
     */
    fun jitter(values: List<Double>): Double? {
        if (values.size < 2) return null
        return values.zipWithNext { a, b -> abs(b - a) }.average()
    }

    /**
     * Throughput over the samples taken after [warmupNs], so TCP slow start and connection set-up
     * do not drag the result down. Falls back to the whole phase when the phase was too short to
     * leave a measured window. [samples] are cumulative byte counts in time order.
     */
    fun throughputAfterWarmup(samples: List<ByteSample>, warmupNs: Long): Double {
        if (samples.size < 2) return 0.0
        val last = samples.last()
        val start = samples.firstOrNull { it.elapsedNs >= warmupNs }?.takeIf { it !== last } ?: samples.first()
        return mbps(last.bytes - start.bytes, last.elapsedNs - start.elapsedNs)
    }

    /** Throughput across the last [windowNs] of [samples] (for the live gauge). */
    fun rollingMbps(samples: List<ByteSample>, windowNs: Long): Double {
        if (samples.size < 2) return 0.0
        val last = samples.last()
        val start = samples.lastOrNull { last.elapsedNs - it.elapsedNs >= windowNs } ?: samples.first()
        return mbps(last.bytes - start.bytes, last.elapsedNs - start.elapsedNs)
    }

    /** Mbps with one decimal place in [locale] (decimal comma in Italian). */
    fun formatMbps(value: Double, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.1f", value)

    /** Milliseconds rounded to a whole number for display. */
    fun formatMs(value: Double, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.0f", value)

    /** Megabytes (decimal) with one decimal place. */
    fun formatMegabytes(bytes: Long, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f", bytes / 1_000_000.0)

    /**
     * Maps Mbps to 0..1 on a logarithmic scale up to [maxMbps], so 5 Mbps and 500 Mbps both move
     * the gauge visibly. 0 Mbps is 0, [maxMbps] or more is 1.
     */
    fun gaugeFraction(mbps: Double, maxMbps: Double = GAUGE_MAX_MBPS): Float {
        if (mbps <= 0.0) return 0f
        val fraction = ln(1.0 + mbps) / ln(1.0 + maxMbps)
        return fraction.coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Next transfer size for a stream: grows one step along [SpeedTestConfig.SIZE_RAMP] when the
     * previous transfer finished faster than [fastNs], otherwise stays the same.
     */
    fun nextSize(currentIndex: Int, lastTransferNs: Long, fastNs: Long = SpeedTestConfig.RAMP_UP_BELOW_NS): Int =
        if (lastTransferNs in 0 until fastNs) minOf(currentIndex + 1, SpeedTestConfig.SIZE_RAMP.lastIndex) else currentIndex

    /**
     * Cloudflare reports its own processing time in `Server-Timing: cfRequestDuration;dur=1.23`.
     * Returns it in milliseconds, or null when absent.
     */
    fun parseServerTimingMs(header: String?): Double? {
        if (header.isNullOrBlank()) return null
        val entry = header.split(',').map { it.trim() }.firstOrNull { it.startsWith("cfRequestDuration", ignoreCase = true) }
            ?: return null
        val dur = entry.split(';').map { it.trim() }.firstOrNull { it.startsWith("dur=", ignoreCase = true) } ?: return null
        return dur.substringAfter('=').toDoubleOrNull()?.takeIf { it >= 0.0 }
    }

    /** Network round trip = client-measured time minus the server's own processing, never below 0. */
    fun latencyMs(measuredNs: Long, serverMs: Double?): Double = max(0.0, measuredNs / 1_000_000.0 - (serverMs ?: 0.0))

    /** Parses `key=value` lines of `/cdn-cgi/trace`. */
    fun parseTrace(body: String): Map<String, String> = body.lineSequence()
        .mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }
        .toMap()

    const val GAUGE_MAX_MBPS = 1_000.0
}

/** A cumulative byte count at [elapsedNs] since the phase started. */
data class ByteSample(val elapsedNs: Long, val bytes: Long)

/** Tunables of the test (NET-001). */
object SpeedTestConfig {
    const val LATENCY_SAMPLES = 10
    const val STREAMS = 4
    /** Transfer sizes each stream ramps through: 100 kB, 1 MB, 10 MB, 25 MB. */
    val SIZE_RAMP = listOf(100_000L, 1_000_000L, 10_000_000L, 25_000_000L)
    /** A transfer faster than this moves the stream to the next size. */
    const val RAMP_UP_BELOW_NS = 1_500_000_000L
    const val SAMPLE_INTERVAL_MS = 250L
    const val WARMUP_NS = 1_000_000_000L
    const val ROLLING_WINDOW_NS = 1_000_000_000L

    /** Phase byte budgets: bounded so a metered connection cannot be drained by the test. */
    const val PHASE_BYTE_CAP_UNMETERED = 400_000_000L
    const val PHASE_BYTE_CAP_METERED = 100_000_000L

    /** Worst-case data a full test may use (download + upload caps plus small overhead). */
    fun maxTestBytes(metered: Boolean): Long =
        2 * (if (metered) PHASE_BYTE_CAP_METERED else PHASE_BYTE_CAP_UNMETERED)

    fun stopRule(metered: Boolean) = PhaseStopRule(
        byteCap = if (metered) PHASE_BYTE_CAP_METERED else PHASE_BYTE_CAP_UNMETERED,
    )
}

/**
 * Decides when a download/upload phase has measured enough (NET-001):
 * - always after [maxNs] (10 s) or once [byteCap] bytes moved;
 * - earlier, after [minNs], when the last [stableSamples] rolling-throughput values are all
 *   within [tolerance] of their mean (the speed has settled).
 */
data class PhaseStopRule(
    val minNs: Long = 4_000_000_000L,
    val maxNs: Long = 10_000_000_000L,
    val stableSamples: Int = 8,
    val tolerance: Double = 0.08,
    val byteCap: Long = SpeedTestConfig.PHASE_BYTE_CAP_UNMETERED,
) {
    fun shouldStop(elapsedNs: Long, bytes: Long, rollingMbps: List<Double>): Boolean {
        if (elapsedNs >= maxNs || bytes >= byteCap) return true
        if (elapsedNs < minNs || rollingMbps.size < stableSamples) return false
        return isStable(rollingMbps.takeLast(stableSamples))
    }

    fun isStable(window: List<Double>): Boolean {
        val mean = window.average()
        if (mean <= 0.0) return false
        return window.all { abs(it - mean) <= mean * tolerance }
    }
}
