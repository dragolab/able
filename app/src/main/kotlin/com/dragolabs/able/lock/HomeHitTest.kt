package com.dragolabs.able.lock

/** Screen rectangle in pixels (pure Kotlin so the rules below run in plain unit tests). */
data class HitBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
}

/** The parts of an accessibility node the home-screen rules need; fakes implement it in tests. */
interface HitNode {
    val bounds: HitBox
    val visible: Boolean
    val clickable: Boolean
    val longClickable: Boolean
    val className: String?

    /** Full resource name, for example `com.google.android.apps.nexuslauncher:id/workspace`. */
    val viewId: String?
    val childCount: Int
    fun child(index: Int): HitNode?
}

/** What a tap at a point on the launcher would hit (LOCK-001, Precise home mode). */
enum class HomeTarget {
    /** Nothing interactive under the point: wallpaper, empty grid cells, gaps between icons. */
    EMPTY,

    /** An icon, folder, widget, search bar or any other clickable/long-clickable launcher view. */
    CONTROL,

    /** The tree could not be read: never treated as empty. */
    UNKNOWN,
}

/** Which launcher surface is showing (Precise home mode intercepts touches only on [WORKSPACE]). */
enum class HomeScene {
    /** Home pages with icons/widgets, nothing floating above them. */
    WORKSPACE,

    /** App drawer, recents, a folder, a long-press menu, the widget sheet, search, ... */
    OTHER,

    /** The launcher layout is not one Able knows (other launchers or a changed Pixel Launcher). */
    UNKNOWN,
}

/**
 * Pure rules for the Precise home mode, separated for unit tests.
 *
 * Launcher3-based launchers (Pixel Launcher included) put every surface into one `drag_layer`
 * view: the home pages (`workspace`), dock (`hotseat`), page indicator and scrims are always
 * there; the app drawer (`apps_view`), recents (`overview_panel`), folders, long-press menus
 * (`popup_container`) and the widget sheet are added as extra children while they are open.
 * Verified with `uiautomator dump` on the API 36 emulator with Pixel Launcher, 2026-11-22:
 * home → {scrim_view, workspace, accessibility_action_view, page_indicator_container,
 * overview_actions_view, hotseat}; drawer adds `apps_view` and drops `workspace`; recents shows
 * `overview_panel`; a long press on empty space shows `popup_container` only.
 */
object HomeHitTest {
    private const val MAX_DEPTH = 40

    /** `drag_layer` children that may be visible while the plain home pages are showing. */
    val workspaceChildren = setOf(
        "workspace",
        "hotseat",
        "scrim_view",
        "page_indicator",
        "page_indicator_container",
        "accessibility_action_view",
        "overview_actions_view",
        "search_container_workspace",
        "qsb_container",
        "drop_target_bar",
    )

    /** Containers that are long-clickable for the home menu but are the "empty" background. */
    private val backgroundContainers = setOf("workspace", "drag_layer", "launcher", "hotseat")

    /**
     * Classifies the launcher surface from the resource names of the visible children of
     * `drag_layer` ([dragLayerChildIds], null for a child without ID). Unknown children (for
     * example a folder or a new floating sheet) mean "not the plain home pages": when unsure,
     * do not intercept.
     */
    fun scene(dragLayerFound: Boolean, dragLayerChildIds: List<String?>): HomeScene {
        if (!dragLayerFound) return HomeScene.UNKNOWN
        val names = dragLayerChildIds.map { it?.substringAfter(":id/", missingDelimiterValue = "") }
        if ("workspace" !in names) return HomeScene.OTHER
        return if (names.all { it != null && it.isNotEmpty() && it in workspaceChildren }) HomeScene.WORKSPACE else HomeScene.OTHER
    }

    /**
     * What a tap at ([x], [y]) would hit. Every visible branch containing the point is checked
     * (overlapping views in a launcher are rare, and the conservative answer is "control"): the
     * point is [HomeTarget.EMPTY] only when no node on any such branch is clickable,
     * long-clickable or an app widget, apart from the background containers that carry the home
     * long-press menu. A null [root] gives [HomeTarget.UNKNOWN].
     */
    fun target(root: HitNode?, x: Int, y: Int): HomeTarget {
        root ?: return HomeTarget.UNKNOWN
        if (!root.bounds.contains(x, y)) return HomeTarget.UNKNOWN
        return if (hasControlAt(root, x, y, 0)) HomeTarget.CONTROL else HomeTarget.EMPTY
    }

    /** True when [node] itself counts as an interactive launcher element. */
    fun isControl(node: HitNode): Boolean {
        val name = node.viewId?.substringAfter(":id/", missingDelimiterValue = "")
        if (name != null && name in backgroundContainers) return false
        if (node.className?.contains("AppWidgetHostView") == true) return true
        return node.clickable || node.longClickable
    }

    private fun hasControlAt(node: HitNode, x: Int, y: Int, depth: Int): Boolean {
        if (depth > MAX_DEPTH) return true
        if (!node.visible || !node.bounds.contains(x, y)) return false
        if (isControl(node)) return true
        for (i in 0 until node.childCount) {
            val child = node.child(i) ?: continue
            if (hasControlAt(child, x, y, depth + 1)) return true
        }
        return false
    }

    /** Deepest clickable node at the point (the one to send `ACTION_CLICK` to), or null. */
    fun <N : HitNode> clickableAt(root: N?, x: Int, y: Int): N? {
        root ?: return null
        @Suppress("UNCHECKED_CAST")
        return findClickable(root, x, y, 0) as N?
    }

    private fun findClickable(node: HitNode, x: Int, y: Int, depth: Int): HitNode? {
        if (depth > MAX_DEPTH || !node.visible || !node.bounds.contains(x, y)) return null
        // Later children are drawn on top: prefer them.
        for (i in node.childCount - 1 downTo 0) {
            val child = node.child(i) ?: continue
            findClickable(child, x, y, depth + 1)?.let { return it }
        }
        return node.takeIf { it.clickable }
    }
}
