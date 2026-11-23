package com.dragolabs.able.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Able mark (BRAND-001): rounded arch "A" whose crossbar is a switch turned on. Drawn with theme
 * colors so it follows dynamic color; geometry matches the launcher icon (108-unit grid).
 * Brand asset reserved to Drago Labs (ADR-011).
 */
@Composable
fun AbleLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    archColor: Color = MaterialTheme.colorScheme.primary,
    switchColor: Color = MaterialTheme.colorScheme.tertiary,
    knobColor: Color = MaterialTheme.colorScheme.onTertiary,
    contentDescription: String? = "Able",
) {
    Canvas(
        modifier
            .size(size)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        // The mark occupies x 32.5..75.5, y 31.5..82.5 of the 108 grid; scale that box to fit.
        val unit = this.size.minDimension / 52f
        fun p(x: Float, y: Float) = Offset((x - 28f) * unit, (y - 30f) * unit)
        val arch = Path().apply {
            moveTo(p(38f, 77f).x, p(38f, 77f).y)
            lineTo(p(38f, 53f).x, p(38f, 53f).y)
            arcTo(Rect(p(38f, 37f), Size(32f * unit, 32f * unit)), 180f, 180f, false)
            lineTo(p(70f, 77f).x, p(70f, 77f).y)
        }
        drawPath(arch, archColor, style = Stroke(width = 11f * unit, cap = StrokeCap.Round))
        drawRoundRect(
            color = switchColor,
            topLeft = p(41.5f, 58.5f),
            size = Size(25f * unit, 11f * unit),
            cornerRadius = CornerRadius(5.5f * unit),
        )
        drawCircle(knobColor, radius = 3.6f * unit, center = p(60.5f, 64f))
    }
}
