package com.dragolabs.able.feature.tools.shell

/** Why a shell command needs an explicit confirmation before it runs. */
enum class ShellRisk {
    DELETE_FILES,
    UNINSTALL_APP,
    CLEAR_APP_DATA,
    REBOOT,
    CHANGE_SETTINGS,
    WINDOW_MANAGER,
    RAW_DISK_WRITE,
    FORMAT,
}

/**
 * Heuristic matcher for commands that can remove apps or data or change system state. It is a
 * safety net, not a sandbox: it errs on the side of asking, and the one-time warning still
 * applies to every command.
 *
 * The command is split into simple commands at shell separators (`;`, `&&`, `||`, `|`, `&`,
 * newlines, subshells and backticks). Quotes are dropped so `sh -c "rm -rf x"` is inspected too,
 * and common wrappers (`su`, `sh -c`, `busybox`, `xargs`, `env VAR=x`, absolute paths such as
 * `/system/bin/rm`) are skipped before looking at the real command name.
 */
object DangerousCommandMatcher {

    private val separators = Regex("""\|\||&&|[;|&\n\r`()]|\$\(""")
    private val whitespace = Regex("""\s+""")
    private val assignment = Regex("""^[A-Za-z_][A-Za-z0-9_]*=.*""")
    private val number = Regex("""^\d+[smhd]?$""")

    private val wrappers = setOf(
        "sudo", "su", "busybox", "toybox", "env", "nohup", "exec", "time", "xargs", "nice",
        "timeout", "sh", "bash", "mksh", "command", "eval", "then", "do", "else", "!",
    )

    fun match(command: String): Set<ShellRisk> {
        val normalized = command.lowercase().replace('"', ' ').replace('\'', ' ').replace('\\', ' ')
        val risks = linkedSetOf<ShellRisk>()
        for (segment in normalized.split(separators)) {
            val tokens = simpleCommand(segment.trim().split(whitespace).filter { it.isNotEmpty() })
            if (tokens.isNotEmpty()) classify(tokens, risks)
        }
        return risks
    }

    fun isDangerous(command: String): Boolean = match(command).isNotEmpty()

    /** Drops leading wrappers, their flags and variable assignments; returns the real command. */
    private fun simpleCommand(tokens: List<String>): List<String> {
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val name = t.substringAfterLast('/')
            when {
                name in wrappers -> i++
                assignment.matches(t) -> i++
                i > 0 && t.startsWith("-") -> i++
                i > 0 && number.matches(t) && tokens[i - 1].substringAfterLast('/') == "timeout" -> i++
                else -> break
            }
        }
        return tokens.drop(i).let { rest -> if (rest.isEmpty()) rest else listOf(rest[0].substringAfterLast('/')) + rest.drop(1) }
    }

    private fun classify(tokens: List<String>, risks: MutableSet<ShellRisk>) {
        val name = tokens[0]
        val args = tokens.drop(1).filterNot { it.startsWith("-") }
        val sub = args.firstOrNull().orEmpty()
        when {
            name == "rm" || name == "rmdir" || name == "unlink" || name == "shred" -> risks += ShellRisk.DELETE_FILES
            name == "dd" -> risks += ShellRisk.RAW_DISK_WRITE
            name == "reboot" || name == "shutdown" || name == "poweroff" -> risks += ShellRisk.REBOOT
            name == "svc" && sub == "power" && args.getOrNull(1) in setOf("reboot", "shutdown") -> risks += ShellRisk.REBOOT
            name == "setprop" && sub == "sys.powerctl" -> risks += ShellRisk.REBOOT
            name == "wm" -> risks += ShellRisk.WINDOW_MANAGER
            name == "format" || name.startsWith("mkfs") -> risks += ShellRisk.FORMAT
            name == "sm" && (sub == "format" || sub == "partition") -> risks += ShellRisk.FORMAT
            name == "settings" && (sub == "put" || sub == "delete" || sub == "reset") -> risks += ShellRisk.CHANGE_SETTINGS
            name == "pm" || (name == "cmd" && sub == "package") -> {
                val verb = if (name == "pm") sub else args.getOrNull(1).orEmpty()
                when {
                    verb.startsWith("uninstall") -> risks += ShellRisk.UNINSTALL_APP
                    verb == "clear" || verb == "trim-caches" -> risks += ShellRisk.CLEAR_APP_DATA
                }
            }
        }
    }
}
