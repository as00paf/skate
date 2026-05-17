package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.commands.project.OpenSceneCommand
import com.pafoid.skate.editor.data.SceneOpenResult
import com.pafoid.skate.editor.events.SceneAction
import com.pafoid.skate.editor.events.SceneAction.*
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.utils.IJobSystem
import com.pafoid.skate.testfixtures.ImmediateJobSystem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OpenSceneCommandTest {
    private lateinit var sceneInitializer: LevelEditorSceneInitializer
    private lateinit var sceneSerializer: SceneSerializer
    private lateinit var sceneManager: SceneManager
    private lateinit var eventSystem: EventSystem
    private lateinit var jobSystem: IJobSystem
    private lateinit var loadedSceneInstance: com.pafoid.skate.engine.ecs.Scene

    @BeforeEach
    fun setup() {
        sceneInitializer = mockk(relaxed = true)
        sceneSerializer = mockk(relaxed = true)
        sceneManager = mockk(relaxed = true)
        eventSystem = EventSystem()
        jobSystem = ImmediateJobSystem()
        loadedSceneInstance = mockk(relaxed = true)
        every { loadedSceneInstance.name } returns "Loaded Scene"
        coEvery { loadedSceneInstance.init() } returns Unit
        every { loadedSceneInstance.destroyScene() } returns Unit

        coEvery { sceneInitializer.loadResources(any()) } returns Unit
        coEvery { sceneInitializer.init(any()) } returns Unit
    }

    @AfterEach
    fun teardown() {
        jobSystem.destroy()
        unmockkAll()
    }

    @Test
    fun `execute_doesNotOpenScene_whenDialogCancelled`() {
        every { sceneSerializer.open(any()) } returns SceneOpenResult.Cancelled
        var cancelled = false
        eventSystem.subscribe<OpenCancelled> { cancelled = true }

        val command = createCommand()
        command.execute()

        verify(exactly = 0) { sceneManager.openSceneBlocking(any(), any()) }
        assertNull(command.openedScene)
        assertEquals(true, cancelled)
        assertEquals(CommandCategory.ASYNC, command.getCategory())
        assertNotNull(command.getCompletionJob())
        assertEquals(false, command.didCompleteSuccessfully())
        assertEquals(false, command.shouldPushToHistoryOnSuccess())
    }

    @Test
    fun `execute_doesNotOpenScene_whenLoadFails`() {
        every { sceneSerializer.open(any()) } returns SceneOpenResult.Failed("broken.scene", "deserialize failure")
        var failureReason: String? = null
        eventSystem.subscribe<OpenFailed> { event -> failureReason = event.reason }

        val command = createCommand()
        command.execute()

        verify(exactly = 0) { sceneManager.openSceneBlocking(any(), any()) }
        assertNull(command.openedScene)
        assertEquals("deserialize failure", failureReason)
    }

    @Test
    fun `execute_opensScene_whenLoadSucceeds`() {
        every { sceneSerializer.open(any()) } returns SceneOpenResult.Loaded("good.scene")
        var openedEvent: OpenSucceeded? = null
        eventSystem.subscribe<OpenSucceeded> { event -> openedEvent = event }

        val command = createCommand()
        command.execute()

        verify(exactly = 1) { sceneManager.openSceneBlocking(any(), any()) }
        assertEquals(loadedSceneInstance, command.openedScene)
        assertNotNull(openedEvent)
        assertEquals(loadedSceneInstance, openedEvent?.scene)
        assertEquals(CommandCategory.ASYNC, command.getCategory())
        assertNotNull(command.getCompletionJob())
        assertEquals(true, command.didCompleteSuccessfully())
        assertEquals(false, command.shouldPushToHistoryOnSuccess())
    }

    @Test
    fun `execute_publishesFailureEvent_whenUnexpectedExceptionOccurs`() {
        every { sceneSerializer.open(any()) } throws IllegalStateException("io failure")
        var failureReason: String? = null
        eventSystem.subscribe<OpenFailed> { event -> failureReason = event.reason }

        val command = createCommand()
        command.execute()

        verify(exactly = 0) { sceneManager.openSceneBlocking(any(), any()) }
        assertNull(command.openedScene)
        assertFalse(command.didCompleteSuccessfully())
        assertEquals("io failure", failureReason)
    }

    private fun createCommand(): OpenSceneCommand {
        return OpenSceneCommand(
            sceneInitializer = sceneInitializer,
            sceneSerializer = sceneSerializer,
            sceneManager = sceneManager,
            jobSystem = jobSystem,
            eventSystem = eventSystem,
            sceneFactory = { _, _ -> loadedSceneInstance }
        )
    }
}
