package com.dragolabs.able.feature.apps.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest

/** One APK file inside an Able backup. */
@Serializable
data class ApkBackupFile(
    /** Session file name, e.g. `base.apk` or `split_config.arm64_v8a.apk`. */
    val name: String,
    val sizeBytes: Long,
    val sha256: String,
)

/**
 * Metadata stored as `backup.json` next to the copied APKs in
 * `filesDir/app_archive/<package>/<versionCode>/`. Kept as a file (not a Room column) so files and
 * metadata are created, verified and deleted together without a database migration.
 */
@Serializable
data class ApkBackupManifest(
    val packageName: String,
    val versionCode: Long,
    val versionName: String? = null,
    /** Package that installed the app (for example `com.android.vending`), if Android reported one. */
    val installer: String? = null,
    val createdAtEpochMs: Long,
    val files: List<ApkBackupFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

/** A verified backup on disk. */
data class ApkBackup(val manifest: ApkBackupManifest, val directory: File) {
    fun file(entry: ApkBackupFile): File = File(directory, entry.name)
}

enum class BackupProblem {
    /** PackageManager does not report the package or its APK paths. */
    NOT_FOUND,
    /** The APK files exist but Able cannot read them. */
    NOT_READABLE,
    /** Not enough free space in Able's private storage. */
    NO_SPACE,
    /** Copying or verifying failed. */
    COPY_FAILED,
}

/** What backing up a package before uninstall would take (shown in the confirmation). */
data class BackupPlan(
    val packageName: String,
    val totalBytes: Long,
    val freeBytes: Long,
    /** A verified backup of the same version already exists and will be reused. */
    val alreadyBackedUp: Boolean,
    val problem: BackupProblem?,
)

sealed interface BackupResult {
    data class Created(val backup: ApkBackup, val reused: Boolean) : BackupResult
    data class Failed(val problem: BackupProblem, val detail: String? = null) : BackupResult
}

/**
 * Copies user-app APKs into Able's private storage before "uninstall for this user", because
 * Android deletes the APK of a user app that was installed only for that user (verified on the API
 * 36.1 emulator and on Android 16, 2026-11-22). `sourceDir` and `splitSourceDirs` are
 * world-readable, so plain file IO is enough.
 */
class ApkBackupStore(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val root: File get() = File(context.filesDir, ROOT_DIR)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private data class Source(val file: File, val name: String)

    private fun appInfo(packageName: String): ApplicationInfo? = runCatching {
        var flags = PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong() or PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) flags = flags or PackageManager.MATCH_ARCHIVED_PACKAGES
        packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags))
    }.getOrNull()

    private fun sources(info: ApplicationInfo): List<Source>? {
        val base = info.sourceDir?.let(::File) ?: return null
        val splits = info.splitSourceDirs.orEmpty().mapIndexed { index, path ->
            val file = File(path)
            val name = file.name.takeIf { PackageCommands.isValidApkName(it) && it != BASE_NAME } ?: "split_$index.apk"
            Source(file, name)
        }
        return listOf(Source(base, BASE_NAME)) + splits
    }

    /** True when the installed APK files of [packageName] still exist on the device. */
    suspend fun isApkPresent(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val info = appInfo(packageName) ?: return@withContext false
        info.sourceDir?.let { File(it).exists() } ?: false
    }

    /** Whether PackageManager reports [packageName] as a (possibly updated) system app; null if unknown. */
    suspend fun isSystemPackage(packageName: String): Boolean? = withContext(Dispatchers.IO) {
        val info = appInfo(packageName) ?: return@withContext null
        info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    /** Installer package reported by Android, if any. */
    suspend fun installerOf(packageName: String): String? = withContext(Dispatchers.IO) {
        runCatching { packageManager.getInstallSourceInfo(packageName).installingPackageName }.getOrNull()
            ?.takeIf(PackageCommands::isValidPackageName)
    }

    private fun versionOf(packageName: String): Pair<Long, String?>? = runCatching {
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong() or PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
        val info = packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags))
        info.longVersionCode to info.versionName
    }.getOrNull()

    /** Estimates the backup for the confirmation dialog without copying anything. */
    suspend fun plan(packageName: String): BackupPlan = withContext(Dispatchers.IO) {
        val free = runCatching { context.filesDir.usableSpace }.getOrDefault(0L)
        val info = appInfo(packageName)
        val sources = info?.let(::sources)
        if (sources == null || sources.none { it.file.exists() }) {
            return@withContext BackupPlan(packageName, 0, free, alreadyBackedUp = false, problem = BackupProblem.NOT_FOUND)
        }
        if (sources.any { !it.file.canRead() }) {
            return@withContext BackupPlan(packageName, 0, free, alreadyBackedUp = false, problem = BackupProblem.NOT_READABLE)
        }
        val total = sources.sumOf { it.file.length() }
        val version = versionOf(packageName)?.first
        val existing = find(packageName)
        val reusable = existing != null && version != null && existing.manifest.versionCode == version &&
            existing.manifest.totalBytes == total
        val problem = if (!reusable && total + SPACE_MARGIN_BYTES > free) BackupProblem.NO_SPACE else null
        BackupPlan(packageName, total, free, alreadyBackedUp = reusable, problem = problem)
    }

    /** Copies the current APKs of [packageName], verifying size and SHA-256, and writes the manifest. */
    suspend fun backup(packageName: String): BackupResult = withContext(Dispatchers.IO) {
        if (!PackageCommands.isValidPackageName(packageName)) return@withContext BackupResult.Failed(BackupProblem.NOT_FOUND)
        val info = appInfo(packageName) ?: return@withContext BackupResult.Failed(BackupProblem.NOT_FOUND)
        val sources = sources(info)?.takeIf { list -> list.all { it.file.exists() } }
            ?: return@withContext BackupResult.Failed(BackupProblem.NOT_FOUND)
        if (sources.any { !it.file.canRead() }) return@withContext BackupResult.Failed(BackupProblem.NOT_READABLE)
        val (versionCode, versionName) = versionOf(packageName) ?: return@withContext BackupResult.Failed(BackupProblem.NOT_FOUND)
        val total = sources.sumOf { it.file.length() }

        find(packageName)?.let { existing ->
            if (existing.manifest.versionCode == versionCode && existing.manifest.totalBytes == total) {
                return@withContext BackupResult.Created(existing, reused = true)
            }
        }
        val free = runCatching { context.filesDir.usableSpace }.getOrDefault(0L)
        if (total + SPACE_MARGIN_BYTES > free) return@withContext BackupResult.Failed(BackupProblem.NO_SPACE)

        val packageDir = File(root, packageName)
        val target = File(packageDir, versionCode.toString())
        val staging = File(packageDir, "$versionCode$STAGING_SUFFIX")
        try {
            staging.deleteRecursively()
            if (!staging.mkdirs()) throw IOException("Cannot create ${staging.name}")
            val entries = sources.map { source ->
                val out = File(staging, source.name)
                val hash = copyWithHash(source.file, out)
                if (out.length() != source.file.length()) throw IOException("Size mismatch for ${source.name}")
                ApkBackupFile(source.name, out.length(), hash)
            }
            val manifest = ApkBackupManifest(
                packageName = packageName,
                versionCode = versionCode,
                versionName = versionName,
                installer = installerOf(packageName),
                createdAtEpochMs = System.currentTimeMillis(),
                files = entries,
            )
            File(staging, MANIFEST).writeText(json.encodeToString(ApkBackupManifest.serializer(), manifest))
            target.deleteRecursively()
            if (!staging.renameTo(target)) throw IOException("Cannot finalize backup")
            // Keep only the newest version of each package.
            packageDir.listFiles()?.filter { it != target }?.forEach { it.deleteRecursively() }
            BackupResult.Created(ApkBackup(manifest, target), reused = false)
        } catch (e: Exception) {
            staging.deleteRecursively()
            val problem = if (e is IOException && free < total + SPACE_MARGIN_BYTES) BackupProblem.NO_SPACE else BackupProblem.COPY_FAILED
            BackupResult.Failed(problem, e.message)
        }
    }

    /** The newest backup of [packageName] whose files are all present with the recorded sizes. */
    suspend fun find(packageName: String): ApkBackup? = withContext(Dispatchers.IO) {
        if (!PackageCommands.isValidPackageName(packageName)) return@withContext null
        val dirs = File(root, packageName).listFiles()?.filter { it.isDirectory && !it.name.endsWith(STAGING_SUFFIX) }
            ?: return@withContext null
        dirs.mapNotNull { dir ->
            val manifest = runCatching {
                json.decodeFromString(ApkBackupManifest.serializer(), File(dir, MANIFEST).readText())
            }.getOrNull() ?: return@mapNotNull null
            val valid = manifest.packageName == packageName &&
                manifest.files.isNotEmpty() &&
                manifest.files.any { it.name == BASE_NAME } &&
                manifest.files.all { PackageCommands.isValidApkName(it.name) && File(dir, it.name).length() == it.sizeBytes }
            if (valid) ApkBackup(manifest, dir) else null
        }.maxByOrNull { it.manifest.versionCode }
    }

    /** Deletes every backup of [packageName]. Returns the bytes freed. */
    suspend fun delete(packageName: String): Long = withContext(Dispatchers.IO) {
        if (!PackageCommands.isValidPackageName(packageName)) return@withContext 0L
        val dir = File(root, packageName)
        val size = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        dir.deleteRecursively()
        size
    }

    private fun copyWithHash(from: File, to: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(from.inputStream().buffered(BUFFER), digest).use { input ->
            to.outputStream().buffered(BUFFER).use { output -> input.copyTo(output, BUFFER) }
        }
        return digest.digest().toHex()
    }

    companion object {
        const val BASE_NAME = "base.apk"
        private const val ROOT_DIR = "app_archive"
        private const val MANIFEST = "backup.json"
        private const val STAGING_SUFFIX = ".tmp"
        private const val BUFFER = 256 * 1024
        /** Free space kept after a backup so Able never fills the device. */
        private const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024

        internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
