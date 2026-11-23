package com.dragolabs.able.core.model

import kotlinx.serialization.Serializable

/**
 * Stable identifiers for Tools destinations. Used by navigation, Home quick actions, tiles,
 * widgets and deep links ("route" intent extra = "tool/<key>").
 */
@Serializable
enum class ToolId(val key: String) {
    TORCH("torch"),
    HOTSPOT("hotspot"),
    STORAGE("storage"),
    MEMORY("memory"),
    SCREEN_LOCK("lock"),
    ACTIVITY_LAUNCHER("activities"),
    SHORTCUT_MAKER("shortcuts"),
    SYSTEM_TOGGLES("toggles"),
    SHELL("shell"),
    APP_LOCK("applock"),
    BUTTON_MAPPER("buttons"),
    GPS("gps"),
    LEVEL("level"),
    RULER("ruler"),
    COMPASS("compass");

    companion object {
        fun fromKey(key: String?): ToolId? = entries.firstOrNull { it.key == key }
    }
}
