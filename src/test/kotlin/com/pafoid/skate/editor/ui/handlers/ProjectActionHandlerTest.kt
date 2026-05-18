package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.events.ProjectEvent.*
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        val logger = mockk<LoggerService>(relaxed = true)
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        val handler = ProjectActionHandler(projectManager, undoRedoManager, logger, eventSystem)
        handler.init()
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
        val logger = mockk<LoggerService>(relaxed = true)
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        val handler = ProjectActionHandler(projectManager, undoRedoManager, logger, eventSystem)
        handler.init()
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
        val logger = mockk<LoggerService>(relaxed = true)
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        val handler = ProjectActionHandler(projectManager, undoRedoManager, logger, eventSystem)
        handler.init()
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

        val handler = ProjectActionHandler(projectManager, undoRedoManager, logger, eventSystem)
        handler.init()

        var successReceived = false
        var failedReceived = false
        eventSystem.subscribe<OpenProjectSucceeded> { successReceived = true }
        eventSystem.subscribe<OpenProjectFailed> { failedReceived = true }

        eventSystem.publish(OpenProjectRequested("C:/test/MyProject.skateproject"))

        assertTrue(successReceived)
        assertFalse(failedReceived)
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

        val handler = ProjectActionHandler(projectManager, undoRedoManager, logger, eventSystem)
        handler.init()

        var successCount = 0
        var failureEvent: CreateProjectFailed? = null
        eventSystem.subscribe<CreateProjectSucceeded> { successCount++ }
        eventSystem.subscribe<CreateProjectFailed> { failureEvent = it }

        eventSystem.publish(CreateProjectRequested("MyProject", "C:/tmp", "v1"))

        assertEquals(0, successCount)
        assertEquals("MyProject", failureEvent?.name)
        assertTrue(failureEvent?.reason?.contains("boom") == true)
    }

    @Test
    fun `close project requested closes current project through handler`() {
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>(relaxed = true)
        val logger = mockk<LoggerService>(relaxed = true)
        every { projectManager.hasProject() } returns true

        val handler = ProjectActionHandler(projectManager, undoRedoManager, logger, eventSystem)
        handler.init()

        eventSystem.publish(CloseProjectRequested)

        verify(exactly = 1) { projectManager.closeProject() }
    }

    @Test
    fun `save project requested executes save command through undo manager`() {
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        val logger = mockk<LoggerService>(relaxed = true)
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }
        every { projectManager.saveProject() } returns true

        val handler = ProjectActionHandler(projectManager, undoRedoManager, logger, eventSystem)
        handler.init()

        eventSystem.publish(SaveProjectRequested)

        verify(exactly = 1) { undoRedoManager.executeCommand(any()) }
        verify(exactly = 1) { projectManager.saveProject() }
    }

    @Test
    fun `load last project requested delegates to project manager`() {
        val eventSystem = EventSystem()
        val projectManager = mockk<ProjectManager>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>(relaxed = true)
        val logger = mockk<LoggerService>(relaxed = true)
        every { projectManager.loadLastProject() } returns false

        val handler = ProjectActionHandler(projectManager, undoRedoManager, logger, eventSystem)
        handler.init()

        eventSystem.publish(LoadLastProjectRequested)

        verify(exactly = 1) { projectManager.loadLastProject() }
    }
}
