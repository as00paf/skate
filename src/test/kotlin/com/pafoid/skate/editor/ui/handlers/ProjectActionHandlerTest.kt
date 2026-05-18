package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.events.CreateFileRequested
import com.pafoid.skate.editor.events.DeleteFileRequested
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.events.RenameFileRequested
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ProjectActionHandlerTest {

    @Test
    fun `create file publishes file system changed event when command succeeds`() {
        val tempDir = Files.createTempDirectory("project-action-create-success").toFile()
        val target = tempDir.resolve("created.txt")
        val eventSystem = EventSystem()
        val undoRedoManager = mockk<UndoRedoManager>()
        val logger = mockk<LoggerService>(relaxed = true)
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        val handler = ProjectActionHandler(undoRedoManager, logger, eventSystem)
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
        val undoRedoManager = mockk<UndoRedoManager>()
        val logger = mockk<LoggerService>(relaxed = true)
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        val handler = ProjectActionHandler(undoRedoManager, logger, eventSystem)
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
        val undoRedoManager = mockk<UndoRedoManager>()
        val logger = mockk<LoggerService>(relaxed = true)
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        val handler = ProjectActionHandler(undoRedoManager, logger, eventSystem)
        handler.init()
        var changedEvents = 0
        eventSystem.subscribe<FileSystemEvent.FileSystemChangedEvent> {
            changedEvents++
        }

        eventSystem.publish(DeleteFileRequested(missing.absolutePath))

        assertEquals(0, changedEvents)
        tempDir.deleteRecursively()
    }
}
