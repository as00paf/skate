package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.input.SetDeadZonesCommand
import com.pafoid.skate.editor.commands.input.snapshot
import com.pafoid.skate.engine.input.DeadZoneMode
import com.pafoid.skate.engine.input.DeadZoneSettings
import org.joml.Vector2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SetDeadZonesCommandTest {
    private fun edited(base: DeadZoneSettings, change: DeadZoneSettings.() -> Unit) = base.snapshot().apply(change)

    @Test
    fun InputSettingsCommand_ExecuteAppliesAfter_UndoRestoresBefore_KeepingInstances() {
        val live = DeadZoneSettings()
        val leftRef = live.leftStick
        val before = live.snapshot()
        val after = edited(live) {
            leftStick.mode = DeadZoneMode.AXIAL; leftStick.inner = 0.3f; leftStick.sensitivity = 2f
            rightTrigger.pressThreshold = 0.8f
        }
        val cmd = SetDeadZonesCommand(live, before, after)
        cmd.execute()
        assertEquals(DeadZoneMode.AXIAL, live.leftStick.mode)
        assertEquals(0.3f, live.leftStick.inner)
        assertEquals(2f, live.leftStick.sensitivity)
        assertEquals(0.8f, live.rightTrigger.pressThreshold)
        assertSame(leftRef, live.leftStick)
        cmd.undo()
        assertEquals(before, live)
    }

    @Test
    fun InputSettingsCommand_ValidatesAfterValues() {
        val live = DeadZoneSettings()
        SetDeadZonesCommand(live, live.snapshot(), edited(live) { leftStick.inner = 5f; leftStick.sensitivity = 99f }).execute()
        assertEquals(0.9f, live.leftStick.inner)
        assertEquals(5f, live.leftStick.sensitivity)
    }

    @Test
    fun InputSettingsCommand_NoOpDetected() {
        val live = DeadZoneSettings()
        assertTrue(SetDeadZonesCommand(live, live.snapshot(), live.snapshot()).isNoOp())
        assertFalse(SetDeadZonesCommand(live, live.snapshot(), edited(live) { leftStick.curve = 2f }).isNoOp())
    }

    @Test
    fun InputSettingsCommand_SensitivityTakesEffectInProcessing() {
        val live = DeadZoneSettings()
        val out = Vector2f()
        live.rightStick.apply(1f, 0f, out)
        assertEquals(1f, out.x, 1e-4f)
        SetDeadZonesCommand(live, live.snapshot(), edited(live) { rightStick.sensitivity = 2f }).execute()
        live.rightStick.apply(1f, 0f, out)
        assertEquals(2f, out.x, 1e-4f)
    }
}
