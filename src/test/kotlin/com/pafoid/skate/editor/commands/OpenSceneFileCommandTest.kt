package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.project.OpenSceneFileCommand
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.JobSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction
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
import java.io.File

class OpenSceneFileCommandTest {
    private lateinit var serializer: Serializer
    private lateinit var sceneManager: SceneManager
    private lateinit var eventSystem: EventSystem
    private lateinit var jobSystem: JobSystem
    private lateinit var loadedScene: Scene

    @BeforeEach
    fun setup() {
        serializer = mockk(relaxed = true)
        sceneManager = mockk(relaxed = true)
        eventSystem = EventSystem()
        jobSystem = JobSystem()
        loadedScene = mockk(relaxed = true)
    }

    @AfterEach
    fun teardown() {
        jobSystem.destroy()
    }

    @Test
    fun `execute opens scene and publishes success when file loads`() {
        val scenePath = "C:/scene/Test.scene"
        val sceneFile = File(scenePath)
        every { serializer.decode<Scene?>(sceneFile.readText()) } returns mockk()
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
        val sceneFile = File(scenePath)
        every { serializer.decode<Scene?>(sceneFile.readText()) } returns null
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
            sceneFile = File(scenePath),
            serializer = serializer,
            sceneManager = sceneManager,
            eventSystem = eventSystem,
        )
    }
}
