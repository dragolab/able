package com.dragolabs.able.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.dragolabs.able.core.designsystem.theme.AbleSpacing
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Haptic wrappers around Material 3 controls (UX-003). Each wrapper performs the semantic
 * haptic for the interaction and then calls through to the Material component with the same
 * parameters. Callers must not add their own haptic call for the same tap; pass a different
 * [AbleHaptic] (for example [AbleHaptic.Reject] for destructive actions) instead.
 */

/** Semantic haptic performed by an Able control. */
enum class AbleHaptic { None, Navigate, Click, Tick, Confirm, Reject, LongPress }

fun AbleHaptics.perform(kind: AbleHaptic) {
    when (kind) {
        AbleHaptic.None -> Unit
        AbleHaptic.Navigate -> navigate()
        AbleHaptic.Click -> click()
        AbleHaptic.Tick -> tick()
        AbleHaptic.Confirm -> confirm()
        AbleHaptic.Reject -> reject()
        AbleHaptic.LongPress -> longPress()
    }
}

/** [Modifier.clickable] that performs [haptic] before [onClick]. */
@Composable
fun Modifier.hapticClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    haptic: AbleHaptic = AbleHaptic.Click,
    onClick: () -> Unit,
): Modifier {
    val haptics = rememberAbleHaptics()
    return clickable(enabled = enabled, onClickLabel = onClickLabel, role = role) {
        haptics.perform(haptic)
        onClick()
    }
}

/**
 * [Modifier.combinedClickable] with haptics: [haptic] on tap, a long-press haptic before
 * [onLongClick] and a click before [onDoubleClick].
 */
@Composable
fun Modifier.hapticCombinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    haptic: AbleHaptic = AbleHaptic.Click,
    onClick: () -> Unit,
): Modifier {
    val haptics = rememberAbleHaptics()
    return combinedClickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick?.let { callback -> { haptics.longPress(); callback() } },
        onDoubleClick = onDoubleClick?.let { callback -> { haptics.click(); callback() } },
        onClick = { haptics.perform(haptic); onClick() },
    )
}

/** [Modifier.toggleable] that plays the on/off toggle haptic before [onValueChange]. */
@Composable
fun Modifier.hapticToggleable(
    value: Boolean,
    enabled: Boolean = true,
    role: Role? = Role.Switch,
    onValueChange: (Boolean) -> Unit,
): Modifier {
    val haptics = rememberAbleHaptics()
    return toggleable(value = value, enabled = enabled, role = role) { on ->
        haptics.toggle(on)
        onValueChange(on)
    }
}

/** [Modifier.selectable] with a selection tick (only when the selection actually changes). */
@Composable
fun Modifier.hapticSelectable(
    selected: Boolean,
    enabled: Boolean = true,
    role: Role? = Role.RadioButton,
    haptic: AbleHaptic = AbleHaptic.Tick,
    onClick: () -> Unit,
): Modifier {
    val haptics = rememberAbleHaptics()
    return selectable(selected = selected, enabled = enabled, role = role) {
        if (!selected) haptics.perform(haptic)
        onClick()
    }
}

// region Buttons

@Composable
fun AbleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    haptic: AbleHaptic = AbleHaptic.Click,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberAbleHaptics()
    Button(
        onClick = { haptics.perform(haptic); onClick() },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun AbleFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    haptic: AbleHaptic = AbleHaptic.Click,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberAbleHaptics()
    FilledTonalButton(
        onClick = { haptics.perform(haptic); onClick() },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun AbleOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    haptic: AbleHaptic = AbleHaptic.Click,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberAbleHaptics()
    OutlinedButton(
        onClick = { haptics.perform(haptic); onClick() },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun AbleTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    haptic: AbleHaptic = AbleHaptic.Click,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberAbleHaptics()
    TextButton(
        onClick = { haptics.perform(haptic); onClick() },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun AbleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = IconButtonDefaults.standardShape,
    haptic: AbleHaptic = AbleHaptic.Click,
    content: @Composable () -> Unit,
) {
    val haptics = rememberAbleHaptics()
    IconButton(
        onClick = { haptics.perform(haptic); onClick() },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        shape = shape,
        content = content,
    )
}

@Composable
fun AbleFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    haptic: AbleHaptic = AbleHaptic.Click,
    content: @Composable () -> Unit,
) {
    val haptics = rememberAbleHaptics()
    FilledTonalIconButton(
        onClick = { haptics.perform(haptic); onClick() },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * Lays out action buttons on one line when they fit and wraps whole buttons onto the next
 * line otherwise, so a label never breaks mid-word. Give button labels `maxLines = 1`.
 */
@Composable
fun AbleButtonFlow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AbleSpacing.sm),
        content = content,
    )
}

// endregion

// region Selection controls

@Composable
fun AbleFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
    haptic: AbleHaptic = AbleHaptic.Tick,
) {
    val haptics = rememberAbleHaptics()
    FilterChip(
        selected = selected,
        onClick = { haptics.perform(haptic); onClick() },
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
    )
}

/** Standalone switch. Inside a list row prefer [SwitchRow], which makes the whole row toggleable. */
@Composable
fun AbleSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val haptics = rememberAbleHaptics()
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { callback -> { on: Boolean -> haptics.toggle(on); callback(on) } },
        modifier = modifier,
        thumbContent = thumbContent,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

@Composable
fun AbleToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: ToggleButtonShapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight),
    colors: ToggleButtonColors = ToggleButtonDefaults.colors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    haptic: AbleHaptic = AbleHaptic.Tick,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberAbleHaptics()
    ToggleButton(
        checked = checked,
        onCheckedChange = { haptics.perform(haptic); onCheckedChange(it) },
        modifier = modifier,
        enabled = enabled,
        shapes = shapes,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * Connected single-choice toggle group (Material 3 Expressive button group). Each option is
 * exposed to TalkBack as a radio button; selecting the current option does nothing.
 */
@Composable
fun <T> AbleSingleChoiceToggleGroup(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    content: @Composable RowScope.(T) -> Unit,
) {
    val haptics = rememberAbleHaptics()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = {
                    if (option != selected) {
                        haptics.tick()
                        onSelect(option)
                    }
                },
                enabled = enabled(option),
                modifier = Modifier
                    .weight(1f)
                    .focusRing()
                    .semantics { role = Role.RadioButton },
                shapes = when {
                    options.size == 1 -> ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight)
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { content(option) }
        }
    }
}

/** Segmented button with a selection tick. Use inside `SingleChoiceSegmentedButtonRow`. */
@Composable
fun SingleChoiceSegmentedButtonRowScope.AbleSegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SegmentedButtonColors = SegmentedButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
    icon: @Composable () -> Unit = { SegmentedButtonDefaults.Icon(selected) },
    label: @Composable () -> Unit,
) {
    val haptics = rememberAbleHaptics()
    SegmentedButton(
        selected = selected,
        onClick = { if (!selected) haptics.tick(); onClick() },
        shape = shape,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        icon = icon,
        label = label,
    )
}

// endregion

// region Slider

/** Number of haptic detents used for continuous sliders (one tick every 5 %). */
private const val CONTINUOUS_DETENTS = 20

private fun sliderDetent(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Int {
    val span = range.endInclusive - range.start
    if (span <= 0f) return 0
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    return if (steps > 0) {
        (fraction * (steps + 1)).roundToInt()
    } else {
        floor(fraction * CONTINUOUS_DETENTS).toInt()
    }
}

/**
 * Slider with haptics: a tick each time the thumb crosses a step (stepped sliders) or every
 * ~5 % of the range (continuous sliders), and a confirm when the gesture ends.
 */
@Composable
fun AbleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val haptics = rememberAbleHaptics()
    // Non-deprecated SliderState API; the state mirrors the caller's hoisted [value].
    val state = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
    SideEffect { if (state.value != value) state.value = value }
    // Last detent that produced a tick during the current gesture; MIN_VALUE when idle.
    val lastDetent = remember { mutableIntStateOf(Int.MIN_VALUE) }
    Slider(
        state = state,
        onValueChange = { newValue ->
            if (lastDetent.intValue == Int.MIN_VALUE) {
                lastDetent.intValue = sliderDetent(value, valueRange, steps)
            }
            val detent = sliderDetent(newValue, valueRange, steps)
            if (detent != lastDetent.intValue) {
                lastDetent.intValue = detent
                haptics.tick()
            }
            onValueChange(newValue)
        },
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = {
            lastDetent.intValue = Int.MIN_VALUE
            haptics.confirm()
            onValueChangeFinished?.invoke()
        },
        colors = colors,
        interactionSource = interactionSource,
    )
}

/**
 * [SliderState] variant of [AbleSlider] for callers that own the state (for example to update it
 * from +/- buttons). [onValueChange] must write the new value back to [state].
 */
@Composable
fun AbleSlider(
    state: SliderState,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val haptics = rememberAbleHaptics()
    val lastDetent = remember { mutableIntStateOf(Int.MIN_VALUE) }
    Slider(
        state = state,
        onValueChange = { newValue ->
            val range = state.trackRange
            if (lastDetent.intValue == Int.MIN_VALUE) {
                lastDetent.intValue = sliderDetent(state.value, range, state.steps)
            }
            val detent = sliderDetent(newValue, range, state.steps)
            if (detent != lastDetent.intValue) {
                lastDetent.intValue = detent
                haptics.tick()
            }
            onValueChange(newValue)
        },
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = {
            lastDetent.intValue = Int.MIN_VALUE
            haptics.confirm()
            onValueChangeFinished?.invoke()
        },
        colors = colors,
        interactionSource = interactionSource,
    )
}

// endregion
