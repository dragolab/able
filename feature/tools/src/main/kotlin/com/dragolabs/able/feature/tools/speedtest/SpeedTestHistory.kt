package com.dragolabs.able.feature.tools.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Connection used for the test, from [ConnectivityManager] (ACCESS_NETWORK_STATE, normal). */
enum class ConnectionType { WIFI, CELLULAR, ETHERNET, OTHER, NONE }

data class ConnectionInfo(val type: ConnectionType, val metered: Boolean)

/** Reads the active network's transport and whether Android treats it as metered. */
fun readConnectionInfo(context: Context): ConnectionInfo {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return ConnectionInfo(ConnectionType.NONE, false)
    val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
    if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        return ConnectionInfo(ConnectionType.NONE, false)
    }
    val type = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
        else -> ConnectionType.OTHER
    }
    return ConnectionInfo(type, metered = cm.isActiveNetworkMetered)
}

/** One saved result (NET-001 history). Throughput in Mbps, latency in ms. */
data class SpeedTestRecord(
    val timestampMs: Long,
    val type: ConnectionType,
    val pingMs: Double?,
    val jitterMs: Double?,
    val downMbps: Double?,
    val upMbps: Double?,
    val bytesUsed: Long,
    val colo: String?,
)

/**
 * Line-based codec for the history file: one record per line, fields separated by `|`. Kept
 * free of org.json so it is unit tested on the JVM. Unknown or broken lines are skipped.
 */
object SpeedTestHistoryCodec {
    const val MAX_ENTRIES = 20
    private const val VERSION = "v1"

    fun encode(records: List<SpeedTestRecord>): String = records.joinToString("\n") { r ->
        listOf(
            VERSION,
            r.timestampMs.toString(),
            r.type.name,
            r.pingMs.orEmpty(),
            r.jitterMs.orEmpty(),
            r.downMbps.orEmpty(),
            r.upMbps.orEmpty(),
            r.bytesUsed.toString(),
            r.colo?.filter { it.isLetterOrDigit() }.orEmpty(),
        ).joinToString("|")
    }

    fun decode(text: String): List<SpeedTestRecord> = text.lineSequence().mapNotNull { line ->
        val f = line.split('|')
        if (f.size != 9 || f[0] != VERSION) return@mapNotNull null
        val timestamp = f[1].toLongOrNull() ?: return@mapNotNull null
        val type = ConnectionType.entries.firstOrNull { it.name == f[2] } ?: ConnectionType.OTHER
        SpeedTestRecord(
            timestampMs = timestamp,
            type = type,
            pingMs = f[3].toDoubleOrNull(),
            jitterMs = f[4].toDoubleOrNull(),
            downMbps = f[5].toDoubleOrNull(),
            upMbps = f[6].toDoubleOrNull(),
            bytesUsed = f[7].toLongOrNull() ?: 0L,
            colo = f[8].ifEmpty { null },
        )
    }.toList()

    /** Newest first, capped at [MAX_ENTRIES]. */
    fun add(history: List<SpeedTestRecord>, record: SpeedTestRecord): List<SpeedTestRecord> =
        (listOf(record) + history).take(MAX_ENTRIES)

    private fun Double?.orEmpty(): String = this?.toString() ?: ""
}

/**
 * Local-only history store under [Context.getNoBackupFilesDir] (never part of cloud or device
 * backups). Nothing is uploaded.
 */
class SpeedTestHistoryStore(context: Context) {
    private val file = File(File(context.applicationContext.noBackupFilesDir, "speedtest"), "history.txt")
    private val mutex = Mutex()
    private val _history = MutableStateFlow<List<SpeedTestRecord>>(emptyList())
    val history: StateFlow<List<SpeedTestRecord>> = _history.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _history.value = runCatching { if (file.exists()) SpeedTestHistoryCodec.decode(file.readText()) else emptyList() }
                .getOrDefault(emptyList())
        }
    }

    suspend fun add(record: SpeedTestRecord) = write { SpeedTestHistoryCodec.add(it, record) }

    suspend fun clear() = write { emptyList() }

    private suspend fun write(change: (List<SpeedTestRecord>) -> List<SpeedTestRecord>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = change(_history.value)
            _history.value = updated
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(SpeedTestHistoryCodec.encode(updated))
            }
        }
    }
}
