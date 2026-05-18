package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.project.CreateFileCommand
import com.pafoid.skate.editor.systems.LoggerService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class CreateFileCommandTest {

    @Test
    fun `execute and undo create file successfully`() {
        val logger = mockk<LoggerService>(relaxed = true)
        val tempDir = Files.createTempDirectory("create-file-command").toFile()
        val file = tempDir.resolve("new-file.txt")
        val command = CreateFileCommand(file.absolutePath, false, logger)

        command.execute()

        assertTrue(file.exists())
        assertTrue(command.wasSuccessful())

        command.undo()

        assertFalse(file.exists())
        tempDir.deleteRecursively()
    }

    @Test
    fun `undo directory creation does not delete non-empty directory`() {
        val logger = mockk<LoggerService>(relaxed = true)
        val tempDir = Files.createTempDirectory("create-directory-command").toFile()
        val directory = tempDir.resolve("new-folder")
        val childFile = directory.resolve("child.txt")
        val command = CreateFileCommand(directory.absolutePath, true, logger)

        command.execute()
        childFile.writeText("keep")

        command.undo()

        assertTrue(directory.exists())
        assertTrue(childFile.exists())
        tempDir.deleteRecursively()
    }

    @Test
    fun `execute fails when target already exists`() {
        val logger = mockk<LoggerService>(relaxed = true)
        val tempDir = Files.createTempDirectory("create-file-command-existing").toFile()
        val file = tempDir.resolve("existing.txt")
        file.writeText("existing")
        val command = CreateFileCommand(file.absolutePath, false, logger)

        command.execute()

        assertFalse(command.wasSuccessful())
        assertTrue(file.exists())
        tempDir.deleteRecursively()
    }

    @Test
    fun `execute_WithNestedPath_CreatesMissingParentsAndUndoKeepsDirectories`() {
        val logger = mockk<LoggerService>(relaxed = true)
        val tempDir = Files.createTempDirectory("create-file-command-nested").toFile()
        val nestedFile = tempDir.resolve("nested/path/new-file.txt")
        val command = CreateFileCommand(nestedFile.absolutePath, false, logger)

        command.execute()
        assertTrue(command.wasSuccessful())
        assertTrue(nestedFile.exists())
        assertTrue(nestedFile.parentFile.exists())

        command.undo()
        assertFalse(nestedFile.exists())
        assertTrue(nestedFile.parentFile.exists())
        tempDir.deleteRecursively()
    }
}
