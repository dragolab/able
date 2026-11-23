package com.dragolabs.able.feature.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.ExpressiveIcon
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import kotlinx.coroutines.launch

private enum class OnboardingPage { WELCOME, PRIVACY, CAPABILITIES }

/** First-launch flow 1 entry point. Calls [onFinished] after completion or Skip has been persisted. */
@Composable
fun OnboardingRoute(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: OnboardingViewModel = viewModel(factory = ableViewModelFactory { OnboardingViewModel(it.preferences) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    OnboardingScreen(
        state = state,
        onTelemetryChoice = viewModel::setTelemetryChoice,
        onFinish = viewModel::finish,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onTelemetryChoice: (Boolean) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = OnboardingPage.entries
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AbleSpacing.sm, vertical = AbleSpacing.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                // Skip is available on every page (flow 1).
                AbleTextButton(onClick = onFinish, enabled = !state.finishing, modifier = Modifier.focusRing(CircleShape)) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { index ->
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
                        when (pages[index]) {
                            OnboardingPage.WELCOME -> WelcomePage()
                            OnboardingPage.PRIVACY -> PrivacyPage(state.telemetryOptIn, onTelemetryChoice)
                            OnboardingPage.CAPABILITIES -> CapabilitiesPage()
                        }
                        Spacer(Modifier.height(AbleSpacing.lg))
                    }
                }
            }
            StepIndicator(
                current = pagerState.currentPage,
                count = pages.size,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = AbleSpacing.md),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AbleSpacing.xl, vertical = AbleSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    AbleOutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier.focusRing(CircleShape),
                    ) { Text(stringResource(R.string.onboarding_back), maxLines = 1) }
                }
                Spacer(Modifier.weight(1f))
                AbleButton(
                    onClick = {
                        if (isLast) onFinish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    enabled = !state.finishing,
                    modifier = Modifier.focusRing(CircleShape),
                    haptic = if (isLast) AbleHaptic.Confirm else AbleHaptic.Click,
                ) {
                    Text(stringResource(if (isLast) R.string.onboarding_get_started else R.string.onboarding_next), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_step_description, current + 1, count)
    Row(
        modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 28.dp else 10.dp, label = "step")
            Box(
                Modifier
                    .height(10.dp)
                    .width(width)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun PageHeader(icon: ImageVector, shape: RoundedPolygon, title: String, body: String) {
    Spacer(Modifier.height(AbleSpacing.md))
    ExpressiveIcon(icon = icon, shape = shape, size = 112.dp, iconSize = 52.dp)
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        body,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WelcomePage() {
    PageHeader(
        Icons.Rounded.Handyman,
        MaterialShapes.Cookie9Sided,
        stringResource(R.string.onboarding_welcome_title),
        stringResource(R.string.onboarding_welcome_body),
    )
    ListGroup(Modifier.fillMaxWidth()) {
        item { shape ->
            ActionRow(
                title = stringResource(R.string.onboarding_welcome_local_title),
                shape = shape,
                supportingText = stringResource(R.string.onboarding_welcome_local_body),
                icon = Icons.Rounded.PhoneAndroid,
            )
        }
        item { shape ->
            ActionRow(
                title = stringResource(R.string.onboarding_welcome_control_title),
                shape = shape,
                supportingText = stringResource(R.string.onboarding_welcome_control_body),
                icon = Icons.Rounded.Tune,
            )
        }
    }
}

@Composable
private fun PrivacyPage(optIn: Boolean, onChoice: (Boolean) -> Unit) {
    PageHeader(
        Icons.Rounded.Shield,
        MaterialShapes.Clover4Leaf,
        stringResource(R.string.onboarding_privacy_title),
        stringResource(R.string.onboarding_privacy_body),
    )
    ListGroup(Modifier.fillMaxWidth()) {
        item { shape ->
            ActionRow(
                title = stringResource(R.string.settings_telemetry_categories_title),
                shape = shape,
                supportingText = stringResource(R.string.settings_telemetry_categories_body),
            )
        }
    }
    ListGroup(Modifier.fillMaxWidth()) {
        item { shape ->
            RadioRow(
                title = stringResource(R.string.onboarding_privacy_no),
                selected = !optIn,
                onSelect = { onChoice(false) },
                shape = shape,
                supportingText = stringResource(R.string.onboarding_privacy_no_supporting),
            )
        }
        item { shape ->
            RadioRow(
                title = stringResource(R.string.onboarding_privacy_yes),
                selected = optIn,
                onSelect = { onChoice(true) },
                shape = shape,
                supportingText = stringResource(R.string.onboarding_privacy_yes_supporting),
            )
        }
    }
    StatusBanner(
        level = StatusLevel.INFO,
        title = stringResource(R.string.onboarding_privacy_how_title),
        message = stringResource(R.string.onboarding_privacy_change_later),
    )
}

@Composable
private fun CapabilitiesPage() {
    PageHeader(
        Icons.Rounded.Tune,
        MaterialShapes.Sunny,
        stringResource(R.string.onboarding_capabilities_title),
        stringResource(R.string.onboarding_capabilities_body),
    )
    ListGroup(Modifier.fillMaxWidth()) {
        listOf(
            Triple(Icons.Rounded.AdminPanelSettings, R.string.settings_access_shizuku_title, R.string.settings_access_shizuku_purpose),
            Triple(Icons.Rounded.QueryStats, R.string.settings_access_usage_title, R.string.settings_access_usage_purpose),
            Triple(Icons.Rounded.Accessibility, R.string.settings_access_accessibility_title, R.string.settings_access_accessibility_purpose),
            Triple(Icons.Rounded.Notifications, R.string.settings_access_notifications_title, R.string.settings_access_notifications_purpose),
            Triple(Icons.Rounded.SettingsApplications, R.string.settings_access_write_settings_title, R.string.settings_access_write_settings_purpose),
            Triple(Icons.Rounded.LocationOn, R.string.settings_access_location_title, R.string.settings_access_location_purpose),
        ).forEach { (icon, title, purpose) ->
            item { shape ->
                ActionRow(
                    title = stringResource(title),
                    shape = shape,
                    supportingText = stringResource(purpose),
                    icon = icon,
                )
            }
        }
    }
    Text(
        stringResource(R.string.onboarding_capabilities_footer),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
