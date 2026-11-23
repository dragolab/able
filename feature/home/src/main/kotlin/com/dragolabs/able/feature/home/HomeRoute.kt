package com.dragolabs.able.feature.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.ableViewModelFactory
import com.dragolabs.able.core.data.shortcut.ShortcutIntentFactory
import com.dragolabs.able.core.data.shortcut.toolForAbleRoute
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.model.ToolId

/**
 * Home dashboard entry point (HOME-001..003, ADR-016). Stateful: obtains the ViewModel and
 * forwards state and intents to the stateless [HomeScreen].
 */
@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenTool: (ToolId) -> Unit,
    modifier: Modifier = Modifier,
    /** Opens a tool from a Home card with its shared-element origin; defaults to [onOpenTool]. */
    onOpenToolFrom: (ToolId, String) -> Unit = { tool, _ -> onOpenTool(tool) },
) {
    val viewModel: HomeViewModel = viewModel(factory = ableViewModelFactory { HomeViewModel(it) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val context = LocalContext.current
    val haptics = rememberAbleHaptics()
    val shortcutIntents = remember(context) { ShortcutIntentFactory(context.applicationContext) }
    LaunchedEffect(state.message) {
        val current = state.message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        if (current == HomeMessage.LAYOUT_RESET) haptics.confirm() else haptics.reject()
        snackbarHostState.showSnackbar(context.getString(current.textRes()))
    }

    HomeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = HomeActions(
            onOpenSettings = onOpenSettings,
            onOpenBattery = onOpenBattery,
            onOpenApps = onOpenApps,
            onOpenTools = onOpenTools,
            onOpenTool = onOpenTool,
            onRequestShizukuPermission = viewModel::requestShizukuPermission,
            onSetCardVisible = viewModel::setCardVisible,
            onMoveCard = viewModel::moveCard,
            onResetLayout = viewModel::resetLayout,
            onLaunchShortcut = { shortcut ->
                when (val result = shortcutIntents.launchFromHome(shortcut)) {
                    ShortcutLaunchResult.Launched -> Unit
                    is ShortcutLaunchResult.OpenRoute -> {
                        val tool = toolForAbleRoute(result.route)
                        when {
                            tool != null -> onOpenTool(tool)
                            result.route == "battery" -> onOpenBattery()
                            result.route == "apps" -> onOpenApps()
                            result.route == "tools" -> onOpenTools()
                            else -> Unit // "home": already here.
                        }
                    }
                    is ShortcutLaunchResult.Failed -> viewModel.reportShortcutFailure(result.reason)
                }
            },
            onOpenShortcutMaker = { onOpenTool(ToolId.SHORTCUT_MAKER) },
            onOpenToolFrom = onOpenToolFrom,
        ),
        modifier = modifier,
    )
}

private fun HomeMessage.textRes(): Int = when (this) {
    HomeMessage.LAYOUT_RESET -> R.string.home_layout_reset_done
    HomeMessage.SHIZUKU_REQUEST_FAILED -> R.string.home_shizuku_request_failed
    HomeMessage.SHORTCUT_FAILED -> R.string.home_shortcut_failed
    HomeMessage.SHORTCUT_UNSUPPORTED -> R.string.home_shortcut_unsupported
    HomeMessage.SHORTCUT_APP_MISSING -> R.string.home_shortcut_app_missing
    HomeMessage.SHORTCUT_BLOCKED -> R.string.home_shortcut_blocked
}
