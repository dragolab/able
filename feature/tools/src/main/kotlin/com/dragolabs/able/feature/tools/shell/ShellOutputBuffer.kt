package com.dragolabs.able.feature.tools.shell

enum class ShellLineKind { COMMAND, STDOUT, STDERR, INFO, EXIT }

/** How a command run ended. */
enum class ShellEnd { EXITED, STOPPED, TIMED_OUT, FAILED }

/**
 * One terminal line. An [ShellLineKind.EXIT] line closes a run: [end] says how it ended and
 * [exitCode] is set only for [ShellEnd.EXITED] (it may still be null when Android did not report one).
 */
data class ShellLine(
    val id: Long,
    val kind: ShellLineKind,
    val text: String,
    val exitCode: Int? = null,
    val end: ShellEnd? = null,
)

/** Bounded terminal scrollback: keeps the newest [maxLines] lines. Not thread-safe; confine to one coroutine. */
class ShellOutputBuffer(private val maxLines: Int = MAX_LINES) {
    private val lines = ArrayDeque<ShellLine>()
    private var nextId = 0L

    /** Lines dropped from the start since the last [clear]. */
    var droppedLines: Long = 0
        private set

    val size: Int get() = lines.size

    fun append(kind: ShellLineKind, text: String, exitCode: Int? = null, end: ShellEnd? = null): ShellLine {
        val line = ShellLine(nextId++, kind, text, exitCode, end)
        lines.addLast(line)
        while (lines.size > maxLines) {
            lines.removeFirst()
            droppedLines++
        }
        return line
    }

    fun snapshot(): List<ShellLine> = lines.toList()

    fun clear() {
        lines.clear()
        droppedLines = 0
    }

    companion object {
        const val MAX_LINES = 5_000

        /** Clipboard/share payloads stay well below the Binder transaction limit. */
        const val MAX_EXPORT_CHARS = 100_000

        /** Plain-text export: stderr and status lines are labelled so the text stands on its own. */
        fun lineToText(line: ShellLine): String = when (line.kind) {
            ShellLineKind.COMMAND -> "$ ${line.text}"
            ShellLineKind.STDOUT -> line.text
            ShellLineKind.STDERR -> "[stderr] ${line.text}"
            ShellLineKind.INFO -> "# ${line.text}"
            ShellLineKind.EXIT -> when (line.end) {
                ShellEnd.STOPPED -> "# stopped"
                ShellEnd.TIMED_OUT -> "# timed out"
                ShellEnd.FAILED -> "# failed"
                else -> "# exit ${line.exitCode ?: "?"}"
            }
        }

        /**
         * Joins [lines] into text, keeping the newest lines when the result would exceed
         * [maxChars]. Returns the text and whether older lines were left out.
         */
        fun export(lines: List<ShellLine>, maxChars: Int = MAX_EXPORT_CHARS): Pair<String, Boolean> {
            val parts = ArrayDeque<String>()
            var total = 0
            var truncated = false
            for (line in lines.asReversed()) {
                val text = lineToText(line)
                if (total + text.length + 1 > maxChars) {
                    truncated = true
                    break
                }
                parts.addFirst(text)
                total += text.length + 1
            }
            return parts.joinToString("\n") to truncated
        }
    }
}
