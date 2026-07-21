package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.events.EnvironmentAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnvironmentActionHandlerTest {

    @Test
    fun `set sun direction requested executes through undo manager and updates config`() {
        val eventSystem = EventSystem()
        val undoRedoManager = mockk<UndoRedoManager>()
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }
        val handler = EnvironmentActionHandler(undoRedoManager, eventSystem)

        val lightConfig = DirectionalLightComponent(direction = Vector3f(0f, -1f, 0f))
        eventSystem.publish(
            EnvironmentAction.SetSunDirectionRequested(
                lightConfig = lightConfig,
                oldValue = Vector3f(0f, -1f, 0f),
                newValue = Vector3f(1f, -1f, 0f).normalize(),
            )
        )

        verify(exactly = 1) { undoRedoManager.executeCommand(any()) }
        assertEquals(Vector3f(1f, -1f, 0f).normalize().x, lightConfig.direction.x, 0.0001f)
        assertEquals(Vector3f(1f, -1f, 0f).normalize().y, lightConfig.direction.y, 0.0001f)
        assertEquals(Vector3f(1f, -1f, 0f).normalize().z, lightConfig.direction.z, 0.0001f)
    }

    @Test
    fun `set ambient light and intensity requested execute through command flow`() {
        val eventSystem = EventSystem()
        val undoRedoManager = mockk<UndoRedoManager>()
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }
        val handler = EnvironmentActionHandler(undoRedoManager, eventSystem)

        val lightingState = LightingStateComponent(ambientLight = Vector3f(0.2f, 0.2f, 0.2f))
        val dayNight = DayNightCycleComponent(ambientIntensity = 0.5f)

        eventSystem.publish(
            EnvironmentAction.SetAmbientLightRequested(
                lightingStateComponent = lightingState,
                oldValue = Vector3f(0.2f, 0.2f, 0.2f),
                newValue = Vector3f(0.4f, 0.5f, 0.6f),
            )
        )
        eventSystem.publish(
            EnvironmentAction.SetAmbientIntensityRequested(
                dayNightCycle = dayNight,
                oldValue = 0.5f,
                newValue = 1.8f,
            )
        )

        verify(exactly = 2) { undoRedoManager.executeCommand(any()) }
        assertEquals(0.4f, lightingState.ambientLight.x, 0.0001f)
        assertEquals(0.5f, lightingState.ambientLight.y, 0.0001f)
        assertEquals(0.6f, lightingState.ambientLight.z, 0.0001f)
        assertEquals(1.8f, dayNight.ambientIntensity, 0.0001f)
    }
}
