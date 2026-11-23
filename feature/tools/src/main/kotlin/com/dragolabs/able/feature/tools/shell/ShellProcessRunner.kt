package com.dragolabs.able.feature.tools.shell

import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull

/** Events streamed while a shell command runs. */
sealed interface ShellEvent {
    data class Stdout(val line: String) : ShellEvent
    data class Stderr(val line: String) : ShellEvent
    data class Exit(val code: Int?) : ShellEvent
}

/**
 * Starts `sh -c <command>` as the Shizuku shell user and streams stdout/stderr line by line.
 *
 * Uses the same reflective call to Shizuku's `newProcess` as `ShizukuAdapter.executeCommand`
 * (the Shizuku API is an implementation detail of `:core:capability`, so it is looked up by
 * name). Cancelling the collector destroys the remote process and closes its streams. Callers
 * must check that Shizuku is READY first; a start failure is thrown as an exception.
 */
class ShellProcessRunner {

    fun run(command: String): Flow<ShellEvent> = channelFlow {
        val process = startProcess(command)
        runCatching { process.outputStream.close() } // No interactive stdin: commands must not wait on input.
        val readers = launch {
            coroutineScope {
                launch { readLines(process.inputStream) { send(ShellEvent.Stdout(it)) } }
                launch { readLines(process.errorStream) { send(ShellEvent.Stderr(it)) } }
            }
        }
        try {
            // join() is cancellable: on Stop/timeout the finally block below destroys the process
            // first, which unblocks the readers (a blocking read does not react to interrupts).
            readers.join()
            // Both streams reached EOF: the process has exited (or closed its outputs).
            val code = withTimeoutOrNull(EXIT_WAIT_MS) { runInterruptible { process.waitFor() } }
            send(ShellEvent.Exit(code))
        } finally {
            runCatching { process.destroy() }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
        }
    }.buffer(Channel.BUFFERED).flowOn(Dispatchers.IO)

    private fun startProcess(command: String): Process {
        val shizuku = Class.forName(SHIZUKU_CLASS)
        val newProcess = shizuku.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
        return newProcess.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
    }

    /**
     * Reads lines from [stream], splitting very long lines at [MAX_LINE_CHARS] so binary or
     * single-line output cannot exhaust memory. Ends on EOF or when the stream is closed.
     */
    private suspend fun readLines(stream: InputStream, emit: suspend (String) -> Unit) {
        val reader = InputStreamReader(stream, Charsets.UTF_8)
        val chunk = CharArray(4_096)
        val line = StringBuilder()
        try {
            while (true) {
                val read = runInterruptible { reader.read(chunk) }
                if (read < 0) break
                for (i in 0 until read) {
                    val c = chunk[i]
                    when {
                        c == '\n' -> {
                            emit(line.toString().removeSuffix("\r"))
                            line.setLength(0)
                        }
                        else -> {
                            line.append(c)
                            if (line.length >= MAX_LINE_CHARS) {
                                emit(line.toString())
                                line.setLength(0)
                            }
                        }
                    }
                }
            }
        } catch (_: IOException) {
            // Stream closed because the process was destroyed.
        }
        if (line.isNotEmpty()) emit(line.toString())
    }

    companion object {
        private const val SHIZUKU_CLASS = "rikka.shizuku.Shizuku"
        private const val EXIT_WAIT_MS = 5_000L
        const val MAX_LINE_CHARS = 4_000
    }
}
