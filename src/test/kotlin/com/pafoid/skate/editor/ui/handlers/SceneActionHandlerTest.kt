package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.data.LogEntry
import com.pafoid.skate.editor.data.SceneOpenResult
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.project.ProjectMetadata
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.editor.systems.EditorMutationGate
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.engine.physics3d.Physics3DFactory
import com.pafoid.skate.engine.utils.IJobSystem
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files

class SceneActionHandlerTest {

    private lateinit var sceneManager: SceneManager
    private lateinit var sceneSerializer: SceneSerializer
    private lateinit var undoRedoManager: UndoRedoManager
    private lateinit var eventSystem: EventSystem
    private lateinit var testLogger: LoggerService
    private lateinit var projectManager: ProjectManager
    private lateinit var jobSystem: IJobSystem
    private lateinit var mutationGate: EditorMutationGate
    private lateinit var physics3DFactory: Physics3DFactory

    private lateinit var tempProjectDir: File

    // Captured log entries for assertions
    private val capturedEditorLogs = mutableListOf<LogEntry>()

    @BeforeEach
    fun setup() {
        // Create temp project directory
        tempProjectDir = Files.createTempDirectory("test-project").toFile()

        eventSystem = EventSystem()
        sceneSerializer = mockk(relaxed = true)
        undoRedoManager = mockk(relaxed = true)
        projectManager = mockk(relaxed = true)
        jobSystem = ImmediateJobSystem()
        mutationGate = mockk(relaxed = true)
        physics3DFactory = mockk(relaxed = true)
        every { mutationGate.blockIfPlaying(any()) } returns false

        // Create a real SceneManager that tracks openScenes
        sceneManager = mockk(relaxed = true)
        val openScenesList = mutableListOf<Scene>()
        every { sceneManager.openScenes } returns openScenesList
        every { sceneManager.activeSceneIndex } returns 0
        every { sceneManager.currentScene } answers { openScenesList.lastOrNull() }
        every { sceneManager.openScene(capture(openScenesList), any()) } answers {
            val scene = openScenesList.last()
            eventSystem.publish(SceneAction.Opened(scene))
            eventSystem.publish(SceneAction.Changed)
            true
        }

        // Mock project manager to return temp directory
        val mockProject = mockk<Project>(relaxed = true)
        val mockMetadata = ProjectMetadata(
            name = "TestProject",
            engineVersion = "v0.50.0.0",
            projectPath = tempProjectDir.absolutePath
        )
        every { mockProject.metadata } returns mockMetadata
        every { mockProject.getProjectDirectory() } returns tempProjectDir
        every { projectManager.currentProject } returns mockProject
        every { projectManager.getProjectDirectory() } returns tempProjectDir

        // When undoRedoManager.executeCommand is called, just execute the command
        every { undoRedoManager.executeCommand(any()) } answers {
            val cmd = firstArg<com.pafoid.skate.editor.commands.Command>()
            cmd.execute()
        }

        // Mock serializer to actually create files
        every { sceneSerializer.saveToFile(any(), any()) } answers {
            val path = secondArg<String>()
            File(path).parentFile?.mkdirs()
            File(path).createNewFile()
        }

        // Create a mock LoggerService that captures log entries
        testLogger = mockk(relaxed = true)
        every { testLogger.log(any<String>(), any(), any()) } answers {
            val message = firstArg<String>()
            val level = secondArg<com.pafoid.skate.engine.data.LogLevel>()
            capturedEditorLogs.add(LogEntry(message, level))
        }

        startKoinForTest()
    }

    @AfterEach
    fun teardown() {
        stopKoin()
        unmockkAll()
        jobSystem.destroy()
        // Clean up temp directory
        if (tempProjectDir.exists()) {
            tempProjectDir.deleteRecursively()
        }
    }

    @Test
    fun `handleCreateRequested_createsSceneFileOnDisk`() {
        // Arrange
        val handler = SceneActionHandler()
        handler.init()

        val capturedSavePath = slot<String>()
        every { sceneSerializer.saveToFile(any(), capture(capturedSavePath)) } answers {
            val path = secondArg<String>()
            File(path).parentFile?.mkdirs()
            File(path).createNewFile()
        }

        // Act
        eventSystem.publish(SceneAction.CreateRequested)

        // Assert
        assertTrue(capturedSavePath.isCaptured)
        val savedPath = capturedSavePath.captured
        assertTrue(savedPath.contains("NewScene_"), "Path should contain 'NewScene_' prefix")
        assertTrue(savedPath.endsWith(".scene"), "Path should end with .scene extension")
        assertTrue(savedPath.contains("Scenes"), "Path should contain 'Scenes' directory")
    }

    @Test
    fun `handleCreateRequested_opensSceneInSceneManager`() {
        // Arrange
        val handler = SceneActionHandler()
        handler.init()

        // Act
        eventSystem.publish(SceneAction.CreateRequested)

        // Assert — verify openSceneBlocking was called with a scene that has the correct name
        val capturedScenes = mutableListOf<Scene>()
        verify { sceneManager.openScene(capture(capturedScenes), any()) }
        assertEquals(1, capturedScenes.size)
        assertTrue(capturedScenes[0].name.startsWith("NewScene_"))
    }

    @Test
    fun `handleCreateRequested_publishesSceneOpenedAndChangedEvents`() {
        // Arrange
        val handler = SceneActionHandler()
        handler.init()

        val sceneOpenedEvents = mutableListOf<SceneAction.Opened>()
        var sceneChangedReceived = false

        eventSystem.subscribe<SceneAction.Opened> { event ->
            sceneOpenedEvents.add(event)
        }
        eventSystem.subscribe<SceneAction.Changed> {
            sceneChangedReceived = true
        }

        // Act
        eventSystem.publish(SceneAction.CreateRequested)

        // Assert
        assertEquals(1, sceneOpenedEvents.size)
        assertTrue(sceneChangedReceived, "SceneChanged event should be published")
    }

    @Test
    fun `handleCreateRequested_publishesSceneCreatedEvent`() {
        // Arrange
        val handler = SceneActionHandler()
        handler.init()

        var sceneCreatedReceived = false
        var createdScene: Scene? = null

        eventSystem.subscribe<SceneAction.Created> { event ->
            sceneCreatedReceived = true
            createdScene = event.scene
        }

        // Act
        eventSystem.publish(SceneAction.CreateRequested)

        // Assert
        assertTrue(sceneCreatedReceived, "SceneCreated event should be published")
        assertNotNull(createdScene)
        assertTrue(createdScene?.name?.startsWith("NewScene_") == true)
    }

    @Test
    fun `handleSceneCreated_opensScene()`() {
        // Arrange
        val handler = SceneActionHandler()
        handler.init()

        val mockScene = mockk<Scene>(relaxed = true)
        every { mockScene.name } returns "TestScene"

        // Act
        eventSystem.publish(SceneAction.Created(mockScene))

        // Assert
        val capturedScenes = mutableListOf<Scene>()
        verify { sceneManager.openScene(capture(capturedScenes), any()) }
        assertEquals(1, capturedScenes.size)
        assertEquals("TestScene", capturedScenes[0].name)
    }

    @Test
    fun `handleCreateRequested_failsGracefully_whenNoProjectDirectory`() {
        // Arrange
        val handler = SceneActionHandler()
        handler.init()

        every { projectManager.getProjectDirectory() } returns null

        // Act
        eventSystem.publish(SceneAction.CreateRequested)

        // Assert - no command should have been executed
        verify(exactly = 0) { undoRedoManager.executeCommand(any()) }

        // A warning should have been logged
        val warnLogs = capturedEditorLogs.filter { it.level == LogLevel.WARN }
        assertTrue(warnLogs.isNotEmpty(), "A warning should be logged when no project directory")
        assertTrue(
            warnLogs.any { it.message.contains("no project directory", ignoreCase = true) },
            "Warning message should mention missing project directory"
        )
    }

    @Test
    fun `handleCreateRequested_generatesUniquePaths`() {
        // Arrange
        val handler = SceneActionHandler()
        handler.init()

        val savedPaths = mutableListOf<String>()
        every { sceneSerializer.saveToFile(any(), any()) } answers {
            val path = secondArg<String>()
            savedPaths.add(path)
            // Actually create the file so generateUniqueScenePath can detect it
            File(path).parentFile?.mkdirs()
            File(path).createNewFile()
        }

        // Act - create three scenes
        eventSystem.publish(SceneAction.CreateRequested)
        eventSystem.publish(SceneAction.CreateRequested)
        eventSystem.publish(SceneAction.CreateRequested)

        // Assert
        assertEquals(3, savedPaths.size)

        val filenames = savedPaths.map { File(it).name }
        assertTrue(filenames.contains("NewScene_1.scene"), "First scene should be NewScene_1.scene")
        assertTrue(filenames.contains("NewScene_2.scene"), "Second scene should be NewScene_2.scene")
        assertTrue(filenames.contains("NewScene_3.scene"), "Third scene should be NewScene_3.scene")

        // All paths should be unique
        assertEquals(3, savedPaths.distinct().size, "All paths should be unique")
    }

    @Test
    fun `handleCreateRequested_usesExistingFileForUniquenessCheck`() {
        // Arrange - pre-create NewScene_1.scene to test uniqueness
        val scenesDir = File(tempProjectDir, "Scenes")
        scenesDir.mkdirs()
        val existingFile = File(scenesDir, "NewScene_1.scene")
        existingFile.createNewFile()

        val handler = SceneActionHandler()
        handler.init()

        val savedPaths = mutableListOf<String>()
        every { sceneSerializer.saveToFile(any(), any()) } answers {
            val path = secondArg<String>()
            savedPaths.add(path)
            File(path).parentFile?.mkdirs()
            File(path).createNewFile()
        }

        // Act
        eventSystem.publish(SceneAction.CreateRequested)

        // Assert - should skip NewScene_1 and use NewScene_2
        assertEquals(1, savedPaths.size)
        assertEquals("NewScene_2.scene", File(savedPaths[0]).name)

        // Cleanup
        existingFile.delete()
    }

    @Test
    fun `handleCreateRequested_logsSuccessMessage`() {
        // Arrange
        val handler = SceneActionHandler()
        handler.init()

        // Act
        eventSystem.publish(SceneAction.CreateRequested)

        // Assert
        val successLogs = capturedEditorLogs.filter {
            it.message.contains("New scene created and saved", ignoreCase = true)
        }
        assertTrue(successLogs.isNotEmpty(), "Success log should be written")
    }

    @Test
    fun `handleOpenRequested_logsCancellationFromCompletionEvent`() {
        val handler = SceneActionHandler()
        handler.init()
        every { sceneSerializer.open(any()) } returns SceneOpenResult.Cancelled

        eventSystem.publish(SceneAction.OpenRequested)

        verify(exactly = 0) { sceneManager.openScene(any(), any()) }
        val cancelLogs = capturedEditorLogs.filter {
            it.message.contains("Scene open cancelled", ignoreCase = true)
        }
        assertTrue(cancelLogs.isNotEmpty(), "Cancellation should be logged after async completion event")
    }

    @Test
    fun `open scene file event routes through scene flow and opens scene`() {
        val handler = SceneActionHandler()
        handler.init()
        val scenePath = File(tempProjectDir, "Scenes/Test.scene").absolutePath
        every { sceneSerializer.loadFromFile(any(), scenePath) } returns true

        eventSystem.publish(FileSystemEvent.OpenSceneFileEvent(scenePath))

        verify { sceneSerializer.loadFromFile(any(), scenePath) }
        verify { sceneManager.openScene(any(), any()) }
    }

    private fun startKoinForTest() {
        stopKoin()
        startKoin {
            printLogger(Level.ERROR)
            modules(module {
                single<SceneManager> { sceneManager }
                single<SceneSerializer> { sceneSerializer }
                single<UndoRedoManager> { undoRedoManager }
                single<EventSystem> { eventSystem }
                single<LoggerService> { testLogger }
                single<ProjectManager> { projectManager }
                single<IJobSystem> { jobSystem }
                single<EditorMutationGate> { mutationGate }
                single<Physics3DFactory> { physics3DFactory }
            })
        }
    }

    private class ImmediateJobSystem : IJobSystem {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

        override fun isMainThread(): Boolean = true

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
