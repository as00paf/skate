package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.input.BindingOperation
import com.pafoid.skate.editor.commands.input.EditBindingsCommand
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.BindingProfile
import com.pafoid.skate.engine.input.ConflictResolution
import com.pafoid.skate.engine.input.DefaultActions
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.Keys
import com.pafoid.skate.engine.input.RebindResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditBindingsCommandTest {
    private val map = DefaultActions.register(ActionMap())
    private val mappings = InputMappings()

    private fun cmd(op: BindingOperation) = EditBindingsCommand(map, mappings, op)
    private val defaultsOfJump = map.bindings(DefaultActions.JUMP)

    @Test
    fun KeyBindings_RebindKeyboardSlot_PersistsInMappings_AndUndoRestores() {
        val c = cmd(BindingOperation.Rebind(DefaultActions.JUMP, 0, ActionBinding.Key(Keys.Q)))
        assertFalse(c.isNoOp())
        c.execute()
        assertEquals(ActionBinding.Key(Keys.Q), map.bindings(DefaultActions.JUMP)[0])
        assertEquals(map.toProfile().overrides, mappings.bindings.overrides)
        c.undo()
        assertEquals(defaultsOfJump, map.bindings(DefaultActions.JUMP))
        assertTrue(mappings.bindings.overrides.isEmpty())
    }

    @Test
    fun KeyBindings_RebindGamepadSlot_Works() {
        cmd(BindingOperation.Rebind(DefaultActions.JUMP, 1, ActionBinding.GamepadButton(2))).execute()
        assertEquals(ActionBinding.GamepadButton(2), map.bindings(DefaultActions.JUMP)[1])
    }

    @Test
    fun KeyBindings_ConflictWithReject_IsNoOpAndReportsConflict() {
        val c = cmd(BindingOperation.Rebind(DefaultActions.JUMP, 0, ActionBinding.Key(Keys.E)))
        assertTrue(c.result is RebindResult.Conflict)
        assertTrue(c.isNoOp())
    }

    @Test
    fun KeyBindings_ConflictSwap_ExchangesBindings_AndUndoRestoresBoth() {
        val mount = map.bindings(DefaultActions.TOGGLE_MOUNT)
        val c = cmd(BindingOperation.Rebind(DefaultActions.JUMP, 0, ActionBinding.Key(Keys.E), ConflictResolution.SWAP))
        c.execute()
        assertEquals(ActionBinding.Key(Keys.E), map.bindings(DefaultActions.JUMP)[0])
        assertTrue(map.bindings(DefaultActions.TOGGLE_MOUNT).contains(ActionBinding.Key(Keys.SPACE)))
        c.undo()
        assertEquals(defaultsOfJump, map.bindings(DefaultActions.JUMP))
        assertEquals(mount, map.bindings(DefaultActions.TOGGLE_MOUNT))
    }

    @Test
    fun KeyBindings_ConflictUnbind_RemovesFromOther() {
        cmd(BindingOperation.Rebind(DefaultActions.JUMP, 0, ActionBinding.Key(Keys.E), ConflictResolution.UNBIND_OTHERS)).execute()
        assertFalse(map.bindings(DefaultActions.TOGGLE_MOUNT).contains(ActionBinding.Key(Keys.E)))
    }

    @Test
    fun KeyBindings_RemoveClearAndInvalid() {
        cmd(BindingOperation.Remove(DefaultActions.JUMP, 0)).execute()
        assertEquals(1, map.bindings(DefaultActions.JUMP).size)
        cmd(BindingOperation.Clear(DefaultActions.JUMP)).execute()
        assertTrue(map.bindings(DefaultActions.JUMP).isEmpty())
        assertTrue(cmd(BindingOperation.Remove(DefaultActions.JUMP, 5)).isNoOp())
        assertTrue(cmd(BindingOperation.Clear("nope")).isNoOp())
    }

    @Test
    fun KeyBindings_ResetActionCategoryAll() {
        cmd(BindingOperation.Clear(DefaultActions.JUMP)).execute()
        cmd(BindingOperation.Clear(DefaultActions.SPRINT)).execute()
        cmd(BindingOperation.Reset(listOf(DefaultActions.JUMP))).execute()
        assertEquals(defaultsOfJump, map.bindings(DefaultActions.JUMP))
        assertTrue(map.isCustomized(DefaultActions.SPRINT))
        val all = cmd(BindingOperation.Reset(map.names.toList()))
        all.execute()
        assertTrue(mappings.bindings.overrides.isEmpty())
        all.undo()
        assertTrue(map.isCustomized(DefaultActions.SPRINT))
    }

    @Test
    fun KeyBindings_ResetWithNothingCustomized_IsNoOp() {
        assertTrue(cmd(BindingOperation.Reset(map.names.toList())).isNoOp())
    }

    @Test
    fun KeyBindings_LoadProfile_RoundTripsThroughJson_AndUndo() {
        cmd(BindingOperation.Rebind(DefaultActions.JUMP, 0, ActionBinding.Key(Keys.Q))).execute()
        val json = map.toProfile("Mine").toJson()
        cmd(BindingOperation.Reset(map.names.toList())).execute()
        val load = cmd(BindingOperation.LoadProfile(BindingProfile.fromJson(json)))
        load.execute()
        assertEquals(ActionBinding.Key(Keys.Q), map.bindings(DefaultActions.JUMP)[0])
        assertEquals("Mine", mappings.bindings.name)
        load.undo()
        assertEquals(defaultsOfJump, map.bindings(DefaultActions.JUMP))
        assertEquals("Default", mappings.bindings.name)
    }

    @Test
    fun KeyBindings_CommandDoesNotTouchLiveMapUntilExecuted() {
        cmd(BindingOperation.Clear(DefaultActions.JUMP))
        assertEquals(defaultsOfJump, map.bindings(DefaultActions.JUMP))
    }
}
