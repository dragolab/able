package com.dragolabs.able.feature.home

import com.dragolabs.able.core.data.shortcut.IconSource
import com.dragolabs.able.core.data.shortcut.ShortcutLaunchFailure
import com.dragolabs.able.core.data.shortcut.ShortcutMeta
import com.dragolabs.able.core.data.shortcut.ShortcutTarget
import com.dragolabs.able.core.database.entity.ShortcutDefinitionEntity
import com.dragolabs.able.core.model.ShortcutTargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteShortcutsTest {

    private fun entity(
        id: String,
        sortOrder: Int,
        pinned: Boolean = false,
        type: String = ShortcutTargetType.URL.name,
        data: String = "https://example.org",
        pkg: String? = null,
        iconType: String = "DEFAULT",
        iconValue: String? = null,
        color: String? = null,
    ) = ShortcutDefinitionEntity(
        id = id,
        label = id,
        targetType = type,
        targetData = data,
        targetPackage = pkg,
        collectionId = null,
        iconType = iconType,
        iconValue = iconValue,
        iconBackgroundColorHex = color,
        sortOrder = sortOrder,
        isPinnedToLauncher = pinned,
        createdAtEpochMs = 0,
    )

    private fun meta(fav: Boolean, text: String? = null) =
        ShortcutMeta(source = if (text != null) IconSource.TEXT else IconSource.TARGET, value = text, favorite = fav).encode()

    @Test
    fun favoritesFirstThenPinnedThenSortOrderAndCapped() {
        val list = (0 until 9).map {
            entity("s$it", sortOrder = it, pinned = it == 5 || it == 7, iconValue = meta(fav = it == 8 || it == 7))
        }
        val result = favoriteShortcuts(list)
        assertEquals(HOME_SHORTCUT_LIMIT, result.size)
        // Favorites (7 is also pinned, 8 is not) by pinned then sortOrder, then pinned 5, then the rest.
        assertEquals(listOf("s7", "s8", "s5", "s0", "s1", "s2"), result.map { it.id })
        assertTrue(result[0].favorite)
    }

    @Test
    fun legacyRawIconValuesAreNotFavorites() {
        val result = favoriteShortcuts(listOf(entity("a", 1, iconValue = "fav=1"), entity("b", 0)))
        assertEquals(listOf("b", "a"), result.map { it.id })
        assertFalse(result.any { it.favorite })
    }

    @Test
    fun unknownTypeAndBadColorAreTolerated() {
        val result = favoriteShortcuts(listOf(entity("x", 0, type = "NOT_A_TYPE", color = "#zz")))
        assertNull(result.single().targetType)
        assertNull(result.single().target)
        assertNull(result.single().iconBackgroundArgb)
    }

    @Test
    fun emojiIconComesFromEncodedMetaAndColorIsParsed() {
        val result = favoriteShortcuts(
            listOf(entity("e", 0, iconType = "TEXT_OR_EMOJI", iconValue = meta(fav = true, text = "🙂A"), color = "#336699")),
        )
        assertEquals("🙂A", result.single().iconText)
        assertEquals(0xFF336699L, result.single().iconBackgroundArgb)
        // Legacy raw text value is still readable.
        assertEquals("B", favoriteShortcuts(listOf(entity("l", 0, iconType = "TEXT_OR_EMOJI", iconValue = "B"))).single().iconText)
        // Material icon keys are not shown as text.
        val material = ShortcutMeta(source = IconSource.MATERIAL, value = "star").encode()
        assertNull(favoriteShortcuts(listOf(entity("m", 0, iconValue = material))).single().iconText)
    }

    @Test
    fun storedTargetsAreDecodedWithSharedCodec() {
        val settings = entity("s", 0, type = ShortcutTargetType.ACTIVITY.name, data = "settings:android.settings.WIFI_SETTINGS")
        val folder = entity("f", 1, type = ShortcutTargetType.FILE_OR_FOLDER.name, data = "content://docs/tree/x", pkg = "folder")
        val able = entity("t", 2, type = ShortcutTargetType.ABLE_ACTION.name, data = "tool/torch")
        val bad = entity("b", 3, type = ShortcutTargetType.URL.name, data = "javascript:alert(1)")
        val result = favoriteShortcuts(listOf(settings, folder, able, bad)).associateBy { it.id }
        assertEquals(ShortcutTarget.SettingsPage("android.settings.WIFI_SETTINGS"), result.getValue("s").target)
        assertEquals(ShortcutTarget.Document("content://docs/tree/x", null, isFolder = true), result.getValue("f").target)
        assertEquals(ShortcutTarget.AbleAction("tool/torch"), result.getValue("t").target)
        assertNull(result.getValue("b").target)
    }

    @Test
    fun launchFailuresMapToHomeMessages() {
        assertEquals(HomeMessage.SHORTCUT_APP_MISSING, ShortcutLaunchFailure.APP_MISSING.toHomeMessage())
        assertEquals(HomeMessage.SHORTCUT_UNSUPPORTED, ShortcutLaunchFailure.INVALID.toHomeMessage())
        assertEquals(HomeMessage.SHORTCUT_BLOCKED, ShortcutLaunchFailure.BLOCKED.toHomeMessage())
        assertEquals(HomeMessage.SHORTCUT_FAILED, ShortcutLaunchFailure.NO_HANDLER.toHomeMessage())
    }
}
