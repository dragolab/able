package com.dragolabs.able.feature.settings.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Public repository the updater reads (UPD-001, ADR-051). Unauthenticated, no user data sent. */
object UpdateSource {
    const val OWNER_REPO = "dragolab/able"
    const val RELEASES_URL = "https://api.github.com/repos/$OWNER_REPO/releases?per_page=20"
    const val RELEASES_PAGE = "https://github.com/$OWNER_REPO/releases"

    /** Generic client name only: no device, account or Able install identifiers. */
    const val USER_AGENT = "Able-Updater"
    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 30_000
}

// --- GitHub REST payload (only the fields Able uses) ----------------------------------------

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String = "",
    @SerialName("content_type") val contentType: String? = null,
)

/** A newer release that can be offered to the user. */
data class AvailableUpdate(
    val version: AppVersion,
    val tag: String,
    val title: String,
    val notes: String,
    /** ISO-8601 timestamp from GitHub, e.g. `2026-11-23T10:00:00Z`; null when absent. */
    val publishedAt: String?,
    val pageUrl: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
)

/** Outcome of comparing the published releases with the installed version. */
sealed interface ReleaseCheck {
    data class UpToDate(val latest: AppVersion?) : ReleaseCheck
    data class Available(val update: AvailableUpdate) : ReleaseCheck

    /** The newest release is newer but has no `.apk` asset to download. */
    data class NoApkAsset(val version: AppVersion, val pageUrl: String) : ReleaseCheck
}

/** Honest, user-explainable failure categories. */
sealed interface UpdateError {
    data object Offline : UpdateError
    data object Timeout : UpdateError

    /** GitHub's unauthenticated limit (60 requests/hour per IP); [resetEpochSeconds] when known. */
    data class RateLimited(val resetEpochSeconds: Long?) : UpdateError
    data class Http(val code: Int) : UpdateError
    data object InvalidResponse : UpdateError
    data object InstalledVersionUnknown : UpdateError

    /** The APK could not be written to Able's cache (for example the storage is full). */
    data object Storage : UpdateError

    /** Downloaded bytes differ from the asset size GitHub published. */
    data class SizeMismatch(val expected: Long, val actual: Long) : UpdateError

    /** The file is not an APK for this app (different package name or unreadable). */
    data object NotAbleApk : UpdateError
}

object ReleaseSelector {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String): List<GitHubRelease> = json.decodeFromString(body)

    /**
     * Picks the newest non-draft release whose tag parses as a version (pre-releases included:
     * every Able beta is published as a GitHub pre-release) and compares it with [installed].
     */
    fun select(releases: List<GitHubRelease>, installed: AppVersion): ReleaseCheck {
        val newest = releases
            .filterNot { it.draft }
            .mapNotNull { release -> AppVersion.parse(release.tagName)?.let { it to release } }
            .maxByOrNull { it.first }
            ?: return ReleaseCheck.UpToDate(latest = null)
        val (version, release) = newest
        if (version <= installed) return ReleaseCheck.UpToDate(latest = version)
        val page = release.htmlUrl ?: UpdateSource.RELEASES_PAGE
        val apk = release.assets
            .filter { it.name.endsWith(".apk", ignoreCase = true) && it.downloadUrl.startsWith("https://") && it.size > 0 }
            .minByOrNull { it.name.length }
            ?: return ReleaseCheck.NoApkAsset(version, page)
        return ReleaseCheck.Available(
            AvailableUpdate(
                version = version,
                tag = release.tagName,
                title = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
                notes = release.body.orEmpty().trim(),
                publishedAt = release.publishedAt,
                pageUrl = page,
                apkName = apk.name,
                apkUrl = apk.downloadUrl,
                apkSize = apk.size,
            ),
        )
    }

    /** Maps a non-2xx GitHub API response; 403/429 with no remaining quota is a rate limit. */
    fun httpError(code: Int, rateLimitRemaining: String?, rateLimitReset: String?): UpdateError = when {
        (code == 403 || code == 429) && (rateLimitRemaining == null || rateLimitRemaining == "0") ->
            UpdateError.RateLimited(rateLimitReset?.toLongOrNull())
        else -> UpdateError.Http(code)
    }

    fun ioError(e: IOException): UpdateError = when (e) {
        is SocketTimeoutException -> UpdateError.Timeout
        is UnknownHostException, is ConnectException, is SSLException -> UpdateError.Offline
        else -> UpdateError.Offline
    }
}

class UpdateException(val error: UpdateError) : Exception(error.toString())

/** JDK-only HTTP client for the GitHub REST API (no third-party libraries, ADR-051). */
class GitHubReleaseClient {

    /** Blocking; call from an IO dispatcher. */
    fun fetchReleases(): List<GitHubRelease> {
        val connection = open(UpdateSource.RELEASES_URL, accept = "application/vnd.github+json")
        try {
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            val code = connection.responseCode
            if (code !in 200..299) {
                throw UpdateException(
                    ReleaseSelector.httpError(
                        code,
                        connection.getHeaderField("X-RateLimit-Remaining"),
                        connection.getHeaderField("X-RateLimit-Reset"),
                    ),
                )
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return runCatching { ReleaseSelector.parse(body) }
                .getOrElse { throw UpdateException(UpdateError.InvalidResponse) }
        } catch (e: IOException) {
            throw UpdateException(ReleaseSelector.ioError(e))
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Opens the asset download. GitHub answers with a redirect to its download CDN
     * (`*.githubusercontent.com`), which [HttpURLConnection] follows for https → https.
     */
    fun <T> download(url: String, block: (stream: InputStream, contentLength: Long) -> T): T {
        val connection = open(url, accept = "application/octet-stream")
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw UpdateException(
                    ReleaseSelector.httpError(code, connection.getHeaderField("X-RateLimit-Remaining"), connection.getHeaderField("X-RateLimit-Reset")),
                )
            }
            return connection.inputStream.use { block(it, connection.contentLengthLong) }
        } catch (e: IOException) {
            throw UpdateException(ReleaseSelector.ioError(e))
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, accept: String): HttpURLConnection {
        require(url.startsWith("https://")) { "Only https is allowed" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = UpdateSource.CONNECT_TIMEOUT_MS
            readTimeout = UpdateSource.READ_TIMEOUT_MS
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", UpdateSource.USER_AGENT)
        }
    }
}
