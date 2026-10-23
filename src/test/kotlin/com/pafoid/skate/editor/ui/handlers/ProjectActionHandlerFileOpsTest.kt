package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.events.ProjectEvent.CreateFileRequested
import com.pafoid.skate.editor.events.ProjectEvent.DeleteFileRequested
import com.pafoid.skate.editor.events.ProjectEvent.RenameFileRequested
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProjectActionHandlerFileOpsTest {

    @TempDir
    lateinit var tempDir: File

    private val eventSystem = EventSystem()
    private var changed = 0
    private var failed: FileSystemEvent.FileSystemOperationFailed? = null

    private fun setup() {
        val engine = mockk<Engine>()
        every { engine.eventSystem } returns eventSystem
        every { engine.logger } returns mockk(relaxed = true)
        every { engine.jobSystem } returns mockk(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>()
        every { undoRedoManager.executeCommand(any()) } answers { firstArg<Command>().execute() }

        ProjectActionHandler(engine, mockk(relaxed = true), mockk(relaxed = true), undoRedoManager, mockk(relaxed = true))
        eventSystem.subscribe<FileSystemEvent.FileSystemChangedEvent> { changed++ }
        eventSystem.subscribe<FileSystemEvent.FileSystemOperationFailed> { failed = it }
    }

    @Test
    fun `create file publishes file system changed event when command succeeds`() {
        setup()
        eventSystem.publish(CreateFileRequested(File(tempDir, "created.txt").absolutePath, false))

        assertEquals(1, changed)
        assertEquals(null, failed)
    }

    @Test
    fun `create file publishes operation failed event when target exists`() {
        setup()
        val existing = File(tempDir, "existing.txt").apply { writeText("content") }

        eventSystem.publish(CreateFileRequested(existing.absolutePath, false))

        assertEquals(0, changed)
        assertEquals("create", failed?.operation)
        assertEquals(existing.absolutePath, failed?.path)
    }

    @Test
    fun `rename file publishes operation failed event when target exists`() {
        setup()
        val source = File(tempDir, "source.txt").apply { writeText("source") }
        val target = File(tempDir, "target.txt").apply { writeText("target") }

        eventSystem.publish(RenameFileRequested(source.absolutePath, target.name))

        assertEquals(0, changed)
        assertEquals("rename", failed?.operation)
        assertNotNull(failed?.reason)
    }

    @Test
    fun `delete file publishes operation failed event when file is missing`() {
        setup()
        val missing = File(tempDir, "missing.txt")

        eventSystem.publish(DeleteFileRequested(missing.absolutePath))

        assertEquals(0, changed)
        assertEquals("delete", failed?.operation)
    }
}
