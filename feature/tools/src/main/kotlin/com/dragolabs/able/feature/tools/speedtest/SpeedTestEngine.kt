package com.dragolabs.able.feature.tools.speedtest

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the test ran, from `/cdn-cgi/trace` (IATA airport code of the data centre + country). */
data class ServerInfo(val colo: String?, val countryCode: String?)

/** Live progress of a throughput phase. */
data class PhaseProgress(val liveMbps: Double, val elapsedNs: Long, val bytes: Long)

/** Outcome of a throughput phase. */
data class PhaseResult(val mbps: Double, val bytes: Long)

/** Outcome of the latency phase. */
data class LatencyResult(val medianMs: Double, val jitterMs: Double?, val samplesMs: List<Double>, val bytes: Long)

/**
 * Speed test against Cloudflare's public endpoints (NET-001), using only [HttpURLConnection]
 * and coroutines. Able contacts `speed.cloudflare.com` only; nothing is sent elsewhere.
 *
 * Every blocking call runs on [Dispatchers.IO]. Cancelling the calling coroutine disconnects all
 * open connections, so Cancel takes effect even when a read is stalled.
 */
class SpeedTestEngine(
    private val baseUrl: String = BASE_URL,
    private val nanoTime: () -> Long = System::nanoTime,
) {

    private val open = ConcurrentHashMap.newKeySet<HttpURLConnection>()

    suspend fun trace(): ServerInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = connect("$baseUrl/cdn-cgi/trace")
            try {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val map = SpeedTestMath.parseTrace(body)
                // The trace also contains the public IP; Able does not keep or show it.
                ServerInfo(colo = map["colo"], countryCode = map["loc"])
            } finally {
                release(conn)
            }
        }.getOrNull()
    }

    /** [SpeedTestConfig.LATENCY_SAMPLES] empty downloads after one discarded warm-up request. */
    suspend fun latency(onSample: (List<Double>) -> Unit): LatencyResult = withContext(Dispatchers.IO) {
        val samples = mutableListOf<Double>()
        var bytes = 0L
        try {
            repeat(SpeedTestConfig.LATENCY_SAMPLES + 1) { index ->
                currentCoroutineContext().ensureActive()
                val conn = connect("$baseUrl/__down?bytes=0")
                try {
                    val start = nanoTime()
                    val code = conn.responseCode
                    val elapsed = nanoTime() - start
                    if (code !in 200..299) throw IOException("HTTP $code")
                    conn.inputStream.use { it.readBytes() }
                    bytes += HEADER_OVERHEAD_BYTES
                    // The first request also pays DNS, TCP and TLS set-up: discard it.
                    if (index > 0) {
                        samples += SpeedTestMath.latencyMs(elapsed, SpeedTestMath.parseServerTimingMs(conn.getHeaderField("Server-Timing")))
                        onSample(samples.toList())
                    }
                } finally {
                    release(conn)
                }
            }
        } finally {
            disconnectAll()
        }
        LatencyResult(
            medianMs = SpeedTestMath.median(samples) ?: 0.0,
            jitterMs = SpeedTestMath.jitter(samples),
            samplesMs = samples,
            bytes = bytes,
        )
    }

    suspend fun download(rule: PhaseStopRule, onProgress: (PhaseProgress) -> Unit): PhaseResult =
        throughputPhase(rule, onProgress) { counter, sizeIndex -> downloadOnce(counter, SpeedTestConfig.SIZE_RAMP[sizeIndex]) }

    suspend fun upload(rule: PhaseStopRule, onProgress: (PhaseProgress) -> Unit): PhaseResult =
        throughputPhase(rule, onProgress) { counter, sizeIndex -> uploadOnce(counter, SpeedTestConfig.SIZE_RAMP[sizeIndex]) }

    private suspend fun throughputPhase(
        rule: PhaseStopRule,
        onProgress: (PhaseProgress) -> Unit,
        transfer: suspend (AtomicLong, Int) -> Unit,
    ): PhaseResult = coroutineScope {
        val counter = AtomicLong(0)
        val failures = AtomicLong(0)
        val start = nanoTime()
        val streams = List(SpeedTestConfig.STREAMS) {
            launch(Dispatchers.IO) {
                var sizeIndex = 0
                while (isActive) {
                    val t0 = nanoTime()
                    try {
                        transfer(counter, sizeIndex)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        if (!isActive) break
                        failures.incrementAndGet()
                        // One stream failing is tolerated; give the network a moment.
                        delay(200)
                        continue
                    }
                    sizeIndex = SpeedTestMath.nextSize(sizeIndex, nanoTime() - t0)
                }
            }
        }
        val samples = mutableListOf(ByteSample(0L, 0L))
        val rolling = mutableListOf<Double>()
        try {
            while (true) {
                delay(SpeedTestConfig.SAMPLE_INTERVAL_MS)
                val elapsed = nanoTime() - start
                val bytes = counter.get()
                samples += ByteSample(elapsed, bytes)
                val live = SpeedTestMath.rollingMbps(samples, SpeedTestConfig.ROLLING_WINDOW_NS)
                rolling += live
                onProgress(PhaseProgress(live, elapsed, bytes))
                if (rule.shouldStop(elapsed, bytes, rolling)) break
                // Every stream keeps failing (offline, blocked): give up honestly.
                if (bytes == 0L && failures.get() >= SpeedTestConfig.STREAMS * 3L) throw IOException("No data transferred")
            }
        } finally {
            streams.forEach { it.cancel() }
            disconnectAll()
        }
        PhaseResult(
            mbps = SpeedTestMath.throughputAfterWarmup(samples, SpeedTestConfig.WARMUP_NS),
            bytes = counter.get(),
        )
    }

    private suspend fun downloadOnce(counter: AtomicLong, size: Long) {
        val conn = connect("$baseUrl/__down?bytes=$size")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            conn.inputStream.use { input ->
                val buffer = ByteArray(CHUNK)
                val context = currentCoroutineContext()
                while (true) {
                    context.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    counter.addAndGet(read.toLong())
                }
            }
        } finally {
            release(conn)
        }
    }

    private suspend fun uploadOnce(counter: AtomicLong, size: Long) {
        val conn = connect("$baseUrl/__up").apply {
            requestMethod = "POST"
            doOutput = true
            setFixedLengthStreamingMode(size)
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        try {
            val context = currentCoroutineContext()
            conn.outputStream.use { output ->
                var remaining = size
                while (remaining > 0) {
                    context.ensureActive()
                    val n = minOf(remaining, UPLOAD_PAYLOAD.size.toLong()).toInt()
                    output.write(UPLOAD_PAYLOAD, 0, n)
                    counter.addAndGet(n.toLong())
                    remaining -= n
                }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            conn.inputStream.use { it.readBytes() }
        } finally {
            release(conn)
        }
    }

    private fun connect(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            useCaches = false
            setRequestProperty("Cache-Control", "no-store")
            setRequestProperty("Accept-Encoding", "identity")
            open += this
        }

    private fun release(conn: HttpURLConnection) {
        open -= conn
    }

    private fun disconnectAll() {
        open.toList().forEach { runCatching { it.disconnect() } }
        open.clear()
    }

    companion object {
        const val BASE_URL = "https://speed.cloudflare.com"
        private const val TIMEOUT_MS = 10_000
        private const val CHUNK = 64 * 1024
        /** Approximate request + response header size counted for each latency request. */
        private const val HEADER_OVERHEAD_BYTES = 1_000L
        /** Random (incompressible) upload payload, reused for every chunk. */
        private val UPLOAD_PAYLOAD = Random(42).nextBytes(CHUNK)
    }
}
