package com.dragolabs.able.lock

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Recognises SystemUI scenes (lock screen, notification shade, bouncer) from the view resource
 * IDs of SystemUI windows only. Resource IDs, not translated labels, so it also works when
 * SystemUI is in Italian. The ID lists follow TapLock's TapLockSystemUi (Apache-2.0,
 * github.com/modelorona/TapLock) and are known to change between Pixel/Android releases;
 * an unrecognised tree is always treated as "do not intercept touches".
 *
 * Nothing read here is stored or sent: only the resulting [Scene] is used.
 */
internal object SystemUiLockState {
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    private const val PREFIX = "$SYSTEM_UI_PACKAGE:id/"

    private val lockScreenIds = setOf(
        "${PREFIX}keyguard_header",
        "${PREFIX}keyguard_status_view",
        "${PREFIX}keyguard_clock_container",
        "${PREFIX}lockscreen_clock_view",
        "${PREFIX}lockscreen_clock_view_large",
    )

    private val shadeIds = setOf(
        "${PREFIX}quick_qs_panel",
        "${PREFIX}quick_settings_panel",
        "${PREFIX}qs_panel",
        "${PREFIX}shade_header_root",
    )

    private val credentialIds = setOf(
        "element:BouncerContent",
        "${PREFIX}keyguard_security_container",
        "${PREFIX}keyguard_bouncer",
        "${PREFIX}keyguard_pin_view",
        "${PREFIX}pinEntry",
        "${PREFIX}passwordEntry",
        "${PREFIX}lockPatternView",
    )

    enum class Scene {
        /** Keyguard content is visible and nothing is covering it: safe to intercept taps. */
        LOCK_SCREEN,

        /** Shade, quick settings or PIN/pattern/password entry visible: SystemUI gets touches. */
        PANEL_OR_BOUNCER,

        /** Nothing recognisable (tree unavailable, occluded keyguard, unknown layout). */
        UNKNOWN,

        /** SystemUI is not showing a shade or bouncer that we recognise (unlocked state). */
        NONE,
    }

    /** Pure classification, separated for unit tests. */
    fun classify(keyguardLocked: Boolean, visibleIds: Set<String>, windowHints: List<String>): Scene {
        // During transitions several scenes can be visible at once: give SystemUI the touches.
        if (visibleIds.any { it in shadeIds || it in credentialIds }) return Scene.PANEL_OR_BOUNCER
        val hasLockScreen = visibleIds.any { it in lockScreenIds }
        if (keyguardLocked) return if (hasLockScreen) Scene.LOCK_SCREEN else Scene.UNKNOWN
        val panelByName = windowHints.any { hint ->
            hint.contains("NotificationShade", ignoreCase = true) ||
                hint.contains("NotificationPanel", ignoreCase = true) ||
                hint.contains("QuickSettings", ignoreCase = true)
        }
        return if (panelByName) Scene.PANEL_OR_BOUNCER else Scene.NONE
    }

    fun read(windows: List<AccessibilityWindowInfo>, keyguardLocked: Boolean): Scene {
        val ids = HashSet<String>()
        val hints = ArrayList<String>()
        var sawSystemUi = false
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
            val root = window.root ?: continue
            if (root.packageName?.toString() != SYSTEM_UI_PACKAGE) continue
            sawSystemUi = true
            collectVisibleIds(root, ids, depth = 0)
            root.className?.toString()?.let(hints::add)
            window.title?.toString()?.let(hints::add)
        }
        if (keyguardLocked && !sawSystemUi) return Scene.UNKNOWN
        return classify(keyguardLocked, ids, hints)
    }

    private fun collectVisibleIds(node: AccessibilityNodeInfo, ids: MutableSet<String>, depth: Int) {
        if (depth > MAX_DEPTH || ids.size > MAX_IDS) return
        if (node.isVisibleToUser) node.viewIdResourceName?.let(ids::add)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectVisibleIds(child, ids, depth + 1)
        }
    }

    private const val MAX_DEPTH = 40
    private const val MAX_IDS = 2000
}
