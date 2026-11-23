package com.dragolabs.able.feature.tools.fountains

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Outcome of a fountain lookup for an area. */
sealed interface FountainsResult {
    data class Success(val fountains: List<Fountain>, val fetchedAtMs: Long, val fromCache: Boolean) : FountainsResult

    /** No network / DNS failure on every server. */
    data object Offline : FountainsResult

    /** Overpass rate limit or gateway timeout (HTTP 429 / 503 / 504) on every server. */
    data object Busy : FountainsResult

    /** Any other failure (bad response, unexpected HTTP status). */
    data object Failed : FountainsResult
}

/** Raw Overpass answer. */
sealed interface OverpassResponse {
    data class Ok(val body: String) : OverpassResponse
    data object Offline : OverpassResponse
    data object Busy : OverpassResponse
    data object Failed : OverpassResponse
}

/** Source of Overpass responses (the HTTP client, or a fake in tests). */
internal fun interface OverpassSource {
    suspend fun query(query: String): OverpassResponse
}

/** Minimal Overpass HTTP client: POST `data=`, JSON, a descriptive User-Agent, server fallback. */
internal class OverpassClient(
    private val userAgent: String,
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
) : OverpassSource {
    override suspend fun query(query: String): OverpassResponse = withContext(Dispatchers.IO) {
        var sawBusy = false
        var sawOffline = false
        for (endpoint in endpoints) {
            when (val r = post(endpoint, OverpassQuery.formBody(query))) {
                is OverpassResponse.Ok -> return@withContext r
                OverpassResponse.Busy -> sawBusy = true
                OverpassResponse.Offline -> sawOffline = true
                OverpassResponse.Failed -> Unit
            }
        }
        when {
            sawBusy -> OverpassResponse.Busy
            sawOffline -> OverpassResponse.Offline
            else -> OverpassResponse.Failed
        }
    }

    private fun post(endpoint: String, body: String): OverpassResponse {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val text = connection.inputStream.use { stream ->
                        stream.readNBytesCompat(MAX_RESPONSE_BYTES).toString(Charsets.UTF_8)
                    }
                    OverpassResponse.Ok(text)
                }
                429, 503, 504 -> OverpassResponse.Busy
                else -> if (code >= 500) OverpassResponse.Busy else OverpassResponse.Failed
            }
        } catch (_: UnknownHostException) {
            OverpassResponse.Offline
        } catch (_: ConnectException) {
            OverpassResponse.Offline
        } catch (_: NoRouteToHostException) {
            OverpassResponse.Offline
        } catch (_: SocketTimeoutException) {
            OverpassResponse.Busy
        } catch (_: IOException) {
            OverpassResponse.Failed
        } catch (_: SecurityException) {
            OverpassResponse.Failed
        } finally {
            connection?.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        if (out.size() >= limit) throw IOException("Overpass response too large")
        return out.toByteArray()
    }

    companion object {
        /**
         * Public Overpass instances listed on the OSM wiki (checked 2026-11-24), tried in order,
         * one at a time (no parallel requests, per the usage policy). The main instance is often
         * overloaded (502/504), so the others matter.
         */
        val DEFAULT_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        )
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024
    }
}

/**
 * Fountains per snapped area: in-memory cache plus a small private disk cache (24 h expiry,
 * oldest entries pruned). Only the area's bounding box ever leaves the device.
 */
internal class FountainsRepository(
    private val client: OverpassSource,
    private val cacheDir: File?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val memory = LinkedHashMap<String, FountainsResult.Success>(16, 0.75f, true)
    private val mutex = Mutex()

    /** Cached result covering [snapped], without network (null when nothing fresh is cached). */
    suspend fun cached(snapped: BoundingBox): FountainsResult.Success? = mutex.withLock {
        val key = FountainArea.key(snapped)
        memory[key]?.takeIf { fresh(it.fetchedAtMs) }
            ?: readDisk(key)?.also { put(key, it) }
    }

    suspend fun load(snapped: BoundingBox, force: Boolean): FountainsResult {
        if (!force) cached(snapped)?.let { return it }
        val key = FountainArea.key(snapped)
        return when (val response = client.query(OverpassQuery.build(snapped))) {
            is OverpassResponse.Ok -> {
                val fountains = runCatching { OverpassParser.parse(response.body) }.getOrNull()
                    ?: return FountainsResult.Failed
                val result = FountainsResult.Success(fountains, clock(), fromCache = false)
                mutex.withLock {
                    put(key, result)
                    writeDisk(key, response.body)
                }
                result
            }
            OverpassResponse.Busy -> FountainsResult.Busy
            OverpassResponse.Offline -> FountainsResult.Offline
            OverpassResponse.Failed -> FountainsResult.Failed
        }
    }

    /** Within the TTL; a timestamp slightly in the future (clock adjustments, file times) still counts. */
    private fun fresh(atMs: Long) = clock() - atMs in -CLOCK_SKEW_MS..CACHE_TTL_MS

    private fun put(key: String, value: FountainsResult.Success) {
        memory[key] = value
        while (memory.size > MEMORY_ENTRIES) memory.remove(memory.keys.first())
    }

    private suspend fun readDisk(key: String): FountainsResult.Success? = withContext(Dispatchers.IO) {
        val dir = cacheDir ?: return@withContext null
        val file = File(dir, "$key.json")
        if (!file.isFile || !fresh(file.lastModified())) return@withContext null
        runCatching { OverpassParser.parse(file.readText()) }.getOrNull()
            ?.let { FountainsResult.Success(it, file.lastModified(), fromCache = true) }
    }

    private suspend fun writeDisk(key: String, body: String) = withContext(Dispatchers.IO) {
        val dir = cacheDir ?: return@withContext
        runCatching {
            dir.mkdirs()
            File(dir, "$key.json").writeText(body)
            val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return@runCatching
            files.forEachIndexed { index, f -> if (index >= DISK_ENTRIES || !fresh(f.lastModified())) f.delete() }
        }
    }

    companion object {
        const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
        private const val CLOCK_SKEW_MS = 5L * 60 * 1000
        private const val MEMORY_ENTRIES = 32
        private const val DISK_ENTRIES = 40

        @Volatile private var instance: FountainsRepository? = null

        /** Process-wide instance, so the in-memory cache survives leaving and reopening the tool. */
        fun get(context: Context): FountainsRepository = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(context: Context): FountainsRepository {
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            return FountainsRepository(OverpassClient(userAgent(version)), File(context.cacheDir, "fountains"))
        }

        fun userAgent(version: String?): String = "Able/${version ?: "dev"} (+https://github.com/dragolab/able)"
    }
}
