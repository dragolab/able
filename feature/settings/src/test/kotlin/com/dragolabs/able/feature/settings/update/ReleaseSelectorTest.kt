package com.dragolabs.able.feature.settings.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSelectorTest {

    private val installed = requireNotNull(AppVersion.parse("0.9.0-beta"))

    private fun release(tag: String, apk: Boolean = true, draft: Boolean = false, size: Long = 12_345_678) =
        """
        {
          "tag_name": "$tag",
          "name": "Able $tag",
          "body": "Notes for $tag",
          "draft": $draft,
          "prerelease": true,
          "published_at": "2026-11-23T10:00:00Z",
          "html_url": "https://github.com/dragolab/able/releases/tag/$tag",
          "unknown_field": {"ignored": true},
          "assets": [
            ${if (apk) """{"name": "$tag.apk", "size": $size, "browser_download_url": "https://github.com/dragolab/able/releases/download/$tag/$tag.apk", "content_type": "application/vnd.android.package-archive"}""" else """{"name": "notes.txt", "size": 10, "browser_download_url": "https://github.com/x/notes.txt"}"""}
          ]
        }
        """.trimIndent()

    private fun parse(vararg releases: String) = ReleaseSelector.parse("[" + releases.joinToString(",") + "]")

    @Test
    fun picksNewestReleaseIncludingPreReleases() {
        val result = ReleaseSelector.select(parse(release("able-0.8-beta"), release("able-0.10-beta"), release("able-0.9-beta")), installed)
        result as ReleaseCheck.Available
        assertEquals("0.10.0-beta", result.update.version.toString())
        assertEquals("able-0.10-beta.apk", result.update.apkName)
        assertEquals(12_345_678L, result.update.apkSize)
        assertEquals("Notes for able-0.10-beta", result.update.notes)
        assertEquals("2026-11-23T10:00:00Z", result.update.publishedAt)
        assertTrue(result.update.apkUrl.startsWith("https://github.com/"))
    }

    @Test
    fun sameVersionIsUpToDate() {
        val result = ReleaseSelector.select(parse(release("able-0.9-beta"), release("able-0.8-beta")), installed)
        assertEquals(ReleaseCheck.UpToDate(AppVersion.parse("0.9.0-beta")), result)
    }

    @Test
    fun draftsAndUnparseableTagsAreIgnored() {
        val result = ReleaseSelector.select(parse(release("able-1.0-beta", draft = true), release("nightly"), release("able-0.9-beta")), installed)
        assertTrue(result is ReleaseCheck.UpToDate)
        assertEquals(ReleaseCheck.UpToDate(null), ReleaseSelector.select(emptyList(), installed))
    }

    @Test
    fun newerReleaseWithoutApkIsReported() {
        val result = ReleaseSelector.select(parse(release("able-0.10-beta", apk = false)), installed)
        result as ReleaseCheck.NoApkAsset
        assertEquals("0.10.0-beta", result.version.toString())
        assertEquals(
            ReleaseCheck.NoApkAsset(result.version, "https://github.com/dragolab/able/releases/tag/able-0.10-beta"),
            result,
        )
    }

    @Test
    fun zeroSizeApkIsNotOffered() {
        val result = ReleaseSelector.select(parse(release("able-0.10-beta", size = 0)), installed)
        assertTrue(result is ReleaseCheck.NoApkAsset)
    }

    @Test
    fun rateLimitIsDistinguishedFromOtherErrors() {
        assertEquals(UpdateError.RateLimited(1_800_000_000L), ReleaseSelector.httpError(403, "0", "1800000000"))
        assertEquals(UpdateError.RateLimited(null), ReleaseSelector.httpError(429, null, null))
        assertEquals(UpdateError.Http(403), ReleaseSelector.httpError(403, "12", "1800000000"))
        assertEquals(UpdateError.Http(404), ReleaseSelector.httpError(404, "59", null))
        assertEquals(UpdateError.Http(500), ReleaseSelector.httpError(500, null, null))
    }

    @Test
    fun networkFailuresMapToHonestErrors() {
        assertEquals(UpdateError.Timeout, ReleaseSelector.ioError(java.net.SocketTimeoutException()))
        assertEquals(UpdateError.Offline, ReleaseSelector.ioError(java.net.UnknownHostException("api.github.com")))
        assertEquals(UpdateError.Offline, ReleaseSelector.ioError(java.net.ConnectException()))
    }

    @Test
    fun autoCheckRunsAtMostOncePerDayAndOnlyWhenEnabled() {
        val day = AppUpdater.AUTO_CHECK_INTERVAL_MS
        val now = 10 * day
        assertTrue(AppUpdater.isAutoCheckDue(enabled = true, onboardingCompleted = true, lastCheckEpochMs = 0, nowMs = now))
        assertTrue(AppUpdater.isAutoCheckDue(true, true, now - day, now))
        assertFalse(AppUpdater.isAutoCheckDue(true, true, now - day + 1, now))
        assertFalse(AppUpdater.isAutoCheckDue(enabled = false, onboardingCompleted = true, lastCheckEpochMs = 0, nowMs = now))
        assertFalse(AppUpdater.isAutoCheckDue(enabled = true, onboardingCompleted = false, lastCheckEpochMs = 0, nowMs = now))
        // Clock moved backwards: check rather than stay silent forever.
        assertTrue(AppUpdater.isAutoCheckDue(true, true, now + day, now))
    }

    @Test
    fun signatureConflictsAreRecognized() {
        assertEquals(InstallFailure.Signature, AppUpdater.installFailure(android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT))
        assertEquals(InstallFailure.Signature, AppUpdater.installFailure(android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE))
        assertEquals(InstallFailure.Aborted, AppUpdater.installFailure(android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED))
        assertEquals(InstallFailure.Generic, AppUpdater.installFailure(android.content.pm.PackageInstaller.STATUS_FAILURE))
    }

    @Test
    fun fresh_addsAPerMinuteCacheBuster() {
        org.junit.Assert.assertEquals(
            "https://example.org/a.json?t=2",
            UpdateSource.fresh("https://example.org/a.json", nowMs = 120_500),
        )
        org.junit.Assert.assertEquals(
            "https://example.org/r?per_page=20&t=1",
            UpdateSource.fresh("https://example.org/r?per_page=20", nowMs = 60_000),
        )
    }

    @Test
    fun manifest_isAOneElementReleaseArray() {
        val body = """[{"tag_name":"able-0.9.3-beta","name":"able-0.9.3-beta","assets":[{"name":"able-0.9.3-beta.apk","size":10,"browser_download_url":"https://github.com/dragolab/able/releases/download/able-0.9.3-beta/able-0.9.3-beta.apk"}]}]"""
        val check = ReleaseSelector.select(ReleaseSelector.parse(body), AppVersion.parse("0.9.2-beta")!!)
        org.junit.Assert.assertTrue(check is ReleaseCheck.Available)
    }
}
