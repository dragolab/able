package com.dragolabs.able.core.data.shortcut

import com.dragolabs.able.core.model.ShortcutIconType
import com.dragolabs.able.core.model.ShortcutItem
import java.net.URLDecoder
import java.net.URLEncoder

enum class IconSource { TARGET, MATERIAL, TEXT, IMAGE }

/** LAUNCHER fills the whole adaptive icon so the launcher's own mask decides the shape. */
enum class IconShape { LAUNCHER, CIRCLE, SQUIRCLE, ROUNDED_SQUARE }

enum class GlyphSize(val fraction: Float) { SMALL(0.36f), MEDIUM(0.46f), LARGE(0.58f) }

/**
 * Shortcut Maker options that have no column of their own, encoded in `iconValue`
 * (`v=1;src=...;val=<URL-encoded>;shape=...;size=...;label=0|1;fav=0|1;pin=<epochMs>`).
 * Raw legacy values (no `v=1` prefix) are read as a plain icon value.
 */
data class ShortcutMeta(
    val source: IconSource = IconSource.TARGET,
    /** Material icon key, text/emoji, or the custom image file name, depending on [source]. */
    val value: String? = null,
    val shape: IconShape = IconShape.LAUNCHER,
    val glyphSize: GlyphSize = GlyphSize.MEDIUM,
    val showLabel: Boolean = true,
    val favorite: Boolean = false,
    val lastPinRequestAtMs: Long? = null,
) {
    val iconType: ShortcutIconType
        get() = when (source) {
            IconSource.TARGET, IconSource.MATERIAL -> ShortcutIconType.DEFAULT
            IconSource.TEXT -> ShortcutIconType.TEXT_OR_EMOJI
            IconSource.IMAGE -> ShortcutIconType.CUSTOM_GALLERY
        }

    /** Text/emoji glyph when the icon source is [IconSource.TEXT] and a value is present. */
    val iconText: String? get() = value?.takeIf { source == IconSource.TEXT && it.isNotBlank() }

    fun encode(): String = buildList {
        add("v=1")
        add("src=${source.name}")
        value?.let { add("val=${URLEncoder.encode(it, Charsets.UTF_8)}") }
        add("shape=${shape.name}")
        add("size=${glyphSize.name}")
        add("label=${if (showLabel) 1 else 0}")
        add("fav=${if (favorite) 1 else 0}")
        lastPinRequestAtMs?.let { add("pin=$it") }
    }.joinToString(";")

    companion object {
        /** Tolerant decoder; values written by other code are treated as a plain icon value. */
        fun decode(raw: String?, iconType: ShortcutIconType): ShortcutMeta {
            val fallbackSource = when (iconType) {
                ShortcutIconType.DEFAULT -> IconSource.TARGET
                ShortcutIconType.TEXT_OR_EMOJI -> IconSource.TEXT
                ShortcutIconType.CUSTOM_GALLERY -> IconSource.IMAGE
            }
            if (raw == null || !raw.startsWith("v=1")) return ShortcutMeta(source = fallbackSource, value = raw)
            val map = raw.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
            }.toMap()
            return ShortcutMeta(
                source = map["src"]?.let { runCatching { IconSource.valueOf(it) }.getOrNull() } ?: fallbackSource,
                value = map["val"]?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8) }.getOrNull() },
                shape = map["shape"]?.let { runCatching { IconShape.valueOf(it) }.getOrNull() } ?: IconShape.LAUNCHER,
                glyphSize = map["size"]?.let { runCatching { GlyphSize.valueOf(it) }.getOrNull() } ?: GlyphSize.MEDIUM,
                showLabel = map["label"] != "0",
                favorite = map["fav"] == "1",
                lastPinRequestAtMs = map["pin"]?.toLongOrNull(),
            )
        }

        /** Same as [decode] for a stored icon type name; unknown names fall back to DEFAULT. */
        fun decode(raw: String?, iconTypeName: String?): ShortcutMeta =
            decode(raw, ShortcutIconType.entries.firstOrNull { it.name == iconTypeName } ?: ShortcutIconType.DEFAULT)
    }
}

val ShortcutItem.meta: ShortcutMeta get() = ShortcutMeta.decode(iconConfig.iconValue, iconConfig.iconType)
