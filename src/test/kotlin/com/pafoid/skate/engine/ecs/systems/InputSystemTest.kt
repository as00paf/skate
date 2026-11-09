package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.events.ActionEvent
import com.pafoid.skate.engine.events.ActionPhase
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionDefinition
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.input.Keys
import com.pafoid.skate.engine.ui.UiActions
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class InputSystemTest {

    private lateinit var inputProvider: InputProvider
    private lateinit var eventSystem: EventSystem
    private lateinit var scene: Scene
    private lateinit var inputSystem: InputSystem

    @BeforeEach
    fun setup() {
        inputProvider = mockk(relaxed = true)
        eventSystem = EventSystem()
        scene = Scene("TestScene")
        scene.isRunning = true
        inputSystem = InputSystem(inputProvider = inputProvider, eventSystem = eventSystem)
        inputSystem.init(scene)
    }

    private fun defineEventAction(id: String = "test.fire") {
        inputSystem.actionMap.define(
            ActionDefinition(id, InputContext.GAMEPLAY, "Test", defaults = listOf(ActionBinding.Key(Keys.SPACE)), publishEvents = true)
        )
    }

    /** GUI-09: while the pause menu is open Esc still reaches the UI. */
    @Test
    fun InputSystem_PausedStillReadsUiCancel() {
        scene.isPaused = true
        every { inputProvider.isKeyPressed(GLFW.GLFW_KEY_ESCAPE) } returns true
        every { inputProvider.keyBeginPress(GLFW.GLFW_KEY_ESCAPE) } returns true

        inputSystem.update(0.016f)

        assertTrue(inputSystem.runsWhilePaused)
        assertTrue(inputSystem.reader.wasActionPressed(UiActions.CANCEL), "Esc must reach the pause menu")
    }

    @Test
    fun InputSystem_InitializesGamepadOnce() {
        assertEquals(SystemManager.ExecutionPriority.EARLY, inputSystem.priority)
        verify(exactly = 1) { inputProvider.initializeGamepad() }
    }

    @Test
    fun InputSystem_RefreshesGamepadsBeforeReadingActions() {
        defineEventAction()
        inputSystem.update(0.016f)
        verifyOrder {
            inputProvider.refreshGamepadState()
            inputProvider.isKeyPressed(any())
        }
    }

    @Test
    fun InputSystem_EngineRegistersOnlyUiNavigation() {
        val contexts = inputSystem.actionMap.definitions().map { it.context }.toSet()
        assertEquals(setOf(InputContext.UI), contexts)
        assertTrue(inputSystem.actionMap.definition("jump") == null, "character actions are an opt-in preset")
    }

    @Test
    fun InputSystem_NoLongerWritesCharacterInput() {
        val state = InputStateComponent()
        scene.children.add(GameObject("Player").addComponent(state))
        state.jumpHeld = true; state.moveDirection.set(1f, 0f)
        every { inputProvider.isKeyPressed(any()) } returns false
        inputSystem.update(0.016f)
        assertTrue(state.jumpHeld, "filled by CharacterInputSampler, not by InputSystem")
        assertEquals(1f, state.moveDirection.x)
    }

    @Test
    fun InputSystem_PublishesActionEventsOnEdgesOnlyForOptedInActions() {
        defineEventAction()
        inputSystem.actionMap.define(ActionDefinition("test.quiet", InputContext.GAMEPLAY, "Test", defaults = listOf(ActionBinding.Key(Keys.SPACE))))
        val events = mutableListOf<ActionEvent>()
        eventSystem.subscribe<ActionEvent> { events.add(it) }

        every { inputProvider.isKeyPressed(Keys.SPACE) } returns true
        inputSystem.update(0.016f)
        inputSystem.update(0.016f)
        every { inputProvider.isKeyPressed(Keys.SPACE) } returns false
        inputSystem.update(0.016f)

        assertEquals(listOf(ActionEvent("test.fire", ActionPhase.STARTED, 1f), ActionEvent("test.fire", ActionPhase.CANCELED, 0f)), events)
    }

    @Test
    fun InputSystem_SamplesHistoryEachUpdate() {
        var x = 0f
        inputSystem.history.track("probe", { x }, { 0f })
        inputSystem.update(0.1f)
        x = 1f
        inputSystem.update(0.1f)
        assertEquals(10f, inputSystem.history.velocity("probe", 1f).x, 1e-3f)
    }

    @Test
    fun InputSystem_ExposesContextStackWithBaseContexts() {
        assertTrue(inputSystem.contexts.isActive(InputContext.GAMEPLAY))
        assertTrue(inputSystem.contexts.isActive(InputContext.UI))
        assertFalse(inputSystem.contexts.isActive(InputContext.EDITOR))
    }

    @Test
    fun InputSystem_HandlesMissingJoystick() {
        every { inputProvider.isJoystickPresent(any()) } returns false
        inputSystem.update(0.016f)
    }
}
