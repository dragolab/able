package com.dragolabs.able.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dragolabs.able.core.designsystem.theme.AbleBrandColors
import com.dragolabs.able.core.designsystem.theme.AbleWordmarkFontFamily

/*
 * Pulse mark geometry (BRAND-001), in the 108-unit launcher grid of the concept artwork:
 * ring centre (50, 56), radius 17, stroke 10; stem x 65..75, y 34..74, corner 5; the level
 * segment is the lower part of the stem (default from y 56, i.e. 45 % of the stem).
 * Mark bounds: x 28..75, y 34..78 (47 x 44 units).
 */
private const val MARK_LEFT = 28f
private const val MARK_TOP = 34f
private const val MARK_WIDTH = 47f
private const val MARK_HEIGHT = 44f
private const val STEM_TOP = 34f
private const val STEM_BOTTOM = 74f

/** Default level of the mark's mint segment (the static brand mark). */
const val AbleLogoDefaultLevel = 0.45f

/** Brand mint for the current theme: the bright variant on dark backgrounds. */
@Composable
fun ableMint(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) AbleBrandColors.MintBright else AbleBrandColors.Mint

/**
 * Able mark (BRAND-001, Pulse): a lowercase "a" built from a ring and a vertical stem whose
 * lower segment is a mint level indicator. Theme-aware: ring and stem follow [markColor]
 * (onSurface by default), the level segment uses the brand mint. [level] (0..1) can show a
 * real value (battery, progress); changes animate with a calm spring unless [animate] is false.
 * Brand asset reserved to Drago Labs (ADR-011).
 */
@Composable
fun AbleLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    markColor: Color = MaterialTheme.colorScheme.onSurface,
    levelColor: Color = ableMint(),
    level: Float = AbleLogoDefaultLevel,
    animate: Boolean = true,
    contentDescription: String? = "Able",
) {
    val target = level.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "ableLogoLevel",
    )
    val shown = if (animate) animated else target
    Canvas(
        modifier
            .size(size)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        val unit = minOf(this.size.width / MARK_WIDTH, this.size.height / MARK_HEIGHT)
        val originX = (this.size.width - MARK_WIDTH * unit) / 2f
        val originY = (this.size.height - MARK_HEIGHT * unit) / 2f
        fun p(x: Float, y: Float) = Offset(originX + (x - MARK_LEFT) * unit, originY + (y - MARK_TOP) * unit)

        drawCircle(markColor, radius = 17f * unit, center = p(50f, 56f), style = Stroke(width = 10f * unit))
        val corner = CornerRadius(5f * unit)
        drawRoundRect(markColor, topLeft = p(65f, STEM_TOP), size = Size(10f * unit, (STEM_BOTTOM - STEM_TOP) * unit), cornerRadius = corner)
        if (shown > 0.001f) {
            // The segment never gets shorter than its width, so it keeps its rounded-pill shape.
            val height = maxOf(10f, (STEM_BOTTOM - STEM_TOP) * shown)
            drawRoundRect(levelColor, topLeft = p(65f, STEM_BOTTOM - height), size = Size(10f * unit, height * unit), cornerRadius = corner)
        }
    }
}

/**
 * Pulse lockup: the mark followed by the lowercase "able" wordmark in Google Sans Flex
 * wght 650, ROND 0. Exposed to accessibility as a single "Able" label.
 */
@Composable
fun AbleWordmark(
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = "Able",
) {
    Row(
        modifier = modifier.clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(height * 0.3f),
    ) {
        AbleLogo(size = height, markColor = color, contentDescription = null)
        Text(
            text = "able",
            color = color,
            style = TextStyle(
                fontFamily = AbleWordmarkFontFamily,
                fontSize = (height.value * 1.05f).sp,
                letterSpacing = (-0.02f).em,
            ),
        )
    }
}
