package com.dragolabs.able.feature.tools.toggles

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import com.dragolabs.able.core.designsystem.component.HelpButton
import androidx.compose.material.icons.rounded.ContentCopy
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleIconButton
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.ConfirmationDialog
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.LoadingState
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.DnsProfile
import com.dragolabs.able.core.model.PrivateDnsMode
import com.dragolabs.able.feature.tools.R
import com.dragolabs.able.feature.tools.common.MessageEffect
import com.dragolabs.able.feature.tools.common.startFirstAvailable
import com.dragolabs.able.core.designsystem.R as DsR

@Composable
fun SystemTogglesRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SystemTogglesViewModel = viewModel(
        factory = ableViewModelFactory { container ->
            SystemTogglesViewModel(
                reader = SystemSettingsReader(container.appContext, container.secureSettings, container.shizuku),
                secureSettings = container.secureSettings,
                shizukuState = container.shizuku.connectionState,
                dnsDao = container.database.dnsDao(),
                actionLog = container.actionLog,
                logScope = container.applicationScope,
                describe = { it.resolve(container.appContext) },
                refreshAccess = container.capabilityStatus::refresh,
            )
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    // Live changes (NFC broadcasts, Settings.Global observers) are collected by the ViewModel.
    LaunchedEffect(state.openSettings) {
        val target = state.openSettings ?: return@LaunchedEffect
        viewModel.consumeOpenSettings()
        val intents = when (target) {
            // The NFC panel is not used: AOSP retires it and redirects to the same settings page.
            SettingsTarget.NFC -> arrayOf(Intent(Settings.ACTION_NFC_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            SettingsTarget.WRITE_SETTINGS -> arrayOf(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS),
            )
            SettingsTarget.DEVELOPER_OPTIONS -> arrayOf(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            )
            SettingsTarget.WIRELESS_DEBUGGING -> viewModel.wirelessDebuggingIntents().toTypedArray()
            SettingsTarget.PRIVATE_DNS -> arrayOf(Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        }
        if (!context.startFirstAvailable(*intents)) viewModel.reportSettingsUnavailable()
    }

    SystemTogglesScreen(
        state = state,
        onBack = onBack,
        onNfcChange = viewModel::setNfc,
        onAutoBrightnessChange = viewModel::setAutoBrightness,
        onUsbDebuggingChange = { viewModel.setUsbDebugging(it) },
        onWirelessDebuggingChange = { viewModel.setWirelessDebugging(it) },
        onOpenDeveloperOptions = viewModel::openDeveloperOptions,
        onOpenWirelessDebugging = viewModel::openWirelessDebugging,
        adbGrantCommand = viewModel.adbGrantCommand,
        adbRevokeCommand = viewModel.adbRevokeCommand,
        onCopyAdbCommand = {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("adb", viewModel.adbGrantCommand))
            viewModel.reportCopied()
        },
        onApplyDns = viewModel::applyDnsProfile,
        onOpenPrivateDnsSettings = viewModel::openPrivateDnsSettings,
        onShowAddDns = { viewModel.showAddDns(true) },
        onDismissAddDns = { viewModel.showAddDns(false) },
        onAddDns = viewModel::addDnsProfile,
        onDeleteDns = viewModel::deleteDnsProfile,
        onConfirmPending = viewModel::confirmPending,
        onDismissPending = viewModel::dismissPending,
        onMessageConsumed = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@Composable
fun SystemTogglesScreen(
    state: SystemTogglesUiState,
    onBack: () -> Unit,
    onNfcChange: (Boolean) -> Unit,
    onAutoBrightnessChange: (Boolean) -> Unit,
    onUsbDebuggingChange: (Boolean) -> Unit,
    onWirelessDebuggingChange: (Boolean) -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onOpenWirelessDebugging: () -> Unit,
    adbGrantCommand: String,
    adbRevokeCommand: String,
    onCopyAdbCommand: () -> Unit,
    onApplyDns: (DnsProfile) -> Unit,
    onOpenPrivateDnsSettings: () -> Unit,
    onShowAddDns: () -> Unit,
    onDismissAddDns: () -> Unit,
    onAddDns: (String, String) -> Unit,
    onDeleteDns: (DnsProfile) -> Unit,
    onConfirmPending: () -> Unit,
    onDismissPending: () -> Unit,
    onMessageConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, onMessageConsumed)

    AbleDetailScaffold(
        title = stringResource(R.string.tools_toggles_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) { padding ->
        val snapshot = state.snapshot
        if (snapshot == null) {
            LoadingState(modifier = Modifier.padding(padding))
            return@AbleDetailScaffold
        }
        ReadableWidth {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(screenContentPadding(padding)),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            ) {
                AccessBanner(state, adbRevokeCommand)
                if (state.busy.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
                        LoadingIndicator()
                        Text(stringResource(R.string.tools_toggles_verifying), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.tools_toggles_section_connectivity), modifier = Modifier.weight(1f))
                    HelpButton(
                        title = stringResource(R.string.tools_toggles_nfc_help_title),
                        text = stringResource(R.string.tools_toggles_nfc_help_text, adbGrantCommand),
                    )
                }
                ListGroup {
                    item { shape ->
                        if (snapshot.nfcSupported) {
                            val nfc = state.display(ToggleKey.NFC) ?: ToggleDisplay(snapshot.nfcEnabled, false)
                            SwitchRow(
                                title = stringResource(R.string.tools_toggles_nfc),
                                checked = nfc.checked,
                                onCheckedChange = onNfcChange,
                                shape = shape,
                                icon = Icons.Rounded.Nfc,
                                enabled = ToggleKey.NFC !in state.busy,
                                supportingText = stringResource(
                                    when {
                                        nfc.transitioning -> transitionRes(nfc.checked)
                                        !state.directNfc -> R.string.tools_toggles_nfc_fallback
                                        snapshot.writeSecureSettings -> R.string.tools_toggles_nfc_direct_advanced
                                        else -> R.string.tools_toggles_nfc_direct_shizuku
                                    },
                                ),
                            )
                        } else {
                            ActionRow(
                                title = stringResource(R.string.tools_toggles_nfc),
                                shape = shape,
                                icon = Icons.Rounded.Nfc,
                                enabled = false,
                                showChevron = false,
                                supportingText = stringResource(R.string.tools_toggles_nfc_unsupported),
                            )
                        }
                    }
                    if (snapshot.nfcSupported && !state.directNfc) {
                        item { shape ->
                            ActionRow(
                                title = stringResource(R.string.tools_toggles_nfc_copy_adb),
                                shape = shape,
                                icon = Icons.Rounded.ContentCopy,
                                supportingText = adbGrantCommand,
                                showChevron = false,
                                onClick = onCopyAdbCommand,
                            )
                        }
                    }
                }

                SectionHeader(stringResource(R.string.tools_toggles_section_display))
                ListGroup {
                    item { shape ->
                        val brightness = state.display(ToggleKey.AUTO_BRIGHTNESS) ?: ToggleDisplay(snapshot.autoBrightness, false)
                        SwitchRow(
                            title = stringResource(R.string.tools_toggles_auto_brightness),
                            checked = brightness.checked,
                            onCheckedChange = onAutoBrightnessChange,
                            shape = shape,
                            icon = Icons.Rounded.BrightnessAuto,
                            enabled = ToggleKey.AUTO_BRIGHTNESS !in state.busy,
                            supportingText = stringResource(
                                when {
                                    brightness.transitioning -> transitionRes(brightness.checked)
                                    snapshot.writeSettingsGranted -> R.string.tools_toggles_direct
                                    else -> R.string.tools_toggles_write_settings_needed
                                },
                            ),
                        )
                    }
                }

                SectionHeader(stringResource(R.string.tools_toggles_section_developer))
                if (!snapshot.developerOptionsEnabled) {
                    StatusBanner(
                        level = StatusLevel.INFO,
                        title = stringResource(R.string.tools_toggles_dev_off_title),
                        message = stringResource(R.string.tools_toggles_dev_off_message),
                        actionLabel = stringResource(DsR.string.ds_open_android_settings),
                        onAction = onOpenDeveloperOptions,
                    )
                }
                val devFallback = stringResource(R.string.tools_toggles_dev_fallback)
                val direct = stringResource(R.string.tools_toggles_direct)
                ListGroup {
                    item { shape ->
                        val usb = state.display(ToggleKey.USB_DEBUGGING) ?: ToggleDisplay(snapshot.usbDebugging, false)
                        SwitchRow(
                            title = stringResource(R.string.tools_toggles_usb_debugging),
                            checked = usb.checked,
                            onCheckedChange = onUsbDebuggingChange,
                            shape = shape,
                            icon = Icons.Rounded.Usb,
                            enabled = ToggleKey.USB_DEBUGGING !in state.busy,
                            supportingText = when {
                                usb.transitioning -> stringResource(transitionRes(usb.checked))
                                state.directSecure -> direct
                                else -> devFallback
                            },
                        )
                    }
                    item { shape ->
                        val wireless = state.display(ToggleKey.WIRELESS_DEBUGGING) ?: ToggleDisplay(snapshot.wirelessDebugging, false)
                        SwitchRow(
                            title = stringResource(R.string.tools_toggles_wireless_debugging),
                            checked = wireless.checked,
                            onCheckedChange = onWirelessDebuggingChange,
                            shape = shape,
                            icon = Icons.Rounded.Wifi,
                            enabled = ToggleKey.WIRELESS_DEBUGGING !in state.busy,
                            supportingText = when {
                                wireless.transitioning -> stringResource(transitionRes(wireless.checked))
                                state.directSecure -> direct
                                else -> devFallback
                            },
                        )
                    }
                    item { shape ->
                        ActionRow(
                            title = stringResource(R.string.tools_toggles_wireless_info),
                            shape = shape,
                            icon = Icons.Rounded.DeveloperMode,
                            supportingText = wirelessInfo(state),
                            onClick = onOpenWirelessDebugging,
                        )
                    }
                }

                SectionHeader(stringResource(R.string.tools_dns_section))
                DnsSection(state, onApplyDns, onOpenPrivateDnsSettings, onShowAddDns, onDeleteDns)
            }
        }
    }

    if (state.showAddDnsDialog) AddDnsDialog(onAdd = onAddDns, onDismiss = onDismissAddDns)

    state.pendingConfirm?.let { pending ->
        ConfirmationDialog(
            title = stringResource(
                if (pending == PendingConfirm.USB_DEBUGGING_OFF) R.string.tools_toggles_confirm_usb_title else R.string.tools_toggles_confirm_wireless_title,
            ),
            message = stringResource(R.string.tools_toggles_confirm_shizuku_message),
            confirmLabel = stringResource(R.string.tools_toggles_confirm_turn_off),
            onConfirm = onConfirmPending,
            onDismiss = onDismissPending,
            destructive = true,
            icon = Icons.Rounded.Warning,
        )
    }
}

private fun transitionRes(target: Boolean): Int =
    if (target) R.string.tools_toggles_turning_on else R.string.tools_toggles_turning_off

@Composable
private fun AccessBanner(state: SystemTogglesUiState, revokeCommand: String) {
    val snapshot = state.snapshot
    when {
        snapshot?.writeSecureSettings == true && snapshot.selfGranted -> Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBanner(
                level = StatusLevel.SUCCESS,
                title = stringResource(R.string.tools_toggles_access_direct_title),
                message = stringResource(R.string.tools_toggles_access_self_granted),
                modifier = Modifier.weight(1f),
            )
            HelpButton(
                title = stringResource(R.string.tools_toggles_self_grant_help_title),
                text = stringResource(R.string.tools_toggles_self_grant_help_text, revokeCommand),
            )
        }
        snapshot?.writeSecureSettings == true -> StatusBanner(
            level = StatusLevel.SUCCESS,
            title = stringResource(R.string.tools_toggles_access_direct_title),
            message = stringResource(R.string.tools_toggles_access_direct_wss),
        )
        state.shizukuReady -> StatusBanner(
            level = StatusLevel.SUCCESS,
            title = stringResource(R.string.tools_toggles_access_direct_title),
            message = stringResource(R.string.tools_toggles_access_shizuku_grant),
        )
        else -> StatusBanner(
            level = StatusLevel.INFO,
            title = stringResource(R.string.tools_toggles_access_settings_title),
            message = stringResource(R.string.tools_toggles_access_settings_message),
        )
    }
}

@Composable
private fun wirelessInfo(state: SystemTogglesUiState): String {
    val snapshot = state.snapshot ?: return ""
    if (!snapshot.wirelessDebugging) return stringResource(R.string.tools_toggles_wireless_off)
    val ip = snapshot.wifiIpAddress ?: return stringResource(R.string.tools_toggles_wireless_no_wifi)
    val address = state.wirelessPort?.let { stringResource(R.string.tools_toggles_wireless_address, ip, it) }
        ?: stringResource(R.string.tools_toggles_wireless_ip_only, ip)
    return stringResource(R.string.tools_toggles_wireless_pairing, address)
}

@Composable
private fun DnsSection(
    state: SystemTogglesUiState,
    onApplyDns: (DnsProfile) -> Unit,
    onOpenPrivateDnsSettings: () -> Unit,
    onShowAddDns: () -> Unit,
    onDeleteDns: (DnsProfile) -> Unit,
) {
    val snapshot = state.snapshot ?: return
    val current = when (snapshot.dnsMode) {
        PrivateDnsMode.OFF -> stringResource(R.string.tools_dns_off)
        PrivateDnsMode.OPPORTUNISTIC -> stringResource(R.string.tools_dns_automatic)
        PrivateDnsMode.HOSTNAME -> snapshot.dnsHostname
    }
    Text(stringResource(R.string.tools_dns_current, current), style = MaterialTheme.typography.bodyMedium)
    Text(
        stringResource(if (state.directSecure) R.string.tools_dns_direct else R.string.tools_dns_fallback),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val activeId = state.activeProfileId
    val enabled = ToggleKey.PRIVATE_DNS !in state.busy
    ListGroup {
        (state.presetProfiles + state.customProfiles).forEach { profile ->
            item { shape ->
                RadioRow(
                    title = profile.displayName(),
                    selected = profile.id == activeId,
                    onSelect = { if (enabled) onApplyDns(profile) },
                    shape = shape,
                    supportingText = profile.hostname.takeIf { profile.mode == PrivateDnsMode.HOSTNAME },
                )
            }
        }
    }
    if (state.customProfiles.isNotEmpty()) {
        Text(stringResource(R.string.tools_dns_saved), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = AbleSpacing.sm))
        ListGroup {
            state.customProfiles.forEach { profile ->
                item { shape ->
                    ActionRow(
                        title = profile.name,
                        shape = shape,
                        icon = Icons.Rounded.Dns,
                        supportingText = profile.hostname,
                        showChevron = false,
                        trailing = {
                            AbleIconButton(onClick = { onDeleteDns(profile) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.tools_dns_delete, profile.name))
                            }
                        },
                    )
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md), modifier = Modifier.padding(top = AbleSpacing.sm)) {
        AbleOutlinedButton(onClick = onShowAddDns, modifier = Modifier.focusRing()) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.tools_dns_add), modifier = Modifier.padding(start = AbleSpacing.sm))
        }
        AbleTextButton(onClick = onOpenPrivateDnsSettings) { Text(stringResource(DsR.string.ds_open_android_settings)) }
    }
}

@Composable
private fun DnsProfile.displayName(): String = when (id) {
    DnsProfile.Off.id -> stringResource(R.string.tools_dns_off)
    DnsProfile.Opportunistic.id -> stringResource(R.string.tools_dns_automatic)
    DnsProfile.Cloudflare.id -> stringResource(R.string.tools_dns_cloudflare)
    DnsProfile.AdGuard.id -> stringResource(R.string.tools_dns_adguard)
    DnsProfile.Google.id -> stringResource(R.string.tools_dns_google)
    DnsProfile.Quad9.id -> stringResource(R.string.tools_dns_quad9)
    else -> name
}

@Composable
private fun AddDnsDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var hostname by rememberSaveable { mutableStateOf("") }
    val valid = isValidDnsHostname(hostname.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
        title = { Text(stringResource(R.string.tools_dns_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.tools_dns_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hostname,
                    onValueChange = { hostname = it.take(253).trim() },
                    label = { Text(stringResource(R.string.tools_dns_hostname)) },
                    singleLine = true,
                    isError = hostname.isNotEmpty() && !valid,
                    supportingText = { Text(stringResource(R.string.tools_dns_hostname_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            AbleTextButton(onClick = { onAdd(name, hostname) }, enabled = valid) { Text(stringResource(DsR.string.ds_save)) }
        },
        dismissButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(DsR.string.ds_cancel)) } },
    )
}
