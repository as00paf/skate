package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionOrigin
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.input.ProjectAction
import com.pafoid.skate.engine.input.ProjectActionSet
import com.pafoid.skate.engine.input.ProjectContext
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** INP-11: the project's actions load with its mappings and its start contexts follow Play. */
class InputSystemProjectActionsTest {
    private val path = "Assets/Data/input.actions.json"
    private val set = ProjectActionSet(
        contexts = listOf(ProjectContext("vehicle", activeAtStart = true)),
        actions = listOf(ProjectAction("fire", "Fire", defaults = listOf(ActionBinding.Key(70)))),
    )
    private val provider: InputProvider = mockk(relaxed = true)
    private val scene = Scene("Test")
    private val system = InputSystem(provider, EventSystem(), readText = { if (it == path) set.toJson() else null })
        .also { it.init(scene) }

    @Test
    fun InputSystem_MappingsLoadProjectActionsReadByScripts() {
        system.mappings = InputMappings(actionsPath = path)
        assertEquals(ActionOrigin.PROJECT, system.actionMap.definition("fire")!!.origin)

        every { provider.isKeyPressed(70) } returns true
        system.update(0.016f)
        assertTrue(system.reader.isActionDown("fire"))

        system.mappings = InputMappings()
        assertEquals(null, system.actionMap.definition("fire"))
    }

    @Test
    fun InputSystem_StartContextsFollowPlay() {
        system.mappings = InputMappings(actionsPath = path)
        val vehicle = InputContext("vehicle")

        system.update(0.016f)
        assertFalse(system.contexts.isActive(vehicle))
        scene.isRunning = true
        system.update(0.016f)
        assertTrue(system.contexts.isActive(vehicle))
        scene.isRunning = false
        system.update(0.016f)
        assertFalse(system.contexts.isActive(vehicle))
    }
}
