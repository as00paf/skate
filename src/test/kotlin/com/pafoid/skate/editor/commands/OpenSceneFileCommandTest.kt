package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.project.OpenSceneFileCommand
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.testfixtures.ImmediateJobSystem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenSceneFileCommandTest {
    private lateinit var sceneSerializer: SceneSerializer
    private lateinit var sceneManager: SceneManager
    private lateinit var eventSystem: EventSystem
    private lateinit var jobSystem: ImmediateJobSystem
    private lateinit var loadedScene: Scene

    @BeforeEach
    fun setup() {
        sceneSerializer = mockk(relaxed = true)
        sceneManager = mockk(relaxed = true)
        eventSystem = EventSystem()
        jobSystem = ImmediateJobSystem()
        loadedScene = mockk(relaxed = true)
    }

    @AfterEach
    fun teardown() {
        jobSystem.destroy()
    }

    @Test
    fun `execute opens scene and publishes success when file loads`() {
        val scenePath = "C:/scene/Test.scene"
        every { sceneSerializer.load(any(), scenePath) } returns true
        var opened: SceneAction.OpenSucceeded? = null
        eventSystem.subscribe<SceneAction.OpenSucceeded> { opened = it }

        val command = createCommand(scenePath)
        command.execute()

        verify { sceneManager.openScene(any(), any()) }
        assertNotNull(command.openedScene)
        assertEquals(loadedScene, opened?.scene)
    }

    @Test
    fun `execute publishes failure and does not open scene when load fails`() {
        val scenePath = "C:/scene/Broken.scene"
        every { sceneSerializer.load(any(), scenePath) } returns false
        var failed: SceneAction.OpenFailed? = null
        eventSystem.subscribe<SceneAction.OpenFailed> { failed = it }

        val command = createCommand(scenePath)
        command.execute()

        verify(exactly = 0) { sceneManager.openScene(any(), any()) }
        assertNull(command.openedScene)
        assertFalse(command.didCompleteSuccessfully())
        assertNotNull(failed)
    }

    private fun createCommand(scenePath: String): OpenSceneFileCommand {
        return OpenSceneFileCommand(
            scenePath = scenePath,
            sceneSerializer = sceneSerializer,
            sceneManager = sceneManager,
            jobSystem = jobSystem,
            eventSystem = eventSystem,
            sceneFactory = { _ -> loadedScene }
        )
    }
}
