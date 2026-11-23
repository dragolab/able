package com.dragolabs.able

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dragolabs.able.core.data.ableContainer
import com.dragolabs.able.core.designsystem.theme.AbleTheme
import com.dragolabs.able.core.model.ThemeMode
import com.dragolabs.able.feature.settings.R as SettingsR
import com.dragolabs.able.feature.settings.update.AppUpdater
import com.dragolabs.able.navigation.AbleApp
import androidx.compose.runtime.rememberCoroutineScope
import com.dragolabs.able.whatsnew.shouldShowWhatsNew
import com.dragolabs.able.whatsnew.WhatsNewDialog
import com.dragolabs.able.navigation.EXTRA_ROUTE
import com.dragolabs.able.navigation.SettingsRoute
import com.dragolabs.able.navigation.routeFromDeepLink
import com.dragolabs.able.widget.BatteryWidget
import com.dragolabs.able.widget.CustomActionsWidget
import com.dragolabs.able.widget.QuickActionsWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val RELEASES_URL = "https://github.com/dragolab/able/releases"

class MainActivity : ComponentActivity() {

    private val pendingDeepLink = mutableStateOf<Any?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = ableContainer

        // Remote-first layout when the device is a TV or has no touchscreen (NFR-TV-001).
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        // Compose moves focus to the first focusable element when the window is not in touch mode.
        // That is wanted for D-pad/TV, but on touch devices it focused search fields and opened the
        // keyboard after any hardware key event, hiding the navigation bar.
        @OptIn(ExperimentalComposeUiApi::class)
        ComposeUiFlags.isInitialFocusOnFocusableAvailable = isTv

        if (savedInstanceState == null) {
            pendingDeepLink.value = routeFromDeepLink(intent?.getStringExtra(EXTRA_ROUTE))
        }

        val shellPreferences = container.preferences.userPreferencesFlow.map {
            ShellPreferences(
                themeMode = it.themeMode,
                onboardingCompleted = it.onboardingCompleted,
                tourCompleted = it.tourCompleted,
                dynamicColor = it.useDynamicColor,
                haptics = it.hapticsEnabled,
                hapticsIntensity = it.hapticsIntensity,
                whatsNewSeenVersionCode = it.whatsNewSeenVersionCode,
            )
        }

        setContent {
            val prefs by shellPreferences.collectAsStateWithLifecycle(initialValue = null)
            val loaded = prefs
            AbleTheme(
                themeMode = loaded?.themeMode ?: ThemeMode.SYSTEM,
                dynamicColor = loaded?.dynamicColor ?: true,
                hapticsEnabled = loaded?.haptics ?: true,
                hapticsIntensity = loaded?.hapticsIntensity ?: com.dragolabs.able.core.model.HapticsIntensity.NORMAL,
            ) {
                if (loaded == null) {
                    // Preferences load in a few milliseconds; avoid flashing onboarding or Home.
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
                } else {
                    val deepLink by pendingDeepLink
                    val updateSnackbar = remember { SnackbarHostState() }
                    Box(Modifier.fillMaxSize()) {
                        AbleApp(
                            isTv = isTv,
                            showOnboarding = !loaded.onboardingCompleted && deepLink == null,
                            showTour = loaded.onboardingCompleted && !loaded.tourCompleted && deepLink == null,
                            deepLink = deepLink,
                            onDeepLinkConsumed = { pendingDeepLink.value = null },
                        )
                        // UPD-001: non-intrusive, one-time notice after the app-start check.
                        SnackbarHost(
                            updateSnackbar,
                            Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars),
                        )
                    }
                    // UPD-002: after an update, show once what changed. A fresh install (onboarding not
                    // done) only records the version, so the first "What's new" comes with the next update.
                    val currentVersionCode = BuildConfig.VERSION_CODE
                    if (!loaded.onboardingCompleted && loaded.whatsNewSeenVersionCode < currentVersionCode) {
                        LaunchedEffect(currentVersionCode) { container.preferences.setWhatsNewSeen(currentVersionCode) }
                    }
                    if (deepLink == null && shouldShowWhatsNew(loaded.onboardingCompleted, loaded.whatsNewSeenVersionCode, currentVersionCode)) {
                        val scope = rememberCoroutineScope()
                        val markSeen: () -> Unit = { scope.launch { container.preferences.setWhatsNewSeen(currentVersionCode) } }
                        WhatsNewDialog(
                            versionName = BuildConfig.VERSION_NAME.removeSuffix("-beta").let { "$it beta" },
                            onDismiss = markSeen,
                            onOpenReleaseNotes = {
                                markSeen()
                                runCatching {
                                    startActivity(
                                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(RELEASES_URL))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                        )
                    }
                    if (savedInstanceState == null) {
                        LaunchedEffect(Unit) {
                            val updater = AppUpdater.get(container)
                            val update = updater.autoCheckIfDue() ?: return@LaunchedEffect
                            updater.markNotified(update)
                            val result = updateSnackbar.showSnackbar(
                                message = getString(SettingsR.string.settings_updates_snackbar, update.version.toString()),
                                actionLabel = getString(SettingsR.string.settings_updates_snackbar_action),
                                withDismissAction = true,
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) pendingDeepLink.value = SettingsRoute
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFromDeepLink(intent.getStringExtra(EXTRA_ROUTE))?.let { pendingDeepLink.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Permission grants are not broadcast; re-read access state whenever Able returns (SET-002).
        ableContainer.capabilityStatus.refresh()
    }

    override fun onStop() {
        super.onStop()
        // Widgets are summaries; refresh them when the user leaves Able so profile/battery changes show up.
        val context = applicationContext
        ableContainer.applicationScope.launch {
            runCatching {
                BatteryWidget().updateAll(context)
                QuickActionsWidget().updateAll(context)
                CustomActionsWidget().updateAll(context)
            }
        }
    }
}

private data class ShellPreferences(
    val themeMode: ThemeMode,
    val onboardingCompleted: Boolean,
    val tourCompleted: Boolean,
    val dynamicColor: Boolean,
    val haptics: Boolean,
    val hapticsIntensity: com.dragolabs.able.core.model.HapticsIntensity,
    val whatsNewSeenVersionCode: Int,
)
