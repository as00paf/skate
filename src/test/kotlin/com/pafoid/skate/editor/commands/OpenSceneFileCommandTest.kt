package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.project.OpenSceneFileCommand
import com.pafoid.skate.engine.assets.serialization.FileKind
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OpenSceneFileCommandTest {
    private lateinit var serializer: Serializer
    private lateinit var sceneManager: SceneManager
    private lateinit var eventSystem: EventSystem
    private lateinit var jobSystem: JobSystem
    private lateinit var loadedScene: Scene

    @BeforeEach
    fun setup() {
        serializer = Serializer()
        sceneManager = mockk(relaxed = true)
        eventSystem = EventSystem()
        jobSystem = JobSystem()
        loadedScene = mockk(relaxed = true)
    }

    @AfterEach
    fun teardown() {
        jobSystem.destroy()
    }

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `execute opens scene when file loads`() {
        val sceneFile = File(tempDir, "Test.scene").apply { writeText(serializer.encodeFile(FileKind.SCENE, Scene("Loaded"))) }

        val command = createCommand(sceneFile)
        command.execute()

        verify { sceneManager.openScene(any()) }
        assertEquals("Loaded", command.openedScene?.name)
        assertTrue(command.didCompleteSuccessfully())
    }

    @Test
    fun `execute publishes failure and does not open scene when load fails`() {
        val sceneFile = File(tempDir, "Broken.scene").apply { writeText("null") }
        var failed: SceneAction.OpenFailed? = null
        eventSystem.subscribe<SceneAction.OpenFailed> { failed = it }

        val command = createCommand(sceneFile)
        command.execute()

        verify(exactly = 0) { sceneManager.openScene(any()) }
        assertNull(command.openedScene)
        assertFalse(command.didCompleteSuccessfully())
        assertNotNull(failed)
    }

    @Test
    fun `scene from an older Skate version reports a recreate message instead of crashing`() {
        // REN-26: schema 1 scenes (AmbientLightComponent, sun on the scene) are not upgraded (rule 8)
        val sceneFile = File(tempDir, "Old.scene").apply {
            writeText("""{"schema": 1, "name": "Old", "sceneName": "Old", "components": [
                {"type": "com.pafoid.skate.engine.ecs.components.AmbientLightComponent", "intensity": 0.25}], "children": []}""")
        }
        var failed: SceneAction.OpenFailed? = null
        eventSystem.subscribe<SceneAction.OpenFailed> { failed = it }

        val command = createCommand(sceneFile)
        command.execute()

        verify(exactly = 0) { sceneManager.openScene(any()) }
        assertFalse(command.didCompleteSuccessfully())
        val reason = failed!!.reason
        assertTrue(reason.contains("Old.scene"), reason)
        assertTrue(reason.contains("older Skate version") && reason.contains("Recreate"), reason)
    }

    private fun createCommand(sceneFile: File): OpenSceneFileCommand {
        return OpenSceneFileCommand(
            sceneFile = sceneFile,
            serializer = serializer,
            sceneManager = sceneManager,
            eventSystem = eventSystem,
        )
    }
}
