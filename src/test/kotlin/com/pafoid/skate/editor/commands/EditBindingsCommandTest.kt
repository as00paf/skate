package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.gameplay.CharacterActions
import com.pafoid.skate.engine.input.UiNavigationActions
import com.pafoid.skate.editor.commands.input.BindingOperation
import com.pafoid.skate.editor.commands.input.EditBindingsCommand
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.BindingProfile
import com.pafoid.skate.engine.input.ConflictResolution
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.Keys
import com.pafoid.skate.engine.input.RebindResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditBindingsCommandTest {
    private val map = CharacterActions.register(UiNavigationActions.register(ActionMap()))
    private val mappings = InputMappings()

    private fun cmd(op: BindingOperation) = EditBindingsCommand(map, mappings, op)
    private val defaultsOfJump = map.bindings(CharacterActions.JUMP)

    @Test
    fun KeyBindings_RebindKeyboardSlot_PersistsInMappings_AndUndoRestores() {
        val c = cmd(BindingOperation.Rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.Q)))
        assertFalse(c.isNoOp())
        c.execute()
        assertEquals(ActionBinding.Key(Keys.Q), map.bindings(CharacterActions.JUMP)[0])
        assertEquals(map.toProfile().overrides, mappings.bindings.overrides)
        c.undo()
        assertEquals(defaultsOfJump, map.bindings(CharacterActions.JUMP))
        assertTrue(mappings.bindings.overrides.isEmpty())
    }

    @Test
    fun KeyBindings_RebindGamepadSlot_Works() {
        cmd(BindingOperation.Rebind(CharacterActions.JUMP, 1, ActionBinding.GamepadButton(2))).execute()
        assertEquals(ActionBinding.GamepadButton(2), map.bindings(CharacterActions.JUMP)[1])
    }

    @Test
    fun KeyBindings_ConflictWithReject_IsNoOpAndReportsConflict() {
        val c = cmd(BindingOperation.Rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.E)))
        assertTrue(c.result is RebindResult.Conflict)
        assertTrue(c.isNoOp())
    }

    @Test
    fun KeyBindings_ConflictSwap_ExchangesBindings_AndUndoRestoresBoth() {
        val mount = map.bindings(CharacterActions.TOGGLE_MOUNT)
        val c = cmd(BindingOperation.Rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.E), ConflictResolution.SWAP))
        c.execute()
        assertEquals(ActionBinding.Key(Keys.E), map.bindings(CharacterActions.JUMP)[0])
        assertTrue(map.bindings(CharacterActions.TOGGLE_MOUNT).contains(ActionBinding.Key(Keys.SPACE)))
        c.undo()
        assertEquals(defaultsOfJump, map.bindings(CharacterActions.JUMP))
        assertEquals(mount, map.bindings(CharacterActions.TOGGLE_MOUNT))
    }

    @Test
    fun KeyBindings_ConflictUnbind_RemovesFromOther() {
        cmd(BindingOperation.Rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.E), ConflictResolution.UNBIND_OTHERS)).execute()
        assertFalse(map.bindings(CharacterActions.TOGGLE_MOUNT).contains(ActionBinding.Key(Keys.E)))
    }

    @Test
    fun KeyBindings_RemoveClearAndInvalid() {
        cmd(BindingOperation.Remove(CharacterActions.JUMP, 0)).execute()
        assertEquals(1, map.bindings(CharacterActions.JUMP).size)
        cmd(BindingOperation.Clear(CharacterActions.JUMP)).execute()
        assertTrue(map.bindings(CharacterActions.JUMP).isEmpty())
        assertTrue(cmd(BindingOperation.Remove(CharacterActions.JUMP, 5)).isNoOp())
        assertTrue(cmd(BindingOperation.Clear("nope")).isNoOp())
    }

    @Test
    fun KeyBindings_ResetActionCategoryAll() {
        cmd(BindingOperation.Clear(CharacterActions.JUMP)).execute()
        cmd(BindingOperation.Clear(CharacterActions.SPRINT)).execute()
        cmd(BindingOperation.Reset(listOf(CharacterActions.JUMP))).execute()
        assertEquals(defaultsOfJump, map.bindings(CharacterActions.JUMP))
        assertTrue(map.isCustomized(CharacterActions.SPRINT))
        val all = cmd(BindingOperation.Reset(map.names.toList()))
        all.execute()
        assertTrue(mappings.bindings.overrides.isEmpty())
        all.undo()
        assertTrue(map.isCustomized(CharacterActions.SPRINT))
    }

    @Test
    fun KeyBindings_ResetWithNothingCustomized_IsNoOp() {
        assertTrue(cmd(BindingOperation.Reset(map.names.toList())).isNoOp())
    }

    @Test
    fun KeyBindings_LoadProfile_RoundTripsThroughJson_AndUndo() {
        cmd(BindingOperation.Rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.Q))).execute()
        val json = map.toProfile("Mine").toJson()
        cmd(BindingOperation.Reset(map.names.toList())).execute()
        val load = cmd(BindingOperation.LoadProfile(BindingProfile.fromJson(json)))
        load.execute()
        assertEquals(ActionBinding.Key(Keys.Q), map.bindings(CharacterActions.JUMP)[0])
        assertEquals("Mine", mappings.bindings.name)
        load.undo()
        assertEquals(defaultsOfJump, map.bindings(CharacterActions.JUMP))
        assertEquals("Default", mappings.bindings.name)
    }

    @Test
    fun KeyBindings_CommandDoesNotTouchLiveMapUntilExecuted() {
        cmd(BindingOperation.Clear(CharacterActions.JUMP))
        assertEquals(defaultsOfJump, map.bindings(CharacterActions.JUMP))
    }
}
