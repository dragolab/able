package com.dragolabs.able.feature.tools.memory

/**
 * Pure parsers for the shell output used by the RAM tool. Formats verified on an Android 16
 * (API 36) emulator on 2026-11-22:
 *
 * - `ps -A -o PID,UID,RSS,NAME` (toybox): header line, then `  1062 10194 237180 com.android.systemui`.
 *   RSS is in KiB.
 * - `dumpsys activity lru`: `  #13: fg       BFGS ----F--TI 1979:com.google.android.apps.messaging/u0a154`
 *   or `  #45: cch  + 5 CEM  --------- 2371:com.google.android.gms.unstable/u0a155`.
 * - `/proc/meminfo`: `SwapTotal:       1509560 kB`.
 *
 * Process discovery follows the approach documented by Shappky (YasserNull/shappky,
 * docs/how_shappky_works.md, read 2026-11-22): list every process with `ps`, then map process
 * names (`package` or `package:suffix`) to installed packages.
 */
internal data class PsRow(val pid: Int, val uid: Int, val rssKb: Long, val name: String)

internal fun parsePs(output: String): List<PsRow> = output.lineSequence()
    .mapNotNull { line ->
        val parts = line.trim().split(WHITESPACE, limit = 4)
        if (parts.size < 4) return@mapNotNull null
        val pid = parts[0].toIntOrNull() ?: return@mapNotNull null
        val uid = parts[1].toIntOrNull() ?: return@mapNotNull null
        val rss = parts[2].toLongOrNull() ?: return@mapNotNull null
        val name = parts[3].trim()
        if (name.isEmpty() || name.startsWith("[")) null else PsRow(pid, uid, rss, name)
    }
    .toList()

/** Coarse importance Android assigns to a process (from the LRU list). */
enum class ProcessImportance { FOREGROUND, VISIBLE, SERVICE, HOME, RECENT, CACHED, PERSISTENT, OTHER }

private val LRU_LINE = Regex("""^\s*#\s*\d+:\s+([a-z]+)\S*.*?\s(\d+):([^\s/]+)/\S+""")

/** pid -> importance, parsed from `dumpsys activity lru`. */
internal fun parseLru(output: String): Map<Int, ProcessImportance> = output.lineSequence()
    .mapNotNull { line ->
        val match = LRU_LINE.find(line) ?: return@mapNotNull null
        val pid = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        pid to importanceOf(match.groupValues[1])
    }
    .toMap()

private fun importanceOf(adj: String): ProcessImportance = when (adj) {
    "fg", "top" -> ProcessImportance.FOREGROUND
    "vis", "prcp", "prcl", "prcm" -> ProcessImportance.VISIBLE
    "svc", "svcb", "bkup", "hvy", "fgs" -> ProcessImportance.SERVICE
    "home" -> ProcessImportance.HOME
    "prev" -> ProcessImportance.RECENT
    "cch", "cac", "cem" -> ProcessImportance.CACHED
    "pers", "psvc", "sys", "ntv" -> ProcessImportance.PERSISTENT
    else -> ProcessImportance.OTHER
}

/** Values in KiB from `/proc/meminfo`. */
internal fun parseMeminfo(output: String): Map<String, Long> = output.lineSequence()
    .mapNotNull { line ->
        val key = line.substringBefore(':', "").trim()
        val value = line.substringAfter(':', "").trim().substringBefore(' ').toLongOrNull()
        if (key.isEmpty() || value == null) null else key to value
    }
    .toMap()

/** Package part of a process name: `com.example:remote` -> `com.example`. */
internal fun basePackageOf(processName: String): String = processName.substringBefore(':')

/** Package names are passed to shell commands: accept only Android's package-name alphabet. */
internal fun isSafePackageName(name: String): Boolean = PACKAGE_NAME.matches(name)

private val WHITESPACE = Regex("\\s+")
private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$")
