package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.data.LogEntry
import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.data.SceneOpenResult
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.project.ProjectMetadata
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.editor.events.SceneChanged
import com.pafoid.skate.editor.events.SceneCreateRequested
import com.pafoid.skate.editor.events.SceneCreated
import com.pafoid.skate.editor.events.SceneOpenRequested
import com.pafoid.skate.editor.events.SceneOpened
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
    private lateinit var sceneInitializer: LevelEditorSceneInitializer
    private lateinit var jobSystem: IJobSystem

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
        sceneInitializer = mockk(relaxed = true)
        jobSystem = ImmediateJobSystem()
        coEvery { sceneInitializer.loadResources(any()) } returns Unit
        coEvery { sceneInitializer.init(any()) } returns Unit

        // Create a real SceneManager that tracks openScenes
        sceneManager = mockk(relaxed = true)
        val openScenesList = mutableListOf<Scene>()
        every { sceneManager.openScenes } returns openScenesList
        every { sceneManager.activeSceneIndex } returns 0
        every { sceneManager.currentScene } answers { openScenesList.lastOrNull() }
        every { sceneManager.openSceneBlocking(capture(openScenesList), any()) } answers {
            val scene = openScenesList.last()
            eventSystem.publish(SceneOpened(scene))
            eventSystem.publish(SceneChanged)
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
        every { testLogger.logEditor(any(), any()) } answers {
            val message = firstArg<String>()
            val level = secondArg<LogLevel>()
            capturedEditorLogs.add(LogEntry(message, level))
        }
        every { testLogger.logEngine(any(), any()) } answers {
            val message = firstArg<String>()
            val level = secondArg<LogLevel>()
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
        eventSystem.publish(SceneCreateRequested)

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
        eventSystem.publish(SceneCreateRequested)

        // Assert — verify openSceneBlocking was called with a scene that has the correct name
        val capturedScenes = mutableListOf<Scene>()
        verify { sceneManager.openSceneBlocking(capture(capturedScenes), any()) }
        assertEquals(1, capturedScenes.size)
        assertTrue(capturedScenes[0].name.startsWith("NewScene_"))
    }

    @Test
    fun `handleCreateRequested_publishesSceneOpenedAndChangedEvents`() {
        // Arrange
        val handler = SceneActionHandler()
        handler.init()

        val sceneOpenedEvents = mutableListOf<SceneOpened>()
        var sceneChangedReceived = false

        eventSystem.subscribe<SceneOpened> { event ->
            sceneOpenedEvents.add(event)
        }
        eventSystem.subscribe<SceneChanged> {
            sceneChangedReceived = true
        }

        // Act
        eventSystem.publish(SceneCreateRequested)

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

        eventSystem.subscribe<SceneCreated> { event ->
            sceneCreatedReceived = true
            createdScene = event.scene
        }

        // Act
        eventSystem.publish(SceneCreateRequested)

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
        eventSystem.publish(SceneCreated(mockScene))

        // Assert
        val capturedScenes = mutableListOf<Scene>()
        verify { sceneManager.openSceneBlocking(capture(capturedScenes), any()) }
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
        eventSystem.publish(SceneCreateRequested)

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
        eventSystem.publish(SceneCreateRequested)
        eventSystem.publish(SceneCreateRequested)
        eventSystem.publish(SceneCreateRequested)

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
        eventSystem.publish(SceneCreateRequested)

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
        eventSystem.publish(SceneCreateRequested)

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

        eventSystem.publish(SceneOpenRequested)

        verify(exactly = 0) { sceneManager.openSceneBlocking(any(), any()) }
        val cancelLogs = capturedEditorLogs.filter {
            it.message.contains("Scene open cancelled", ignoreCase = true)
        }
        assertTrue(cancelLogs.isNotEmpty(), "Cancellation should be logged after async completion event")
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
                single<LevelEditorSceneInitializer> { sceneInitializer }
                single<ProjectManager> { projectManager }
                single<IJobSystem> { jobSystem }
            })
        }
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
