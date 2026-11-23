package com.dragolabs.able.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.data.shortcut.ShortcutIntentFactory
import com.dragolabs.able.core.data.shortcut.ShortcutLaunchFailure
import com.dragolabs.able.core.data.shortcut.ShortcutLaunchOutcome
import com.dragolabs.able.core.data.shortcut.ShortcutMeta
import com.dragolabs.able.core.data.shortcut.ShortcutTarget
import com.dragolabs.able.core.data.shortcut.decodeTarget
import com.dragolabs.able.core.database.entity.ShortcutDefinitionEntity
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.HomeCardType
import com.dragolabs.able.core.model.ShortcutTargetType

// ---------------------------------------------------------------------------------------------
// Model
// ---------------------------------------------------------------------------------------------

/** A shortcut definition shown on Home (created with Shortcut Maker, SHORT-001..003). */
data class HomeShortcut(
    val id: String,
    val label: String,
    /** Null when the stored type is not recognized. */
    val targetType: ShortcutTargetType?,
    /** Decoded with the shared codec; null when the stored data is unknown or invalid (cannot be opened). */
    val target: ShortcutTarget?,
    /** Text or emoji chosen as icon in Shortcut Maker, if any. */
    val iconText: String?,
    val iconBackgroundArgb: Long?,
    val favorite: Boolean,
    val pinnedToLauncher: Boolean,
)

/** Maximum shortcuts shown on the Home card; the rest stay in Shortcut Maker. */
internal const val HOME_SHORTCUT_LIMIT = 6

/**
 * Home lists shortcuts the user marked as favorite in Shortcut Maker (`fav=1` in the encoded
 * icon metadata) first, then those pinned to the launcher, then the user's own order (`sortOrder`).
 */
internal fun favoriteShortcuts(entities: List<ShortcutDefinitionEntity>): List<HomeShortcut> =
    entities
        .map { it to ShortcutMeta.decode(it.iconValue, it.iconType) }
        .sortedWith(
            compareByDescending<Pair<ShortcutDefinitionEntity, ShortcutMeta>> { it.second.favorite }
                .thenByDescending { it.first.isPinnedToLauncher }
                .thenBy { it.first.sortOrder },
        )
        .take(HOME_SHORTCUT_LIMIT)
        .map { (entity, meta) -> entity.toHomeShortcut(meta) }

private fun ShortcutDefinitionEntity.toHomeShortcut(meta: ShortcutMeta) = HomeShortcut(
    id = id,
    label = label,
    targetType = ShortcutTargetType.entries.firstOrNull { it.name == targetType },
    target = decodeTarget(targetType, targetData, targetPackage),
    iconText = meta.iconText?.let(::clampGlyph),
    iconBackgroundArgb = iconBackgroundColorHex?.let(::parseHexColor),
    favorite = meta.favorite,
    pinnedToLauncher = isPinnedToLauncher,
)

/** Keeps at most [max] code points so a surrogate pair is never split. */
private fun clampGlyph(text: String, max: Int = 4): String {
    val trimmed = text.trim()
    return if (trimmed.codePointCount(0, trimmed.length) <= max) trimmed else trimmed.substring(0, trimmed.offsetByCodePoints(0, max))
}

private fun parseHexColor(hex: String): Long? {
    val digits = hex.removePrefix("#")
    val value = digits.toLongOrNull(16) ?: return null
    return when (digits.length) {
        6 -> 0xFF000000L or value
        8 -> value
        else -> null
    }
}

// ---------------------------------------------------------------------------------------------
// Launch (shared ShortcutIntentFactory in :core:data; never trusts stored data blindly)
// ---------------------------------------------------------------------------------------------

sealed interface ShortcutLaunchResult {
    data object Launched : ShortcutLaunchResult

    /** Able action: Home is already inside `MainActivity`, so the route is navigated in-app. */
    data class OpenRoute(val route: String) : ShortcutLaunchResult
    data class Failed(val reason: HomeMessage) : ShortcutLaunchResult
}

/** Opens a shortcut through the shared factory. Failures are reported, never hidden. */
internal fun ShortcutIntentFactory.launchFromHome(shortcut: HomeShortcut): ShortcutLaunchResult {
    val target = shortcut.target ?: return ShortcutLaunchResult.Failed(HomeMessage.SHORTCUT_UNSUPPORTED)
    if (target is ShortcutTarget.AbleAction) return ShortcutLaunchResult.OpenRoute(target.route)
    return when (val outcome = launch(target)) {
        ShortcutLaunchOutcome.Launched -> ShortcutLaunchResult.Launched
        is ShortcutLaunchOutcome.Failed -> ShortcutLaunchResult.Failed(outcome.reason.toHomeMessage())
    }
}

internal fun ShortcutLaunchFailure.toHomeMessage(): HomeMessage = when (this) {
    ShortcutLaunchFailure.INVALID, ShortcutLaunchFailure.UNSUPPORTED -> HomeMessage.SHORTCUT_UNSUPPORTED
    ShortcutLaunchFailure.APP_MISSING -> HomeMessage.SHORTCUT_APP_MISSING
    ShortcutLaunchFailure.NO_HANDLER -> HomeMessage.SHORTCUT_FAILED
    ShortcutLaunchFailure.BLOCKED -> HomeMessage.SHORTCUT_BLOCKED
}

// ---------------------------------------------------------------------------------------------
// FAVORITE_SHORTCUTS card
// ---------------------------------------------------------------------------------------------

@Composable
internal fun FavoriteShortcutsCard(
    shortcuts: List<HomeShortcut>,
    onLaunch: (HomeShortcut) -> Unit,
    onOpenShortcutMaker: () -> Unit,
) {
    AbleCard(
        title = stringResource(R.string.home_card_shortcuts_title),
        subtitle = if (shortcuts.isEmpty()) null else stringResource(R.string.home_shortcuts_subtitle),
        icon = HomeCardType.FAVORITE_SHORTCUTS.icon(),
        iconShape = MaterialShapes.Flower,
        style = AbleCardStyle.Outlined,
    ) {
        if (shortcuts.isEmpty()) {
            Text(
                stringResource(R.string.home_shortcuts_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onOpenShortcutMaker, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.home_shortcuts_create), modifier = Modifier.padding(start = AbleSpacing.sm))
            }
        } else {
            ShortcutGrid(shortcuts, onLaunch)
            FilledTonalButton(onClick = onOpenShortcutMaker, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.home_shortcuts_open_maker))
            }
        }
    }
}

@Composable
private fun ShortcutGrid(shortcuts: List<HomeShortcut>, onLaunch: (HomeShortcut) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 420.dp) 4 else 3
        Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            shortcuts.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                    row.forEach { shortcut ->
                        ShortcutTile(shortcut, onClick = { onLaunch(shortcut) }, modifier = Modifier.weight(1f))
                    }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(shortcut: HomeShortcut, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberAbleHaptics()
    val shape = MaterialTheme.shapes.large
    Surface(
        onClick = { haptics.click(); onClick() },
        modifier = modifier
            .heightIn(min = 96.dp)
            .focusRing(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(AbleSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs),
        ) {
            ShortcutBadge(shortcut)
            Text(
                shortcut.label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShortcutBadge(shortcut: HomeShortcut) {
    val container = shortcut.iconBackgroundArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primaryContainer
    val content = if (shortcut.iconBackgroundArgb != null) {
        if (container.luminanceApprox() > 0.5f) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(shape = CircleShape, color = container, contentColor = content, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            val text = shortcut.iconText
            if (text != null) {
                Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            } else {
                Icon(shortcut.icon(), contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun HomeShortcut.icon(): ImageVector = when (val t = target) {
    is ShortcutTarget.App -> Icons.Rounded.Apps
    is ShortcutTarget.Activity -> Icons.AutoMirrored.Rounded.OpenInNew
    is ShortcutTarget.SettingsPage -> Icons.Rounded.Settings
    is ShortcutTarget.Document -> if (t.isFolder) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.InsertDriveFile
    is ShortcutTarget.Website -> Icons.Rounded.Language
    is ShortcutTarget.AbleAction -> Icons.Rounded.AutoAwesome
    null -> Icons.AutoMirrored.Rounded.OpenInNew
}
