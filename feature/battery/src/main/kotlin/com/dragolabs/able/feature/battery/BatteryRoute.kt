package com.dragolabs.able.feature.battery

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.designsystem.component.OnTopLevelReselect
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.feature.battery.editor.EditorResult
import com.dragolabs.able.feature.battery.editor.ProfileEditorRoute
import com.dragolabs.able.feature.battery.ui.BatteryScreen
import java.util.UUID

/**
 * Battery destination (BAT-*, MON-*, BASE-*). Stateful entry point: owns the ViewModels, Android
 * permission launchers and settings intents, and forwards everything else to [BatteryScreen].
 */
@Composable
fun BatteryRoute(
    onOpenSettings: () -> Unit,
    onOpenApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BatteryViewModel = viewModel(factory = BatteryViewModel.Factory)
    val monitorViewModel: BatteryMonitorViewModel = viewModel(factory = BatteryMonitorViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val monitor by monitorViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(BatteryTab.OVERVIEW) }
    var pendingAlert by rememberSaveable { mutableStateOf<AlertKind?>(null) }
    // Profile editor overlay: a fresh token per opening scopes a fresh editor ViewModel.
    var editorToken by rememberSaveable { mutableStateOf<String?>(null) }
    var editorProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var scrollToTop by rememberSaveable { mutableIntStateOf(0) }

    OnTopLevelReselect("battery") {
        editorToken = null
        tab = BatteryTab.OVERVIEW
        scrollToTop++
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshAccess()
        onPauseOrDispose { }
    }

    val monitorPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) monitorViewModel.startMonitor() else monitorViewModel.onNotificationPermissionDenied()
    }
    val alertPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = pendingAlert ?: return@rememberLauncherForActivityResult
        pendingAlert = null
        if (granted) monitorViewModel.enableAlert(kind) else monitorViewModel.onAlertPermissionDenied(kind)
    }

    fun hasNotificationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // One snackbar host for both ViewModels.
    val message = state.message ?: monitor.message
    val haptics = rememberAbleHaptics()
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        if (state.message != null) viewModel.consumeMessage() else monitorViewModel.consumeMessage()
        // UX-003: confirm when Android/Able accepted the change, reject when it failed or was refused.
        if (current.isFailure()) haptics.reject() else haptics.confirm()
        snackbarHostState.showSnackbar(context.getString(current.textRes()))
    }

    val token = editorToken
    if (token != null) {
        ProfileEditorRoute(
            profileId = editorProfileId,
            sessionKey = token,
            onClose = { result ->
                editorToken = null
                result?.messageOrNull()?.let(viewModel::showMessage)
            },
            modifier = modifier,
        )
        return
    }

    BatteryScreen(
        state = state,
        monitor = monitor,
        selectedTab = tab,
        onSelectTab = { tab = it },
        scrollToTopRequests = scrollToTop,
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        onEvent = { event ->
            when (event) {
                is BatteryEvent.SetProfileEnabled -> viewModel.setProfileEnabled(event.profileId, event.enabled)
                is BatteryEvent.SetManualActive -> viewModel.setManualActive(event.profileId, event.active)
                is BatteryEvent.EditProfile -> {
                    editorProfileId = event.profileId
                    editorToken = UUID.randomUUID().toString()
                }
                BatteryEvent.NewProfile -> {
                    editorProfileId = null
                    editorToken = UUID.randomUUID().toString()
                }
                is BatteryEvent.SetAutomationPaused -> viewModel.setAutomationPaused(event.paused)
                BatteryEvent.OpenApps -> onOpenApps()
                is BatteryEvent.OpenSettings ->
                    if (!context.openSettings(event.target)) viewModel.showMessage(BatteryMessage.SETTINGS_OPEN_FAILED)

                BatteryEvent.StartMonitor ->
                    if (hasNotificationPermission()) monitorViewModel.startMonitor()
                    else monitorPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                BatteryEvent.StopMonitor -> monitorViewModel.stopMonitor()
                BatteryEvent.ResetStatistics -> monitorViewModel.resetStatistics()
                is BatteryEvent.SetThermalThreshold -> monitorViewModel.setThermalThreshold(event.celsius)
                is BatteryEvent.SetResetOnPlug -> monitorViewModel.setResetOnPlug(event.enabled)
                is BatteryEvent.SetResetOnBoot -> monitorViewModel.setResetOnBoot(event.enabled)
                is BatteryEvent.SetResetThreshold -> monitorViewModel.setResetThreshold(event.percent)
                is BatteryEvent.SetRetention -> monitorViewModel.setRetention(event.days)
                BatteryEvent.RequestDeleteHistory -> monitorViewModel.requestDeleteHistory()
                BatteryEvent.ConfirmDeleteHistory -> monitorViewModel.confirmDeleteHistory()
                BatteryEvent.DismissDeleteHistory -> monitorViewModel.dismissDeleteHistory()
                is BatteryEvent.SetTemperatureUnit -> monitorViewModel.setTemperatureUnit(event.unit)
                is BatteryEvent.SetNotificationLayout -> monitorViewModel.setNotificationLayout(event.layout)
                is BatteryEvent.SetNotificationShowPower -> monitorViewModel.setNotificationShowPower(event.show)
                is BatteryEvent.EnableAlert ->
                    if (hasNotificationPermission()) {
                        monitorViewModel.enableAlert(event.kind)
                    } else {
                        pendingAlert = event.kind
                        alertPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                is BatteryEvent.DisableAlert -> monitorViewModel.disableAlert(event.kind)
                is BatteryEvent.SetIdleAlertThreshold -> monitorViewModel.setIdleAlertThreshold(event.percentPerHour)
                is BatteryEvent.SetBaselineMinDifference -> monitorViewModel.setBaselineMinDifference(event.percentPerHour)
                BatteryEvent.DismissBaselineOffer -> monitorViewModel.dismissBaselineOffer()
                is BatteryEvent.SetSessionFilter -> monitorViewModel.setFilter(event.filter)
                is BatteryEvent.EditSession -> monitorViewModel.editSession(event.sessionId)
                BatteryEvent.DismissNoteEditor -> monitorViewModel.dismissNoteEditor()
                is BatteryEvent.SaveNote -> monitorViewModel.saveNote(event.sessionId, event.text, event.tags)

                BatteryEvent.ReadAdvancedHealth -> viewModel.readAdvancedHealth()
                BatteryEvent.ConnectShizuku -> if (!viewModel.connectShizuku()) viewModel.openShizukuApp(context)

                is BatteryEvent.ToggleReviewed -> viewModel.toggleReviewed(event.item)
                BatteryEvent.RefreshUsage -> viewModel.refreshUsage()
            }
        },
    )
}

private fun EditorResult.messageOrNull(): BatteryMessage? = when (this) {
    EditorResult.SAVED -> BatteryMessage.PROFILE_SAVED
    EditorResult.DELETED -> BatteryMessage.PROFILE_DELETED
    EditorResult.DUPLICATED -> BatteryMessage.PROFILE_DUPLICATED
    EditorResult.RESET -> BatteryMessage.PROFILE_RESET
    EditorResult.NOT_FOUND -> BatteryMessage.PROFILE_NOT_FOUND
}

private fun BatteryMessage.isFailure(): Boolean = when (this) {
    BatteryMessage.PROFILE_NOT_FOUND,
    BatteryMessage.SETTINGS_OPEN_FAILED,
    BatteryMessage.MONITOR_START_FAILED,
    BatteryMessage.NOTIFICATION_DENIED,
    BatteryMessage.ALERT_PERMISSION_DENIED,
    BatteryMessage.SHIZUKU_NOT_INSTALLED,
    -> true
    BatteryMessage.PROFILE_SAVED,
    BatteryMessage.PROFILE_DELETED,
    BatteryMessage.PROFILE_DUPLICATED,
    BatteryMessage.PROFILE_RESET,
    BatteryMessage.HISTORY_DELETED,
    BatteryMessage.NOTE_SAVED,
    BatteryMessage.STATS_RESET,
    -> false
}

private fun BatteryMessage.textRes(): Int = when (this) {
    BatteryMessage.PROFILE_SAVED -> R.string.battery_msg_profile_saved
    BatteryMessage.PROFILE_DELETED -> R.string.battery_msg_profile_deleted
    BatteryMessage.PROFILE_DUPLICATED -> R.string.battery_msg_profile_duplicated
    BatteryMessage.PROFILE_RESET -> R.string.battery_msg_profile_reset
    BatteryMessage.PROFILE_NOT_FOUND -> R.string.battery_msg_profile_not_found
    BatteryMessage.SETTINGS_OPEN_FAILED -> R.string.battery_msg_settings_failed
    BatteryMessage.MONITOR_START_FAILED -> R.string.battery_msg_monitor_start_failed
    BatteryMessage.NOTIFICATION_DENIED -> R.string.battery_msg_notification_denied
    BatteryMessage.ALERT_PERMISSION_DENIED -> R.string.battery_msg_alert_permission_denied
    BatteryMessage.HISTORY_DELETED -> R.string.battery_msg_history_deleted
    BatteryMessage.NOTE_SAVED -> R.string.battery_msg_note_saved
    BatteryMessage.STATS_RESET -> R.string.battery_msg_stats_reset
    BatteryMessage.SHIZUKU_NOT_INSTALLED -> R.string.battery_msg_shizuku_missing
}

/** Opens an Android settings page, trying a fallback when the primary page is missing. */
private fun Context.openSettings(target: SettingsTarget): Boolean {
    val candidates: List<Intent> = when (target) {
        SettingsTarget.BatteryUsage -> listOf(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        SettingsTarget.BatterySaver -> listOf(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        SettingsTarget.Network -> listOf(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
        SettingsTarget.Hotspot -> listOf(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), Intent(Settings.ACTION_WIRELESS_SETTINGS))
        SettingsTarget.Display -> listOf(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        SettingsTarget.UsageAccess -> listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        SettingsTarget.AppNotifications -> listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
        is SettingsTarget.AppDetails -> listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", target.packageName, null)),
        )
    }
    return candidates.any { intent ->
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
