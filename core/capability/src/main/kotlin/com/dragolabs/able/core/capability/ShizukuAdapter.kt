package com.dragolabs.able.core.capability

import android.content.pm.PackageManager
import com.dragolabs.able.core.common.AbleError
import com.dragolabs.able.core.common.AbleResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

enum class ShizukuConnectionState {
    NOT_AVAILABLE,
    WAITING_FOR_PERMISSION,
    PERMISSION_DENIED,
    READY
}

class ShizukuAdapter {

    companion object {
        @Volatile
        private var _instance: ShizukuAdapter? = null
        val instance: ShizukuAdapter
            get() = _instance ?: synchronized(this) {
                _instance ?: ShizukuAdapter().apply { init() }.also { _instance = it }
            }
    }

    private val _connectionState = MutableStateFlow(ShizukuConnectionState.NOT_AVAILABLE)
    val connectionState: StateFlow<ShizukuConnectionState> = _connectionState.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _connectionState.value = ShizukuConnectionState.NOT_AVAILABLE
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            _connectionState.value = ShizukuConnectionState.READY
        } else {
            _connectionState.value = ShizukuConnectionState.PERMISSION_DENIED
        }
    }

    fun init() {
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        updateState()
    }

    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    fun isReady(): Boolean = _connectionState.value == ShizukuConnectionState.READY

    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    fun updateState() {
        if (!isBinderAlive()) {
            _connectionState.value = ShizukuConnectionState.NOT_AVAILABLE
            return
        }

        if (Shizuku.getVersion() < 11) {
            _connectionState.value = ShizukuConnectionState.NOT_AVAILABLE
            return
        }

        val hasPermission = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }

        _connectionState.value = if (hasPermission) {
            ShizukuConnectionState.READY
        } else {
            ShizukuConnectionState.WAITING_FOR_PERMISSION
        }
    }

    fun requestPermission(requestCode: Int = 1001): Boolean {
        return try {
            if (isBinderAlive()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    _connectionState.value = ShizukuConnectionState.READY
                    true
                } else {
                    Shizuku.requestPermission(requestCode)
                    true
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun openShizukuApp(context: android.content.Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes a privileged shell command via Shizuku with a safety timeout.
     */
    suspend fun executeCommand(command: String, timeoutMs: Long = 5000L): AbleResult<String> = withContext(Dispatchers.IO) {
        if (!isReady()) {
            return@withContext AbleResult.Failure(AbleError.ShizukuUnavailable())
        }

        try {
            val result = withTimeoutOrNull(timeoutMs) {
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
                val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val error = process.errorStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    AbleResult.Success(output.trim())
                } else {
                    AbleResult.Failure(AbleError.Security("Command failed (exit $exitCode): $error"))
                }
            }

            result ?: AbleResult.Failure(AbleError.Security("Command timed out after ${timeoutMs}ms"))
        } catch (e: Exception) {
            AbleResult.Failure(AbleError.Security("Shizuku command execution error: ${e.message}", e))
        }
    }
}
