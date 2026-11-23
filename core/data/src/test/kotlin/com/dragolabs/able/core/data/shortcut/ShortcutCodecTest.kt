package com.dragolabs.able.core.data.shortcut

import com.dragolabs.able.core.model.ShortcutIconType
import com.dragolabs.able.core.model.ShortcutTargetType
import com.dragolabs.able.core.model.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutCodecTest {

    @Test
    fun webUrlsAreNormalizedToHttps() {
        assertEquals("https://example.com", normalizeWebUrl("example.com"))
        assertEquals("https://example.com/path?q=1", normalizeWebUrl("  example.com/path?q=1 "))
        assertEquals("http://example.com", normalizeWebUrl("HTTP://example.com"))
        assertEquals("https://sub.example.co.uk:8443/x", normalizeWebUrl("https://sub.example.co.uk:8443/x"))
        assertEquals("https://example.com:8080/a", normalizeWebUrl("example.com:8080/a"))
        assertEquals("https://192.168.1.1", normalizeWebUrl("192.168.1.1"))
        assertEquals("http://localhost:3000", normalizeWebUrl("http://localhost:3000"))
    }

    @Test
    fun unsafeOrMalformedUrlsAreRejected() {
        listOf(
            "", "   ", "javascript:alert(1)", "file:///sdcard/a.txt", "intent://x#Intent;end", "ftp://example.com",
            "mailto:a@b.com", "example", "exa mple.com", "https://", "https://-bad.com", "https://example.c",
            "https://999.1.1.1", "https://example.123",
        ).forEach { assertNull("expected rejection: '$it'", normalizeWebUrl(it)) }
    }

    @Test
    fun targetsRoundTripThroughStoredFormat() {
        val targets = listOf(
            ShortcutTarget.App("com.example"),
            ShortcutTarget.Activity("com.example", "com.example.ui.Main"),
            ShortcutTarget.Activity("com.example", "com.example.ui.Outer\$Inner"),
            ShortcutTarget.SettingsPage("android.settings.WIFI_SETTINGS"),
            ShortcutTarget.SettingsPage("android.settings.panel.action.INTERNET_CONNECTIVITY"),
            ShortcutTarget.Document("content://docs/document/1%3Aa.pdf", "application/pdf", isFolder = false),
            ShortcutTarget.Document("content://docs/document/2", null, isFolder = false),
            ShortcutTarget.Document("content://docs/tree/primary%3ADownload", null, isFolder = true),
            ShortcutTarget.Website("https://example.com"),
            ShortcutTarget.AbleAction("tool/torch"),
            ShortcutTarget.AbleAction("battery"),
        )
        targets.forEach { target ->
            val e = target.encode()
            assertEquals(target, decodeTarget(e.type, e.data, e.packageName))
            assertEquals(target, decodeTarget(e.type.name, e.data, e.packageName))
        }
    }

    @Test
    fun storedFormatMatchesAgreedEncoding() {
        // Settings page: ACTIVITY + "settings:<action>" + no package.
        val settings = ShortcutTarget.SettingsPage("android.settings.WIFI_SETTINGS").encode()
        assertEquals(EncodedTarget(ShortcutTargetType.ACTIVITY, "settings:android.settings.WIFI_SETTINGS", null), settings)
        // Folder: FILE_OR_FOLDER + tree URI + "folder".
        val folder = ShortcutTarget.Document("content://docs/tree/x", null, isFolder = true).encode()
        assertEquals(EncodedTarget(ShortcutTargetType.FILE_OR_FOLDER, "content://docs/tree/x", "folder"), folder)
        // File: content URI + "mime:<type>".
        val file = ShortcutTarget.Document("content://x/1", "image/png", false).encode()
        assertEquals(EncodedTarget(ShortcutTargetType.FILE_OR_FOLDER, "content://x/1", "mime:image/png"), file)
        // Activity: targetPackage + class name.
        val activity = ShortcutTarget.Activity("com.example", "com.example.Main").encode()
        assertEquals(EncodedTarget(ShortcutTargetType.ACTIVITY, "com.example.Main", "com.example"), activity)
        assertEquals(EncodedTarget(ShortcutTargetType.ABLE_ACTION, "tool/torch", null), ShortcutTarget.AbleAction(ableToolRoute(ToolId.TORCH)).encode())
    }

    @Test
    fun legacyAndRelativeFormsAreAcceptedOnRead() {
        assertEquals(ShortcutTarget.Activity("com.example", "com.example.Main"), decodeTarget(ShortcutTargetType.ACTIVITY, ".Main", "com.example"))
        assertEquals(ShortcutTarget.Activity("com.example", "com.example.Main"), decodeTarget(ShortcutTargetType.ACTIVITY, "com.example/.Main", null))
        assertEquals(ShortcutTarget.AbleAction("tool/torch"), decodeTarget(ShortcutTargetType.ABLE_ACTION, "torch", null))
        assertEquals(ShortcutTarget.App("com.example"), decodeTarget(ShortcutTargetType.APPLICATION, "com.example", null))
    }

    @Test
    fun malformedOrUnsafeStoredTargetsDecodeToNull() {
        assertNull(decodeTarget(ShortcutTargetType.ACTIVITY, "", "com.example"))
        assertNull(decodeTarget(ShortcutTargetType.ACTIVITY, "com.example.Main", "not a package"))
        assertNull(decodeTarget(ShortcutTargetType.ACTIVITY, "com.example.Ma in", "com.example"))
        assertNull(decodeTarget(ShortcutTargetType.ACTIVITY, "settings:", null))
        assertNull(decodeTarget(ShortcutTargetType.ACTIVITY, "settings:bad action;x", null))
        assertNull(decodeTarget(ShortcutTargetType.APPLICATION, "", null))
        assertNull(decodeTarget(ShortcutTargetType.APPLICATION, "nodots", null))
        assertNull(decodeTarget(ShortcutTargetType.FILE_OR_FOLDER, "/sdcard/file", null))
        assertNull(decodeTarget(ShortcutTargetType.FILE_OR_FOLDER, "file:///sdcard/file", null))
        assertNull(decodeTarget(ShortcutTargetType.FILE_OR_FOLDER, "content://", null))
        assertNull(decodeTarget(ShortcutTargetType.URL, "javascript:alert(1)", null))
        assertNull(decodeTarget(ShortcutTargetType.ABLE_ACTION, "tool/unknown", null))
        assertNull(decodeTarget("NOT_A_TYPE", "https://example.com", null))
    }

    @Test
    fun ableRoutesAreValidated() {
        ToolId.entries.forEach {
            assertTrue(isValidAbleRoute(ableToolRoute(it)))
            assertEquals(it, toolForAbleRoute(ableToolRoute(it)))
        }
        ableTopLevelRoutes.forEach { assertTrue(isValidAbleRoute(it)); assertNull(toolForAbleRoute(it)) }
        assertFalse(isValidAbleRoute("settings/secret"))
        assertFalse(isValidAbleRoute("tool/"))
    }

    @Test
    fun metaRoundTripsAndToleratesForeignValues() {
        val meta = ShortcutMeta(IconSource.TEXT, "A;=é🙂", IconShape.SQUIRCLE, GlyphSize.LARGE, showLabel = false, favorite = true, lastPinRequestAtMs = 42L)
        assertEquals(meta, ShortcutMeta.decode(meta.encode(), meta.iconType))
        assertEquals(ShortcutIconType.TEXT_OR_EMOJI, meta.iconType)
        assertEquals("A;=é🙂", meta.iconText)
        val foreign = ShortcutMeta.decode("🙂", ShortcutIconType.TEXT_OR_EMOJI)
        assertEquals(IconSource.TEXT, foreign.source)
        assertEquals("🙂", foreign.value)
        assertFalse(foreign.favorite)
    }

    @Test
    fun metaEncodingUsesAgreedKeys() {
        val encoded = ShortcutMeta(IconSource.MATERIAL, "star", favorite = true, lastPinRequestAtMs = 7L).encode()
        assertEquals("v=1;src=MATERIAL;val=star;shape=LAUNCHER;size=MEDIUM;label=1;fav=1;pin=7", encoded)
        val decoded = ShortcutMeta.decode("v=1;src=TEXT;val=%F0%9F%99%82;fav=1", "DEFAULT")
        assertTrue(decoded.favorite)
        assertEquals("🙂", decoded.iconText)
        assertNull(ShortcutMeta.decode("v=1;src=MATERIAL;val=star", "DEFAULT").iconText)
        assertEquals(IconSource.TARGET, ShortcutMeta.decode(null, "UNKNOWN").source)
    }
}
