package com.dragolabs.able.feature.apps.data

/** Package state changes Able can request through Shizuku (APP-004). */
enum class PackageAction { DISABLE, ENABLE, UNINSTALL_FOR_USER, REINSTALL_FOR_USER }

/** Package state as read back from PackageManager for the current user. */
data class PackageStateSnapshot(
    val installedForUser: Boolean,
    val enabled: Boolean,
    val archivedBySystem: Boolean,
)

/**
 * Builds the shell commands Able sends through Shizuku. Package names are validated strictly
 * before they are interpolated so a crafted name can never inject shell syntax.
 */
object PackageCommands {

    /** Maximum package name length accepted by Android (PackageManager validation). */
    const val MAX_PACKAGE_NAME_LENGTH = 255

    /** Android package name: at least two segments, each starting with a letter. */
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun isValidPackageName(name: String): Boolean =
        name.length <= MAX_PACKAGE_NAME_LENGTH && PACKAGE_NAME.matches(name)

    /** Returns the `pm`/`cmd package` command for [action], or null if any input is invalid. */
    fun command(action: PackageAction, userId: Int, packageName: String): String? {
        if (userId < 0 || !isValidPackageName(packageName)) return null
        return when (action) {
            PackageAction.DISABLE -> "pm disable-user --user $userId $packageName"
            PackageAction.ENABLE -> "pm enable --user $userId $packageName"
            PackageAction.UNINSTALL_FOR_USER -> "pm uninstall -k --user $userId $packageName"
            PackageAction.REINSTALL_FOR_USER -> "cmd package install-existing --user $userId $packageName"
        }
    }

    fun forceStop(userId: Int, packageName: String): String? =
        if (userId < 0 || !isValidPackageName(packageName)) null else "am force-stop --user $userId $packageName"

    fun pidOf(packageName: String): String? =
        if (!isValidPackageName(packageName)) null else "pidof $packageName || true"

    // --- Install session used to restore a user app from Able's APK backup -----------------
    //
    // Verified on the API 36.1 emulator (2026-11-22): after `pm uninstall -k --user 0` of a user
    // app installed only for user 0 the APK is deleted and `install-existing` fails with
    // NameNotFoundException, while the session below reinstalls it and the kept data survives.
    // `-i <installer>` is accepted from the shell uid and sets installerPackageName.

    /** Split/base file name inside a session: plain characters only, ending in `.apk`. */
    private val APK_NAME = Regex("^[A-Za-z0-9_][A-Za-z0-9._-]{0,120}\\.apk$")

    private val SESSION_ID = Regex("""created install session \[(\d+)]""")

    fun isValidApkName(name: String): Boolean = APK_NAME.matches(name)

    /**
     * `pm install-create` for a reinstall (`-r`) for [userId], attributed to [installer] when it is
     * a valid package name (otherwise the shell is the installer).
     */
    fun installCreate(userId: Int, installer: String?): String? {
        if (userId < 0) return null
        val attribution = installer?.takeIf(::isValidPackageName)?.let { " -i $it" }.orEmpty()
        return "pm install-create -r$attribution --user $userId"
    }

    /** Streams [sizeBytes] bytes from stdin (`-`) into [sessionId] as [apkName]. */
    fun installWrite(sessionId: Int, sizeBytes: Long, apkName: String): String? {
        if (sessionId <= 0 || sizeBytes <= 0 || !isValidApkName(apkName)) return null
        return "pm install-write -S $sizeBytes $sessionId $apkName -"
    }

    fun installCommit(sessionId: Int): String? = if (sessionId <= 0) null else "pm install-commit $sessionId"

    fun installAbandon(sessionId: Int): String? = if (sessionId <= 0) null else "pm install-abandon $sessionId"

    /** Parses `Success: created install session [1234]`; null when the output has no session id. */
    fun parseSessionId(output: String): Int? =
        SESSION_ID.find(output)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

    /**
     * Whether a `pm` session step failed. `pm` reports failures either with a non-zero exit code,
     * a `Failure [...]` line, or a Java exception trace, sometimes while exiting 0.
     */
    fun isShellFailure(exitCode: Int, stdout: String, stderr: String): Boolean {
        if (exitCode != 0) return true
        val text = "$stdout\n$stderr"
        return text.contains("Failure", ignoreCase = true) ||
            text.contains("Exception", ignoreCase = false) ||
            text.contains("Error:", ignoreCase = false)
    }

    /** Short failure reason from `pm` output, e.g. `INSTALL_FAILED_VERSION_DOWNGRADE`. */
    fun failureReason(stdout: String, stderr: String): String? {
        val text = "$stdout\n$stderr"
        Regex("""Failure \[([^\]]+)]""").find(text)?.let { return it.groupValues[1].trim() }
        return text.lineSequence().map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("at ") && !it.startsWith("Exception occurred") }
    }

    /** Store page for [packageName]: the `market://` URI and the web fallback. */
    fun storeUris(packageName: String): Pair<String, String>? {
        if (!isValidPackageName(packageName)) return null
        return "market://details?id=$packageName" to "https://play.google.com/store/apps/details?id=$packageName"
    }

    /**
     * Whether the read-back [state] confirms [action]. A null state means PackageManager no
     * longer knows the package at all, which confirms an uninstall and nothing else.
     */
    fun isConfirmed(action: PackageAction, state: PackageStateSnapshot?): Boolean = when (action) {
        PackageAction.DISABLE -> state != null && state.installedForUser && !state.enabled
        PackageAction.ENABLE -> state != null && state.installedForUser && state.enabled
        PackageAction.UNINSTALL_FOR_USER -> state == null || !state.installedForUser
        PackageAction.REINSTALL_FOR_USER -> state != null && state.installedForUser && !state.archivedBySystem
    }
}
