package com.dragolabs.able.buttons

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import com.dragolabs.able.feature.tools.buttons.ButtonGesture
import com.dragolabs.able.feature.tools.buttons.ButtonMapperConfig
import com.dragolabs.able.feature.tools.buttons.ButtonMapperSettings

/**
 * Volume key shortcuts (BTN-001..BTN-003) through `AccessibilityService.onKeyEvent`.
 *
 * The service asks for key filtering (`FLAG_REQUEST_FILTER_KEY_EVENTS`, allowed by
 * `android:canRequestFilterKeyEvents="true"`) only while [wantsKeyEvents] is true, so with the
 * tool off no key passes through Able. `onKeyEvent` sees events "before they are passed to the
 * rest of the system"; returning true consumes them (AccessibilityService#onKeyEvent reference,
 * checked 2026-11-22). Decisions come from the pure [VolumeKeyGestureMachine].
 *
 * Honest limits, also shown in the tool:
 * - the power key is not delivered to Accessibility services (Android's window policy handles
 *   it before input filtering), so it cannot be remapped; screen on/off can only be observed;
 * - with the screen off Android routes volume keys to the audio system, not to the filter, so
 *   shortcuts work only with the screen on (lock screen included);
 * - during calls and while ringing every key passes through untouched.
 */
internal class ButtonShortcutController(
    private val context: Context,
    private val onActiveChanged: () -> Unit,
    private val haptic: () -> Unit,
) {
    private val settings = ButtonMapperSettings(context)
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val executor = ButtonActionExecutor(context)
    private val machine = VolumeKeyGestureMachine()
    private var config = ButtonMapperConfig()

    private val timeoutRunnable = Runnable { onTimeout() }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != ButtonMapperSettings.KEY_LAST_DETECTED) reload()
    }

    /** True when key filtering is needed (tool on and at least one gesture mapped, or testing). */
    val wantsKeyEvents: Boolean get() = config.active || (config.enabled && config.testMode)

    fun start() {
        settings.registerListener(prefsListener)
        reload()
    }

    fun stop() {
        settings.unregisterListener(prefsListener)
        handler.removeCallbacksAndMessages(null)
        machine.reset()
    }

    private fun reload() {
        val wasActive = wantsKeyEvents
        config = settings.read()
        val gestures = if (config.testMode) ButtonGesture.entries.toSet() else config.mappings.keys
        machine.config = VolumeKeyGestureMachine.Config(
            gestures = gestures,
            doublePressMs = config.doublePressMs,
            longPressMs = config.longPressMs,
            undoFirstStep = config.undoVolumeStep,
        )
        handler.removeCallbacks(timeoutRunnable)
        if (wasActive != wantsKeyEvents) onActiveChanged()
    }

    fun onScreenOff() {
        machine.reset()
        handler.removeCallbacks(timeoutRunnable)
    }

    /** Must return quickly: Android waits for the answer before dispatching the key. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!wantsKeyEvents) return false
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> VolumeKey.UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKey.DOWN
            else -> return false
        }
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        if (down && event.repeatCount == 0 && !canIntercept()) {
            machine.reset()
            return false
        }
        val decision = machine.onKey(key, down, event.repeatCount, event.eventTime)
        decision.effects.forEach(::apply)
        scheduleTimeout()
        return decision.consume
    }

    private fun canIntercept(): Boolean {
        if (power?.isInteractive != true) return false
        val mode = audio?.mode ?: AudioManager.MODE_NORMAL
        return mode == AudioManager.MODE_NORMAL
    }

    private fun scheduleTimeout() {
        handler.removeCallbacks(timeoutRunnable)
        val deadline = machine.nextDeadline ?: return
        handler.postAtTime(timeoutRunnable, deadline)
    }

    private fun onTimeout() {
        machine.onTimeout(SystemClock.uptimeMillis()).forEach(::apply)
        scheduleTimeout()
    }

    private fun apply(effect: KeyEffect) {
        when (effect) {
            is KeyEffect.Trigger -> trigger(effect.gesture)
            is KeyEffect.VolumeStep -> adjust(if (effect.key == VolumeKey.UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            is KeyEffect.UndoVolumeStep -> adjust(if (effect.key == VolumeKey.UP) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE, 0)
        }
    }

    /** Lets Android choose the stream (media, ring, call) as the volume key itself would. */
    private fun adjust(direction: Int, flags: Int) {
        runCatching { audio?.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, flags) }
    }

    private fun trigger(gesture: ButtonGesture) {
        val testOnly = config.testMode
        settings.reportDetected(gesture, System.currentTimeMillis(), testOnly)
        if (config.haptic || testOnly) haptic()
        if (testOnly) return
        val target = config.mappings[gesture] ?: return
        executor.run(target)
    }
}
