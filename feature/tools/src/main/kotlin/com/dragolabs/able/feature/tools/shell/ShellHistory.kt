package com.dragolabs.able.feature.tools.shell

/** Pure command-history rules (persisted list: oldest first, newest last). */
object ShellHistory {
    const val MAX_ENTRIES = 100

    /** Adds [command] as the newest entry, removing an older duplicate and trimming to [max]. */
    fun record(history: List<String>, command: String, max: Int = MAX_ENTRIES): List<String> {
        val entry = command.trim()
        if (entry.isEmpty()) return history.takeLast(max)
        return (history.filterNot { it == entry } + entry).takeLast(max)
    }
}

/**
 * Up/down navigation through [ShellHistory] entries, like a terminal. The text typed before the
 * first "previous" step is kept as a draft and restored when navigating past the newest entry.
 */
class HistoryNavigator {
    /** Index into the entries, or null when not navigating (editing the draft). */
    var position: Int? = null
        private set
    private var draft: String = ""

    fun canGoPrevious(entries: List<String>): Boolean {
        val p = position
        return entries.isNotEmpty() && (p == null || p > 0)
    }

    fun canGoNext(): Boolean = position != null

    /** Returns the older entry to show, or null when there is none. */
    fun previous(entries: List<String>, currentText: String): String? {
        if (entries.isEmpty()) return null
        val p = position
        val next = when {
            p == null -> {
                draft = currentText
                entries.lastIndex
            }
            p > 0 -> minOf(p - 1, entries.lastIndex)
            else -> return null
        }
        position = next
        return entries[next]
    }

    /** Returns the newer entry, or the saved draft after the newest entry; null when not navigating. */
    fun next(entries: List<String>): String? {
        val p = position ?: return null
        val n = p + 1
        return if (n > entries.lastIndex) {
            position = null
            draft
        } else {
            position = n
            entries[n]
        }
    }

    fun reset() {
        position = null
        draft = ""
    }
}

/** A saved command ("bookmark"). Selecting one only fills the input; it never runs by itself. */
data class ShellSnippet(val id: String, val label: String, val command: String)

object ShellSnippets {
    /** Replaces the snippet with the same id, or appends it. */
    fun upsert(list: List<ShellSnippet>, snippet: ShellSnippet): List<ShellSnippet> =
        if (list.any { it.id == snippet.id }) list.map { if (it.id == snippet.id) snippet else it } else list + snippet

    fun remove(list: List<ShellSnippet>, id: String): List<ShellSnippet> = list.filterNot { it.id == id }

    /** Safe, read-only starter commands. Labels are resolved by the store at seed time. */
    val STARTER_COMMANDS: List<String> = listOf(
        "getprop ro.build.version.release",
        "pm list packages -3",
        "dumpsys battery",
        "settings get global adb_enabled",
        "cmd package list packages -d",
        "df -h /data",
    )
}
