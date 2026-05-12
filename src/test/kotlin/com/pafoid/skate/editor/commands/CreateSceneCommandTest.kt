package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.commands.project.CreateSceneCommand
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.scene.SceneData
import com.pafoid.skate.engine.events.SceneCreated
import com.pafoid.skate.engine.utils.IJobSystem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CreateSceneCommandTest {
    private lateinit var sceneInitializer: LevelEditorSceneInitializer
    private lateinit var sceneSerializer: SceneSerializer
    private lateinit var eventSystem: EventSystem
    private lateinit var jobSystem: IJobSystem
    private lateinit var createdSceneInstance: Scene

    @BeforeEach
    fun setup() {
        sceneSerializer = mockk(relaxed = true)
        sceneInitializer = mockk(relaxed = true)
        eventSystem = EventSystem()
        jobSystem = ImmediateJobSystem()
        createdSceneInstance = mockk(relaxed = true)
        every { createdSceneInstance.sceneData } returns SceneData()
        every { createdSceneInstance.name } returns "TestScene"
        coEvery { createdSceneInstance.init() } returns Unit
        coEvery { sceneInitializer.loadResources(any()) } returns Unit
        coEvery { sceneInitializer.init(any()) } returns Unit
    }

    @AfterEach
    fun teardown() {
        jobSystem.destroy()
        unmockkAll()
    }

    @Test
    fun `execute_createsSceneAndSetsFilePath`() {
        val expectedPath = "C:\\workspace\\Scenes\\TestScene.scene"
        val command = createCommand(expectedPath)

        command.execute()

        val createdScene = command.createdScene
        assertNotNull(createdScene)
        val scene = createdScene ?: return
        assertEquals("TestScene", scene.name)
        assertEquals(expectedPath, scene.sceneData.levelPath)
    }

    @Test
    fun `execute_callsSceneSerializer_saveToFile`() {
        val expectedPath = "C:\\workspace\\Scenes\\TestScene.scene"
        val command = createCommand(expectedPath)

        command.execute()

        val savedSceneSlot = slot<Scene>()
        val savedPathSlot = slot<String>()
        verify { sceneSerializer.saveToFile(capture(savedSceneSlot), capture(savedPathSlot)) }
        assertEquals(expectedPath, savedPathSlot.captured)
        assertEquals(createdSceneInstance, savedSceneSlot.captured)
    }

    @Test
    fun `execute_publishesSceneCreatedEvent`() {
        val command = createCommand("test.scene")
        var createdEvent: SceneCreated? = null
        eventSystem.subscribe<SceneCreated> { event -> createdEvent = event }

        command.execute()

        assertNotNull(createdEvent)
        assertTrue(createdEvent?.scene?.name?.startsWith("TestScene") == true)
    }

    @Test
    fun `execute_setsCreatedSceneProperty`() {
        val command = createCommand("test.scene")

        assertNull(command.createdScene)
        command.execute()

        assertNotNull(command.createdScene)
    }

    @Test
    fun `undo_isNoOp`() {
        val command = createCommand("test.scene")

        command.execute()
        command.undo()

        verify(exactly = 1) { sceneSerializer.saveToFile(any<Scene>(), any<String>()) }
    }

    private fun createCommand(path: String): CreateSceneCommand {
        return CreateSceneCommand(
            name = "TestScene",
            sceneInitializer = sceneInitializer,
            sceneSerializer = sceneSerializer,
            filePath = path,
            jobSystem = jobSystem,
            eventSystem = eventSystem,
            sceneFactory = { _, _ -> createdSceneInstance }
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
