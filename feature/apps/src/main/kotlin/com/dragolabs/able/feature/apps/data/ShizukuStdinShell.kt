package com.dragolabs.able.feature.apps.data

import com.dragolabs.able.core.capability.ShizukuAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Result of a shell command run through Shizuku. */
data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val failed: Boolean get() = PackageCommands.isShellFailure(exitCode, stdout, stderr)
}

/**
 * Runs a shell command through Shizuku and lets the caller write to the process stdin. Needed for
 * `pm install-write ... -`, which reads the APK from stdin, so no world-readable temporary copy is
 * ever created. Uses the same `Shizuku.newProcess` reflection as [ShizukuAdapter.executeCommand]
 * (the Shizuku API is not on this module's compile classpath; the app ships it at runtime).
 */
class ShizukuStdinShell(private val shizuku: ShizukuAdapter) {

    /**
     * Runs [command] with `sh -c`. [writeStdin] receives the process stdin, which is closed
     * afterwards. Returns null when Shizuku is not ready or the process cannot be started; a
     * timeout destroys the process and reports exit code [EXIT_TIMEOUT].
     */
    suspend fun run(
        command: String,
        timeoutMs: Long,
        writeStdin: ((OutputStream) -> Unit)? = null,
    ): ShellResult? = withContext(Dispatchers.IO) {
        if (!shizuku.isReady()) return@withContext null
        val process = runCatching { newProcess(arrayOf("sh", "-c", command)) }.getOrNull() ?: return@withContext null
        // Drain both streams concurrently so a full pipe never blocks the writer.
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val outReader = drain(process.inputStream, stdout)
        val errReader = drain(process.errorStream, stderr)
        val deadline = System.currentTimeMillis() + timeoutMs
        // A blocked stdin write cannot be cancelled; destroying the process closes the pipe.
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val watchdog = thread(isDaemon = true, name = "able-shell-watchdog") {
            try {
                Thread.sleep(timeoutMs)
                if (!done.get()) runCatching { process.destroy() }
            } catch (_: InterruptedException) {
            }
        }
        var writeError: Throwable? = null
        try {
            process.outputStream.use { stream -> writeStdin?.invoke(stream) }
        } catch (t: Throwable) {
            writeError = t
        }
        val finished = awaitExit(process, (deadline - System.currentTimeMillis()).coerceAtLeast(1))
        done.set(true)
        watchdog.interrupt()
        if (!finished) runCatching { process.destroy() }
        outReader.join(STREAM_JOIN_MS)
        errReader.join(STREAM_JOIN_MS)
        val exit = if (finished) runCatching { process.exitValue() }.getOrDefault(EXIT_UNKNOWN) else EXIT_TIMEOUT
        val err = buildString {
            append(synchronized(stderr) { stderr.toString() })
            writeError?.let { append("\nstdin: ").append(it.message ?: it.javaClass.simpleName) }
            if (!finished) append("\nTimed out after ${timeoutMs}ms")
        }
        ShellResult(
            exitCode = if (writeError != null && exit == 0) EXIT_WRITE_FAILED else exit,
            stdout = synchronized(stdout) { stdout.toString() }.trim(),
            stderr = err.trim(),
        )
    }

    private fun drain(stream: InputStream, into: StringBuilder): Thread = thread(isDaemon = true, name = "able-shell-drain") {
        runCatching {
            stream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    synchronized(into) { if (into.length < MAX_OUTPUT_CHARS) into.appendRange(buffer, 0, read) }
                }
            }
        }
    }

    /**
     * `ShizukuRemoteProcess` implements `waitForTimeout` remotely; `Process.waitFor(long, TimeUnit)`
     * polls `exitValue()`, which does not throw `IllegalThreadStateException` across Binder.
     */
    private fun awaitExit(process: Process, timeoutMs: Long): Boolean {
        val remote = runCatching {
            process.javaClass.getMethod("waitForTimeout", Long::class.javaPrimitiveType, TimeUnit::class.java)
        }.getOrNull()
        if (remote != null) {
            runCatching { return remote.invoke(process, timeoutMs, TimeUnit.MILLISECONDS) as Boolean }
        }
        var exited = false
        val waiter = thread(isDaemon = true, name = "able-shell-wait") {
            exited = runCatching { process.waitFor(); true }.getOrDefault(false)
        }
        waiter.join(timeoutMs)
        return exited
    }

    private fun newProcess(cmd: Array<String>): Process {
        val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
        val method = shizukuClass.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
        return method.invoke(null, cmd, null, null) as Process
    }

    companion object {
        const val EXIT_TIMEOUT = -1
        const val EXIT_UNKNOWN = -2
        const val EXIT_WRITE_FAILED = -3
        private const val STREAM_JOIN_MS = 2_000L
        private const val MAX_OUTPUT_CHARS = 64 * 1024
    }
}
