package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.commands.project.OpenSceneCommand
import com.pafoid.skate.editor.data.SceneOpenResult
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneOpenCancelled
import com.pafoid.skate.engine.events.SceneOpenFailed
import com.pafoid.skate.engine.events.SceneOpenSucceeded
import com.pafoid.skate.engine.utils.IJobSystem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
        eventSystem.subscribe<SceneOpenCancelled> { cancelled = true }

        val command = createCommand()
        command.execute()

        verify(exactly = 0) { sceneManager.openSceneBlocking(any(), any()) }
        assertNull(command.openedScene)
        assertEquals(true, cancelled)
    }

    @Test
    fun `execute_doesNotOpenScene_whenLoadFails`() {
        every { sceneSerializer.open(any()) } returns SceneOpenResult.Failed("broken.scene", "deserialize failure")
        var failureReason: String? = null
        eventSystem.subscribe<SceneOpenFailed> { event -> failureReason = event.reason }

        val command = createCommand()
        command.execute()

        verify(exactly = 0) { sceneManager.openSceneBlocking(any(), any()) }
        assertNull(command.openedScene)
        assertEquals("deserialize failure", failureReason)
    }

    @Test
    fun `execute_opensScene_whenLoadSucceeds`() {
        every { sceneSerializer.open(any()) } returns SceneOpenResult.Loaded("good.scene")
        var openedEvent: SceneOpenSucceeded? = null
        eventSystem.subscribe<SceneOpenSucceeded> { event -> openedEvent = event }

        val command = createCommand()
        command.execute()

        verify(exactly = 1) { sceneManager.openSceneBlocking(any(), any()) }
        assertEquals(loadedSceneInstance, command.openedScene)
        assertNotNull(openedEvent)
        assertEquals(loadedSceneInstance, openedEvent?.scene)
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

    private class ImmediateJobSystem : IJobSystem {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

        override fun update() = Unit

        override fun runAsync(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

        override fun runOnMain(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

        override fun <T> runAsyncDeferred(block: suspend CoroutineScope.() -> T): Deferred<T> =
            scope.async(block = block)

        override fun runIO(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

        override fun destroy() {
            scope.coroutineContext[Job]?.cancel()
        }
    }
}
