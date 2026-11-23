package com.dragolabs.able.feature.tools.hotspot

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.WifiTetheringOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleButtonFlow
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.AbleFilledTonalButton
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleSingleChoiceToggleGroup
import com.dragolabs.able.core.designsystem.component.AbleSwitch
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.ConfirmationDialog
import com.dragolabs.able.core.designsystem.component.ExpandableRow
import com.dragolabs.able.core.designsystem.component.ExpressiveIcon
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.labelRes
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.MessageEffect
import com.dragolabs.able.feature.tools.common.startFirstAvailable

// ---------------------------------------------------------------------------------------------
// Route
// ---------------------------------------------------------------------------------------------

@Composable
fun HotspotRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: HotspotViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            HotspotViewModel(
                dataSource = HotspotDataSource(container.appContext, container.shizuku),
                shizukuState = container.shizuku.connectionState,
                actionLog = container.actionLog,
                logScope = container.applicationScope,
                describe = { it.resolve(container.appContext) },
                refreshAccess = container.capabilityStatus::refresh,
                requestShizukuPermission = { container.shizuku.requestPermission() },
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        // Shizuku access and the hotspot itself may change while Able is in the background.
        viewModel.refresh()
        onPauseOrDispose { }
    }
    HotspotScreen(
        state = state,
        actions = HotspotActions(
            onBack = onBack,
            onOpenSettings = {
                val opened = context.startFirstAvailable(
                    Intent(ACTION_TETHER_SETTINGS),
                    Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
                    Intent(Settings.ACTION_WIRELESS_SETTINGS),
                    Intent(Settings.ACTION_SETTINGS),
                )
                if (!opened) viewModel.reportSettingsUnavailable()
            },
            onAuthorizeShizuku = viewModel::authorizeShizuku,
            onOpenShizuku = { if (!context.ableContainer.shizuku.openShizukuApp(context)) viewModel.reportShizukuMissing() },
            onRefresh = viewModel::refresh,
            onSetEnabled = viewModel::setEnabled,
            onStartEditing = viewModel::startEditing,
            onDraftChange = viewModel::updateDraft,
            onCancelEditing = viewModel::cancelEditing,
            draftNeedsRestart = viewModel::draftNeedsRestart,
            onApply = viewModel::applyDraft,
            onBlock = viewModel::blockClient,
            onUnblock = viewModel::unblockClient,
            onShare = { payload ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, payload)
                if (!context.startFirstAvailable(Intent.createChooser(send, null))) viewModel.reportSettingsUnavailable()
            },
            onMessageConsumed = viewModel::consumeMessage,
        ),
        modifier = modifier,
    )
}

private const val ACTION_TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"

class HotspotActions(
    val onBack: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onAuthorizeShizuku: () -> Unit,
    val onOpenShizuku: () -> Unit,
    val onRefresh: () -> Unit,
    val onSetEnabled: (Boolean) -> Unit,
    val onStartEditing: () -> Unit,
    val onDraftChange: (HotspotConfig) -> Unit,
    val onCancelEditing: () -> Unit,
    val draftNeedsRestart: () -> Boolean,
    val onApply: () -> Unit,
    val onBlock: (String) -> Unit,
    val onUnblock: (String) -> Unit,
    val onShare: (String) -> Unit,
    val onMessageConsumed: () -> Unit,
)

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

@Composable
fun HotspotScreen(state: HotspotUiState, actions: HotspotActions, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, actions.onMessageConsumed)
    AbleDetailScaffold(
        title = stringResource(R.string.tools_hotspot_title),
        onBack = actions.onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) { padding ->
        ReadableWidth {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(screenContentPadding(padding)),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.lg),
            ) {
                StatusCard(state, actions)
                if (!state.canControl) {
                    ShizukuBanner(state.shizuku, actions)
                } else {
                    state.loadFailure?.let {
                        StatusBanner(
                            level = StatusLevel.WARNING,
                            title = stringResource(R.string.hotspot_load_failed_title),
                            message = stringResource(R.string.hotspot_load_failed_message, it),
                            actionLabel = stringResource(R.string.hotspot_refresh),
                            onAction = actions.onRefresh,
                        )
                    }
                    val config = state.snapshot?.config
                    when {
                        state.draft != null -> EditCard(state, state.draft, actions)
                        config != null -> ConfigCard(config, state, actions)
                        state.loading -> Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) { LoadingIndicator() }
                        state.snapshot != null -> StatusBanner(
                            level = StatusLevel.WARNING,
                            title = stringResource(R.string.hotspot_config_unavailable_title),
                            message = stringResource(R.string.hotspot_config_unavailable_message),
                        )
                    }
                    if (state.draft == null && config != null) ClientsCard(state, config, actions)
                    state.snapshot?.errors?.takeIf { it.isNotEmpty() }?.let { DiagnosticsRow(it) }
                }
                AbleOutlinedButton(onClick = actions.onOpenSettings, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.Settings, contentDescription = null)
                    Text(stringResource(R.string.hotspot_open_settings), modifier = Modifier.padding(start = AbleSpacing.sm))
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: HotspotUiState, actions: HotspotActions) {
    val status = state.status
    val (labelRes, level) = when (status) {
        HotspotState.ENABLED -> R.string.hotspot_state_on to StatusLevel.SUCCESS
        HotspotState.ENABLING -> R.string.hotspot_state_enabling to StatusLevel.INFO
        HotspotState.DISABLED -> R.string.hotspot_state_off to StatusLevel.INFO
        HotspotState.DISABLING -> R.string.hotspot_state_disabling to StatusLevel.INFO
        HotspotState.FAILED -> R.string.hotspot_state_failed to StatusLevel.CRITICAL
        HotspotState.UNKNOWN -> R.string.hotspot_state_unknown to StatusLevel.WARNING
    }
    val on = status == HotspotState.ENABLED || status == HotspotState.ENABLING
    AbleCard(style = AbleCardStyle.Hero) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg)) {
            ExpressiveIcon(
                icon = if (on) Icons.Rounded.WifiTethering else Icons.Rounded.WifiTetheringOff,
                shape = MaterialShapes.Cookie9Sided,
                size = 64.dp,
                iconSize = 32.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
                Text(stringResource(R.string.hotspot_status), style = MaterialTheme.typography.titleMedium)
                StatusPill(text = stringResource(labelRes), level = level)
                state.snapshot?.config?.ssid?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.canControl) {
                if (state.work == HotspotWork.TOGGLING) {
                    LoadingIndicator()
                } else {
                    val label = stringResource(R.string.hotspot_toggle)
                    AbleSwitch(
                        checked = on,
                        onCheckedChange = actions.onSetEnabled,
                        enabled = !state.busy && status != HotspotState.ENABLING && status != HotspotState.DISABLING,
                        modifier = Modifier
                            .focusRing()
                            .semantics { contentDescription = label },
                    )
                }
            }
        }
        if (status == HotspotState.UNKNOWN && !state.canControl) {
            Text(
                stringResource(R.string.hotspot_state_unknown_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ShizukuBanner(shizuku: ShizukuConnectionState, actions: HotspotActions) {
    val running = shizuku == ShizukuConnectionState.WAITING_FOR_PERMISSION || shizuku == ShizukuConnectionState.PERMISSION_DENIED
    AbleCard(
        title = stringResource(R.string.hotspot_shizuku_title),
        subtitle = stringResource(shizuku.labelRes()),
        icon = Icons.Rounded.Tune,
        style = AbleCardStyle.Outlined,
        trailing = {
            HelpButton(
                title = stringResource(R.string.hotspot_shizuku_title),
                text = stringResource(R.string.hotspot_shizuku_help),
            )
        },
    ) {
        Text(
            stringResource(R.string.hotspot_shizuku_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AbleButtonFlow {
            if (running) {
                AbleButton(onClick = actions.onAuthorizeShizuku, modifier = Modifier.focusRing()) {
                    Text(stringResource(R.string.hotspot_shizuku_authorize))
                }
            }
            AbleFilledTonalButton(onClick = actions.onOpenShizuku, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.hotspot_shizuku_open))
            }
            AbleTextButton(onClick = actions.onRefresh, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.hotspot_refresh))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Configuration display
// ---------------------------------------------------------------------------------------------

@Composable
private fun ConfigCard(config: HotspotConfig, state: HotspotUiState, actions: HotspotActions) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showQr by rememberSaveable { mutableStateOf(false) }
    val payload = WifiQrPayload.build(config.ssid, config.passphrase, config.security, config.hidden)
    AbleCard(
        title = stringResource(R.string.hotspot_config_title),
        icon = Icons.Rounded.WifiTethering,
        trailing = {
            AbleIconButton(onClick = actions.onRefresh, enabled = !state.loading && !state.busy, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.hotspot_refresh))
            }
        },
    ) {
        ListGroup {
            item { shape -> ActionRow(title = stringResource(R.string.hotspot_ssid), supportingText = config.ssid.ifEmpty { "—" }, shape = shape) }
            item { shape ->
                val hidden = stringResource(R.string.hotspot_password_hidden)
                val password = when {
                    !config.security.needsPassphrase -> stringResource(R.string.hotspot_password_none)
                    config.passphrase == null -> stringResource(R.string.hotspot_password_unreadable)
                    showPassword -> config.passphrase
                    else -> hidden
                }
                ActionRow(
                    title = stringResource(R.string.hotspot_password),
                    supportingText = password,
                    shape = shape,
                    showChevron = false,
                    onClick = if (config.passphrase != null && config.security.needsPassphrase) ({ showPassword = !showPassword }) else null,
                    trailing = if (config.passphrase != null && config.security.needsPassphrase) {
                        {
                            Icon(
                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(if (showPassword) R.string.hotspot_password_hide else R.string.hotspot_password_show),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            item { shape -> ActionRow(title = stringResource(R.string.hotspot_security), supportingText = stringResource(securityLabel(config.security)), shape = shape) }
            item { shape -> ActionRow(title = stringResource(R.string.hotspot_band), supportingText = stringResource(bandLabel(config.band)), shape = shape) }
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.hotspot_hidden),
                    supportingText = stringResource(if (config.hidden) R.string.hotspot_yes else R.string.hotspot_no),
                    shape = shape,
                )
            }
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.hotspot_max_clients),
                    supportingText = if (config.maxClients <= 0) stringResource(R.string.hotspot_max_clients_none) else config.maxClients.toString(),
                    shape = shape,
                )
            }
            item { shape -> ActionRow(title = stringResource(R.string.hotspot_auto_off), supportingText = autoOffText(config), shape = shape) }
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.hotspot_mac_randomization),
                    supportingText = stringResource(macLabel(config.macRandomization)),
                    shape = shape,
                )
            }
        }
        AbleButtonFlow {
            AbleButton(onClick = actions.onStartEditing, enabled = !state.busy, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
                Text(stringResource(R.string.hotspot_edit), modifier = Modifier.padding(start = AbleSpacing.sm))
            }
            if (payload != null) {
                AbleFilledTonalButton(onClick = { showQr = !showQr }, modifier = Modifier.focusRing()) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null)
                    Text(
                        stringResource(if (showQr) R.string.hotspot_qr_hide else R.string.hotspot_qr_show),
                        modifier = Modifier.padding(start = AbleSpacing.sm),
                    )
                }
            }
        }
        if (payload == null) {
            Text(
                stringResource(R.string.hotspot_qr_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (showQr) {
            QrSection(payload, config.ssid, actions)
        }
    }
}

@Composable
private fun QrSection(payload: String, ssid: String, actions: HotspotActions) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
        val qr = remember(payload) { QrCode.encode(payload) }
        if (qr == null) {
            Text(stringResource(R.string.hotspot_qr_too_long), style = MaterialTheme.typography.bodyMedium)
        } else {
            val description = stringResource(R.string.hotspot_qr_description, ssid)
            Box(Modifier.semantics { contentDescription = description }) {
                Canvas(Modifier.size(240.dp)) {
                    // White background and a 4-module quiet zone keep the code scannable in dark theme.
                    drawRect(Color.White)
                    val cell = size.minDimension / (qr.size + 8)
                    for (y in 0 until qr.size) {
                        for (x in 0 until qr.size) {
                            if (qr.isDark(x, y)) {
                                drawRect(Color.Black, topLeft = Offset((x + 4) * cell, (y + 4) * cell), size = Size(cell + 0.5f, cell + 0.5f))
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.hotspot_qr_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AbleTextButton(onClick = { actions.onShare(payload) }, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.Share, contentDescription = null)
                Text(stringResource(R.string.hotspot_share), modifier = Modifier.padding(start = AbleSpacing.sm))
            }
            HelpButton(title = stringResource(R.string.hotspot_share), text = stringResource(R.string.hotspot_share_help))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Editing
// ---------------------------------------------------------------------------------------------

private val TIMEOUT_OPTIONS_MS = listOf(-1L, 5 * 60_000L, 10 * 60_000L, 30 * 60_000L, 60 * 60_000L)

@Composable
private fun EditCard(state: HotspotUiState, draft: HotspotConfig, actions: HotspotActions) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    val capabilities = state.capabilities
    val errors = state.draftErrors
    fun update(transform: HotspotConfig.() -> HotspotConfig) = actions.onDraftChange(draft.transform())

    AbleCard(
        title = stringResource(R.string.hotspot_edit_title),
        icon = Icons.Rounded.Edit,
        style = AbleCardStyle.Outlined,
        trailing = { HelpButton(title = stringResource(R.string.hotspot_edit_title), text = stringResource(R.string.hotspot_edit_help)) },
    ) {
        val ssidError = errors.firstOrNull { it == HotspotFieldError.SSID_EMPTY || it == HotspotFieldError.SSID_TOO_LONG }
        OutlinedTextField(
            value = draft.ssid,
            onValueChange = { update { copy(ssid = it) } },
            label = { Text(stringResource(R.string.hotspot_ssid)) },
            singleLine = true,
            isError = ssidError != null,
            supportingText = { Text(ssidError?.let { stringResource(errorLabel(it)) } ?: stringResource(R.string.hotspot_ssid_hint)) },
            modifier = Modifier.fillMaxWidth().focusRing(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.hotspot_security), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            HelpButton(title = stringResource(R.string.hotspot_security), text = stringResource(R.string.hotspot_security_help))
        }
        ListGroup {
            HotspotSecurity.editable.forEach { option ->
                item { shape ->
                    val unsupported = capabilities.supportsWpa3 == false && (option == HotspotSecurity.WPA3_SAE || option == HotspotSecurity.WPA3_SAE_TRANSITION)
                    RadioRow(
                        title = stringResource(securityLabel(option)),
                        supportingText = if (unsupported) stringResource(R.string.hotspot_unsupported_here) else null,
                        selected = draft.security == option,
                        onSelect = { if (!unsupported) update { copy(security = option) } },
                        shape = shape,
                    )
                }
            }
        }

        if (draft.security.needsPassphrase) {
            val passError = errors.firstOrNull {
                it == HotspotFieldError.PASSPHRASE_TOO_SHORT || it == HotspotFieldError.PASSPHRASE_TOO_LONG || it == HotspotFieldError.PASSPHRASE_NOT_ASCII
            }
            OutlinedTextField(
                value = draft.passphrase.orEmpty(),
                onValueChange = { update { copy(passphrase = it) } },
                label = { Text(stringResource(R.string.hotspot_password)) },
                singleLine = true,
                isError = passError != null,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    AbleIconButton(onClick = { showPassword = !showPassword }, haptic = AbleHaptic.Tick) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(if (showPassword) R.string.hotspot_password_hide else R.string.hotspot_password_show),
                        )
                    }
                },
                supportingText = {
                    Text(passError?.let { stringResource(errorLabel(it)) } ?: stringResource(R.string.hotspot_password_hint, draft.passphrase.orEmpty().length))
                },
                modifier = Modifier.fillMaxWidth().focusRing(),
            )
        } else {
            StatusBanner(level = StatusLevel.WARNING, title = stringResource(R.string.hotspot_open_warning_title), message = stringResource(R.string.hotspot_open_warning))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.hotspot_band), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            HelpButton(title = stringResource(R.string.hotspot_band), text = stringResource(R.string.hotspot_band_help))
        }
        if (draft.band == HotspotBand.DUAL) {
            Text(stringResource(R.string.hotspot_band_dual_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AbleSingleChoiceToggleGroup(
            options = HotspotBand.editable,
            selected = draft.band.takeIf { it in HotspotBand.editable },
            onSelect = { update { copy(band = it) } },
            enabled = { option ->
                when (option) {
                    HotspotBand.BAND_5GHZ -> capabilities.supports5Ghz != false
                    HotspotBand.BAND_6GHZ -> capabilities.supports6Ghz != false
                    else -> true
                }
            },
        ) { option -> Text(stringResource(bandShortLabel(option))) }
        errors.firstOrNull { it == HotspotFieldError.BAND_UNSUPPORTED || it == HotspotFieldError.BAND_6GHZ_NEEDS_WPA3 }?.let {
            Text(stringResource(errorLabel(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        ListGroup {
            item { shape ->
                SwitchRow(
                    title = stringResource(R.string.hotspot_hidden),
                    supportingText = stringResource(R.string.hotspot_hidden_hint),
                    checked = draft.hidden,
                    onCheckedChange = { update { copy(hidden = it) } },
                    shape = shape,
                )
            }
            item { shape ->
                SwitchRow(
                    title = stringResource(R.string.hotspot_auto_off),
                    supportingText = stringResource(R.string.hotspot_auto_off_hint),
                    checked = draft.autoShutdown,
                    onCheckedChange = { update { copy(autoShutdown = it) } },
                    shape = shape,
                )
            }
        }
        if (draft.autoShutdown) {
            AbleButtonFlow {
                TIMEOUT_OPTIONS_MS.forEach { option ->
                    AbleFilterChip(
                        selected = HotspotEdit.sameTimeout(draft.shutdownTimeoutMs, option),
                        onClick = { update { copy(shutdownTimeoutMs = option) } },
                        label = { Text(timeoutLabel(option)) },
                        modifier = Modifier.focusRing(),
                    )
                }
            }
        }

        val maxError = errors.contains(HotspotFieldError.MAX_CLIENTS_INVALID)
        var maxText by rememberSaveable(draft.maxClients) { mutableStateOf(draft.maxClients.toString()) }
        OutlinedTextField(
            value = maxText,
            onValueChange = { text ->
                val digits = text.filter(Char::isDigit).take(3)
                maxText = digits
                update { copy(maxClients = digits.toIntOrNull() ?: 0) }
            },
            label = { Text(stringResource(R.string.hotspot_max_clients)) },
            singleLine = true,
            isError = maxError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                val max = capabilities.maxSupportedClients
                Text(
                    when {
                        maxError -> stringResource(R.string.hotspot_error_max_clients, max ?: HotspotValidator.MAX_CLIENTS_FALLBACK)
                        max != null && max > 0 -> stringResource(R.string.hotspot_max_clients_hint_max, max)
                        else -> stringResource(R.string.hotspot_max_clients_hint)
                    },
                )
            },
            modifier = Modifier.fillMaxWidth().focusRing(),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
            AbleButton(
                onClick = { if (actions.draftNeedsRestart()) confirmRestart = true else actions.onApply() },
                enabled = errors.isEmpty() && !state.busy,
                haptic = AbleHaptic.Confirm,
                modifier = Modifier.focusRing(),
            ) { Text(stringResource(R.string.hotspot_apply)) }
            AbleTextButton(onClick = actions.onCancelEditing, enabled = !state.busy, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.hotspot_cancel))
            }
            if (state.work == HotspotWork.APPLYING) LoadingIndicator()
        }
    }

    if (confirmRestart) {
        ConfirmationDialog(
            title = stringResource(R.string.hotspot_restart_title),
            message = stringResource(R.string.hotspot_restart_message),
            confirmLabel = stringResource(R.string.hotspot_restart_confirm),
            onConfirm = { confirmRestart = false; actions.onApply() },
            onDismiss = { confirmRestart = false },
            destructive = true,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Clients
// ---------------------------------------------------------------------------------------------

@Composable
private fun ClientsCard(state: HotspotUiState, config: HotspotConfig, actions: HotspotActions) {
    var pendingBlock by rememberSaveable { mutableStateOf<String?>(null) }
    val clients = state.snapshot?.clients
    AbleCard(
        title = stringResource(R.string.hotspot_clients_title),
        icon = Icons.Rounded.Devices,
        trailing = { HelpButton(title = stringResource(R.string.hotspot_clients_title), text = stringResource(R.string.hotspot_clients_help)) },
    ) {
        when {
            clients == null -> Text(
                stringResource(R.string.hotspot_clients_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            clients.isEmpty() -> Text(
                stringResource(if (state.status == HotspotState.ENABLED) R.string.hotspot_clients_none else R.string.hotspot_clients_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> ListGroup {
                clients.forEach { client ->
                    item { shape ->
                        ActionRow(
                            title = client.hostname ?: client.macAddress,
                            supportingText = listOfNotNull(client.macAddress.takeIf { client.hostname != null }, client.ipAddress).joinToString(" · ").ifEmpty { null },
                            shape = shape,
                            showChevron = false,
                            trailing = {
                                AbleIconButton(
                                    onClick = { pendingBlock = client.macAddress },
                                    enabled = !state.busy,
                                    haptic = AbleHaptic.Reject,
                                    modifier = Modifier.focusRing(),
                                ) {
                                    Icon(Icons.Rounded.Block, contentDescription = stringResource(R.string.hotspot_block_client, client.hostname ?: client.macAddress))
                                }
                            },
                        )
                    }
                }
            }
        }
        if (config.blockedClients.isNotEmpty()) {
            Text(stringResource(R.string.hotspot_blocked_title), style = MaterialTheme.typography.titleSmall)
            ListGroup {
                config.blockedClients.forEach { mac ->
                    item { shape ->
                        ActionRow(
                            title = mac,
                            shape = shape,
                            showChevron = false,
                            trailing = {
                                AbleTextButton(onClick = { actions.onUnblock(mac) }, enabled = !state.busy, modifier = Modifier.focusRing()) {
                                    Text(stringResource(R.string.hotspot_unblock))
                                }
                            },
                        )
                    }
                }
            }
        }
        if (state.work == HotspotWork.BLOCKING) LoadingIndicator()
    }

    pendingBlock?.let { mac ->
        val forceDisconnect = state.capabilities.clientForceDisconnect
        ConfirmationDialog(
            title = stringResource(R.string.hotspot_block_title),
            message = stringResource(
                if (forceDisconnect == false) R.string.hotspot_block_message_reconnect else R.string.hotspot_block_message,
                mac,
            ),
            confirmLabel = stringResource(R.string.hotspot_block),
            onConfirm = { pendingBlock = null; actions.onBlock(mac) },
            onDismiss = { pendingBlock = null },
            destructive = true,
            icon = Icons.Rounded.Block,
        )
    }
}

@Composable
private fun DiagnosticsRow(errors: List<String>) {
    ExpandableRow(
        title = stringResource(R.string.hotspot_diagnostics_title),
        summary = stringResource(R.string.hotspot_diagnostics_summary, errors.size),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(errors.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------------------------------------
// Labels
// ---------------------------------------------------------------------------------------------

private fun securityLabel(security: HotspotSecurity): Int = when (security) {
    HotspotSecurity.OPEN -> R.string.hotspot_security_open
    HotspotSecurity.WPA2_PSK -> R.string.hotspot_security_wpa2
    HotspotSecurity.WPA3_SAE_TRANSITION -> R.string.hotspot_security_transition
    HotspotSecurity.WPA3_SAE -> R.string.hotspot_security_wpa3
    HotspotSecurity.WPA3_OWE_TRANSITION, HotspotSecurity.WPA3_OWE -> R.string.hotspot_security_owe
    HotspotSecurity.UNKNOWN -> R.string.hotspot_unknown
}

private fun bandLabel(band: HotspotBand): Int = when (band) {
    HotspotBand.BAND_2GHZ -> R.string.hotspot_band_2
    HotspotBand.BAND_5GHZ -> R.string.hotspot_band_5
    HotspotBand.BAND_6GHZ -> R.string.hotspot_band_6
    HotspotBand.DUAL -> R.string.hotspot_band_dual
    HotspotBand.UNKNOWN -> R.string.hotspot_unknown
}

private fun bandShortLabel(band: HotspotBand): Int = when (band) {
    HotspotBand.BAND_2GHZ -> R.string.hotspot_band_2_short
    HotspotBand.BAND_5GHZ -> R.string.hotspot_band_5_short
    HotspotBand.BAND_6GHZ -> R.string.hotspot_band_6_short
    else -> R.string.hotspot_unknown
}

private fun macLabel(value: HotspotMacRandomization): Int = when (value) {
    HotspotMacRandomization.NONE -> R.string.hotspot_mac_none
    HotspotMacRandomization.PERSISTENT -> R.string.hotspot_mac_persistent
    HotspotMacRandomization.NON_PERSISTENT -> R.string.hotspot_mac_non_persistent
    HotspotMacRandomization.UNKNOWN -> R.string.hotspot_unknown
}

private fun errorLabel(error: HotspotFieldError): Int = when (error) {
    HotspotFieldError.SSID_EMPTY -> R.string.hotspot_error_ssid_empty
    HotspotFieldError.SSID_TOO_LONG -> R.string.hotspot_error_ssid_long
    HotspotFieldError.PASSPHRASE_TOO_SHORT -> R.string.hotspot_error_password_short
    HotspotFieldError.PASSPHRASE_TOO_LONG -> R.string.hotspot_error_password_long
    HotspotFieldError.PASSPHRASE_NOT_ASCII -> R.string.hotspot_error_password_ascii
    HotspotFieldError.BAND_UNSUPPORTED -> R.string.hotspot_error_band_unsupported
    HotspotFieldError.BAND_6GHZ_NEEDS_WPA3 -> R.string.hotspot_error_band_6_wpa3
    HotspotFieldError.MAX_CLIENTS_INVALID -> R.string.hotspot_error_max_clients_generic
    HotspotFieldError.TIMEOUT_INVALID -> R.string.hotspot_error_timeout
    HotspotFieldError.MAC_INVALID -> R.string.hotspot_error_mac
}

@Composable
private fun timeoutLabel(ms: Long): String =
    if (ms <= 0) stringResource(R.string.hotspot_timeout_default) else stringResource(R.string.hotspot_timeout_minutes, (ms / 60_000).toInt())

@Composable
private fun autoOffText(config: HotspotConfig): String = when {
    !config.autoShutdown -> stringResource(R.string.hotspot_auto_off_disabled)
    else -> timeoutLabel(config.shutdownTimeoutMs)
}
