package com.dragolabs.able.feature.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.database.UserPreferencesRepository
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleLogo
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TourUiState(val finishing: Boolean = false, val finished: Boolean = false)

/** Guided tour (UX-001). Finishing or skipping both persist `tourCompleted = true`. */
class TourViewModel(private val preferences: UserPreferencesRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(TourUiState())
    val uiState: StateFlow<TourUiState> = _uiState.asStateFlow()

    fun finish() {
        if (_uiState.value.finishing || _uiState.value.finished) return
        _uiState.update { it.copy(finishing = true) }
        viewModelScope.launch {
            preferences.setTourCompleted(true)
            _uiState.update { TourUiState(finishing = false, finished = true) }
        }
    }
}

private data class TourHighlight(val icon: ImageVector, val titleRes: Int, val bodyRes: Int)

private data class TourPage(
    val icon: ImageVector?,
    val shape: RoundedPolygon,
    val titleRes: Int,
    val bodyRes: Int,
    val highlights: List<TourHighlight> = emptyList(),
)

private val tourPages = listOf(
    TourPage(null, MaterialShapes.Cookie12Sided, R.string.tour_welcome_title, R.string.tour_welcome_body),
    TourPage(
        Icons.Rounded.Home, MaterialShapes.Cookie9Sided, R.string.tour_home_title, R.string.tour_home_body,
        listOf(
            TourHighlight(Icons.Rounded.Dashboard, R.string.tour_home_cards_title, R.string.tour_home_cards_body),
            TourHighlight(Icons.Rounded.Tune, R.string.tour_home_customize_title, R.string.tour_home_customize_body),
            TourHighlight(Icons.Rounded.Warning, R.string.tour_home_banners_title, R.string.tour_home_banners_body),
        ),
    ),
    TourPage(
        Icons.Rounded.BatteryChargingFull, MaterialShapes.Clover4Leaf, R.string.tour_battery_title, R.string.tour_battery_body,
        listOf(
            TourHighlight(Icons.Rounded.Tune, R.string.tour_battery_profiles_title, R.string.tour_battery_profiles_body),
            TourHighlight(Icons.Rounded.Schedule, R.string.tour_battery_triggers_title, R.string.tour_battery_triggers_body),
            TourHighlight(Icons.Rounded.MonitorHeart, R.string.tour_battery_monitor_title, R.string.tour_battery_monitor_body),
        ),
    ),
    TourPage(
        Icons.Rounded.Apps, MaterialShapes.Sunny, R.string.tour_apps_title, R.string.tour_apps_body,
        listOf(
            TourHighlight(Icons.Rounded.FilterList, R.string.tour_apps_info_title, R.string.tour_apps_info_body),
            TourHighlight(Icons.Rounded.Archive, R.string.tour_apps_manage_title, R.string.tour_apps_manage_body),
            TourHighlight(Icons.Rounded.AdminPanelSettings, R.string.tour_apps_shizuku_title, R.string.tour_apps_shizuku_body),
        ),
    ),
    TourPage(
        Icons.Rounded.Handyman, MaterialShapes.SoftBurst, R.string.tour_tools_title, R.string.tour_tools_body,
        listOf(
            TourHighlight(Icons.Rounded.FlashlightOn, R.string.tour_tools_everyday_title, R.string.tour_tools_everyday_body),
            TourHighlight(Icons.AutoMirrored.Rounded.Shortcut, R.string.tour_tools_shortcuts_title, R.string.tour_tools_shortcuts_body),
            TourHighlight(Icons.Rounded.Lock, R.string.tour_tools_lock_title, R.string.tour_tools_lock_body),
            TourHighlight(Icons.Rounded.ToggleOn, R.string.tour_tools_toggles_title, R.string.tour_tools_toggles_body),
        ),
    ),
    TourPage(
        Icons.Rounded.Widgets, MaterialShapes.Cookie6Sided, R.string.tour_tiles_title, R.string.tour_tiles_body,
        listOf(
            TourHighlight(Icons.Rounded.SpaceDashboard, R.string.tour_tiles_qs_title, R.string.tour_tiles_qs_body),
            TourHighlight(Icons.Rounded.Widgets, R.string.tour_tiles_widgets_title, R.string.tour_tiles_widgets_body),
        ),
    ),
    TourPage(
        Icons.Rounded.Shield, MaterialShapes.Gem, R.string.tour_settings_title, R.string.tour_settings_body,
        listOf(
            TourHighlight(Icons.Rounded.Security, R.string.tour_settings_access_title, R.string.tour_settings_access_body),
            TourHighlight(Icons.Rounded.BugReport, R.string.tour_settings_crash_title, R.string.tour_settings_crash_body),
            TourHighlight(Icons.Rounded.SettingsBackupRestore, R.string.tour_settings_backup_title, R.string.tour_settings_backup_body),
        ),
    ),
)

/**
 * Guided tour entry point (UX-001). Shown after onboarding and from Settings → Help. Calls
 * [onFinished] once `tourCompleted` has been persisted (after Done or Skip).
 */
@Composable
fun TourRoute(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TourViewModel = viewModel(factory = ableViewModelFactory { TourViewModel(it.preferences) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }
    TourScreen(finishing = state.finishing, onFinish = viewModel::finish, modifier = modifier)
}

@Composable
fun TourScreen(finishing: Boolean, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState { tourPages.size }
    val scope = rememberCoroutineScope()
    val haptics = rememberAbleHaptics()
    val isLast = pagerState.currentPage == tourPages.lastIndex
    val progress by animateFloatAsState(
        targetValue = (pagerState.currentPage + 1f) / tourPages.size,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "tourProgress",
    )
    val stepDescription = stringResource(R.string.tour_step_description, pagerState.currentPage + 1, tourPages.size)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AbleSpacing.lg, vertical = AbleSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).semantics { contentDescription = stepDescription },
                )
                Spacer(Modifier.size(AbleSpacing.md))
                // Skip is available on every page and also marks the tour as completed.
                AbleTextButton(onClick = onFinish, enabled = !finishing, modifier = Modifier.focusRing(CircleShape)) {
                    Text(stringResource(R.string.tour_skip))
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(horizontal = AbleSpacing.xl)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TourPageContent(tourPages[index], visible = pagerState.currentPage == index)
                        Spacer(Modifier.height(AbleSpacing.lg))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AbleSpacing.xl, vertical = AbleSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    AbleOutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier.focusRing(CircleShape),
                    ) { Text(stringResource(R.string.tour_back), maxLines = 1) }
                }
                Spacer(Modifier.weight(1f))
                AbleButton(
                    onClick = {
                        if (isLast) {
                            haptics.confirm()
                            onFinish()
                        } else {
                            haptics.click()
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    enabled = !finishing,
                    modifier = Modifier.focusRing(CircleShape),
                    haptic = AbleHaptic.None,
                ) {
                    Text(stringResource(if (isLast) R.string.tour_done else R.string.tour_next), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TourPageContent(page: TourPage, visible: Boolean) {
    Spacer(Modifier.height(AbleSpacing.md))
    TourIllustration(page, visible)
    Text(
        stringResource(page.titleRes),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        stringResource(page.bodyRes),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (page.highlights.isNotEmpty()) {
        ListGroup(Modifier.fillMaxWidth()) {
            page.highlights.forEach { h ->
                item { shape ->
                    ActionRow(
                        title = stringResource(h.titleRes),
                        shape = shape,
                        supportingText = stringResource(h.bodyRes),
                        icon = h.icon,
                    )
                }
            }
        }
    }
}

/** Expressive illustration: a slowly turning Material shape that springs in when the page settles. */
@Composable
private fun TourIllustration(page: TourPage, visible: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "illustrationScale",
    )
    val rotation by rememberInfiniteTransition(label = "illustration").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "illustrationRotation",
    )
    Box(
        Modifier
            .size(160.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(160.dp).graphicsLayer { rotationZ = rotation },
            shape = page.shape.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {}
        Surface(
            modifier = Modifier.size(96.dp),
            shape = MaterialShapes.Circle.toShape(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (page.icon == null) {
                    AbleLogo(size = 64.dp, contentDescription = null)
                } else {
                    Icon(
                        page.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
    }
}
