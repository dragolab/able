package com.dragolabs.able.feature.tools

import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.capability.DeviceCapabilities
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleSharedKeys
import com.dragolabs.able.core.designsystem.component.AbleTopLevelScaffold
import com.dragolabs.able.core.designsystem.component.ActionTile
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.AbleSharedBounds
import com.dragolabs.able.core.designsystem.component.scrollToTopOnReselect
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.ToolId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------------------------

enum class ToolSection(@StringRes val titleRes: Int) {
    DEVICE(R.string.tools_section_device),
    FILES_AND_APPS(R.string.tools_section_files_apps),
    MEASURE(R.string.tools_section_measure),
    ADVANCED(R.string.tools_section_advanced),
}

/** One hub entry. [unavailableReason] is non-null when the device cannot run the tool. */
@Immutable
data class ToolEntry(
    val id: ToolId,
    val section: ToolSection,
    @StringRes val unavailableReason: Int? = null,
    @StringRes val hintRes: Int? = null,
) {
    val available: Boolean get() = unavailableReason == null
}

@Immutable
data class ToolsUiState(val entries: List<ToolEntry> = emptyList())

/** Pure mapping from device capabilities to hub entries, kept separate for unit testing. */
internal fun buildToolEntries(
    device: DeviceCapabilities,
    hasLocation: Boolean,
    shizuku: ShizukuConnectionState,
): List<ToolEntry> {
    val privilegedHint = if (shizuku == ShizukuConnectionState.READY) R.string.tools_hint_direct else R.string.tools_hint_settings
    return listOf(
        ToolEntry(ToolId.TORCH, ToolSection.DEVICE, if (device.hasFlashTorch) null else R.string.tools_reason_no_flash, R.string.tools_hint_torch),
        ToolEntry(ToolId.HOTSPOT, ToolSection.DEVICE, hintRes = R.string.tools_hint_hotspot),
        ToolEntry(ToolId.SCREEN_LOCK, ToolSection.DEVICE, hintRes = R.string.tools_hint_lock),
        ToolEntry(ToolId.SYSTEM_TOGGLES, ToolSection.DEVICE, hintRes = privilegedHint),
        ToolEntry(ToolId.BUTTON_MAPPER, ToolSection.DEVICE, hintRes = R.string.tools_hint_buttons),
        ToolEntry(ToolId.APP_LOCK, ToolSection.ADVANCED, hintRes = R.string.tools_hint_applock),
        ToolEntry(ToolId.SHELL, ToolSection.ADVANCED, hintRes = R.string.tools_hint_shell),
        ToolEntry(ToolId.STORAGE, ToolSection.FILES_AND_APPS, hintRes = R.string.tools_hint_storage),
        ToolEntry(ToolId.MEMORY, ToolSection.FILES_AND_APPS, hintRes = R.string.tools_hint_memory),
        ToolEntry(ToolId.SHORTCUT_MAKER, ToolSection.FILES_AND_APPS, hintRes = R.string.tools_hint_shortcuts),
        ToolEntry(ToolId.ACTIVITY_LAUNCHER, ToolSection.FILES_AND_APPS, hintRes = R.string.tools_hint_activities),
        ToolEntry(ToolId.GPS, ToolSection.MEASURE, if (hasLocation) null else R.string.tools_reason_no_location, R.string.tools_hint_gps),
        ToolEntry(ToolId.LEVEL, ToolSection.MEASURE, if (device.hasAccelerometer) null else R.string.tools_reason_no_accelerometer, R.string.tools_hint_level),
        ToolEntry(ToolId.RULER, ToolSection.MEASURE, if (device.hasTouchscreen) null else R.string.tools_reason_no_touchscreen, R.string.tools_hint_ruler),
        ToolEntry(
            ToolId.COMPASS,
            ToolSection.MEASURE,
            if (device.hasMagnetometer && device.hasAccelerometer) null else R.string.tools_reason_no_magnetometer,
            R.string.tools_hint_compass,
        ),
    )
}

class ToolsViewModel(
    device: DeviceCapabilities,
    hasLocation: Boolean,
    shizukuState: StateFlow<ShizukuConnectionState>,
) : ViewModel() {
    val uiState: StateFlow<ToolsUiState> = shizukuState
        .map { ToolsUiState(buildToolEntries(device, hasLocation, it)) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ToolsUiState(buildToolEntries(device, hasLocation, shizukuState.value)),
        )
}

// ---------------------------------------------------------------------------------------------
// Route + screen
// ---------------------------------------------------------------------------------------------

@Composable
fun ToolsRoute(
    onOpenSettings: () -> Unit,
    onOpenTool: (ToolId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ToolsViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            ToolsViewModel(
                device = container.capabilityStatus.device,
                hasLocation = container.appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION),
                shizukuState = container.shizuku.connectionState,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ToolsScreen(state = state, onOpenSettings = onOpenSettings, onOpenTool = onOpenTool, modifier = modifier)
}

@Composable
fun ToolsScreen(
    state: ToolsUiState,
    onOpenSettings: () -> Unit,
    onOpenTool: (ToolId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Saveable: the hub keeps its scroll position under a tool, so the tile it was opened from
    // is composed at the same place when the tool shrinks back into it.
    val gridState = rememberLazyGridState()
    AbleTopLevelScaffold(
        title = stringResource(R.string.tools_title),
        subtitle = stringResource(R.string.tools_subtitle),
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 164.dp),
            state = gridState,
            // Re-selecting Tools on the hub scrolls back to the top (SHELL-001 re-select).
            modifier = Modifier
                .fillMaxSize()
                .scrollToTopOnReselect("tools") { gridState.animateScrollToItem(0) },
            contentPadding = screenContentPadding(padding),
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
        ) {
            ToolSection.entries.forEach { section ->
                val entries = state.entries.filter { it.section == section }
                if (entries.isEmpty()) return@forEach
                item(key = "header-${section.name}", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(stringResource(section.titleRes))
                }
                items(entries, key = { it.id.key }) { entry ->
                    val presentation = entry.id.presentation()
                    val reason = entry.unavailableReason?.let { stringResource(it) }
                    // App-open transform into the tool screen (same key as ToolDetailRoute).
                    AbleSharedBounds(
                        key = if (entry.available) AbleSharedKeys.tool(AbleSharedKeys.ORIGIN_TOOLS, entry.id.key) else null,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, // ActionTile color
                    ) {
                        ActionTile(
                            icon = presentation.icon,
                            label = stringResource(presentation.labelRes),
                            shape = presentation.shape,
                            enabled = entry.available,
                            supportingText = reason ?: entry.hintRes?.let { stringResource(it) },
                            onClick = {
                                if (entry.available) {
                                    onOpenTool(entry.id)
                                } else if (reason != null) {
                                    // Unavailable tools stay visible but explain why instead of opening.
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.tools_unavailable_snackbar, context.getString(presentation.labelRes), reason),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Icon, label and expressive shape for each tool. Shared with the detail dispatcher. */
@Immutable
internal data class ToolPresentation(val icon: ImageVector, @StringRes val labelRes: Int, val shape: RoundedPolygon)

internal fun ToolId.presentation(): ToolPresentation = when (this) {
    ToolId.TORCH -> ToolPresentation(Icons.Rounded.FlashlightOn, R.string.tools_torch_title, MaterialShapes.Sunny)
    ToolId.HOTSPOT -> ToolPresentation(Icons.Rounded.WifiTethering, R.string.tools_hotspot_title, MaterialShapes.Cookie9Sided)
    ToolId.SCREEN_LOCK -> ToolPresentation(Icons.Rounded.Lock, R.string.tools_lock_title, MaterialShapes.Clover4Leaf)
    ToolId.MEMORY -> ToolPresentation(Icons.Rounded.Memory, R.string.tools_memory_title, MaterialShapes.Cookie6Sided)
    ToolId.SHELL -> ToolPresentation(Icons.Rounded.Terminal, R.string.tools_shell_title, MaterialShapes.Square)
    ToolId.APP_LOCK -> ToolPresentation(Icons.Rounded.Fingerprint, R.string.tools_applock_title, MaterialShapes.Clover8Leaf)
    ToolId.BUTTON_MAPPER -> ToolPresentation(Icons.Rounded.SmartButton, R.string.tools_buttons_title, MaterialShapes.Pill)
    ToolId.SYSTEM_TOGGLES -> ToolPresentation(Icons.Rounded.Tune, R.string.tools_toggles_title, MaterialShapes.Cookie7Sided)
    ToolId.STORAGE -> ToolPresentation(Icons.Rounded.FolderOpen, R.string.tools_storage_title, MaterialShapes.Square)
    ToolId.ACTIVITY_LAUNCHER -> ToolPresentation(Icons.Rounded.Apps, R.string.tools_activities_title, MaterialShapes.Pill)
    ToolId.SHORTCUT_MAKER -> ToolPresentation(Icons.Rounded.AddToHomeScreen, R.string.tools_shortcuts_title, MaterialShapes.Ghostish)
    ToolId.GPS -> ToolPresentation(Icons.Rounded.MyLocation, R.string.tools_gps_title, MaterialShapes.Circle)
    ToolId.LEVEL -> ToolPresentation(Icons.Rounded.Balance, R.string.tools_level_title, MaterialShapes.Pentagon)
    ToolId.RULER -> ToolPresentation(Icons.Rounded.Straighten, R.string.tools_ruler_title, MaterialShapes.Slanted)
    ToolId.COMPASS -> ToolPresentation(Icons.Rounded.Explore, R.string.tools_compass_title, MaterialShapes.Cookie12Sided)
}
