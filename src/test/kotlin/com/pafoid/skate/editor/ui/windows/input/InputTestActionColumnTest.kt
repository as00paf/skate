package com.pafoid.skate.editor.ui.windows.input

import com.pafoid.skate.editor.ui.windows.InputLogAction
import com.pafoid.skate.editor.ui.windows.InputLogEntry
import com.pafoid.skate.editor.ui.windows.InputTestingModel
import com.pafoid.skate.engine.gameplay.CharacterActions
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionDefinition
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.ContextBlocking
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.InputContextStack
import com.pafoid.skate.engine.input.Keys
import com.pafoid.skate.engine.input.UiNavigationActions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-52I: the event log's Action column names the bound actions each input fired (EDT-51 §3.1). */
class InputTestActionColumnTest {

    private val map = CharacterActions.register(UiNavigationActions.register(ActionMap()))
    private val ride = InputContext("ride")

    @Test
    fun ActionColumn_KeyEventListsTheActionsBoundToIt() {
        val stack = InputContextStack.withDefaults()
        assertEquals(listOf(InputLogAction(CharacterActions.JUMP, live = true)),
            InputTestingModel.actionsFor(map, stack, ActionBinding.Key(Keys.SPACE)))
        assertEquals(listOf(InputLogAction(CharacterActions.TOGGLE_MOUNT, live = true)),
            InputTestingModel.actionsFor(map, stack, ActionBinding.Key(Keys.E)))
        assertTrue(InputTestingModel.actionsFor(map, stack, ActionBinding.Key(Keys.Q)).isEmpty(), "unbound key fires nothing")
    }

    @Test
    fun ActionColumn_GamepadButtonCanFireActionsInSeveralContexts() {
        val actions = InputTestingModel.actionsFor(map, InputContextStack.withDefaults(), ActionBinding.GamepadButton(0))
        assertEquals(setOf(CharacterActions.JUMP, UiNavigationActions.CONFIRM), actions.map { it.actionId }.toSet())
        assertTrue(actions.all { it.live }, "ui passes everything through, so both fire")
    }

    @Test
    fun ActionColumn_ShadowedOrSilencedActionsAreNotLive() {
        map.define(ActionDefinition("ride.ollie", ride, "Ride", defaults = listOf(ActionBinding.Key(Keys.SPACE))))
        val stack = InputContextStack.withDefaults()
        assertEquals(listOf(InputLogAction(CharacterActions.JUMP, true), InputLogAction("ride.ollie", false)),
            InputTestingModel.actionsFor(map, stack, ActionBinding.Key(Keys.SPACE)), "ride is not on the stack")

        val h = stack.push(ride, priority = 10, blocking = ContextBlocking.CONSUME_BOUND)
        assertEquals(listOf(InputLogAction(CharacterActions.JUMP, false), InputLogAction("ride.ollie", true)),
            InputTestingModel.actionsFor(map, stack, ActionBinding.Key(Keys.SPACE)), "ride takes Space from jump")
        stack.pop(h)

        stack.push(InputContext.UI, priority = 1_000, blocking = ContextBlocking.ALL)
        assertEquals(listOf(InputLogAction(CharacterActions.JUMP, false), InputLogAction("ride.ollie", false)),
            InputTestingModel.actionsFor(map, stack, ActionBinding.Key(Keys.SPACE)), "the pause menu silences gameplay")
    }

    @Test
    fun ActionColumn_AxisBindingsMatchTheirStickAndNoMapMeansNoActions() {
        val actions = InputTestingModel.actionsFor(map, null, ActionBinding.GamepadAxis(4))
        assertEquals(listOf(InputLogAction(CharacterActions.SPRINT, true)), actions)
        assertTrue(InputTestingModel.actionsFor(null, null, ActionBinding.Key(Keys.SPACE)).isEmpty())
    }

    @Test
    fun ActionColumn_CopiedLineNamesTheActions() {
        val entry = InputLogEntry("Keyboard: space Pressed", listOf(InputLogAction("jump", true), InputLogAction("ride.ollie", false)))
        assertEquals("Keyboard: space Pressed -> jump, ride.ollie?", entry.line { it.actionId + if (it.live) "" else "?" })
        assertEquals("Mouse: 0 Released", InputLogEntry("Mouse: 0 Released", emptyList()).line { it.actionId })
    }
}
