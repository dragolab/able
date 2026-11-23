package com.dragolabs.able.feature.apps.data

import com.dragolabs.able.core.capability.ShizukuAdapter
import com.dragolabs.able.core.common.AbleResult
import kotlinx.coroutines.delay

enum class PackageActionOutcome {
    /** Read-back state matches the requested change. */
    APPLIED,
    /** The command failed and the state did not change. */
    REJECTED,
    /** The command was accepted but the read-back state does not (yet) confirm it. */
    NOT_CONFIRMED,
}

data class PackageActionResult(
    val outcome: PackageActionOutcome,
    val before: PackageStateSnapshot?,
    val after: PackageStateSnapshot?,
    val detail: String? = null,
    /**
     * No command was run: Android deleted the APK of this user app and Able has no backup, so only
     * the store can reinstall it (the kept data is reused on reinstall).
     */
    val storeRequired: Boolean = false,
)

/**
 * Runs package state changes through Shizuku and verifies them by re-querying PackageManager
 * (APP-004). Never fakes success: the outcome is derived from the read-back state.
 */
class PackageActionRunner(
    private val catalog: InstalledAppCatalog,
    private val shizuku: ShizukuAdapter,
    private val stdinShell: ShizukuStdinShell = ShizukuStdinShell(shizuku),
) {

    suspend fun run(action: PackageAction, packageName: String): PackageActionResult {
        val before = catalog.readState(packageName)
        val command = PackageCommands.command(action, catalog.currentUserId, packageName)
            ?: return PackageActionResult(PackageActionOutcome.REJECTED, before, before, "Invalid package name")
        val response = shizuku.executeCommand(command, COMMAND_TIMEOUT_MS)
        delay(VERIFY_DELAY_MS)
        val after = catalog.readState(packageName)
        val confirmed = PackageCommands.isConfirmed(action, after)
        val commandDetail = when (response) {
            is AbleResult.Failure -> response.error.message
            is AbleResult.Success -> response.data.takeIf { it.isNotBlank() }
            AbleResult.Loading -> null
        }
        // `pm` sometimes exits 0 while printing "Failure [...]".
        val reportedFailure = response is AbleResult.Failure ||
            (response is AbleResult.Success && response.data.contains("Failure", ignoreCase = true))
        val outcome = when {
            confirmed -> PackageActionOutcome.APPLIED
            reportedFailure && after == before -> PackageActionOutcome.REJECTED
            else -> PackageActionOutcome.NOT_CONFIRMED
        }
        return PackageActionResult(outcome, before, after, commandDetail)
    }

    /**
     * Reinstalls [packageName] for the current user from Able's APK [backup] through a `pm`
     * install session fed over stdin (create → write each APK → commit; abandon on failure).
     * Data kept by the earlier `uninstall -k` is preserved by Android. The outcome is derived from
     * the read-back FLAG_INSTALLED state.
     */
    suspend fun installFromBackup(packageName: String, backup: ApkBackup): PackageActionResult {
        val before = catalog.readState(packageName)
        if (!PackageCommands.isValidPackageName(packageName) || backup.manifest.packageName != packageName) {
            return PackageActionResult(PackageActionOutcome.REJECTED, before, before, "Invalid package name")
        }
        val user = catalog.currentUserId
        // Prefer the original installer attribution (accepted from the shell uid on API 36), then
        // fall back to the shell as installer.
        val attributions: List<String?> = backup.manifest.installer?.let { listOf(it, null) } ?: listOf(null)
        var lastDetail: String? = null
        var committed = false
        for (installer in attributions) {
            val attempt = installSession(user, installer, backup)
            lastDetail = attempt.detail
            if (attempt.committed) {
                committed = true
                break
            }
            if (!attempt.retryable) break
        }
        delay(VERIFY_DELAY_MS)
        val after = catalog.readState(packageName)
        val confirmed = PackageCommands.isConfirmed(PackageAction.REINSTALL_FOR_USER, after)
        val outcome = when {
            confirmed -> PackageActionOutcome.APPLIED
            !committed && after == before -> PackageActionOutcome.REJECTED
            else -> PackageActionOutcome.NOT_CONFIRMED
        }
        return PackageActionResult(outcome, before, after, if (confirmed) null else lastDetail)
    }

    private data class SessionAttempt(val committed: Boolean, val detail: String?, val retryable: Boolean)

    private suspend fun installSession(user: Int, installer: String?, backup: ApkBackup): SessionAttempt {
        val create = PackageCommands.installCreate(user, installer)
            ?: return SessionAttempt(false, "Invalid user", retryable = false)
        val created = stdinShell.run(create, COMMAND_TIMEOUT_MS)
            ?: return SessionAttempt(false, "Shizuku unavailable", retryable = false)
        val sessionId = PackageCommands.parseSessionId(created.stdout)
        if (created.failed || sessionId == null) {
            return SessionAttempt(false, PackageCommands.failureReason(created.stdout, created.stderr), retryable = true)
        }
        suspend fun abandon(detail: String?, retryable: Boolean): SessionAttempt {
            PackageCommands.installAbandon(sessionId)?.let { stdinShell.run(it, COMMAND_TIMEOUT_MS) }
            return SessionAttempt(false, detail, retryable)
        }
        for (entry in backup.manifest.files) {
            val file = backup.file(entry)
            val write = PackageCommands.installWrite(sessionId, entry.sizeBytes, entry.name)
                ?: return abandon("Invalid backup file ${entry.name}", retryable = false)
            if (file.length() != entry.sizeBytes) return abandon("Backup file changed: ${entry.name}", retryable = false)
            var hash: String? = null
            val written = stdinShell.run(write, WRITE_TIMEOUT_MS) { stdin ->
                hash = streamWithHash(file, stdin)
            } ?: return abandon("Shizuku unavailable", retryable = false)
            if (written.failed) {
                return abandon(PackageCommands.failureReason(written.stdout, written.stderr), retryable = false)
            }
            if (hash != entry.sha256) return abandon("Backup checksum mismatch: ${entry.name}", retryable = false)
        }
        val commit = PackageCommands.installCommit(sessionId) ?: return abandon("Invalid session", retryable = false)
        var result = stdinShell.run(commit, COMMIT_TIMEOUT_MS)
            ?: return abandon("Shizuku unavailable", retryable = false)
        if (result.failed && (result.stdout + result.stderr).contains("Files still open")) {
            // The write side may close a moment after `install-write` returns.
            delay(COMMIT_RETRY_DELAY_MS)
            result = stdinShell.run(commit, COMMIT_TIMEOUT_MS) ?: return abandon("Shizuku unavailable", retryable = false)
        }
        if (result.failed) {
            // Retrying with another installer attribution only helps when the installer was refused.
            val reason = PackageCommands.failureReason(result.stdout, result.stderr)
            val installerRefused = installer != null && "${result.stdout}\n${result.stderr}".contains("installer", ignoreCase = true)
            return abandon(reason, retryable = installerRefused)
        }
        return SessionAttempt(true, null, retryable = false)
    }

    private fun streamWithHash(file: java.io.File, out: java.io.OutputStream): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        java.security.DigestInputStream(file.inputStream().buffered(STREAM_BUFFER), digest).use { input ->
            input.copyTo(out, STREAM_BUFFER)
        }
        out.flush()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val COMMAND_TIMEOUT_MS = 15_000L
        const val WRITE_TIMEOUT_MS = 180_000L
        const val COMMIT_TIMEOUT_MS = 120_000L
        const val COMMIT_RETRY_DELAY_MS = 800L
        const val VERIFY_DELAY_MS = 400L
        const val STREAM_BUFFER = 256 * 1024
    }
}
