package com.dragolabs.able.feature.tools.shortcuts

import com.dragolabs.able.core.data.shortcut.ShortcutMeta
import com.dragolabs.able.core.data.shortcut.ShortcutTarget
import com.dragolabs.able.core.model.ShortcutItem
import com.dragolabs.able.core.model.ShortcutTargetType
import com.dragolabs.able.feature.tools.activities.AppActivities
import com.dragolabs.able.feature.tools.activities.LaunchableActivity
import com.dragolabs.able.feature.tools.activities.filterActivities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutDefinitionsTest {

    @Test
    fun settingsFilterKeepsResolvableActionsInOrderWithoutDuplicates() {
        val resolved = mapOf(
            "a.WIFI" to "settings/Wifi",
            "a.APPS" to "settings/Apps",
            "a.MANAGE_APPS" to "settings/Apps", // same screen as a.APPS
            "a.DISPLAY" to "settings/Display",
        )
        val result = availableSettingsActions(listOf("a.WIFI", "a.MISSING", "a.APPS", "a.MANAGE_APPS", "a.DISPLAY", "a.WIFI")) { resolved[it] }
        assertEquals(listOf("a.WIFI", "a.APPS", "a.DISPLAY"), result)
        assertTrue(availableSettingsActions(curatedSettingsActions) { null }.isEmpty())
    }

    @Test
    fun targetKindsCoverFoldersAndSettings() {
        assertEquals(TargetKind.FOLDER, ShortcutTarget.Document("content://docs/tree/x", null, isFolder = true).kind)
        assertEquals(TargetKind.FILE, ShortcutTarget.Document("content://docs/document/x", null, isFolder = false).kind)
        assertEquals(TargetKind.SETTINGS, ShortcutTarget.SettingsPage("android.settings.WIFI_SETTINGS").kind)
    }

    @Test
    fun recentHistoryUsesLastPinRequestElseCreation() {
        val base = ShortcutItem(id = "a", label = "A", targetType = ShortcutTargetType.URL, targetData = "https://example.com", createdAtEpochMs = 10L)
        assertEquals(10L, base.lastActivityAtMs)
        assertEquals(99L, base.withMeta(ShortcutMeta(lastPinRequestAtMs = 99L)).lastActivityAtMs)
    }

    @Test
    fun iconTextIsClampedByCodePoint() {
        assertEquals("AB", clampIconText(" AB "))
        assertEquals("🙂🙂🙂🙂", clampIconText("🙂🙂🙂🙂🙂"))
    }

    @Test
    fun argbHexRoundTrips() {
        assertEquals(0xFF112233.toInt(), parseArgbHex("#112233"))
        assertEquals(0x80112233.toInt(), parseArgbHex(argbToHex(0x80112233.toInt())))
        assertNull(parseArgbHex("#12"))
        assertNull(parseArgbHex(null))
    }

    @Test
    fun activityFilterCanKeepMainEntriesOnly() {
        val app = AppActivities(
            "com.example", "Example",
            listOf(
                LaunchableActivity("com.example", "com.example.Main", "Example", "Example", true),
                LaunchableActivity("com.example", "com.example.Debug", "Debug", "Example", false),
            ),
        )
        val main = filterActivities(listOf(app), "", mainOnly = true)
        assertEquals(listOf("com.example.Main"), main.single().activities.map { it.className })
        assertTrue(filterActivities(listOf(app), "debug", mainOnly = true).isEmpty())
    }
}
