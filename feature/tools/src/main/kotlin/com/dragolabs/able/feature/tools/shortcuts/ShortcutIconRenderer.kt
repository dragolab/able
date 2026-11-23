package com.dragolabs.able.feature.tools.shortcuts

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import com.dragolabs.able.core.data.shortcut.GlyphSize
import com.dragolabs.able.core.data.shortcut.IconShape
import com.dragolabs.able.core.data.shortcut.IconSource
import kotlin.math.min

/** Everything needed to draw one shortcut icon. */
data class IconRenderInput(
    val source: IconSource,
    /** App/activity icon (source TARGET). */
    val targetDrawable: Drawable? = null,
    /** White alpha mask of a Material symbol (source MATERIAL, or TARGET without a drawable). */
    val glyphMask: Bitmap? = null,
    val text: String? = null,
    /** Custom gallery image (source IMAGE). */
    val image: Bitmap? = null,
    /** Null keeps an app icon's own background. */
    val backgroundArgb: Int? = null,
    val shape: IconShape = IconShape.LAUNCHER,
    val glyphSize: GlyphSize = GlyphSize.MEDIUM,
)

/**
 * Draws a 108×108 dp adaptive-icon bitmap ([IconCompat.createWithAdaptiveBitmap]). The launcher
 * applies its own mask (circle, squircle...) to the outer 72 dp; shapes other than
 * [IconShape.LAUNCHER] are drawn inside that area on a transparent background, so the result is
 * the intersection of Able's shape and the launcher's mask.
 */
object ShortcutIconRenderer {

    fun render(input: IconRenderInput, sizePx: Int): Bitmap {
        val size = sizePx.toFloat()
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)

        // Original adaptive app icon, untouched: draw both layers full bleed.
        val drawable = input.targetDrawable
        if (input.source == IconSource.TARGET && drawable is AdaptiveIconDrawable && input.backgroundArgb == null && input.shape == IconShape.LAUNCHER) {
            drawable.background?.let { it.setBounds(0, 0, sizePx, sizePx); it.draw(canvas) }
            drawable.foreground?.let { it.setBounds(0, 0, sizePx, sizePx); it.draw(canvas) }
            return bitmap
        }

        val tile = tileRect(input.shape, size)
        val clip = shapePath(input.shape, tile)
        val background = input.backgroundArgb ?: if (drawable != null) Color.TRANSPARENT else DEFAULT_BACKGROUND
        canvas.save()
        canvas.clipPath(clip)
        if (Color.alpha(background) > 0) canvas.drawColor(background)

        val glyphColor = contentColorFor(background)
        val glyphSide = tile.width() * (input.glyphSize.fraction / SAFE_FRACTION_OF_TILE(input.shape))
            .coerceAtMost(if (input.source == IconSource.IMAGE) 1f else 0.9f)
        val glyphRect = RectF(
            tile.centerX() - glyphSide / 2f,
            tile.centerY() - glyphSide / 2f,
            tile.centerX() + glyphSide / 2f,
            tile.centerY() + glyphSide / 2f,
        )

        when (input.source) {
            IconSource.TARGET -> when {
                // App icon in Able's shape: its own layers fill the shape.
                drawable is AdaptiveIconDrawable && input.backgroundArgb == null -> drawAdaptiveLayers(canvas, drawable, tile, clipOval = false)
                drawable is AdaptiveIconDrawable -> drawAdaptiveLayers(canvas, drawable, glyphRect, clipOval = true)
                drawable != null -> drawBitmap(canvas, drawable.toBitmap(glyphSide.toInt().coerceAtLeast(1), glyphSide.toInt().coerceAtLeast(1)), glyphRect, null)
                input.glyphMask != null -> drawBitmap(canvas, input.glyphMask, glyphRect, glyphColor)
            }
            IconSource.MATERIAL -> input.glyphMask?.let { drawBitmap(canvas, it, glyphRect, glyphColor) }
            IconSource.TEXT -> drawText(canvas, input.text.orEmpty(), glyphRect, glyphColor)
            IconSource.IMAGE -> input.image?.let { drawCenterCrop(canvas, it, if (input.glyphSize == GlyphSize.LARGE) tile else glyphRect) }
        }
        canvas.restore()
        return bitmap
    }

    /** Readable foreground (black or white) for a background, by WCAG contrast. */
    fun contentColorFor(background: Int): Int {
        if (Color.alpha(background) < 128) return Color.WHITE
        val opaque = ColorUtils.setAlphaComponent(background, 255)
        return if (ColorUtils.calculateContrast(Color.WHITE, opaque) >= ColorUtils.calculateContrast(Color.BLACK, opaque)) Color.WHITE else Color.BLACK
    }

    private fun tileRect(shape: IconShape, size: Float): RectF = if (shape == IconShape.LAUNCHER) {
        RectF(0f, 0f, size, size)
    } else {
        val side = size * SHAPED_TILE_DP / ADAPTIVE_DP
        val inset = (size - side) / 2f
        RectF(inset, inset, inset + side, inset + side)
    }

    /** The launcher mask covers 72 of 108 dp, so in LAUNCHER mode the glyph is scaled against that. */
    @Suppress("FunctionName")
    private fun SAFE_FRACTION_OF_TILE(shape: IconShape): Float = if (shape == IconShape.LAUNCHER) ADAPTIVE_DP / VISIBLE_DP else 1f

    fun shapePath(shape: IconShape, r: RectF): Path = Path().apply {
        when (shape) {
            IconShape.LAUNCHER -> addRect(r, Path.Direction.CW)
            IconShape.CIRCLE -> addOval(r, Path.Direction.CW)
            IconShape.ROUNDED_SQUARE -> {
                val radius = r.width() * 0.22f
                addRoundRect(r, radius, radius, Path.Direction.CW)
            }
            IconShape.SQUIRCLE -> {
                // Superellipse approximation (n≈4) with cubic curves.
                val cx = r.centerX()
                val cy = r.centerY()
                val hw = r.width() / 2f
                val k = 0.92f
                moveTo(cx, r.top)
                cubicTo(cx + hw * k, r.top, r.right, cy - hw * k, r.right, cy)
                cubicTo(r.right, cy + hw * k, cx + hw * k, r.bottom, cx, r.bottom)
                cubicTo(cx - hw * k, r.bottom, r.left, cy + hw * k, r.left, cy)
                cubicTo(r.left, cy - hw * k, cx - hw * k, r.top, cx, r.top)
                close()
            }
        }
    }

    private fun drawAdaptiveLayers(canvas: Canvas, drawable: AdaptiveIconDrawable, area: RectF, clipOval: Boolean) {
        // Adaptive layers are 108 dp with the visible part in the middle 72 dp: scale so the
        // visible part of the app icon fills [area].
        val full = area.width() * ADAPTIVE_DP / VISIBLE_DP
        val left = (area.centerX() - full / 2f).toInt()
        val top = (area.centerY() - full / 2f).toInt()
        val right = (area.centerX() + full / 2f).toInt()
        val bottom = (area.centerY() + full / 2f).toInt()
        canvas.save()
        if (clipOval) canvas.clipPath(Path().apply { addOval(area, Path.Direction.CW) })
        drawable.background?.let { it.setBounds(left, top, right, bottom); it.draw(canvas) }
        drawable.foreground?.let { it.setBounds(left, top, right, bottom); it.draw(canvas) }
        canvas.restore()
    }

    private fun drawBitmap(canvas: Canvas, bitmap: Bitmap, dst: RectF, tint: Int?) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (tint != null) paint.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, null, dst, paint)
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, dst: RectF) {
        val scale = maxOf(dst.width() / bitmap.width, dst.height() / bitmap.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dst.centerX() - bitmap.width * scale / 2f, dst.centerY() - bitmap.height * scale / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        }
        canvas.drawRect(dst, paint)
    }

    private fun drawText(canvas: Canvas, text: String, dst: RectF, color: Int) {
        if (text.isBlank()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = dst.height()
        }
        // Shrink until the text fits the glyph box.
        val bounds = android.graphics.Rect()
        repeat(12) {
            paint.getTextBounds(text, 0, text.length, bounds)
            val fit = min(dst.width() / bounds.width().coerceAtLeast(1), dst.height() / bounds.height().coerceAtLeast(1))
            if (fit >= 1f) return@repeat
            paint.textSize *= fit.coerceAtLeast(0.5f)
        }
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, dst.centerX(), dst.centerY() - bounds.exactCenterY(), paint)
    }

    private const val ADAPTIVE_DP = 108f
    private const val VISIBLE_DP = 72f
    private const val SHAPED_TILE_DP = 70f
    private const val DEFAULT_BACKGROUND = 0xFF3F51B5.toInt()
}
