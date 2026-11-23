package com.dragolabs.able.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.capability.ShizukuConnectionState
import com.dragolabs.able.core.data.AccessSnapshot
import com.dragolabs.able.core.designsystem.component.AbleFilledTonalButton
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.AbleLogo
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.ExpandableRow
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.ReadableWidth
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.labelRes
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.component.statusLevel
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.ThemeMode

/** Optional accesses listed in the SET-002 checklist. */
enum class AccessKind {
    SHIZUKU,
    USAGE_ACCESS,
    ACCESSIBILITY,
    NOTIFICATIONS,
    WRITE_SETTINGS,
    WRITE_SECURE_SETTINGS,
    PRECISE_LOCATION,
}

/**
 * User intents from the stateless Settings screen. Nullable navigation callbacks hide their row
 * when the host does not provide a destination (never show a control without a route).
 */
class SettingsActions(
    val onThemeSelected: (ThemeMode) -> Unit,
    val onLanguageSelected: (String?) -> Unit,
    val onDynamicColorChanged: (Boolean) -> Unit,
    val onHapticsChanged: (Boolean) -> Unit,
    val onAccessAction: (AccessKind) -> Unit,
    val onTelemetryChanged: (Boolean) -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onRestorePrevious: () -> Unit,
    val onResetHomeLayout: () -> Unit,
    val onOpenWebsite: () -> Unit,
    val onSendEmail: () -> Unit,
    val onOpenGitHub: () -> Unit = {},
    val onReplayTour: (() -> Unit)? = null,
    val onOpenTilesWidgets: (() -> Unit)? = null,
    val onOpenLicenses: (() -> Unit)? = null,
)

private data class AccessGroup(val titleRes: Int, val kinds: List<AccessKind>)

private val accessGroups = listOf(
    AccessGroup(R.string.settings_access_group_battery_apps, listOf(AccessKind.SHIZUKU, AccessKind.USAGE_ACCESS)),
    AccessGroup(R.string.settings_access_group_screen_lock, listOf(AccessKind.ACCESSIBILITY)),
    AccessGroup(R.string.settings_access_group_monitor, listOf(AccessKind.NOTIFICATIONS)),
    AccessGroup(R.string.settings_access_group_system, listOf(AccessKind.WRITE_SETTINGS, AccessKind.WRITE_SECURE_SETTINGS)),
    AccessGroup(R.string.settings_access_group_gps, listOf(AccessKind.PRECISE_LOCATION)),
)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    AbleDetailScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        ReadableWidth {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = screenContentPadding(padding),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            ) {
                appearanceSection(state, actions)
                accessSection(state.access, actions)
                privacySection(state, actions)
                backupSection(state, actions)
                homeLayoutSection(actions)
                helpSection(actions)
                aboutSection(state, actions)
            }
        }
    }
}

private fun LazyListScope.header(textRes: Int, help: Pair<Int, Int>? = null) {
    item(key = "header_$textRes") {
        Row(
            Modifier.fillMaxWidth().padding(top = AbleSpacing.md, start = AbleSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(stringResource(textRes), Modifier.weight(1f).semantics { heading() })
            if (help != null) {
                HelpButton(title = stringResource(help.first), text = stringResource(help.second))
            }
        }
    }
}

// --- Appearance (SET-001, UX-003) -------------------------------------------------------------

private fun LazyListScope.appearanceSection(state: SettingsUiState, actions: SettingsActions) {
    header(R.string.settings_section_appearance)
    item(key = "theme") {
        Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            SubHeader(R.string.settings_theme_title)
            ListGroup {
                listOf(
                    ThemeMode.SYSTEM to R.string.settings_theme_system,
                    ThemeMode.LIGHT to R.string.settings_theme_light,
                    ThemeMode.DARK to R.string.settings_theme_dark,
                ).forEach { (mode, label) ->
                    item { shape ->
                        RadioRow(
                            title = stringResource(label),
                            selected = state.themeMode == mode,
                            onSelect = { actions.onThemeSelected(mode) },
                            shape = shape,
                        )
                    }
                }
            }
        }
    }
    item(key = "language") {
        Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            SubHeader(R.string.settings_language_title)
            ListGroup {
                listOf(
                    null to R.string.settings_language_system,
                    AppLanguageController.ITALIAN to R.string.settings_language_italian,
                    AppLanguageController.ENGLISH to R.string.settings_language_english,
                ).forEach { (tag, label) ->
                    item { shape ->
                        RadioRow(
                            title = stringResource(label),
                            selected = state.languageTag == tag,
                            onSelect = { actions.onLanguageSelected(tag) },
                            shape = shape,
                            supportingText = if (tag == null) stringResource(R.string.settings_language_system_supporting) else null,
                        )
                    }
                }
            }
        }
    }
    item(key = "appearance_switches") {
        ListGroup {
            item { shape ->
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color_title),
                    checked = state.useDynamicColor,
                    onCheckedChange = actions.onDynamicColorChanged,
                    shape = shape,
                    supportingText = stringResource(R.string.settings_dynamic_color_supporting),
                    icon = Icons.Rounded.Palette,
                )
            }
            item { shape ->
                SwitchRow(
                    title = stringResource(R.string.settings_haptics_title),
                    checked = state.hapticsEnabled,
                    onCheckedChange = actions.onHapticsChanged,
                    shape = shape,
                    supportingText = stringResource(R.string.settings_haptics_supporting),
                    icon = Icons.Rounded.TouchApp,
                )
            }
        }
    }
}

@Composable
private fun SubHeader(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = AbleSpacing.xs),
    )
}

// --- Access checklist (SET-002) -------------------------------------------------------------

private fun LazyListScope.accessSection(access: AccessSnapshot, actions: SettingsActions) {
    header(R.string.settings_section_access)
    item(key = "access_intro") {
        Text(
            stringResource(R.string.settings_access_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AbleSpacing.xs),
        )
    }
    accessGroups.forEach { group ->
        item(key = "access_${group.titleRes}") {
            Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                SubHeader(group.titleRes)
                ListGroup {
                    group.kinds.forEach { kind ->
                        item { shape -> AccessRow(kind, access, shape, onAction = { actions.onAccessAction(kind) }) }
                    }
                }
            }
        }
    }
}

private data class AccessPresentation(
    val icon: ImageVector,
    val titleRes: Int,
    val purposeRes: Int,
    val stateText: String,
    val level: StatusLevel,
    /** UX-002 help for advanced or risky accesses: (title, body). */
    val help: Pair<Int, Int>? = null,
)

@Composable
private fun accessPresentation(kind: AccessKind, access: AccessSnapshot): AccessPresentation {
    val granted = stringResource(R.string.settings_access_state_granted)
    val notGranted = stringResource(R.string.settings_access_state_not_granted)
    fun simple(icon: ImageVector, title: Int, purpose: Int, value: Boolean, missing: StatusLevel = StatusLevel.WARNING) =
        AccessPresentation(icon, title, purpose, if (value) granted else notGranted, if (value) StatusLevel.SUCCESS else missing)
    return when (kind) {
        AccessKind.SHIZUKU -> AccessPresentation(
            Icons.Rounded.AdminPanelSettings,
            R.string.settings_access_shizuku_title,
            R.string.settings_access_shizuku_purpose,
            stringResource(access.shizuku.labelRes()),
            access.shizuku.statusLevel(),
            help = R.string.settings_access_shizuku_title to R.string.settings_help_shizuku,
        )
        AccessKind.USAGE_ACCESS -> simple(Icons.Rounded.QueryStats, R.string.settings_access_usage_title, R.string.settings_access_usage_purpose, access.usageAccess)
        AccessKind.ACCESSIBILITY -> simple(Icons.Rounded.Accessibility, R.string.settings_access_accessibility_title, R.string.settings_access_accessibility_purpose, access.accessibilityEnabled)
        AccessKind.NOTIFICATIONS -> simple(Icons.Rounded.Notifications, R.string.settings_access_notifications_title, R.string.settings_access_notifications_purpose, access.notifications)
        AccessKind.WRITE_SETTINGS -> simple(Icons.Rounded.SettingsApplications, R.string.settings_access_write_settings_title, R.string.settings_access_write_settings_purpose, access.writeSettings)
        AccessKind.WRITE_SECURE_SETTINGS -> simple(
            Icons.Rounded.Terminal,
            R.string.settings_access_secure_title,
            R.string.settings_access_secure_purpose,
            access.writeSecureSettings,
            StatusLevel.INFO,
        ).copy(help = R.string.settings_access_secure_title to R.string.settings_help_secure)
        AccessKind.PRECISE_LOCATION -> simple(Icons.Rounded.LocationOn, R.string.settings_access_location_title, R.string.settings_access_location_purpose, access.fineLocation, StatusLevel.INFO)
    }
}

/** Compact row: title + status pill; purpose, action and help are revealed on expand. */
@Composable
private fun AccessRow(kind: AccessKind, access: AccessSnapshot, shape: Shape, onAction: () -> Unit) {
    val p = accessPresentation(kind, access)
    ExpandableRow(
        title = stringResource(p.titleRes),
        shape = shape,
        icon = p.icon,
        status = { StatusPill(p.stateText, p.level) },
    ) {
        Text(
            stringResource(p.purposeRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AbleFilledTonalButton(onClick = onAction, modifier = Modifier.focusRing(CircleShape)) {
                Text(stringResource(accessActionLabel(kind, access)))
            }
            Spacer(Modifier.weight(1f))
            p.help?.let { (title, body) -> HelpButton(title = stringResource(title), text = stringResource(body)) }
        }
    }
}

private fun accessActionLabel(kind: AccessKind, access: AccessSnapshot): Int = when (kind) {
    AccessKind.SHIZUKU -> when (access.shizuku) {
        ShizukuConnectionState.WAITING_FOR_PERMISSION -> R.string.settings_access_action_authorize_shizuku
        else -> R.string.settings_access_action_open_shizuku
    }
    AccessKind.NOTIFICATIONS -> if (access.notifications) R.string.settings_access_action_manage else R.string.settings_access_action_allow
    AccessKind.WRITE_SECURE_SETTINGS -> R.string.settings_access_action_adb
    AccessKind.USAGE_ACCESS, AccessKind.ACCESSIBILITY, AccessKind.WRITE_SETTINGS, AccessKind.PRECISE_LOCATION ->
        R.string.settings_access_action_manage
}

// --- Privacy (PRIV-001, ADR-015) ------------------------------------------------------------

private fun LazyListScope.privacySection(state: SettingsUiState, actions: SettingsActions) {
    header(R.string.settings_section_privacy, help = R.string.settings_telemetry_title to R.string.settings_help_crash_reports)
    item(key = "telemetry") {
        ListGroup {
            item { shape ->
                SwitchRow(
                    title = stringResource(R.string.settings_telemetry_title),
                    checked = state.telemetryOptIn,
                    onCheckedChange = actions.onTelemetryChanged,
                    shape = shape,
                    supportingText = stringResource(R.string.settings_telemetry_supporting),
                    icon = Icons.Rounded.BugReport,
                )
            }
            item { shape ->
                ExpandableRow(
                    title = stringResource(R.string.settings_telemetry_categories_title),
                    shape = shape,
                ) {
                    Text(
                        stringResource(R.string.settings_telemetry_categories_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// --- Backup & transfer (CFG-001..007) -------------------------------------------------------

private fun LazyListScope.backupSection(state: SettingsUiState, actions: SettingsActions) {
    header(R.string.settings_section_backup, help = R.string.settings_section_backup to R.string.settings_help_backup)
    item(key = "backup") {
        Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            if (state.exporting) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
            ListGroup {
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.settings_backup_export_title),
                        shape = shape,
                        supportingText = stringResource(R.string.settings_backup_export_supporting),
                        icon = Icons.Rounded.Upload,
                        enabled = !state.exporting,
                        onClick = actions.onExport,
                    )
                }
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.settings_backup_import_title),
                        shape = shape,
                        supportingText = stringResource(R.string.settings_backup_import_supporting),
                        icon = Icons.Rounded.Download,
                        enabled = !state.exporting,
                        onClick = actions.onImport,
                    )
                }
                if (state.rollbackAvailable) {
                    item { shape ->
                        ActionRow(
                            title = stringResource(R.string.settings_backup_restore_title),
                            shape = shape,
                            supportingText = stringResource(R.string.settings_backup_restore_supporting),
                            icon = Icons.Rounded.Restore,
                            onClick = actions.onRestorePrevious,
                        )
                    }
                }
            }
        }
    }
}

// --- Home layout ------------------------------------------------------------------------------

private fun LazyListScope.homeLayoutSection(actions: SettingsActions) {
    header(R.string.settings_section_home)
    item(key = "home_reset") {
        ListGroup {
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.settings_home_reset_title),
                    shape = shape,
                    supportingText = stringResource(R.string.settings_home_reset_supporting),
                    icon = Icons.Rounded.Dashboard,
                    onClick = actions.onResetHomeLayout,
                )
            }
        }
    }
}

// --- Help (UX-001) ----------------------------------------------------------------------------

private fun LazyListScope.helpSection(actions: SettingsActions) {
    val replay = actions.onReplayTour
    val tiles = actions.onOpenTilesWidgets
    if (replay == null && tiles == null) return
    header(R.string.settings_section_help)
    item(key = "help") {
        ListGroup {
            if (replay != null) {
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.settings_help_tour_title),
                        shape = shape,
                        supportingText = stringResource(R.string.settings_help_tour_supporting),
                        icon = Icons.Rounded.School,
                        onClick = replay,
                    )
                }
            }
            if (tiles != null) {
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.settings_tiles_widgets_title),
                        shape = shape,
                        supportingText = stringResource(R.string.settings_tiles_widgets_supporting),
                        icon = Icons.Rounded.Widgets,
                        onClick = tiles,
                    )
                }
            }
        }
    }
}

// --- About (ABOUT-001) ------------------------------------------------------------------------

private fun LazyListScope.aboutSection(state: SettingsUiState, actions: SettingsActions) {
    header(R.string.settings_section_about)
    item(key = "about_hero") {
        AbleCard(style = AbleCardStyle.Tonal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbleLogo(size = 56.dp, contentDescription = null)
                Spacer(Modifier.width(AbleSpacing.lg))
                Column {
                    Text(stringResource(R.string.settings_about_app_name), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(
                            R.string.settings_about_version_value,
                            state.appVersion.ifEmpty { stringResource(R.string.settings_about_version_unknown) },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.settings_about_made_by),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    item(key = "about") {
        ListGroup {
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.settings_about_website),
                    shape = shape,
                    supportingText = SettingsLinks.WEBSITE,
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = actions.onOpenWebsite,
                )
            }
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.settings_about_email),
                    shape = shape,
                    supportingText = SettingsLinks.EMAIL,
                    icon = Icons.Rounded.Email,
                    onClick = actions.onSendEmail,
                )
            }
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.settings_about_github),
                    shape = shape,
                    supportingText = SettingsLinks.GITHUB.removePrefix("https://"),
                    icon = Icons.Rounded.Code,
                    onClick = actions.onOpenGitHub,
                )
            }
            val licenses = actions.onOpenLicenses
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.settings_about_licenses),
                    shape = shape,
                    supportingText = stringResource(R.string.settings_about_licenses_body),
                    icon = Icons.Rounded.Description,
                    onClick = licenses,
                )
            }
        }
    }
}

object SettingsLinks {
    const val WEBSITE = "https://dragolabs.it"
    const val EMAIL = "info.dragolabs@gmail.com"

    /** ABOUT-001: public source repository (GPLv3), published 2026-11-22. */
    const val GITHUB = "https://github.com/dragolab/able"

    /** Uses the installed package name so debug variants with a suffix get a working command. */
    fun adbGrantCommand(packageName: String) = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
}
