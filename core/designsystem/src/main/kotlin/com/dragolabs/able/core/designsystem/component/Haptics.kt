package com.dragolabs.able.core.designsystem.component

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import com.dragolabs.able.core.model.HapticsIntensity

/** Whether Able's haptic feedback is enabled (UX-003). Provided by [com.dragolabs.able.core.designsystem.theme.AbleTheme]. */
val LocalAbleHapticsEnabled = staticCompositionLocalOf { true }

/** Able's haptic strength (UX-003). Provided by [com.dragolabs.able.core.designsystem.theme.AbleTheme]. */
val LocalAbleHapticsIntensity = staticCompositionLocalOf { HapticsIntensity.NORMAL }

/**
 * Semantic haptics (UX-003) with a distinct feel per action.
 *
 * When the device vibrator supports the needed [VibrationEffect.Composition] primitives, each
 * semantic plays a short composition scaled by the user's [HapticsIntensity]; otherwise it falls
 * back to the platform haptic constants through Compose (a lighter or heavier constant per
 * intensity). Both paths respect the system "touch feedback" setting: compositions are played
 * with [VibrationAttributes.USAGE_TOUCH] and skipped when touch feedback is off; platform
 * constants are filtered by the framework. Able's own setting can turn everything off.
 */
@Stable
class AbleHaptics internal constructor(
    private val view: View,
    private val fallback: HapticFeedback,
    private val vibrator: Vibrator?,
    private val intensity: HapticsIntensity,
) {
    private val strength: Float = when (intensity) {
        HapticsIntensity.OFF -> 0f
        HapticsIntensity.LIGHT -> 0.5f
        HapticsIntensity.NORMAL -> 0.75f
        HapticsIntensity.STRONG -> 1f
    }

    private fun play(pattern: HapticPattern) {
        if (intensity == HapticsIntensity.OFF) return
        val v = vibrator
        if (v != null && pattern.primitives.isNotEmpty() && PrimitiveSupport.supportsAll(v, pattern.primitiveIds)) {
            if (!systemTouchFeedbackEnabled()) return
            val composition = VibrationEffect.startComposition()
            pattern.primitives.forEach { p ->
                composition.addPrimitive(p.id, (p.scale * strength).coerceIn(MIN_SCALE, 1f), p.delayMs)
            }
            runCatching { v.vibrate(composition.compose(), TouchAttributes) }
                .onFailure { fallback.performHapticFeedback(pattern.fallbackFor(intensity)) }
        } else {
            fallback.performHapticFeedback(pattern.fallbackFor(intensity))
        }
    }

    private fun systemTouchFeedbackEnabled(): Boolean =
        view.isHapticFeedbackEnabled &&
            runCatching {
                @Suppress("DEPRECATION")
                Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
            }.getOrDefault(true)

    /** Lightest tick: switching navigation destination or tab. */
    fun navigate() = play(HapticPattern.Navigate)

    /** Ordinary tap (button, card, list row). */
    fun click() = play(HapticPattern.Click)

    /** Selection change in a group (radio, segmented buttons, chips, slider detents). */
    fun tick() = play(HapticPattern.Tick)

    /** Switch turned on (rising) or off (falling): two distinct feels. */
    fun toggle(on: Boolean) = play(if (on) HapticPattern.ToggleOn else HapticPattern.ToggleOff)

    /** An action completed or was confirmed. */
    fun confirm() = play(HapticPattern.Confirm)

    /** An action was rejected, failed, or is destructive (double thud). */
    fun reject() = play(HapticPattern.Reject)

    fun longPress() = play(HapticPattern.LongPress)

    private companion object {
        const val MIN_SCALE = 0.05f
        val TouchAttributes: VibrationAttributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
    }
}

/** One primitive of a composition; [scale] is the value at [HapticsIntensity.STRONG]. */
private class HapticPrimitive(val id: Int, val scale: Float, val delayMs: Int = 0)

private enum class HapticPattern(
    val primitives: List<HapticPrimitive>,
    /** Fallback platform constants for LIGHT, NORMAL and STRONG. */
    private val light: HapticFeedbackType,
    private val normal: HapticFeedbackType,
    private val strong: HapticFeedbackType,
) {
    Navigate(
        listOf(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 1f)),
        HapticFeedbackType.SegmentFrequentTick, HapticFeedbackType.SegmentTick, HapticFeedbackType.ContextClick,
    ),
    Tick(
        listOf(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f)),
        HapticFeedbackType.SegmentFrequentTick, HapticFeedbackType.SegmentTick, HapticFeedbackType.SegmentTick,
    ),
    Click(
        listOf(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f)),
        HapticFeedbackType.SegmentTick, HapticFeedbackType.ContextClick, HapticFeedbackType.VirtualKey,
    ),
    ToggleOn(
        listOf(
            HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f),
            HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, delayMs = 20),
        ),
        HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOn,
    ),
    ToggleOff(
        listOf(
            HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.9f),
            HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.7f, delayMs = 50),
        ),
        HapticFeedbackType.ToggleOff, HapticFeedbackType.ToggleOff, HapticFeedbackType.ToggleOff,
    ),
    Confirm(
        listOf(
            HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f),
            HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.7f, delayMs = 60),
        ),
        HapticFeedbackType.ContextClick, HapticFeedbackType.Confirm, HapticFeedbackType.Confirm,
    ),
    Reject(
        listOf(
            HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.9f),
            HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.7f, delayMs = 90),
        ),
        HapticFeedbackType.Reject, HapticFeedbackType.Reject, HapticFeedbackType.Reject,
    ),
    LongPress(
        listOf(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f)),
        HapticFeedbackType.ContextClick, HapticFeedbackType.LongPress, HapticFeedbackType.LongPress,
    ),
    ;

    val primitiveIds: IntArray = primitives.map { it.id }.distinct().toIntArray()

    fun fallbackFor(intensity: HapticsIntensity): HapticFeedbackType = when (intensity) {
        HapticsIntensity.LIGHT -> light
        HapticsIntensity.STRONG -> strong
        else -> normal
    }
}

/** Caches which composition primitives the default vibrator supports (queried once per process). */
private object PrimitiveSupport {
    private val cache = HashMap<Int, Boolean>()

    @Synchronized
    fun supportsAll(vibrator: Vibrator, ids: IntArray): Boolean {
        if (!vibrator.hasVibrator()) return false
        val unknown = ids.filter { it !in cache }
        if (unknown.isNotEmpty()) {
            val result = runCatching { vibrator.arePrimitivesSupported(*unknown.toIntArray()) }.getOrNull()
            unknown.forEachIndexed { index, id -> cache[id] = result?.getOrNull(index) == true }
        }
        return ids.all { cache[it] == true }
    }
}

private fun Context.defaultVibrator(): Vibrator? =
    runCatching { getSystemService(VibratorManager::class.java)?.defaultVibrator }.getOrNull()

@Composable
fun rememberAbleHaptics(): AbleHaptics {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val enabled = LocalAbleHapticsEnabled.current
    val intensity = if (enabled) LocalAbleHapticsIntensity.current else HapticsIntensity.OFF
    return remember(view, haptic, intensity) {
        AbleHaptics(view, haptic, if (view.isInEditMode) null else view.context.defaultVibrator(), intensity)
    }
}
