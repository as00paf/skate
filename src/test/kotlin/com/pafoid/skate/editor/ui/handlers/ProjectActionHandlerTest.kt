package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.events.ProjectEvent.CloseProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.CreateFileRequested
import com.pafoid.skate.editor.events.ProjectEvent.CreateProjectFailed
import com.pafoid.skate.editor.events.ProjectEvent.CreateProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.CreateProjectSucceeded
import com.pafoid.skate.editor.events.ProjectEvent.DeleteFileRequested
import com.pafoid.skate.editor.events.ProjectEvent.LoadLastProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.OpenProjectFailed
import com.pafoid.skate.editor.events.ProjectEvent.OpenProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.OpenProjectSucceeded
import com.pafoid.skate.editor.events.ProjectEvent.RenameFileRequested
import com.pafoid.skate.editor.events.ProjectEvent.SaveRequested
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.JobSystem
import com.pafoid.skate.engine.core.LoggerService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ProjectActionHandlerTest {

    @Test
    fun `create file publishes file system changed event when command succeeds`() {
        val tempDir = Files.createTempDirectory("project-action-create-success").toFile()
        val target = tempDir.resolve("created.txt")
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        val engine: Engine = mockk()
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        val handler = ProjectActionHandler(engine, projectManager, undoRedoManager, mockk())

        var changedEvents = 0
        eventSystem.subscribe<FileSystemEvent.FileSystemChangedEvent> {
            changedEvents++
        }

        eventSystem.publish(CreateFileRequested(target.absolutePath, false))

        assertEquals(1, changedEvents)
        tempDir.deleteRecursively()
    }

    @Test
    fun `rename file does not publish file system changed event when command fails`() {
        val tempDir = Files.createTempDirectory("project-action-rename-failure").toFile()
        val source = tempDir.resolve("source.txt")
        val existingTarget = tempDir.resolve("target.txt")
        source.writeText("source")
        existingTarget.writeText("target")
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }
        val engine: Engine = mockk()
        val handler = ProjectActionHandler(engine, projectManager, undoRedoManager, mockk())
        var changedEvents = 0
        eventSystem.subscribe<FileSystemEvent.FileSystemChangedEvent> {
            changedEvents++
        }

        eventSystem.publish(RenameFileRequested(source.absolutePath, existingTarget.name))

        assertEquals(0, changedEvents)
        tempDir.deleteRecursively()
    }

    @Test
    fun `delete file does not publish file system changed event when command fails`() {
        val tempDir = Files.createTempDirectory("project-action-delete-failure").toFile()
        val missing = tempDir.resolve("missing.txt")
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }
        val engine: Engine = mockk()
        val handler = ProjectActionHandler(engine, projectManager, undoRedoManager, mockk())

        var changedEvents = 0
        eventSystem.subscribe<FileSystemEvent.FileSystemChangedEvent> {
            changedEvents++
        }

        eventSystem.publish(DeleteFileRequested(missing.absolutePath))

        assertEquals(0, changedEvents)
        tempDir.deleteRecursively()
    }

    @Test
    fun `open project requested publishes success when open command succeeds`() {
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        val logger = mockk<LoggerService>(relaxed = true)
        every { projectManager.openProject(any()) } returns true
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }
        val engine: Engine = mockk()
        val handler = ProjectActionHandler(engine, projectManager, undoRedoManager, mockk())

        var successReceived = false
        var failedReceived = false
        eventSystem.subscribe<OpenProjectSucceeded> { successReceived = true }
        eventSystem.subscribe<OpenProjectFailed> { failedReceived = true }

        eventSystem.publish(OpenProjectRequested("C:/test/MyProject.skateproject"))

        assertTrue(successReceived)
        assertFalse(failedReceived)
        verify(exactly = 1) { undoRedoManager.executeCommand(any()) }
        verify(exactly = 1) { projectManager.openProject(any()) }
    }

    @Test
    fun `open project requested from worker thread queues execution onto main job system`() {
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        val logger = mockk<LoggerService>(relaxed = true)
        val jobSystem = JobSystem()
        every { projectManager.openProject(any()) } returns true
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        val engine: Engine = mockk()
        val handler = ProjectActionHandler(engine, projectManager, undoRedoManager, mockk())

        var successReceived = false
        eventSystem.subscribe<OpenProjectSucceeded> { successReceived = true }

        eventSystem.publish(OpenProjectRequested("C:/test/MyProject.skateproject"))

        assertFalse(successReceived)
        verify(exactly = 0) { projectManager.openProject(any()) }

        assertTrue(successReceived)
        verify(exactly = 1) { projectManager.openProject(any()) }
    }

    @Test
    fun `create project requested publishes failed when create command fails`() {
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        val logger = mockk<LoggerService>(relaxed = true)
        every { projectManager.createProject(any(), any(), any()) } returns Result.failure(IllegalStateException("boom"))
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        val engine: Engine = mockk()
        val handler = ProjectActionHandler(engine, projectManager, undoRedoManager, mockk())

        var successCount = 0
        var failureEvent: CreateProjectFailed? = null
        eventSystem.subscribe<CreateProjectSucceeded> { successCount++ }
        eventSystem.subscribe<CreateProjectFailed> { failureEvent = it }

        eventSystem.publish(CreateProjectRequested("MyProject", "C:/tmp", "v1"))

        assertEquals(0, successCount)
        assertEquals("MyProject", failureEvent?.name)
        assertTrue(failureEvent?.reason?.contains("boom") == true)
        verify(exactly = 1) { undoRedoManager.executeCommand(any()) }
        verify(exactly = 1) { projectManager.createProject(any(), any(), any()) }
    }

    @Test
    fun `close project requested closes current project through handler`() {
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        every { projectManager.hasProject() } returns true
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        val engine: Engine = mockk()
        val handler = ProjectActionHandler(engine, projectManager, undoRedoManager, mockk())

        eventSystem.publish(CloseProjectRequested)

        verify(exactly = 1) { undoRedoManager.executeCommand(any()) }
        verify(exactly = 1) { projectManager.closeProject() }
    }

    @Test
    fun `save project requested executes save command through undo manager`() {
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }
        every { projectManager.saveProject() } returns true

        val engine: Engine = mockk()
        val handler = ProjectActionHandler(engine, projectManager, undoRedoManager, mockk())

        eventSystem.publish(SaveRequested)

        verify(exactly = 1) { undoRedoManager.executeCommand(any()) }
        verify(exactly = 1) { projectManager.saveProject() }
    }

    @Test
    fun `load last project requested delegates to project manager`() {
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        every { projectManager.loadLastProject() } returns false
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        val engine: Engine = mockk()
        val handler = ProjectActionHandler(engine, projectManager, undoRedoManager, mockk())

        eventSystem.publish(LoadLastProjectRequested)

        verify(exactly = 1) { undoRedoManager.executeCommand(any()) }
        verify(exactly = 1) { projectManager.loadLastProject() }
    }

    @Test
    fun `create file publishes operation failed event when command fails`() {
        val tempDir = Files.createTempDirectory("project-action-create-fail").toFile()
        val existing = tempDir.resolve("existing.txt")
        existing.writeText("content")
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }
        val engine: Engine = mockk()
        val handler = ProjectActionHandler(engine, projectManager, undoRedoManager, mockk())
        var failedEvent: FileSystemEvent.FileSystemOperationFailed? = null
        var changedEvents = 0
        eventSystem.subscribe<FileSystemEvent.FileSystemOperationFailed> { failedEvent = it }
        eventSystem.subscribe<FileSystemEvent.FileSystemChangedEvent> { changedEvents++ }

        eventSystem.publish(CreateFileRequested(existing.absolutePath, false))

        assertNotNull(failedEvent)
        assertEquals("create", failedEvent?.operation)
        assertEquals(existing.absolutePath, failedEvent?.path)
        assertNotNull(failedEvent?.reason)
        assertEquals(0, changedEvents)
        tempDir.deleteRecursively()
    }

    @Test
    fun `rename file publishes operation failed event when command fails`() {
        val tempDir = Files.createTempDirectory("project-action-rename-fail-event").toFile()
        val source = tempDir.resolve("source.txt")
        val existingTarget = tempDir.resolve("target.txt")
        source.writeText("source")
        existingTarget.writeText("target")
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        val logger = mockk<LoggerService>(relaxed = true)
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }
        val engine: Engine = mockk()
        val handler = ProjectActionHandler(engine, projectManager, undoRedoManager, mockk())
        var failedEvent: FileSystemEvent.FileSystemOperationFailed? = null
        var changedEvents = 0
        eventSystem.subscribe<FileSystemEvent.FileSystemOperationFailed> { failedEvent = it }
        eventSystem.subscribe<FileSystemEvent.FileSystemChangedEvent> { changedEvents++ }

        eventSystem.publish(RenameFileRequested(source.absolutePath, existingTarget.name))

        assertNotNull(failedEvent)
        assertEquals("rename", failedEvent?.operation)
        assertEquals(source.absolutePath, failedEvent?.path)
        assertNotNull(failedEvent?.reason)
        assertEquals(0, changedEvents)
        tempDir.deleteRecursively()
    }

    @Test
    fun `delete file publishes operation failed event when command fails`() {
        val tempDir = Files.createTempDirectory("project-action-delete-fail-event").toFile()
        val missing = tempDir.resolve("missing.txt")
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        val logger = mockk<LoggerService>(relaxed = true)
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        val engine: Engine = mockk()
        val handler = ProjectActionHandler(engine, projectManager, undoRedoManager, mockk())
        var failedEvent: FileSystemEvent.FileSystemOperationFailed? = null
        var changedEvents = 0
        eventSystem.subscribe<FileSystemEvent.FileSystemOperationFailed> { failedEvent = it }
        eventSystem.subscribe<FileSystemEvent.FileSystemChangedEvent> { changedEvents++ }

        eventSystem.publish(DeleteFileRequested(missing.absolutePath))

        assertNotNull(failedEvent)
        assertEquals("delete", failedEvent?.operation)
        assertEquals(missing.absolutePath, failedEvent?.path)
        assertNotNull(failedEvent?.reason)
        assertEquals(0, changedEvents)
        tempDir.deleteRecursively()
    }
}
