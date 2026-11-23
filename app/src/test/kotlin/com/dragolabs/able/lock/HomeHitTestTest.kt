package com.dragolabs.able.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeHitTestTest {
    private data class Fake(
        override val bounds: HitBox,
        override val viewId: String? = null,
        override val clickable: Boolean = false,
        override val longClickable: Boolean = false,
        override val className: String? = "android.view.ViewGroup",
        override val visible: Boolean = true,
        val children: List<Fake> = emptyList(),
    ) : HitNode {
        override val childCount: Int get() = children.size
        override fun child(index: Int): HitNode = children[index]
    }

    private fun id(name: String) = "com.google.android.apps.nexuslauncher:id/$name"

    /** Shape of the Pixel Launcher home tree on the API 36 emulator (uiautomator dump, 2026-11-22). */
    private val home = Fake(
        HitBox(0, 0, 1080, 2400),
        viewId = id("drag_layer"),
        children = listOf(
            Fake(
                HitBox(0, 0, 1080, 2400),
                viewId = id("workspace"),
                longClickable = true,
                children = listOf(
                    Fake(
                        HitBox(50, 93, 1030, 1809),
                        children = listOf(
                            Fake(HitBox(50, 93, 1030, 354), viewId = id("smartspace_card_pager"), longClickable = true),
                            Fake(HitBox(50, 1548, 266, 1809), clickable = true, longClickable = true, className = "android.widget.TextView"),
                            Fake(HitBox(305, 1548, 521, 1809), clickable = true, longClickable = true, className = "android.widget.TextView"),
                            Fake(
                                HitBox(50, 600, 1030, 1000),
                                className = "com.android.launcher3.widget.LauncherAppWidgetHostView",
                                children = listOf(Fake(HitBox(60, 610, 300, 700), className = "android.widget.TextView")),
                            ),
                        ),
                    ),
                ),
            ),
            Fake(HitBox(0, 63, 1080, 2337), viewId = id("accessibility_action_view"), className = "android.view.View"),
            Fake(
                HitBox(0, 1905, 1080, 2400),
                viewId = id("hotseat"),
                children = listOf(
                    Fake(HitBox(71, 1905, 244, 2100), clickable = true, longClickable = true),
                    Fake(HitBox(78, 2125, 1002, 2290), viewId = id("search_container_hotseat"), clickable = true, longClickable = true),
                ),
            ),
        ),
    )

    @Test
    fun wallpaperArea_isEmpty() = assertEquals(HomeTarget.EMPTY, HomeHitTest.target(home, 540, 1_200))

    @Test
    fun gapBetweenIcons_isEmpty() = assertEquals(HomeTarget.EMPTY, HomeHitTest.target(home, 285, 1_650))

    @Test
    fun icon_isControl() = assertEquals(HomeTarget.CONTROL, HomeHitTest.target(home, 150, 1_650))

    @Test
    fun dockIconAndSearchBar_areControls() {
        assertEquals(HomeTarget.CONTROL, HomeHitTest.target(home, 150, 2_000))
        assertEquals(HomeTarget.CONTROL, HomeHitTest.target(home, 540, 2_200))
    }

    @Test
    fun emptyDockArea_isEmpty() = assertEquals(HomeTarget.EMPTY, HomeHitTest.target(home, 540, 2_350))

    @Test
    fun widgetWithoutClickableChildren_isStillControl() =
        assertEquals(HomeTarget.CONTROL, HomeHitTest.target(home, 800, 900))

    @Test
    fun longClickableSmartspace_isControl() = assertEquals(HomeTarget.CONTROL, HomeHitTest.target(home, 500, 200))

    @Test
    fun invisibleIcon_isIgnored() {
        val tree = Fake(
            HitBox(0, 0, 100, 100),
            viewId = id("workspace"),
            children = listOf(Fake(HitBox(0, 0, 50, 50), clickable = true, visible = false)),
        )
        assertEquals(HomeTarget.EMPTY, HomeHitTest.target(tree, 10, 10))
    }

    @Test
    fun missingTreeOrOutsidePoint_isUnknown() {
        assertEquals(HomeTarget.UNKNOWN, HomeHitTest.target(null, 10, 10))
        assertEquals(HomeTarget.UNKNOWN, HomeHitTest.target(home, 2_000, 10))
    }

    @Test
    fun clickableAt_returnsDeepestClickable() {
        val node = HomeHitTest.clickableAt(home, 150, 1_650)
        assertEquals(HitBox(50, 1548, 266, 1809), node?.bounds)
        assertNull(HomeHitTest.clickableAt(home, 540, 1_200))
    }

    @Test
    fun scene_homePages() = assertEquals(
        HomeScene.WORKSPACE,
        HomeHitTest.scene(
            true,
            listOf("scrim_view", "workspace", "accessibility_action_view", "page_indicator_container", "overview_actions_view", "hotseat").map(::id),
        ),
    )

    @Test
    fun scene_drawerRecentsMenuOrFolder_isOther() {
        assertEquals(HomeScene.OTHER, HomeHitTest.scene(true, listOf(id("scrim_view"), id("apps_view"), id("hotseat"))))
        assertEquals(HomeScene.OTHER, HomeHitTest.scene(true, listOf(id("overview_panel"))))
        assertEquals(HomeScene.OTHER, HomeHitTest.scene(true, listOf(id("workspace"), id("hotseat"), id("popup_container"))))
        // A floating view without an ID (folder, drag view) is never assumed to be harmless.
        assertEquals(HomeScene.OTHER, HomeHitTest.scene(true, listOf(id("workspace"), id("hotseat"), null)))
    }

    @Test
    fun scene_unknownLauncher() = assertEquals(HomeScene.UNKNOWN, HomeHitTest.scene(false, emptyList()))
}
