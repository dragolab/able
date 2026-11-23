package com.dragolabs.able.feature.tools.shortcuts

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dragolabs.able.core.data.shortcut.ShortcutTarget
import com.dragolabs.able.core.designsystem.component.ExpressiveIcon
import java.io.File

/** Launcher masks used to preview how different launchers may crop the icon. */
enum class PreviewMask { CIRCLE, SQUIRCLE, ROUNDED }

@Composable
internal fun PreviewMask.shape(): Shape = when (this) {
    PreviewMask.CIRCLE -> CircleShape
    PreviewMask.SQUIRCLE -> MaterialShapes.Square.toShape()
    PreviewMask.ROUNDED -> RoundedCornerShape(22)
}

/**
 * The rendered launcher bitmap, cropped like a launcher would: the adaptive icon is 108 dp and
 * the launcher shows the middle 72 dp through its mask.
 */
@Composable
internal fun ShortcutIconView(
    spec: IconSpec,
    iconDir: File,
    loader: TargetDrawableLoader,
    size: Dp,
    modifier: Modifier = Modifier,
    mask: PreviewMask = PreviewMask.CIRCLE,
) {
    val sizePx = with(LocalDensity.current) { (size * 1.5f).roundToPx() }
    val bitmap = rememberShortcutIconBitmap(spec, iconDir, loader, sizePx)
    LaunchedIcon(bitmap, size, mask.shape(), modifier)
}

@Composable
internal fun LaunchedIcon(bitmap: ImageBitmap?, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
        } else {
            Box(Modifier.size(size).clip(shape))
        }
    }
}

/** Small icon of a picker entry: app/activity icon, otherwise the target's symbol. */
@Composable
internal fun TargetIcon(target: ShortcutTarget, loader: TargetDrawableLoader, size: Dp = 40.dp) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val hasDrawable = target is ShortcutTarget.App || target is ShortcutTarget.Activity
    val bitmap by produceState<ImageBitmap?>(null, target, sizePx) {
        if (hasDrawable) value = runCatching { loader.load(target)?.toBitmap(sizePx, sizePx)?.asImageBitmap() }.getOrNull()
    }
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = Modifier.size(size))
    } else {
        ExpressiveIcon(
            icon = target.symbol(),
            size = size,
            iconSize = size * 0.55f,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
