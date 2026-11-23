package com.dragolabs.able.feature.tools.applock

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ScreenLockController
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ClearRestoredFocusOnTouch
import com.dragolabs.able.core.designsystem.component.ExpressiveIcon
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.listGroupItemShape
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.MessageEffect
import com.dragolabs.able.feature.tools.common.ToolsSearchField
import com.dragolabs.able.feature.tools.common.UiMessage
import com.dragolabs.able.feature.tools.common.highlightMatches
import com.dragolabs.able.feature.tools.common.startFirstAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.dragolabs.able.core.designsystem.R as DsR

@Immutable
data class AppLockUiState(
    val serviceEnabled: Boolean = false,
    val deviceSecure: Boolean = false,
    val biometricAvailable: Boolean = false,
    val config: AppLockConfig = AppLockConfig(),
    val apps: List<LauncherApp>? = null,
    val message: UiMessage? = null,
)

/** App lock tool (APPLOCK-001..APPLOCK-003). Locking itself runs in the `:app` Accessibility service. */
class AppLockViewModel(
    private val context: Context,
    private val controller: ScreenLockController,
    private val settings: AppLockSettings,
    private val appsSource: LauncherAppsSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(readState(AppLockUiState()))
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { settings.changes().collect { config -> _uiState.update { it.copy(config = config) } } }
        viewModelScope.launch {
            val apps = runCatching { appsSource.load() }.getOrDefault(emptyList())
            _uiState.update { it.copy(apps = apps) }
        }
    }

    private fun readState(previous: AppLockUiState): AppLockUiState {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val biometrics = context.getSystemService(BiometricManager::class.java)
        return previous.copy(
            serviceEnabled = runCatching { controller.isServiceEnabled() }.getOrDefault(false),
            deviceSecure = keyguard?.isDeviceSecure == true,
            biometricAvailable = runCatching {
                biometrics?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
            }.getOrDefault(false),
            config = settings.read(),
        )
    }

    /** Accessibility grants and screen-lock changes are not broadcast; re-read on resume. */
    fun refresh() = _uiState.update(::readState)

    fun setEnabled(enabled: Boolean) {
        if (enabled && !_uiState.value.deviceSecure) {
            _uiState.update { it.copy(message = UiMessage(R.string.applock_needs_secure_lock)) }
            return
        }
        settings.setEnabled(enabled)
    }

    fun setLocked(packageName: String, locked: Boolean) = settings.setLocked(packageName, locked)

    fun setGrace(grace: AppLockGrace) = settings.setGrace(grace)

    fun setLockAble(enabled: Boolean) = settings.setLockAble(enabled)

    fun reportSettingsUnavailable() = _uiState.update { it.copy(message = UiMessage(DsR.string.ds_no_handler)) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}

@Composable
fun AppLockRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AppLockViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            AppLockViewModel(
                context = container.appContext,
                controller = container.screenLock,
                settings = AppLockSettings(container.appContext),
                appsSource = LauncherAppsSource(container.appContext),
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    AppLockScreen(
        state = state,
        onBack = onBack,
        actions = AppLockActions(
            onOpenAccessibility = {
                if (!context.startFirstAvailable(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))) {
                    viewModel.reportSettingsUnavailable()
                }
            },
            onOpenSecurity = {
                if (!context.startFirstAvailable(Intent(Settings.ACTION_SECURITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))) {
                    viewModel.reportSettingsUnavailable()
                }
            },
            onEnabledChange = viewModel::setEnabled,
            onLockedChange = viewModel::setLocked,
            onGraceChange = viewModel::setGrace,
            onLockAbleChange = viewModel::setLockAble,
            onMessageConsumed = viewModel::consumeMessage,
        ),
        modifier = modifier,
    )
}

@Immutable
class AppLockActions(
    val onOpenAccessibility: () -> Unit,
    val onOpenSecurity: () -> Unit,
    val onEnabledChange: (Boolean) -> Unit,
    val onLockedChange: (String, Boolean) -> Unit,
    val onGraceChange: (AppLockGrace) -> Unit,
    val onLockAbleChange: (Boolean) -> Unit,
    val onMessageConsumed: () -> Unit,
)

@Composable
fun AppLockScreen(
    state: AppLockUiState,
    onBack: () -> Unit,
    actions: AppLockActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, actions.onMessageConsumed)
    var query by rememberSaveable { mutableStateOf("") }
    ClearRestoredFocusOnTouch()
    AbleDetailScaffold(
        title = stringResource(R.string.tools_applock_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) { padding ->
        ReadableWidth {
            val apps = state.apps
            val q = query.trim()
            val filtered = apps?.filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
                // Locked apps first so the current choice is visible without scrolling.
                ?.sortedByDescending { it.packageName in state.config.lockedPackages }
            LazyColumn(
                contentPadding = screenContentPadding(padding),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
            ) {
                item(key = "hero") { HeroCard(state) }
                item(key = "requirements") { RequirementsCard(state, actions) }
                item(key = "settings") { SettingsCard(state, actions) }
                item(key = "apps_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(text = stringResource(R.string.applock_apps_title), modifier = Modifier.weight(1f))
                            HelpButton(
                                title = stringResource(R.string.applock_help_apps_title),
                                text = stringResource(R.string.applock_help_apps_text),
                            )
                        }
                        ToolsSearchField(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = stringResource(R.string.applock_search_placeholder),
                        )
                    }
                }
                when {
                    filtered == null -> item(key = "loading") {
                        Row(Modifier.fillMaxWidth().padding(AbleSpacing.lg), horizontalArrangement = Arrangement.Center) { LoadingIndicator() }
                    }
                    filtered.isEmpty() -> item(key = "empty") {
                        Text(stringResource(R.string.applock_apps_empty), style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> itemsIndexed(filtered, key = { _, app -> app.packageName }) { index, app ->
                        AppRow(
                            app = app,
                            query = q,
                            locked = app.packageName in state.config.lockedPackages,
                            enabled = state.config.enabled,
                            index = index,
                            count = filtered.size,
                            onLockedChange = { actions.onLockedChange(app.packageName, it) },
                        )
                    }
                }
                item(key = "limits") {
                    StatusBanner(
                        level = StatusLevel.INFO,
                        title = stringResource(R.string.applock_limits_title),
                        message = stringResource(R.string.applock_limits_body),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(state: AppLockUiState) {
    val working = state.config.enabled && state.serviceEnabled && state.deviceSecure
    val count = state.config.lockedPackages.size + if (state.config.lockAble) 1 else 0
    AbleCard(style = AbleCardStyle.Hero) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg)) {
            ExpressiveIcon(
                icon = if (working) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                shape = MaterialShapes.Clover8Leaf,
                size = 64.dp,
                iconSize = 32.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                Text(stringResource(R.string.applock_hero_title), style = MaterialTheme.typography.titleMedium)
                StatusPill(
                    text = stringResource(
                        when {
                            !state.config.enabled -> R.string.applock_status_off
                            working -> R.string.applock_status_on
                            else -> R.string.applock_status_blocked
                        },
                    ),
                    level = when {
                        !state.config.enabled -> StatusLevel.INFO
                        working -> StatusLevel.SUCCESS
                        else -> StatusLevel.WARNING
                    },
                )
            }
        }
        Text(
            if (count == 0) stringResource(R.string.applock_hero_none) else pluralStringResource(R.plurals.applock_hero_count, count, count),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RequirementsCard(state: AppLockUiState, actions: AppLockActions) {
    val haptics = rememberAbleHaptics()
    AbleCard(title = stringResource(R.string.applock_requirements_title), icon = Icons.Rounded.Accessibility, style = AbleCardStyle.Outlined) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            StatusPill(
                text = stringResource(if (state.serviceEnabled) R.string.applock_service_on else R.string.applock_service_off),
                level = if (state.serviceEnabled) StatusLevel.SUCCESS else StatusLevel.WARNING,
                modifier = Modifier.weight(1f, fill = false),
            )
            HelpButton(
                title = stringResource(R.string.applock_help_service_title),
                text = stringResource(R.string.applock_help_service_text),
            )
        }
        if (!state.serviceEnabled) {
            Button(onClick = { haptics.click(); actions.onOpenAccessibility() }, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.applock_open_accessibility))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            StatusPill(
                text = stringResource(
                    when {
                        !state.deviceSecure -> R.string.applock_secure_off
                        state.biometricAvailable -> R.string.applock_secure_biometric
                        else -> R.string.applock_secure_credential
                    },
                ),
                level = if (state.deviceSecure) StatusLevel.SUCCESS else StatusLevel.CRITICAL,
                modifier = Modifier.weight(1f, fill = false),
            )
            HelpButton(
                title = stringResource(R.string.applock_help_auth_title),
                text = stringResource(R.string.applock_help_auth_text),
            )
        }
        if (!state.deviceSecure) {
            OutlinedButton(onClick = { haptics.click(); actions.onOpenSecurity() }, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.applock_open_security))
            }
        }
    }
}

@Composable
private fun SettingsCard(state: AppLockUiState, actions: AppLockActions) {
    AbleCard(title = stringResource(R.string.applock_settings_title), icon = Icons.Rounded.Fingerprint, style = AbleCardStyle.Outlined) {
        ListGroup {
            item { shape ->
                SwitchRow(
                    title = stringResource(R.string.applock_enable_title),
                    supportingText = stringResource(R.string.applock_enable_summary),
                    checked = state.config.enabled,
                    onCheckedChange = actions.onEnabledChange,
                    icon = Icons.Rounded.Lock,
                    shape = shape,
                )
            }
            item { shape ->
                SwitchRow(
                    title = stringResource(R.string.applock_lock_able_title),
                    supportingText = stringResource(R.string.applock_lock_able_summary),
                    checked = state.config.lockAble,
                    onCheckedChange = actions.onLockAbleChange,
                    icon = Icons.Rounded.Password,
                    enabled = state.config.enabled,
                    shape = shape,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(text = stringResource(R.string.applock_grace_title), modifier = Modifier.weight(1f))
            HelpButton(
                title = stringResource(R.string.applock_help_grace_title),
                text = stringResource(R.string.applock_help_grace_text),
            )
        }
        ListGroup {
            AppLockGrace.entries.forEach { grace ->
                item { shape ->
                    RadioRow(
                        title = graceLabel(grace),
                        selected = state.config.grace == grace,
                        onSelect = { actions.onGraceChange(grace) },
                        shape = shape,
                    )
                }
            }
        }
    }
}

@Composable
private fun graceLabel(grace: AppLockGrace): String = when (grace) {
    AppLockGrace.IMMEDIATELY -> stringResource(R.string.applock_grace_immediately)
    else -> pluralStringResource(R.plurals.applock_grace_minutes, grace.minutes, grace.minutes)
}

@Composable
private fun AppRow(
    app: LauncherApp,
    query: String,
    locked: Boolean,
    enabled: Boolean,
    index: Int,
    count: Int,
    onLockedChange: (Boolean) -> Unit,
) {
    val haptics = rememberAbleHaptics()
    val shape = listGroupItemShape(index, count)
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(shape)
            .toggleable(value = locked, enabled = enabled, role = Role.Switch) {
                haptics.toggle(it)
                onLockedChange(it)
            }
            .focusRing(shape),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { AppIcon(app.packageName) },
        headlineContent = { Text(highlightMatches(app.label, query)) },
        supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1) },
        trailingContent = { Switch(checked = locked, onCheckedChange = null, enabled = enabled) },
    )
}
