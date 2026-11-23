package com.dragolabs.able.buttons

import com.dragolabs.able.feature.tools.buttons.ButtonGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeKeyGestureMachineTest {
    private fun machine(vararg gestures: ButtonGesture, undo: Boolean = true) = VolumeKeyGestureMachine().apply {
        config = VolumeKeyGestureMachine.Config(gestures = gestures.toSet(), doublePressMs = 300, longPressMs = 500, undoFirstStep = undo)
    }

    private fun VolumeKeyGestureMachine.press(key: VolumeKey, downAt: Long, upAt: Long): Pair<KeyDecision, KeyDecision> =
        onKey(key, true, 0, downAt) to onKey(key, false, 0, upAt)

    @Test
    fun noGestures_everythingPassesThrough() {
        val m = machine()
        val (down, up) = m.press(VolumeKey.UP, 0, 80)
        assertFalse(down.consume)
        assertFalse(up.consume)
        assertTrue(down.effects.isEmpty() && up.effects.isEmpty())
    }

    @Test
    fun doublePress_firstPassesSecondTriggersAndUndoes() {
        val m = machine(ButtonGesture.DOUBLE_UP)
        val (d1, u1) = m.press(VolumeKey.UP, 0, 80)
        assertFalse(d1.consume)
        assertFalse(u1.consume)
        val (d2, u2) = m.press(VolumeKey.UP, 200, 260)
        assertTrue(d2.consume)
        assertTrue(u2.consume)
        assertEquals(listOf(KeyEffect.Trigger(ButtonGesture.DOUBLE_UP), KeyEffect.UndoVolumeStep(VolumeKey.UP)), d2.effects)
    }

    @Test
    fun doublePress_withoutUndo_onlyTriggers() {
        val m = machine(ButtonGesture.DOUBLE_DOWN, undo = false)
        m.press(VolumeKey.DOWN, 0, 80)
        val (d2, _) = m.press(VolumeKey.DOWN, 200, 260)
        assertEquals(listOf(KeyEffect.Trigger(ButtonGesture.DOUBLE_DOWN)), d2.effects)
    }

    @Test
    fun doublePress_tooSlow_isTwoNormalPresses() {
        val m = machine(ButtonGesture.DOUBLE_UP)
        m.press(VolumeKey.UP, 0, 80)
        val (d2, _) = m.press(VolumeKey.UP, 500, 560)
        assertFalse(d2.consume)
        assertTrue(d2.effects.isEmpty())
    }

    @Test
    fun doublePress_thirdPressIsNormal() {
        val m = machine(ButtonGesture.DOUBLE_UP)
        m.press(VolumeKey.UP, 0, 80)
        m.press(VolumeKey.UP, 200, 260)
        val (d3, u3) = m.press(VolumeKey.UP, 400, 460)
        assertFalse(d3.consume)
        assertFalse(u3.consume)
    }

    @Test
    fun doublePress_heldFirstPressDoesNotCount() {
        val m = machine(ButtonGesture.DOUBLE_UP)
        m.onKey(VolumeKey.UP, true, 0, 0)
        m.onKey(VolumeKey.UP, true, 1, 520) // auto-repeat while changing volume
        m.onKey(VolumeKey.UP, false, 0, 900)
        val (d2, _) = m.press(VolumeKey.UP, 1000, 1060)
        assertFalse(d2.consume)
    }

    @Test
    fun doublePress_otherKeyDoesNotCount() {
        val m = machine(ButtonGesture.DOUBLE_UP)
        m.press(VolumeKey.UP, 0, 80)
        val (d, _) = m.press(VolumeKey.DOWN, 150, 200)
        assertFalse(d.consume)
    }

    @Test
    fun longPress_triggersAtDeadlineAndConsumesRest() {
        val m = machine(ButtonGesture.LONG_UP)
        val down = m.onKey(VolumeKey.UP, true, 0, 0)
        assertTrue(down.consume)
        assertEquals(500L, m.nextDeadline)
        assertTrue(m.onKey(VolumeKey.UP, true, 1, 400).consume)
        assertEquals(listOf(KeyEffect.Trigger(ButtonGesture.LONG_UP)), m.onTimeout(500))
        assertNull(m.nextDeadline)
        val up = m.onKey(VolumeKey.UP, false, 0, 900)
        assertTrue(up.consume)
        assertTrue(up.effects.isEmpty())
    }

    @Test
    fun longPress_shortReleaseEmulatesVolumeStep() {
        val m = machine(ButtonGesture.LONG_DOWN)
        val (down, up) = m.press(VolumeKey.DOWN, 0, 100)
        assertTrue(down.consume)
        assertTrue(up.consume)
        assertEquals(listOf(KeyEffect.VolumeStep(VolumeKey.DOWN)), up.effects)
        assertTrue(m.onTimeout(600).isEmpty())
    }

    @Test
    fun longPress_onlyAffectsItsKey() {
        val m = machine(ButtonGesture.LONG_UP)
        val (down, up) = m.press(VolumeKey.DOWN, 0, 100)
        assertFalse(down.consume)
        assertFalse(up.consume)
    }

    @Test
    fun longAndDouble_onSameKey() {
        val m = machine(ButtonGesture.LONG_UP, ButtonGesture.DOUBLE_UP)
        val (_, u1) = m.press(VolumeKey.UP, 0, 80)
        assertEquals(listOf(KeyEffect.VolumeStep(VolumeKey.UP)), u1.effects)
        val d2 = m.onKey(VolumeKey.UP, true, 0, 200)
        assertEquals(listOf(KeyEffect.Trigger(ButtonGesture.DOUBLE_UP), KeyEffect.UndoVolumeStep(VolumeKey.UP)), d2.effects)
        assertNull(m.nextDeadline)
        assertTrue(m.onKey(VolumeKey.UP, false, 0, 260).consume)
    }

    @Test
    fun combo_triggersOnQuickReleaseAndUndoesFirstStep() {
        val m = machine(ButtonGesture.VOLUME_BOTH)
        val upDown = m.onKey(VolumeKey.UP, true, 0, 0)
        assertFalse(upDown.consume)
        val downDown = m.onKey(VolumeKey.DOWN, true, 0, 50)
        assertTrue(downDown.consume)
        assertTrue(downDown.effects.isEmpty())
        val upUp = m.onKey(VolumeKey.UP, false, 0, 200)
        assertFalse(upUp.consume) // its DOWN was passed: keep the stream consistent
        assertEquals(listOf(KeyEffect.Trigger(ButtonGesture.VOLUME_BOTH), KeyEffect.UndoVolumeStep(VolumeKey.UP)), upUp.effects)
        val downUp = m.onKey(VolumeKey.DOWN, false, 0, 220)
        assertTrue(downUp.consume)
        assertTrue(downUp.effects.isEmpty())
    }

    @Test
    fun combo_longHoldIsLeftToAndroidAccessibilityShortcut() {
        val m = machine(ButtonGesture.VOLUME_BOTH)
        m.onKey(VolumeKey.UP, true, 0, 0)
        m.onKey(VolumeKey.DOWN, true, 0, 50)
        m.onKey(VolumeKey.DOWN, true, 1, 600)
        val release = m.onKey(VolumeKey.DOWN, false, 0, 3_200)
        assertTrue(release.effects.isEmpty())
        assertTrue(m.onKey(VolumeKey.UP, false, 0, 3_250).effects.isEmpty())
    }

    @Test
    fun combo_withLongPressPending_cancelsLongPress() {
        val m = machine(ButtonGesture.VOLUME_BOTH, ButtonGesture.LONG_UP)
        assertTrue(m.onKey(VolumeKey.UP, true, 0, 0).consume)
        assertTrue(m.onKey(VolumeKey.DOWN, true, 0, 60).consume)
        assertNull(m.nextDeadline)
        val upUp = m.onKey(VolumeKey.UP, false, 0, 150)
        assertTrue(upUp.consume)
        // UP's DOWN was consumed, so no volume step happened and nothing is undone.
        assertEquals(listOf(KeyEffect.Trigger(ButtonGesture.VOLUME_BOTH)), upUp.effects)
        assertTrue(m.onKey(VolumeKey.DOWN, false, 0, 170).consume)
    }

    @Test
    fun combo_secondKeyTooLate_isNormal() {
        val m = machine(ButtonGesture.VOLUME_BOTH)
        m.onKey(VolumeKey.UP, true, 0, 0)
        val late = m.onKey(VolumeKey.DOWN, true, 0, 900)
        assertFalse(late.consume)
    }

    @Test
    fun streamStaysConsistent_upWithoutDownPasses() {
        val m = machine(ButtonGesture.LONG_UP)
        assertFalse(m.onKey(VolumeKey.UP, false, 0, 10).consume)
    }

    @Test
    fun reset_dropsPendingState() {
        val m = machine(ButtonGesture.DOUBLE_UP)
        m.press(VolumeKey.UP, 0, 80)
        m.reset()
        assertFalse(m.onKey(VolumeKey.UP, true, 0, 200).consume)
    }
}
