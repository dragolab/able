package com.dragolabs.able.feature.settings.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import com.dragolabs.able.core.data.AbleContainer
import com.dragolabs.able.core.database.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Result of the last check, shown in Settings → Updates. */
sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState

    /** A previous check (possibly in an earlier session) saw [version]; details need a new check. */
    data class KnownNewer(val version: AppVersion) : CheckState
    data class UpToDate(val latest: AppVersion?) : CheckState
    data class Available(val update: AvailableUpdate) : CheckState
    data class NoApk(val version: AppVersion, val pageUrl: String) : CheckState
    data class Failed(val error: UpdateError) : CheckState
}

/** Download and installation progress (only ever started by a user tap). */
sealed interface InstallState {
    data object Idle : InstallState

    /** "Install unknown apps" is off for Able; the user must allow it in Android settings. */
    data object NeedsPermission : InstallState
    data class Downloading(val bytes: Long, val total: Long) : InstallState

    /** The session was committed; waiting for Android. */
    data object Installing : InstallState

    /** Android needs the user to confirm in the system installer dialog. */
    data class AwaitingConfirmation(val confirmIntent: Intent) : InstallState

    /** Rarely observed: Android normally restarts Able right after a successful self-update. */
    data object Installed : InstallState
    data class Failed(val failure: InstallFailure, val detail: String? = null) : InstallState
}

sealed interface InstallFailure {
    data class Download(val error: UpdateError) : InstallFailure

    /** STATUS_FAILURE_CONFLICT / STATUS_FAILURE_INCOMPATIBLE: different signing key or a downgrade. */
    data object Signature : InstallFailure
    data object Aborted : InstallFailure
    data object Blocked : InstallFailure
    data object InvalidApk : InstallFailure
    data object StorageFull : InstallFailure
    data object Generic : InstallFailure
}

data class UpdaterState(
    val installedVersion: String = "",
    val check: CheckState = CheckState.Idle,
    val install: InstallState = InstallState.Idle,
    val autoCheck: Boolean = true,
    val lastCheckEpochMs: Long = 0L,
)

/**
 * In-app updater from GitHub Releases (UPD-001, ADR-051). Contacts only `api.github.com` to
 * check and, after the user taps "Download and install", the release asset URL on `github.com`
 * (redirected to GitHub's download CDN). Sends no user data. Installation goes through the
 * Android [PackageInstaller] session API and its system confirmation dialog.
 *
 * Process-wide singleton so a download keeps running when Settings is closed and the install
 * result receiver can report back.
 */
class AppUpdater internal constructor(
    private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val scope: CoroutineScope,
    private val client: GitHubReleaseClient = GitHubReleaseClient(),
) {
    private val installedName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
    }.getOrNull().orEmpty()
    private val installed: AppVersion? = AppVersion.parse(installedName)

    private val _state = MutableStateFlow(UpdaterState(installedVersion = installedName))
    val state: StateFlow<UpdaterState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    private val updatesDir: File get() = File(context.cacheDir, "updates")

    init {
        scope.launch {
            preferences.userPreferencesFlow.collect { prefs ->
                _state.update { s ->
                    val known = AppVersion.parse(prefs.updateLatestSeenVersion)
                    val check = if (s.check == CheckState.Idle && known != null && installed != null && known > installed) {
                        CheckState.KnownNewer(known)
                    } else {
                        s.check
                    }
                    s.copy(autoCheck = prefs.updateAutoCheck, lastCheckEpochMs = prefs.updateLastCheckEpochMs, check = check)
                }
            }
        }
        // Leftover APKs from an earlier session are useless after the install or a cancellation.
        scope.launch(Dispatchers.IO) { runCatching { updatesDir.listFiles()?.forEach { it.delete() } } }
    }

    fun setAutoCheck(enabled: Boolean) {
        scope.launch { preferences.setUpdateAutoCheck(enabled) }
    }

    /** User-initiated check. */
    fun check() {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch { runCheck() }
    }

    private suspend fun runCheck(): CheckState {
        _state.update { it.copy(check = CheckState.Checking) }
        val result = if (installed == null) {
            CheckState.Failed(UpdateError.InstalledVersionUnknown)
        } else {
            try {
                val releases = withContext(Dispatchers.IO) { client.fetchReleases() }
                when (val r = ReleaseSelector.select(releases, installed)) {
                    is ReleaseCheck.UpToDate -> CheckState.UpToDate(r.latest)
                    is ReleaseCheck.Available -> CheckState.Available(r.update)
                    is ReleaseCheck.NoApkAsset -> CheckState.NoApk(r.version, r.pageUrl)
                }
            } catch (e: UpdateException) {
                CheckState.Failed(e.error)
            }
        }
        if (result !is CheckState.Failed) {
            val latest = when (result) {
                is CheckState.UpToDate -> result.latest
                is CheckState.Available -> result.update.version
                is CheckState.NoApk -> result.version
                else -> null
            }
            preferences.recordUpdateCheck(System.currentTimeMillis(), latest?.toString())
        }
        _state.update { it.copy(check = result) }
        return result
    }

    /**
     * App-start check (UPD-001): only when enabled, after onboarding, and at most once every
     * 24 h. Never downloads. Returns the update to announce once, or null.
     */
    suspend fun autoCheckIfDue(nowMs: Long = System.currentTimeMillis()): AvailableUpdate? {
        val prefs = preferences.userPreferencesFlow.first()
        if (!isAutoCheckDue(prefs.updateAutoCheck, prefs.onboardingCompleted, prefs.updateLastCheckEpochMs, nowMs)) return null
        if (checkJob?.isActive == true) return null
        val result = runCheck()
        val update = (result as? CheckState.Available)?.update ?: return null
        return update.takeIf { prefs.updateNotifiedVersion != it.version.toString() }
    }

    fun markNotified(update: AvailableUpdate) {
        scope.launch { preferences.setUpdateNotifiedVersion(update.version.toString()) }
    }

    /** On resume: once "Install unknown apps" is allowed, offer Download and install again. */
    fun onResume() = _state.update {
        if (it.install == InstallState.NeedsPermission && canRequestInstalls()) it.copy(install = InstallState.Idle) else it
    }

    fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** User tapped "Download and install". */
    fun downloadAndInstall(update: AvailableUpdate) {
        if (downloadJob?.isActive == true) return
        if (!canRequestInstalls()) {
            _state.update { it.copy(install = InstallState.NeedsPermission) }
            return
        }
        downloadJob = scope.launch {
            _state.update { it.copy(install = InstallState.Downloading(0, update.apkSize)) }
            val file = try {
                withContext(Dispatchers.IO) { download(update) }
            } catch (e: UpdateException) {
                _state.update { it.copy(install = InstallState.Failed(InstallFailure.Download(e.error))) }
                return@launch
            }
            _state.update { it.copy(install = InstallState.Installing) }
            try {
                withContext(Dispatchers.IO) { commitSession(file) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(install = InstallState.Failed(InstallFailure.Generic, e.message)) }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.update { it.copy(install = InstallState.Idle) }
        scope.launch(Dispatchers.IO) { runCatching { updatesDir.listFiles()?.forEach { it.delete() } } }
    }

    fun dismissInstallResult() = _state.update {
        when (it.install) {
            is InstallState.Downloading, InstallState.Installing -> it
            else -> it.copy(install = InstallState.Idle)
        }
    }

    private suspend fun download(update: AvailableUpdate): File {
        val dir = updatesDir
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        if (!dir.isDirectory && !dir.mkdirs()) throw UpdateException(UpdateError.Storage)
        val safeName = update.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "able-update.apk" }
        val target = File(dir, safeName)
        val ctx = coroutineContext
        val written = client.download(update.apkUrl) { input, _ ->
            var total = 0L
            var lastReported = 0L
            try {
                target.outputStream().buffered().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER)
                    while (true) {
                        ctx.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        try {
                            out.write(buffer, 0, n)
                        } catch (_: IOException) {
                            throw UpdateException(UpdateError.Storage)
                        }
                        total += n
                        if (total > update.apkSize) throw UpdateException(UpdateError.SizeMismatch(update.apkSize, total))
                        if (total - lastReported >= PROGRESS_STEP || total == update.apkSize) {
                            lastReported = total
                            _state.update { it.copy(install = InstallState.Downloading(total, update.apkSize)) }
                        }
                    }
                }
            } catch (e: UpdateException) {
                target.delete()
                throw e
            }
            total
        }
        if (written != update.apkSize || target.length() != update.apkSize) {
            target.delete()
            throw UpdateException(UpdateError.SizeMismatch(update.apkSize, written))
        }
        // Defense in depth: the file must be an APK of this very app; Android still verifies the signature.
        val archive = context.packageManager.getPackageArchiveInfo(target.path, PackageManager.PackageInfoFlags.of(0))
        if (archive?.packageName != context.packageName) {
            target.delete()
            throw UpdateException(UpdateError.NotAbleApk)
        }
        return target
    }

    private fun commitSession(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            // Always show Android's own confirmation: nothing installs without the user agreeing.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            try {
                apk.inputStream().use { input ->
                    session.openWrite("able.apk", 0, apk.length()).use { out ->
                        input.copyTo(out, DEFAULT_BUFFER)
                        session.fsync(out)
                    }
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS)
                    .setPackage(context.packageName)
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    // Mutable: PackageInstaller adds the status extras. The intent is explicit.
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            } catch (e: Exception) {
                session.abandon()
                throw e
            }
        }
    }

    /** Called by [UpdateInstallReceiver] with the PackageInstaller status. */
    internal fun onInstallStatus(status: Int, message: String?, confirmIntent: Intent?) {
        val next = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                confirmIntent?.let { InstallState.AwaitingConfirmation(it) } ?: InstallState.Failed(InstallFailure.Generic, message)
            PackageInstaller.STATUS_SUCCESS -> InstallState.Installed
            else -> InstallState.Failed(installFailure(status), message)
        }
        _state.update { it.copy(install = next) }
        if (next !is InstallState.AwaitingConfirmation) {
            scope.launch(Dispatchers.IO) { runCatching { updatesDir.listFiles()?.forEach { it.delete() } } }
        }
    }

    companion object {
        private const val DEFAULT_BUFFER = 64 * 1024
        private const val PROGRESS_STEP = 256L * 1024
        const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

        @Volatile
        private var instance: AppUpdater? = null

        fun get(container: AbleContainer): AppUpdater = instance ?: synchronized(this) {
            instance ?: AppUpdater(container.appContext, container.preferences, container.applicationScope).also { instance = it }
        }

        /** Pure gate for the app-start check; a clock moved backwards counts as due. */
        fun isAutoCheckDue(enabled: Boolean, onboardingCompleted: Boolean, lastCheckEpochMs: Long, nowMs: Long): Boolean {
            if (!enabled || !onboardingCompleted) return false
            if (lastCheckEpochMs <= 0L || lastCheckEpochMs > nowMs) return true
            return nowMs - lastCheckEpochMs >= AUTO_CHECK_INTERVAL_MS
        }

        fun installFailure(status: Int): InstallFailure = when (status) {
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallFailure.Signature
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallFailure.Aborted
            PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallFailure.Blocked
            PackageInstaller.STATUS_FAILURE_INVALID -> InstallFailure.InvalidApk
            PackageInstaller.STATUS_FAILURE_STORAGE -> InstallFailure.StorageFull
            else -> InstallFailure.Generic
        }
    }
}
