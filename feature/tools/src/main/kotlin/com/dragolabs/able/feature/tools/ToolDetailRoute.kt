package com.dragolabs.able.feature.tools

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dragolabs.able.core.designsystem.component.AbleSharedBounds
import com.dragolabs.able.core.model.ToolId
import com.dragolabs.able.feature.tools.activities.ActivityLauncherRoute
import com.dragolabs.able.feature.tools.gps.GpsToolScreen
import com.dragolabs.able.feature.tools.hotspot.HotspotRoute
import com.dragolabs.able.feature.tools.lock.ScreenLockRoute
import com.dragolabs.able.feature.tools.measure.CompassScreen
import com.dragolabs.able.feature.tools.measure.ScreenRulerScreen
import com.dragolabs.able.feature.tools.measure.SpiritLevelScreen
import com.dragolabs.able.feature.tools.shortcuts.ShortcutMakerRoute
import com.dragolabs.able.feature.tools.storage.StorageRoute
import com.dragolabs.able.feature.tools.toggles.SystemTogglesRoute
import com.dragolabs.able.feature.tools.torch.TorchRoute

/**
 * Entry point for every tool destination (`tool/<ToolId.key>`).
 *
 * [sharedKey] is the key of the hub tile/Home card that opened the tool: the whole screen is the
 * container end of the app-open transform, so it grows from that card and shrinks back into it
 * on back (including the predictive back gesture). Null (deep links, search) disables it.
 */
@Composable
fun ToolDetailRoute(
    toolId: ToolId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    sharedKey: String? = null,
) {
    // The wrapper owns the shared bounds so every tool screen participates without changes.
    // The tool is laid out once at full size and revealed by the growing bounds (no scaling).
    AbleSharedBounds(
        key = sharedKey,
        modifier = modifier.fillMaxSize(),
        isContainer = true,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ToolDetailContent(toolId = toolId, onBack = onBack, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ToolDetailContent(
    toolId: ToolId,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    when (toolId) {
        ToolId.TORCH -> TorchRoute(onBack = onBack, modifier = modifier)
        ToolId.HOTSPOT -> HotspotRoute(onBack = onBack, modifier = modifier)
        ToolId.STORAGE -> StorageRoute(onBack = onBack, modifier = modifier)
        ToolId.SCREEN_LOCK -> ScreenLockRoute(onBack = onBack, modifier = modifier)
        ToolId.ACTIVITY_LAUNCHER -> ActivityLauncherRoute(onBack = onBack, modifier = modifier)
        ToolId.SHORTCUT_MAKER -> ShortcutMakerRoute(onBack = onBack, modifier = modifier)
        ToolId.FAKE_GPS -> com.dragolabs.able.feature.tools.fakegps.FakeGpsRoute(onBack = onBack, modifier = modifier)
        ToolId.MEMORY -> com.dragolabs.able.feature.tools.memory.MemoryRoute(onBack = onBack, modifier = modifier)
        ToolId.SHELL -> com.dragolabs.able.feature.tools.shell.ShellRoute(onBack = onBack, modifier = modifier)
        ToolId.APP_LOCK -> com.dragolabs.able.feature.tools.applock.AppLockRoute(onBack = onBack, modifier = modifier)
        ToolId.BUTTON_MAPPER -> com.dragolabs.able.feature.tools.buttons.ButtonMapperRoute(onBack = onBack, modifier = modifier)
        ToolId.SYSTEM_TOGGLES -> SystemTogglesRoute(onBack = onBack, modifier = modifier)
        ToolId.GPS -> GpsToolScreen(onBack = onBack, modifier = modifier)
        ToolId.LEVEL -> SpiritLevelScreen(onBack = onBack, modifier = modifier)
        ToolId.RULER -> ScreenRulerScreen(onBack = onBack, modifier = modifier)
        ToolId.COMPASS -> CompassScreen(onBack = onBack, modifier = modifier)
    }
}
