package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.gameplay.CharacterActions
import com.pafoid.skate.engine.input.UiNavigationActions
import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import org.lwjgl.glfw.GLFW
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GUI-09 review: Esc opens the pause menu in Play and only deselects while editing; no scene saves in Play. */
class PlayModeGuardsTest {
    private val actions = CharacterActions.register(UiNavigationActions.register(ActionMap()))
    private val cancel get() = actions.bindings(UiNavigationActions.CANCEL)
    private val defaultDeselect = EditorInputMappings().deselectAll.keyboardKey

    @Test
    fun DefaultDeselectKey_IsTheGameCancelKey() {
        // The conflict this guard exists for; if the defaults diverge the guard simply never triggers.
        assertTrue(cancel.any { it is ActionBinding.Key && it.key == defaultDeselect })
    }

    @Test
    fun Esc_DeselectsWhileEditing() {
        assertFalse(PlayModeGuards.deselectClaimedByGame(playing = false, deselectKey = defaultDeselect, cancelBindings = cancel))
    }

    @Test
    fun Esc_BelongsToThePauseMenuInPlay() {
        assertTrue(PlayModeGuards.deselectClaimedByGame(playing = true, deselectKey = defaultDeselect, cancelBindings = cancel))
    }

    @Test
    fun ReboundDeselectKey_StillWorksInPlay() {
        assertFalse(PlayModeGuards.deselectClaimedByGame(playing = true, deselectKey = GLFW.GLFW_KEY_BACKSPACE, cancelBindings = cancel))
    }

    @Test
    fun GameRebindingCancelAwayFromEsc_GivesEscBackToDeselect() {
        actions.clearBindings(UiNavigationActions.CANCEL)
        actions.addBinding(UiNavigationActions.CANCEL, ActionBinding.Key(GLFW.GLFW_KEY_BACKSPACE))
        assertFalse(PlayModeGuards.deselectClaimedByGame(playing = true, deselectKey = defaultDeselect, cancelBindings = cancel))
        assertTrue(PlayModeGuards.deselectClaimedByGame(playing = true, deselectKey = GLFW.GLFW_KEY_BACKSPACE, cancelBindings = cancel))
    }

    @Test
    fun GamepadCancelBindings_DoNotClaimKeyboardKeys() {
        val padOnly = listOf(ActionBinding.GamepadButton(defaultDeselect))
        assertFalse(PlayModeGuards.deselectClaimedByGame(playing = true, deselectKey = defaultDeselect, cancelBindings = padOnly))
    }

    @Test
    fun SceneSaves_AreRefusedInPlayOnly() {
        assertTrue(PlayModeGuards.canSaveScene(playing = false))
        assertFalse(PlayModeGuards.canSaveScene(playing = true))
    }
}
