package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.project.DeleteFileCommand
import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DeleteFileCommandTest {

    @Test
    fun `execute and undo move file to trash then restore`() {
        val logger = mockk<LoggerService>(relaxed = true)
        val tempDir = Files.createTempDirectory("delete-file-command-test").toFile()
        val file = tempDir.resolve("notes.txt")
        file.writeText("hello")

        val command = DeleteFileCommand(file.absolutePath, logger)
        command.execute()

        assertFalse(file.exists())
        assertTrue(command.wasSuccessful())
        assertTrue(tempDir.listFiles()?.any { it.name.startsWith(".trash_notes.txt_") } == true)

        command.undo()

        assertTrue(file.exists())
        verify { logger.logEditor(match { it.contains("Deleted: notes.txt") }, any<LogLevel>()) }
        verify { logger.logEditor(match { it.contains("Restored: notes.txt") }, any<LogLevel>()) }

        tempDir.deleteRecursively()
    }

    @Test
    fun `execute logs and exits when file does not exist`() {
        val logger = mockk<LoggerService>(relaxed = true)
        val tempDir = Files.createTempDirectory("delete-file-command-missing").toFile()
        val missing = tempDir.resolve("missing.txt")

        val command = DeleteFileCommand(missing.absolutePath, logger)
        command.execute()

        assertFalse(command.wasSuccessful())
        verify { logger.logEditor(match { it.contains("Delete skipped, file does not exist") }, any<LogLevel>()) }
        tempDir.deleteRecursively()
    }
}
