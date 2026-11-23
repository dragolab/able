package com.dragolabs.able.buttons

import com.dragolabs.able.feature.tools.buttons.ButtonGesture

/** The two keys Able can map. The power key is never delivered to Accessibility services. */
enum class VolumeKey { UP, DOWN }

/** Side effects the service performs for a key event; the machine itself has no Android types. */
sealed interface KeyEffect {
    data class Trigger(val gesture: ButtonGesture) : KeyEffect

    /** Change the volume by one step, as the consumed short press would have done. */
    data class VolumeStep(val key: VolumeKey) : KeyEffect

    /** Revert the step that the passed-through first press already applied. */
    data class UndoVolumeStep(val key: VolumeKey) : KeyEffect
}

data class KeyDecision(val consume: Boolean, val effects: List<KeyEffect> = emptyList())

/**
 * Volume key gesture recognition (BTN-001), pure and unit-tested.
 *
 * Accessibility services can only *filter* key events: an event that is passed on cannot be
 * delayed and replayed later, and a consumed one cannot be re-injected. The machine therefore
 * decides on each event, keeping the stream well formed (a consumed DOWN always has its UP
 * consumed, a passed DOWN always has its UP passed):
 *
 * - **Double press:** the first press passes through (the volume changes by one step, then
 *   optionally reverted with [KeyEffect.UndoVolumeStep]); only the second press, if it starts
 *   within the double-press window after the first release, is consumed and triggers. Normal
 *   presses are never delayed.
 * - **Long press:** only when a long press is assigned to that key, its DOWN is consumed and a
 *   timer runs. Released early, Able applies the step itself ([KeyEffect.VolumeStep]) at release;
 *   held past the long-press time, the action runs. Holding the key to change the volume
 *   continuously is therefore not available for that key (documented trade-off).
 * - **Both keys together:** the second key going down while the first is held, within
 *   [COMBO_WINDOW_MS], starts a combo; it triggers when the first of the two keys is released,
 *   only if both were held less than [COMBO_MAX_HOLD_MS]. Holding both volume keys is also
 *   Android's Accessibility shortcut (about 3 s): Android detects that chord in the window
 *   policy (`interceptKeyBeforeQueueing`, AOSP PhoneWindowManager / KeyGestureController, main,
 *   checked 2026-11-22), before Accessibility key filtering, so consuming here never blocks
 *   it, and a long hold never also runs Able's action. A passed-through first key's step is
 *   reverted.
 *
 * Times are monotonic milliseconds. The caller schedules [onTimeout] at [nextDeadline].
 */
class VolumeKeyGestureMachine {
    data class Config(
        val gestures: Set<ButtonGesture> = emptySet(),
        val doublePressMs: Int = 350,
        val longPressMs: Int = 500,
        val undoFirstStep: Boolean = true,
    )

    private class KeyState {
        var pressed = false
        var downAt = 0L
        var consumed = false
        var longPending = false
        var fired = false
        var lastTapUpAt = 0L

        /** True when the current/last press changed the volume (passed through or emulated). */
        var steppedVolume = false
    }

    var config = Config()
        set(value) {
            field = value
            reset()
        }

    private val states = mapOf(VolumeKey.UP to KeyState(), VolumeKey.DOWN to KeyState())

    /** Start of a pending "both keys" combo, 0 when none. */
    private var comboStartAt = 0L
    private var comboUndo: VolumeKey? = null

    /** Uptime at which [onTimeout] must be called, or null. */
    val nextDeadline: Long?
        get() = states.values.filter { it.longPending }.minOfOrNull { it.downAt + config.longPressMs }

    fun reset() {
        comboStartAt = 0L
        comboUndo = null
        states.values.forEach {
            it.pressed = false
            it.consumed = false
            it.longPending = false
            it.fired = false
            it.lastTapUpAt = 0L
            it.steppedVolume = false
        }
    }

    fun onKey(key: VolumeKey, down: Boolean, repeatCount: Int, timeMs: Long): KeyDecision {
        val state = states.getValue(key)
        return if (down) onDown(key, state, repeatCount, timeMs) else onUp(key, state, timeMs)
    }

    private fun other(key: VolumeKey) = if (key == VolumeKey.UP) VolumeKey.DOWN else VolumeKey.UP

    private fun onDown(key: VolumeKey, state: KeyState, repeatCount: Int, timeMs: Long): KeyDecision {
        if (repeatCount > 0 || state.pressed) {
            // Auto-repeat of a held key follows the decision taken for its first DOWN.
            return KeyDecision(consume = state.consumed)
        }
        val gestures = config.gestures
        state.pressed = true
        state.downAt = timeMs
        state.fired = false
        state.longPending = false

        // 1. Both keys together.
        val otherKey = other(key)
        val otherState = states.getValue(otherKey)
        if (ButtonGesture.VOLUME_BOTH in gestures && otherState.pressed && !otherState.fired &&
            timeMs - otherState.downAt <= COMBO_WINDOW_MS
        ) {
            comboStartAt = timeMs
            comboUndo = if (!otherState.consumed && config.undoFirstStep) otherKey else null
            otherState.longPending = false
            otherState.fired = true
            otherState.lastTapUpAt = 0L
            state.consumed = true
            state.fired = true
            state.lastTapUpAt = 0L
            return KeyDecision(consume = true)
        }

        // 2. Second press of a double press.
        val double = doubleGesture(key)
        if (double in gestures && state.lastTapUpAt != 0L && timeMs - state.lastTapUpAt in 0..config.doublePressMs.toLong()) {
            val effects = mutableListOf<KeyEffect>(KeyEffect.Trigger(double))
            if (state.steppedVolume && config.undoFirstStep) effects += KeyEffect.UndoVolumeStep(key)
            state.lastTapUpAt = 0L
            state.steppedVolume = false
            state.consumed = true
            state.fired = true
            return KeyDecision(consume = true, effects = effects)
        }

        // 3. Possible long press: hold the DOWN back and decide at release or timeout.
        if (longGesture(key) in gestures) {
            state.consumed = true
            state.longPending = true
            state.steppedVolume = false
            return KeyDecision(consume = true)
        }

        // 4. Ordinary press: pass it on untouched.
        state.consumed = false
        state.steppedVolume = true
        return KeyDecision(consume = false)
    }

    private fun onUp(key: VolumeKey, state: KeyState, timeMs: Long): KeyDecision {
        if (!state.pressed) return KeyDecision(consume = false)
        state.pressed = false
        val wasShort = timeMs - state.downAt < config.longPressMs
        val comboEffects = finishCombo(timeMs)
        if (comboEffects != null) return KeyDecision(consume = state.consumed, effects = comboEffects)
        if (!state.consumed) {
            // Passed press: it can be the first half of a double press if it was a short tap.
            state.lastTapUpAt = if (!state.fired && wasShort) timeMs else 0L
            return KeyDecision(consume = false)
        }
        if (state.longPending) {
            // Released before the long-press time: give the user the volume step they expect.
            state.longPending = false
            state.steppedVolume = true
            state.lastTapUpAt = timeMs
            return KeyDecision(consume = true, effects = listOf(KeyEffect.VolumeStep(key)))
        }
        state.lastTapUpAt = 0L
        return KeyDecision(consume = true)
    }

    /** First release of a pending combo: trigger if it was a short press of both keys. */
    private fun finishCombo(timeMs: Long): List<KeyEffect>? {
        if (comboStartAt == 0L) return null
        val held = timeMs - comboStartAt
        val undo = comboUndo
        comboStartAt = 0L
        comboUndo = null
        if (held >= COMBO_MAX_HOLD_MS) return emptyList()
        return buildList {
            add(KeyEffect.Trigger(ButtonGesture.VOLUME_BOTH))
            if (undo != null) add(KeyEffect.UndoVolumeStep(undo))
        }
    }

    /** Fires long presses whose time has come. */
    fun onTimeout(timeMs: Long): List<KeyEffect> {
        val effects = mutableListOf<KeyEffect>()
        for ((key, state) in states) {
            if (state.longPending && state.pressed && timeMs - state.downAt >= config.longPressMs) {
                state.longPending = false
                state.fired = true
                state.lastTapUpAt = 0L
                effects += KeyEffect.Trigger(longGesture(key))
            }
        }
        return effects
    }

    companion object {
        /** Max gap between the two DOWNs of "both keys together". */
        const val COMBO_WINDOW_MS = 400L

        /** Holding both keys this long is left to Android's Accessibility shortcut. */
        const val COMBO_MAX_HOLD_MS = 1_000L

        fun doubleGesture(key: VolumeKey) = if (key == VolumeKey.UP) ButtonGesture.DOUBLE_UP else ButtonGesture.DOUBLE_DOWN

        fun longGesture(key: VolumeKey) = if (key == VolumeKey.UP) ButtonGesture.LONG_UP else ButtonGesture.LONG_DOWN
    }
}
