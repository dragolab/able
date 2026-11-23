package com.dragolabs.able.feature.battery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.dragolabs.able.core.data.battery.ActiveProfile
import com.dragolabs.able.core.designsystem.component.AbleButton
import com.dragolabs.able.core.designsystem.component.AbleButtonFlow
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleFilledTonalButton
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleSwitch
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.ExpressiveIcon
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.MetricGrid
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusBanner
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.BatteryState
import com.dragolabs.able.core.model.BatteryStatus
import com.dragolabs.able.core.model.PlugType
import com.dragolabs.able.feature.battery.BatteryEvent
import com.dragolabs.able.feature.battery.BatteryUiState
import com.dragolabs.able.feature.battery.ProfilesUiState
import com.dragolabs.able.feature.battery.R
import com.dragolabs.able.feature.battery.SettingsTarget

/**
 * Status tab (owner decision 2026-11-22): what is active now → what can activate → manage.
 * Several profiles can be active together; the most restrictive app action wins.
 */
@Composable
internal fun OverviewTab(
    state: BatteryUiState,
    contentPadding: PaddingValues,
    onEvent: (BatteryEvent) -> Unit,
    scrollToTopRequests: Int = 0,
) {
    val gridState = rememberLazyStaggeredGridState()
    LaunchedEffect(scrollToTopRequests) {
        if (scrollToTopRequests > 0) gridState.animateScrollToItem(0)
    }
    val profiles = state.profiles
    BatteryCardGrid(contentPadding, state = gridState) {
        fullLine("hero") { BatteryHeroCard(state, onEvent) }
        if (!profiles.anyTriggerEnabled && !profiles.automationPaused) {
            // ADR-005: one message only — nothing starts on its own.
            fullLine("triggers-off") {
                StatusBanner(
                    level = StatusLevel.INFO,
                    title = stringResource(R.string.battery_triggers_off_title),
                    message = stringResource(R.string.battery_triggers_off_body),
                )
            }
        }
        if (profiles.active.isNotEmpty()) {
            fullLine("applies-now") { AppliesNowCard(profiles) }
        }
        fullLine("profiles-header") { ProfilesHeader(onEvent) }
        profiles.profiles.forEach { profile ->
            card("profile-${profile.id}") { ProfileCard(profile, profiles.activeFor(profile.id), onEvent) }
        }
        fullLine("automation") { AutomationCard(profiles, onEvent) }
    }
}

@Composable
internal fun stateLabel(status: BatteryStatus): String = stringResource(
    when (status.state) {
        BatteryState.CHARGING -> R.string.battery_state_charging
        BatteryState.FULL -> R.string.battery_state_full
        BatteryState.DISCHARGING -> R.string.battery_state_discharging
        BatteryState.NOT_CHARGING -> R.string.battery_state_not_charging
        BatteryState.UNKNOWN -> R.string.battery_state_unknown
    },
)

@Composable
private fun plugLabel(plug: PlugType): String? = when (plug) {
    PlugType.NONE -> null
    PlugType.AC -> stringResource(R.string.battery_plug_ac)
    PlugType.USB -> stringResource(R.string.battery_plug_usb)
    PlugType.WIRELESS -> stringResource(R.string.battery_plug_wireless)
    PlugType.DOCK -> stringResource(R.string.battery_plug_dock)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatteryHeroCard(state: BatteryUiState, onEvent: (BatteryEvent) -> Unit) {
    val status = state.status
    val level = status.levelPercent
    val profiles = state.profiles
    val icon = when {
        status.isCharging -> Icons.Rounded.BatteryChargingFull
        level != null && level <= LOW_LEVEL -> Icons.Rounded.BatteryAlert
        else -> Icons.Rounded.BatteryFull
    }
    val levelText = level?.let { stringResource(R.string.battery_percent_value, it) } ?: unavailable()
    val stateText = listOfNotNull(stateLabel(status), plugLabel(status.plugType)).joinToString(" · ")
    AbleCard(
        style = AbleCardStyle.Hero,
        icon = icon,
        iconShape = MaterialShapes.Cookie9Sided,
        title = stringResource(R.string.battery_hero_title),
        subtitle = stateText,
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.lg)) {
            Text(
                levelText,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.semantics { contentDescription = "$levelText, $stateText" },
            )
            MetricGrid(
                metrics = listOf(
                    stringResource(R.string.battery_metric_temperature) to temperatureText(status.temperatureCelsius, state.temperatureUnit),
                ),
                modifier = Modifier.weight(1f).padding(bottom = AbleSpacing.sm),
                columns = 1,
            )
        }
        if (level != null) {
            LinearWavyProgressIndicator(progress = { level / 100f }, modifier = Modifier.fillMaxWidth())
        }
        Text(stringResource(R.string.battery_active_now), style = MaterialTheme.typography.titleMedium)
        when {
            profiles.active.isEmpty() -> Text(
                stringResource(R.string.battery_active_none),
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
            ) {
                profiles.active.forEach { active ->
                    ActiveChip(active, onClick = { onEvent(BatteryEvent.EditProfile(active.profile.id)) })
                }
            }
        }
        if (profiles.automationPaused) {
            StatusPill(text = stringResource(R.string.battery_automation_paused_pill), level = StatusLevel.WARNING)
        }
        Text(stringResource(R.string.battery_profile_actions_inactive), style = MaterialTheme.typography.bodySmall)
    }
}

/** Chip of an active profile: color, icon, name and why it is active. */
@Composable
private fun ActiveChip(active: ActiveProfile, onClick: () -> Unit) {
    val res = LocalContext.current.resources
    val tone = active.profile.tone()
    val name = active.profile.displayName()
    val reason = active.reasons.joinToString(" · ") { it.text(res) }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = tone.container,
        contentColor = tone.content,
        modifier = Modifier
            .focusRing(MaterialTheme.shapes.extraLarge)
            .semantics { contentDescription = res.getString(R.string.battery_active_chip_cd, name, reason) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AbleSpacing.md, vertical = AbleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
        ) {
            Icon(active.profile.iconVector(), contentDescription = null, modifier = Modifier.size(20.dp))
            Column {
                Text(name, style = MaterialTheme.typography.labelLarge)
                Text(reason, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Effective per-app actions of the active profiles and which profile caused each one. */
@Composable
private fun AppliesNowCard(profiles: ProfilesUiState) {
    val res = LocalContext.current.resources
    val actions = profiles.evaluation.appActions.values.sortedByDescending { it.level.restrictiveness }
    val names = profiles.profiles.associate { it.id to it.displayName(res) }
    val title = stringResource(R.string.battery_applies_title)
    AbleCard(
        title = title,
        subtitle = if (actions.isEmpty()) {
            stringResource(R.string.battery_applies_none)
        } else {
            stringResource(R.string.battery_applies_count, actions.size)
        },
        icon = Icons.Rounded.Tune,
        iconShape = MaterialShapes.Clover4Leaf,
        trailing = { HelpButton(title = title, text = stringResource(R.string.battery_help_most_restrictive)) },
    ) {
        if (actions.isNotEmpty()) {
            ListGroup {
                actions.take(MAX_APPLIES_SHOWN).forEach { action ->
                    item { shape ->
                        ActionRow(
                            title = profiles.appLabels[action.packageName] ?: action.packageName,
                            supportingText = stringResource(
                                R.string.battery_applies_row,
                                levelLabel(action.level),
                                action.sourceProfileIds.mapNotNull { names[it] }.joinToString(", "),
                            ),
                            shape = shape,
                        )
                    }
                }
            }
            if (actions.size > MAX_APPLIES_SHOWN) {
                SupportingText(stringResource(R.string.battery_protected_more, actions.size - MAX_APPLIES_SHOWN))
            }
        }
        if (profiles.batterySaverSuggestedBy.isNotEmpty()) {
            SupportingText(
                stringResource(
                    R.string.battery_saver_suggested,
                    profiles.batterySaverSuggestedBy.joinToString(", ") { it.displayName(res) },
                ),
            )
        }
    }
}

@Composable
private fun ProfilesHeader(onEvent: (BatteryEvent) -> Unit) {
    val title = stringResource(R.string.battery_profiles_title)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = AbleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(title, Modifier.weight(1f))
        HelpButton(title = title, text = stringResource(R.string.battery_help_profiles))
        AbleFilledTonalButton(
            onClick = { onEvent(BatteryEvent.NewProfile) },
            modifier = Modifier.focusRing(),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.battery_profile_new), modifier = Modifier.padding(start = AbleSpacing.xs), maxLines = 1)
        }
    }
}

@Composable
private fun ProfileCard(profile: BatteryProfile, active: ActiveProfile?, onEvent: (BatteryEvent) -> Unit) {
    val res = LocalContext.current.resources
    val tone = profile.tone()
    val name = profile.displayName()
    val statusText = when {
        !profile.enabled -> stringResource(R.string.battery_profile_status_off)
        active != null -> stringResource(R.string.battery_profile_status_active, active.reasons.joinToString(" · ") { it.text(res) })
        profile.hasEnabledTrigger -> stringResource(R.string.battery_profile_status_ready)
        else -> stringResource(R.string.battery_profile_status_idle)
    }
    val statusLevel = when {
        active != null -> StatusLevel.SUCCESS
        profile.enabled && profile.hasEnabledTrigger -> StatusLevel.INFO
        else -> StatusLevel.WARNING
    }
    val rules = profile.restrictiveRules.size
    AbleCard(
        onClick = { onEvent(BatteryEvent.EditProfile(profile.id)) },
        status = { StatusPill(text = statusText, level = statusLevel) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md)) {
            ExpressiveIcon(
                icon = profile.iconVector(),
                shape = if (active != null) MaterialShapes.Cookie9Sided else MaterialShapes.Circle,
                containerColor = tone.container,
                contentColor = tone.content,
            )
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (profile.enabled) profile.triggerSummary(res) else stringResource(R.string.battery_profile_disabled_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val switchState = stringResource(if (profile.enabled) R.string.battery_profile_status_on else R.string.battery_profile_status_off)
            val switchLabel = stringResource(R.string.battery_profile_enabled_cd, name)
            AbleSwitch(
                checked = profile.enabled,
                onCheckedChange = { onEvent(BatteryEvent.SetProfileEnabled(profile.id, it)) },
                modifier = Modifier
                    .focusRing()
                    .semantics {
                        contentDescription = switchLabel
                        stateDescription = switchState
                    },
            )
        }
        Text(
            if (rules == 0) stringResource(R.string.battery_profile_rules_none) else res.getQuantityString(R.plurals.battery_profile_rules_count, rules, rules),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AbleButtonFlow {
            if (profile.enabled) {
                if (profile.manualActive) {
                    AbleOutlinedButton(
                        onClick = { onEvent(BatteryEvent.SetManualActive(profile.id, false)) },
                        modifier = Modifier.focusRing(),
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.battery_profile_stop), modifier = Modifier.padding(start = AbleSpacing.xs), maxLines = 1)
                    }
                } else {
                    AbleButton(
                        onClick = { onEvent(BatteryEvent.SetManualActive(profile.id, true)) },
                        modifier = Modifier.focusRing(),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.battery_profile_activate), modifier = Modifier.padding(start = AbleSpacing.xs), maxLines = 1)
                    }
                }
            }
            AbleTextButton(onClick = { onEvent(BatteryEvent.EditProfile(profile.id)) }, modifier = Modifier.focusRing()) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.battery_profile_edit), modifier = Modifier.padding(start = AbleSpacing.xs), maxLines = 1)
            }
        }
    }
}

@Composable
private fun AutomationCard(profiles: ProfilesUiState, onEvent: (BatteryEvent) -> Unit) {
    val pauseTitle = stringResource(R.string.battery_automation_pause)
    AbleCard(
        title = stringResource(R.string.battery_automation_title),
        icon = Icons.Rounded.Shield,
        iconShape = MaterialShapes.Clover4Leaf,
        style = AbleCardStyle.Outlined,
        trailing = { HelpButton(title = pauseTitle, text = stringResource(R.string.battery_help_pause_triggers)) },
    ) {
        ListGroup {
            item { shape ->
                SwitchRow(
                    title = pauseTitle,
                    supportingText = stringResource(R.string.battery_automation_pause_body),
                    checked = profiles.automationPaused,
                    onCheckedChange = { onEvent(BatteryEvent.SetAutomationPaused(it)) },
                    shape = shape,
                    icon = Icons.Rounded.PauseCircle,
                )
            }
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.battery_protected_title),
                    supportingText = if (profiles.protectedApps.isEmpty()) {
                        stringResource(R.string.battery_protected_empty)
                    } else {
                        stringResource(R.string.battery_protected_count, profiles.protectedApps.size)
                    },
                    icon = Icons.Rounded.Shield,
                    shape = shape,
                    onClick = { onEvent(BatteryEvent.OpenApps) },
                )
            }
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.battery_saver_title),
                    supportingText = stringResource(R.string.battery_saver_body),
                    icon = Icons.Rounded.BatterySaver,
                    shape = shape,
                    onClick = { onEvent(BatteryEvent.OpenSettings(SettingsTarget.BatterySaver)) },
                )
            }
        }
    }
}

private const val LOW_LEVEL = 15
private const val MAX_APPLIES_SHOWN = 6
