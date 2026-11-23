package com.dragolabs.able.feature.battery.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragolabs.able.core.data.battery.ProfileIssue
import com.dragolabs.able.core.data.battery.ProfileIssueKind
import com.dragolabs.able.core.designsystem.R as DsR
import com.dragolabs.able.core.designsystem.component.AbleCard
import com.dragolabs.able.core.designsystem.component.AbleCardStyle
import com.dragolabs.able.core.designsystem.component.AbleDetailScaffold
import com.dragolabs.able.core.designsystem.component.AbleFilledTonalButton
import com.dragolabs.able.core.designsystem.component.AbleFilterChip
import com.dragolabs.able.core.designsystem.component.AbleHaptic
import com.dragolabs.able.core.designsystem.component.AbleOutlinedButton
import com.dragolabs.able.core.designsystem.component.AbleButtonFlow
import com.dragolabs.able.core.designsystem.component.AbleSlider
import com.dragolabs.able.core.designsystem.component.AbleSwitch
import com.dragolabs.able.core.designsystem.component.AbleTextButton
import com.dragolabs.able.core.designsystem.component.AbleToggleButton
import com.dragolabs.able.core.designsystem.component.ActionRow
import com.dragolabs.able.core.designsystem.component.ConfirmationDialog
import com.dragolabs.able.core.designsystem.component.HelpButton
import com.dragolabs.able.core.designsystem.component.ListGroup
import com.dragolabs.able.core.designsystem.component.LoadingState
import com.dragolabs.able.core.designsystem.component.RadioRow
import com.dragolabs.able.core.designsystem.component.SectionHeader
import com.dragolabs.able.core.designsystem.component.StatusLevel
import com.dragolabs.able.core.designsystem.component.StatusPill
import com.dragolabs.able.core.designsystem.component.SwitchRow
import com.dragolabs.able.core.designsystem.component.focusRing
import com.dragolabs.able.core.designsystem.component.hapticClickable
import com.dragolabs.able.core.designsystem.component.labelRes as builtInLabelRes
import com.dragolabs.able.core.designsystem.component.listGroupItemShape
import com.dragolabs.able.core.designsystem.component.rememberAbleHaptics
import com.dragolabs.able.core.designsystem.component.screenContentPadding
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import com.dragolabs.able.core.model.AppInterventionLevel
import com.dragolabs.able.core.model.BatteryProfile
import com.dragolabs.able.core.model.ProfileColor
import com.dragolabs.able.core.model.ProfileIcon
import com.dragolabs.able.core.model.ProfileTrigger
import com.dragolabs.able.feature.battery.R
import com.dragolabs.able.feature.battery.data.RuleAppEntry
import com.dragolabs.able.feature.battery.ui.SupportingText
import com.dragolabs.able.feature.battery.ui.descriptionRes
import com.dragolabs.able.feature.battery.ui.displayName
import com.dragolabs.able.feature.battery.ui.formatMinutes
import com.dragolabs.able.feature.battery.ui.labelRes
import com.dragolabs.able.feature.battery.ui.tone
import com.dragolabs.able.feature.battery.ui.vector
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Stateful profile editor. [sessionKey] scopes a fresh ViewModel to each opening; [onClose]
 * receives the outcome (null when the user left without changes or discarded them).
 */
@Composable
internal fun ProfileEditorRoute(
    profileId: String?,
    sessionKey: String,
    onClose: (EditorResult?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ProfileEditorViewModel = viewModel(
        key = "battery-profile-editor-$sessionKey",
        factory = ProfileEditorViewModel.factory(profileId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberAbleHaptics()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.result) {
        val result = state.result ?: return@LaunchedEffect
        viewModel.consumeResult()
        if (result == EditorResult.NOT_FOUND) haptics.reject() else haptics.confirm()
        onClose(result)
    }
    val requestBack = { if (state.dirty) confirmDiscard = true else onClose(null) }
    BackHandler(onBack = requestBack)

    ProfileEditorScreen(
        state = state,
        onBack = requestBack,
        actions = EditorActions(
            onName = viewModel::setName,
            onIcon = viewModel::setIcon,
            onColor = viewModel::setColor,
            onEnabled = viewModel::setEnabled,
            onOfferSaver = viewModel::setOfferBatterySaver,
            onAddTrigger = viewModel::addTrigger,
            onUpdateTrigger = viewModel::updateTrigger,
            onRemoveTrigger = viewModel::removeTrigger,
            onAppLevel = viewModel::setAppLevel,
            onQuery = viewModel::setQuery,
            onOnlyWithRules = viewModel::setOnlyWithRules,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDuplicate = viewModel::duplicate,
            onReset = viewModel::resetToDefault,
        ),
        modifier = modifier,
    )

    if (confirmDiscard) {
        ConfirmationDialog(
            title = stringResource(R.string.battery_editor_discard_title),
            message = stringResource(R.string.battery_editor_discard_body),
            confirmLabel = stringResource(R.string.battery_editor_discard),
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                onClose(null)
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

internal class EditorActions(
    val onName: (String) -> Unit,
    val onIcon: (ProfileIcon) -> Unit,
    val onColor: (ProfileColor) -> Unit,
    val onEnabled: (Boolean) -> Unit,
    val onOfferSaver: (Boolean) -> Unit,
    val onAddTrigger: (NewTriggerType) -> Unit,
    val onUpdateTrigger: (Int, ProfileTrigger) -> Unit,
    val onRemoveTrigger: (Int) -> Unit,
    val onAppLevel: (String, AppInterventionLevel) -> Unit,
    val onQuery: (String) -> Unit,
    val onOnlyWithRules: (Boolean) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onDuplicate: (String) -> Unit,
    val onReset: () -> Unit,
)

@Composable
internal fun ProfileEditorScreen(
    state: ProfileEditorUiState,
    onBack: () -> Unit,
    actions: EditorActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    val draft = state.draft
    var pickerPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    AbleDetailScaffold(
        title = stringResource(if (state.isNew) R.string.battery_editor_new_title else R.string.battery_editor_edit_title),
        subtitle = if (state.loading) null else draft.displayName().takeIf { draft.builtIn || draft.name.isNotBlank() },
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = {
            AbleTextButton(
                onClick = actions.onSave,
                enabled = state.canSave,
                haptic = AbleHaptic.Confirm,
                modifier = Modifier.focusRing(),
            ) { Text(stringResource(DsR.string.ds_save)) }
        },
    ) { padding ->
        if (state.loading) {
            LoadingState()
            return@AbleDetailScaffold
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = AbleSpacing.maxContentWidth).fillMaxWidth(),
                contentPadding = screenContentPadding(padding),
                verticalArrangement = Arrangement.spacedBy(AbleSpacing.md),
            ) {
                item("notice") { SupportingText(stringResource(R.string.battery_profile_actions_inactive)) }
                item("identity") { IdentityCard(state, actions) }
                triggerItems(draft, state.issues, actions)
                item("options") { OptionsCard(draft, actions) }
                appRuleItems(state, actions, onPick = { pickerPackage = it })
                if (!state.isNew) {
                    item("manage") {
                        ManageCard(
                            profile = draft,
                            onDuplicate = actions.onDuplicate,
                            onRequestReset = { confirmReset = true },
                            onRequestDelete = { confirmDelete = true },
                            enabled = !state.busy,
                        )
                    }
                }
            }
        }
    }

    pickerPackage?.let { pkg ->
        val label = state.apps?.firstOrNull { it.packageName == pkg }?.label ?: pkg
        LevelPickerDialog(
            appLabel = label,
            current = draft.appRules[pkg] ?: AppInterventionLevel.OSSERVA,
            onDismiss = { pickerPackage = null },
            onSelect = { level ->
                pickerPackage = null
                actions.onAppLevel(pkg, level)
            },
        )
    }
    if (confirmDelete) {
        ConfirmationDialog(
            title = stringResource(R.string.battery_editor_delete_title, draft.displayName()),
            message = stringResource(R.string.battery_editor_delete_body),
            confirmLabel = stringResource(R.string.battery_editor_delete),
            destructive = true,
            icon = Icons.Rounded.Delete,
            onConfirm = { confirmDelete = false; actions.onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmReset) {
        ConfirmationDialog(
            title = stringResource(R.string.battery_editor_reset_title),
            message = stringResource(R.string.battery_editor_reset_body),
            confirmLabel = stringResource(R.string.battery_editor_reset),
            destructive = true,
            icon = Icons.Rounded.RestartAlt,
            onConfirm = { confirmReset = false; actions.onReset() },
            onDismiss = { confirmReset = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityCard(state: ProfileEditorUiState, actions: EditorActions) {
    val draft = state.draft
    val nameMissing = state.issues.any { it.kind == ProfileIssueKind.NAME_REQUIRED }
    val builtInName = draft.builtInType?.let { stringResource(it.builtInLabelRes()) }
    AbleCard {
        OutlinedTextField(
            value = draft.name,
            onValueChange = actions.onName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.battery_editor_name)) },
            placeholder = builtInName?.let { { Text(it) } },
            isError = nameMissing && state.dirty,
            supportingText = {
                Text(
                    when {
                        builtInName != null -> stringResource(R.string.battery_editor_name_builtin_hint, builtInName)
                        nameMissing -> stringResource(R.string.battery_issue_name_required)
                        else -> stringResource(R.string.battery_editor_name_hint)
                    },
                )
            },
        )
        Text(stringResource(R.string.battery_editor_icon), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            ProfileIcon.entries.forEach { icon ->
                val label = stringResource(icon.labelRes())
                AbleToggleButton(
                    checked = draft.iconKey == icon.key,
                    onCheckedChange = { actions.onIcon(icon) },
                    modifier = Modifier.focusRing().semantics { contentDescription = label },
                ) { Icon(icon.vector(), contentDescription = null) }
            }
        }
        Text(stringResource(R.string.battery_editor_color), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.md), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
            ProfileColor.entries.forEach { color -> ColorSwatch(color, selected = draft.colorKey == color.key) { actions.onColor(color) } }
        }
        SwitchRow(
            title = stringResource(R.string.battery_editor_enabled),
            supportingText = stringResource(R.string.battery_editor_enabled_body),
            checked = draft.enabled,
            onCheckedChange = actions.onEnabled,
            shape = listGroupItemShape(0, 1),
        )
    }
}

@Composable
private fun ColorSwatch(color: ProfileColor, selected: Boolean, onClick: () -> Unit) {
    val tone = color.tone()
    val label = stringResource(color.labelRes())
    Box(
        modifier = Modifier
            .size(44.dp)
            .focusRing(CircleShape)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .background(tone.container, CircleShape)
            .then(if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), CircleShape) else Modifier)
            .hapticClickable(role = Role.RadioButton, haptic = AbleHaptic.Tick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = tone.content)
    }
}

private fun LazyListScope.triggerItems(draft: BatteryProfile, issues: List<ProfileIssue>, actions: EditorActions) {
    item("triggers-header") {
        val title = stringResource(R.string.battery_editor_triggers)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = AbleSpacing.sm)) {
            SectionHeader(title, Modifier.weight(1f))
            HelpButton(title = title, text = stringResource(R.string.battery_help_triggers_editor))
            AddTriggerButton(actions.onAddTrigger)
        }
    }
    val shown = draft.triggers.withIndex().filter { it.value !is ProfileTrigger.Manual }
    if (shown.isEmpty()) {
        item("triggers-empty") { SupportingText(stringResource(R.string.battery_editor_no_triggers)) }
    }
    shown.forEach { (index, trigger) ->
        item("trigger-$index-${trigger::class.simpleName}") {
            TriggerCard(
                trigger = trigger,
                issues = issues.filter { it.triggerIndex == index }.map { it.kind },
                onChange = { actions.onUpdateTrigger(index, it) },
                onRemove = { actions.onRemoveTrigger(index) },
            )
        }
    }
}

@Composable
private fun AddTriggerButton(onAdd: (NewTriggerType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AbleFilledTonalButton(onClick = { open = true }, modifier = Modifier.focusRing()) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.battery_editor_add_trigger), modifier = Modifier.padding(start = AbleSpacing.xs), maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val haptics = rememberAbleHaptics()
            NewTriggerType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.titleRes())) },
                    leadingIcon = { Icon(type.icon(), contentDescription = null) },
                    onClick = {
                        haptics.click()
                        open = false
                        onAdd(type)
                    },
                )
            }
        }
    }
}

private fun NewTriggerType.titleRes(): Int = when (this) {
    NewTriggerType.BATTERY_BELOW -> R.string.battery_trigger_type_battery
    NewTriggerType.SCHEDULE -> R.string.battery_trigger_type_schedule
    NewTriggerType.SCREEN_OFF -> R.string.battery_trigger_type_screen_off
    NewTriggerType.CHARGING -> R.string.battery_trigger_type_charging
}

private fun NewTriggerType.icon(): ImageVector = when (this) {
    NewTriggerType.BATTERY_BELOW -> Icons.Rounded.BatteryAlert
    NewTriggerType.SCHEDULE -> Icons.Rounded.Schedule
    NewTriggerType.SCREEN_OFF -> Icons.Rounded.ScreenLockPortrait
    NewTriggerType.CHARGING -> Icons.Rounded.BatteryChargingFull
}

private fun ProfileTrigger.type(): NewTriggerType? = when (this) {
    is ProfileTrigger.BatteryBelow -> NewTriggerType.BATTERY_BELOW
    is ProfileTrigger.Schedule -> NewTriggerType.SCHEDULE
    is ProfileTrigger.ScreenOffFor -> NewTriggerType.SCREEN_OFF
    is ProfileTrigger.Charging -> NewTriggerType.CHARGING
    is ProfileTrigger.Manual -> null
}

@Composable
private fun TriggerCard(
    trigger: ProfileTrigger,
    issues: List<ProfileIssueKind>,
    onChange: (ProfileTrigger) -> Unit,
    onRemove: () -> Unit,
) {
    val type = trigger.type() ?: return
    val title = stringResource(type.titleRes())
    val stateText = stringResource(if (trigger.enabled) R.string.battery_profile_status_on else R.string.battery_profile_status_off)
    AbleCard(
        title = title,
        icon = type.icon(),
        style = if (trigger.enabled) AbleCardStyle.Standard else AbleCardStyle.Outlined,
        trailing = {
            HelpButton(title = title, text = stringResource(type.helpRes()))
            AbleSwitch(
                checked = trigger.enabled,
                onCheckedChange = { onChange(trigger.withEnabled(it)) },
                modifier = Modifier.focusRing().semantics { contentDescription = "$title, $stateText" },
            )
        },
        status = {
            if (!trigger.enabled) StatusPill(text = stringResource(R.string.battery_trigger_off_pill), level = StatusLevel.WARNING)
        },
    ) {
        when (trigger) {
            is ProfileTrigger.BatteryBelow -> BatteryBelowEditor(trigger, onChange)
            is ProfileTrigger.Schedule -> ScheduleEditor(trigger, onChange)
            is ProfileTrigger.ScreenOffFor -> ScreenOffEditor(trigger, onChange)
            is ProfileTrigger.Charging -> SupportingText(stringResource(R.string.battery_trigger_charging_body))
            is ProfileTrigger.Manual -> Unit
        }
        issues.forEach { issue ->
            Text(stringResource(issue.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        AbleTextButton(onClick = onRemove, haptic = AbleHaptic.Reject, modifier = Modifier.focusRing()) {
            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.battery_editor_remove_trigger), modifier = Modifier.padding(start = AbleSpacing.xs))
        }
    }
}

private fun NewTriggerType.helpRes(): Int = when (this) {
    NewTriggerType.BATTERY_BELOW -> R.string.battery_help_trigger_battery
    NewTriggerType.SCHEDULE -> R.string.battery_help_trigger_schedule
    NewTriggerType.SCREEN_OFF -> R.string.battery_help_trigger_screen_off
    NewTriggerType.CHARGING -> R.string.battery_help_trigger_charging
}

private fun ProfileIssueKind.messageRes(): Int = when (this) {
    ProfileIssueKind.NAME_REQUIRED -> R.string.battery_issue_name_required
    ProfileIssueKind.PERCENT_OUT_OF_RANGE -> R.string.battery_issue_percent_range
    ProfileIssueKind.RESET_NOT_ABOVE_ACTIVATE -> R.string.battery_issue_reset_above
    ProfileIssueKind.SCHEDULE_EMPTY -> R.string.battery_issue_schedule_empty
    ProfileIssueKind.NO_DAYS -> R.string.battery_issue_no_days
    ProfileIssueKind.SCREEN_OFF_OUT_OF_RANGE -> R.string.battery_issue_screen_off_range
}

@Composable
private fun BatteryBelowEditor(trigger: ProfileTrigger.BatteryBelow, onChange: (ProfileTrigger) -> Unit) {
    LabeledSlider(
        label = stringResource(R.string.battery_trigger_activate_label, trigger.activatePercent),
        value = trigger.activatePercent.toFloat(),
        range = 1f..95f,
        onValue = { value ->
            val activate = value.roundToInt()
            // Keep the end level above the start level so the profile cannot flip on and off.
            val reset = if (trigger.resetPercent <= activate) (activate + 10).coerceAtMost(100) else trigger.resetPercent
            onChange(trigger.copy(activatePercent = activate, resetPercent = reset))
        },
    )
    LabeledSlider(
        label = stringResource(R.string.battery_trigger_reset_label, trigger.resetPercent),
        value = trigger.resetPercent.toFloat(),
        range = 2f..100f,
        onValue = { onChange(trigger.copy(resetPercent = it.roundToInt())) },
    )
    SwitchRow(
        title = stringResource(R.string.battery_trigger_only_unplugged),
        supportingText = stringResource(R.string.battery_trigger_only_unplugged_body),
        checked = trigger.onlyUnplugged,
        onCheckedChange = { onChange(trigger.copy(onlyUnplugged = it)) },
        shape = listGroupItemShape(0, 1),
    )
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Int = 1, onValue: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        AbleSlider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            steps = (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditor(trigger: ProfileTrigger.Schedule, onChange: (ProfileTrigger) -> Unit) {
    var editingStart by rememberSaveable { mutableStateOf<Boolean?>(null) }
    AbleButtonFlow {
        AbleOutlinedButton(onClick = { editingStart = true }, modifier = Modifier.weight(1f).focusRing()) {
            Text(stringResource(R.string.battery_schedule_start_value, formatMinutes(trigger.startMinutes)), maxLines = 1)
        }
        AbleOutlinedButton(onClick = { editingStart = false }, modifier = Modifier.weight(1f).focusRing()) {
            Text(stringResource(R.string.battery_schedule_end_value, formatMinutes(trigger.endMinutes)), maxLines = 1)
        }
    }
    if (trigger.endMinutes < trigger.startMinutes) {
        SupportingText(stringResource(R.string.battery_trigger_schedule_overnight))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AbleSpacing.xs), verticalArrangement = Arrangement.spacedBy(AbleSpacing.xs)) {
        val locale = Locale.getDefault()
        (1..7).forEach { day ->
            val selected = day in trigger.days
            AbleFilterChip(
                selected = selected,
                onClick = { onChange(trigger.copy(days = if (selected) trigger.days - day else trigger.days + day)) },
                label = { Text(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale)) },
                modifier = Modifier.focusRing(),
            )
        }
    }
    editingStart?.let { isStart ->
        TimeDialog(
            title = stringResource(if (isStart) R.string.battery_schedule_start else R.string.battery_schedule_end),
            initialMinutes = if (isStart) trigger.startMinutes else trigger.endMinutes,
            onDismiss = { editingStart = null },
            onConfirm = { minutes ->
                editingStart = null
                onChange(if (isStart) trigger.copy(startMinutes = minutes) else trigger.copy(endMinutes = minutes))
            },
        )
    }
}

@Composable
private fun TimeDialog(title: String, initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimeInput(state = pickerState) },
        confirmButton = {
            AbleTextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }, haptic = AbleHaptic.Confirm) {
                Text(stringResource(DsR.string.ds_save))
            }
        },
        dismissButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(DsR.string.ds_cancel)) } },
    )
}

@Composable
private fun ScreenOffEditor(trigger: ProfileTrigger.ScreenOffFor, onChange: (ProfileTrigger) -> Unit) {
    LabeledSlider(
        label = stringResource(R.string.battery_night_delay_value, trigger.minutes),
        value = trigger.minutes.toFloat().coerceIn(5f, 240f),
        range = 5f..240f,
        step = 5,
        onValue = { onChange(trigger.copy(minutes = ((it / 5f).roundToInt() * 5).coerceIn(5, 240))) },
    )
    SupportingText(stringResource(R.string.battery_night_screen_off_note))
}

@Composable
private fun OptionsCard(draft: BatteryProfile, actions: EditorActions) {
    val title = stringResource(R.string.battery_editor_offer_saver)
    AbleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SwitchRow(
                title = title,
                supportingText = stringResource(R.string.battery_editor_offer_saver_body),
                checked = draft.offerBatterySaver,
                onCheckedChange = actions.onOfferSaver,
                shape = listGroupItemShape(0, 1),
                icon = Icons.Rounded.BatterySaver,
                modifier = Modifier.weight(1f),
            )
            HelpButton(title = title, text = stringResource(R.string.battery_help_offer_saver))
        }
    }
}

private fun LazyListScope.appRuleItems(state: ProfileEditorUiState, actions: EditorActions, onPick: (String) -> Unit) {
    val draft = state.draft
    item("apps-header") {
        val title = stringResource(R.string.battery_editor_apps)
        Column(verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm), modifier = Modifier.padding(top = AbleSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(title, Modifier.weight(1f))
                HelpButton(title = title, text = stringResource(R.string.battery_help_app_levels))
            }
            val count = draft.restrictiveRules.size
            SupportingText(
                if (count == 0) {
                    stringResource(R.string.battery_profile_rules_none)
                } else {
                    LocalContext.current.resources.getQuantityString(R.plurals.battery_profile_rules_count, count, count)
                },
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = actions.onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.battery_editor_search_apps)) },
            )
            AbleFilterChip(
                selected = state.onlyWithRules,
                onClick = { actions.onOnlyWithRules(!state.onlyWithRules) },
                label = { Text(stringResource(R.string.battery_editor_only_rules)) },
                modifier = Modifier.focusRing(),
            )
        }
    }
    val apps = state.apps
    if (apps == null) {
        item("apps-loading") { SupportingText(stringResource(R.string.battery_editor_apps_loading)) }
        return
    }
    val visible = state.visibleApps
    if (visible.isEmpty()) {
        item("apps-empty") { SupportingText(stringResource(R.string.battery_editor_apps_empty)) }
        return
    }
    itemsIndexed(visible, key = { _, app -> "app-${app.packageName}" }) { index, app ->
        AppRuleRow(
            app = app,
            level = draft.appRules[app.packageName] ?: AppInterventionLevel.OSSERVA,
            isProtected = app.packageName in state.protectedPackages,
            shape = listGroupItemShape(index, visible.size),
            onClick = { onPick(app.packageName) },
        )
    }
}

@Composable
private fun AppRuleRow(
    app: RuleAppEntry,
    level: AppInterventionLevel,
    isProtected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    ActionRow(
        title = app.label,
        supportingText = if (isProtected) stringResource(R.string.battery_editor_app_protected) else stringResource(level.labelRes()),
        icon = Icons.Rounded.Apps,
        shape = shape,
        enabled = !isProtected,
        onClick = if (isProtected) null else onClick,
        trailing = if (level != AppInterventionLevel.OSSERVA && !isProtected) {
            { StatusPill(text = stringResource(level.labelRes()), level = StatusLevel.WARNING) }
        } else {
            null
        },
    )
}

@Composable
private fun LevelPickerDialog(
    appLabel: String,
    current: AppInterventionLevel,
    onDismiss: () -> Unit,
    onSelect: (AppInterventionLevel) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Apps, contentDescription = null) },
        title = { Text(stringResource(R.string.battery_editor_level_title, appLabel)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm)) {
                ListGroup {
                    AppInterventionLevel.entries.forEach { level ->
                        item { shape ->
                            RadioRow(
                                title = stringResource(level.labelRes()),
                                supportingText = stringResource(level.descriptionRes()),
                                selected = level == current,
                                onSelect = { onSelect(level) },
                                shape = shape,
                            )
                        }
                    }
                }
                SupportingText(stringResource(R.string.battery_editor_level_note))
            }
        },
        confirmButton = {},
        dismissButton = { AbleTextButton(onClick = onDismiss) { Text(stringResource(DsR.string.ds_cancel)) } },
    )
}

@Composable
private fun ManageCard(
    profile: BatteryProfile,
    onDuplicate: (String) -> Unit,
    onRequestReset: () -> Unit,
    onRequestDelete: () -> Unit,
    enabled: Boolean,
) {
    val copyName = stringResource(R.string.battery_profile_copy_name, profile.displayName())
    AbleCard(title = stringResource(R.string.battery_editor_manage), style = AbleCardStyle.Outlined) {
        ListGroup {
            item { shape ->
                ActionRow(
                    title = stringResource(R.string.battery_editor_duplicate),
                    supportingText = stringResource(R.string.battery_editor_duplicate_body),
                    icon = Icons.Rounded.ContentCopy,
                    shape = shape,
                    enabled = enabled,
                    onClick = { onDuplicate(copyName) },
                )
            }
            if (profile.builtIn) {
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.battery_editor_reset),
                        supportingText = stringResource(R.string.battery_editor_reset_row),
                        icon = Icons.Rounded.RestartAlt,
                        shape = shape,
                        enabled = enabled,
                        onClick = onRequestReset,
                    )
                }
            } else {
                item { shape ->
                    ActionRow(
                        title = stringResource(R.string.battery_editor_delete),
                        icon = Icons.Rounded.Delete,
                        shape = shape,
                        enabled = enabled,
                        onClick = onRequestDelete,
                    )
                }
            }
        }
        if (profile.builtIn) SupportingText(stringResource(R.string.battery_editor_builtin_note))
    }
}
