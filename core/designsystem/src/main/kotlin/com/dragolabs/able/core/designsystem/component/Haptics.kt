package com.dragolabs.able.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Whether Able's haptic feedback is enabled (UX-003). Provided by [com.dragolabs.able.core.designsystem.theme.AbleTheme]. */
val LocalAbleHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Semantic haptics (UX-003). Uses Compose's platform haptics, which already respect the
 * system "touch feedback" setting; Able's own switch can turn them off entirely.
 */
@Stable
class AbleHaptics internal constructor(
    private val haptic: HapticFeedback,
    private val enabled: Boolean,
) {
    private fun perform(type: HapticFeedbackType) {
        if (enabled) haptic.performHapticFeedback(type)
    }

    /** Light tick for ordinary taps (navigation, opening a card). */
    fun click() = perform(HapticFeedbackType.ContextClick)

    /** Selection change in a group (radio, segmented buttons, chips). */
    fun tick() = perform(HapticFeedbackType.SegmentTick)

    fun toggle(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** An action completed or was confirmed. */
    fun confirm() = perform(HapticFeedbackType.Confirm)

    /** An action was rejected, failed, or is destructive. */
    fun reject() = perform(HapticFeedbackType.Reject)

    fun longPress() = perform(HapticFeedbackType.LongPress)
}

@Composable
fun rememberAbleHaptics(): AbleHaptics {
    val haptic = LocalHapticFeedback.current
    val enabled = LocalAbleHapticsEnabled.current
    return remember(haptic, enabled) { AbleHaptics(haptic, enabled) }
}
