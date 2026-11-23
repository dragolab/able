package com.dragolabs.able.feature.tools.speedtest

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.UiMessage
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SpeedPhase { IDLE, LATENCY, DOWNLOAD, UPLOAD, DONE, CANCELLED, FAILED }

@Immutable
data class SpeedTestUiState(
    val phase: SpeedPhase = SpeedPhase.IDLE,
    val connection: ConnectionInfo = ConnectionInfo(ConnectionType.NONE, false),
    val server: ServerInfo? = null,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    /** Value the gauge animates to: live Mbps while a throughput phase runs. */
    val liveMbps: Double = 0.0,
    /** Live rolling-throughput samples of the running phase (for the graph). */
    val liveSamples: List<Float> = emptyList(),
    /** 0..1 progress of the running phase (time-based, the phase may stop earlier). */
    val phaseProgress: Float = 0f,
    val bytesUsed: Long = 0L,
    val history: List<SpeedTestRecord> = emptyList(),
    /** Metered-connection confirmation pending, with the worst-case data estimate. */
    val meteredWarningBytes: Long? = null,
    val message: UiMessage? = null,
) {
    val running: Boolean get() = phase == SpeedPhase.LATENCY || phase == SpeedPhase.DOWNLOAD || phase == SpeedPhase.UPLOAD
}

class SpeedTestViewModel(
    private val appContext: Context,
    private val engine: SpeedTestEngine = SpeedTestEngine(),
    private val store: SpeedTestHistoryStore = SpeedTestHistoryStore(appContext),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeedTestUiState(connection = readConnectionInfo(appContext)))
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null

    init {
        viewModelScope.launch {
            store.load()
            store.history.collect { history -> _uiState.update { it.copy(history = history) } }
        }
    }

    fun refreshConnection() {
        if (_uiState.value.running) return
        _uiState.update { it.copy(connection = readConnectionInfo(appContext)) }
    }

    /** Start pressed: asks for confirmation first on metered networks (NET-001 data warning). */
    fun requestStart() {
        if (_uiState.value.running) return
        val connection = readConnectionInfo(appContext)
        _uiState.update { it.copy(connection = connection) }
        when {
            connection.type == ConnectionType.NONE -> _uiState.update { it.copy(message = UiMessage(R.string.speedtest_offline_message)) }
            connection.metered -> _uiState.update { it.copy(meteredWarningBytes = SpeedTestConfig.maxTestBytes(metered = true)) }
            else -> start(connection)
        }
    }

    fun confirmMetered() {
        _uiState.update { it.copy(meteredWarningBytes = null) }
        start(_uiState.value.connection)
    }

    fun dismissMetered() = _uiState.update { it.copy(meteredWarningBytes = null) }

    fun cancel() {
        testJob?.cancel()
    }

    fun clearHistory() {
        viewModelScope.launch { store.clear() }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun start(connection: ConnectionInfo) {
        testJob?.cancel()
        _uiState.update {
            it.copy(
                phase = SpeedPhase.LATENCY,
                server = null,
                pingMs = null,
                jitterMs = null,
                downMbps = null,
                upMbps = null,
                liveMbps = 0.0,
                liveSamples = emptyList(),
                phaseProgress = 0f,
                bytesUsed = 0L,
            )
        }
        val rule = SpeedTestConfig.stopRule(connection.metered)
        testJob = viewModelScope.launch {
            var used = 0L
            try {
                val server = engine.trace()
                _uiState.update { it.copy(server = server) }

                val latency = engine.latency { samples ->
                    _uiState.update {
                        it.copy(
                            pingMs = SpeedTestMath.median(samples),
                            jitterMs = SpeedTestMath.jitter(samples),
                            phaseProgress = samples.size / SpeedTestConfig.LATENCY_SAMPLES.toFloat(),
                        )
                    }
                }
                used += latency.bytes
                _uiState.update { it.copy(pingMs = latency.medianMs, jitterMs = latency.jitterMs, bytesUsed = used) }

                _uiState.update { it.copy(phase = SpeedPhase.DOWNLOAD, liveSamples = emptyList(), phaseProgress = 0f) }
                val down = engine.download(rule) { p -> onProgress(p, used, rule) }
                used += down.bytes
                _uiState.update { it.copy(downMbps = down.mbps, bytesUsed = used, liveMbps = 0.0) }

                _uiState.update { it.copy(phase = SpeedPhase.UPLOAD, liveSamples = emptyList(), phaseProgress = 0f) }
                val up = engine.upload(rule) { p -> onProgress(p, used, rule) }
                used += up.bytes
                _uiState.update { it.copy(upMbps = up.mbps, bytesUsed = used, liveMbps = up.mbps, phase = SpeedPhase.DONE, phaseProgress = 1f) }

                val s = _uiState.value
                store.add(
                    SpeedTestRecord(
                        timestampMs = System.currentTimeMillis(),
                        type = connection.type,
                        pingMs = s.pingMs,
                        jitterMs = s.jitterMs,
                        downMbps = s.downMbps,
                        upMbps = s.upMbps,
                        bytesUsed = used,
                        colo = s.server?.colo,
                    ),
                )
            } catch (e: CancellationException) {
                _uiState.update { it.copy(phase = SpeedPhase.CANCELLED, liveMbps = 0.0) }
                throw e
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(phase = SpeedPhase.FAILED, liveMbps = 0.0, message = UiMessage(R.string.speedtest_failed_message))
                }
            }
        }
    }

    private fun onProgress(p: PhaseProgress, usedBefore: Long, rule: PhaseStopRule) {
        _uiState.update {
            it.copy(
                liveMbps = p.liveMbps,
                liveSamples = (it.liveSamples + p.liveMbps.toFloat()).takeLast(MAX_GRAPH_POINTS),
                bytesUsed = usedBefore + p.bytes,
                phaseProgress = (p.elapsedNs / rule.maxNs.toFloat()).coerceIn(0f, 1f),
            )
        }
    }

    override fun onCleared() {
        testJob?.cancel()
    }

    private companion object {
        const val MAX_GRAPH_POINTS = 48
    }
}
