package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.project.RenameFileCommand
import com.pafoid.skate.editor.systems.LoggerService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RenameFileCommandTest {

    @Test
    fun `execute and undo rename file successfully`() {
        val logger = mockk<LoggerService>(relaxed = true)
        val tempDir = Files.createTempDirectory("rename-file-command").toFile()
        val original = tempDir.resolve("source.txt")
        val renamed = tempDir.resolve("renamed.txt")
        original.writeText("content")
        val command = RenameFileCommand(original.absolutePath, renamed.name, logger)

        command.execute()

        assertTrue(command.wasSuccessful())
        assertFalse(original.exists())
        assertTrue(renamed.exists())

        command.undo()

        assertTrue(original.exists())
        assertFalse(renamed.exists())
        tempDir.deleteRecursively()
    }

    @Test
    fun `execute fails when destination already exists`() {
        val logger = mockk<LoggerService>(relaxed = true)
        val tempDir = Files.createTempDirectory("rename-file-command-existing-target").toFile()
        val original = tempDir.resolve("source.txt")
        val destination = tempDir.resolve("existing.txt")
        original.writeText("source")
        destination.writeText("destination")
        val command = RenameFileCommand(original.absolutePath, destination.name, logger)

        command.execute()

        assertFalse(command.wasSuccessful())
        assertTrue(original.exists())
        assertTrue(destination.exists())
        tempDir.deleteRecursively()
    }

    @Test
    fun `execute_WithSameDestinationName_SkipsRenameAndKeepsOriginalFile`() {
        val logger = mockk<LoggerService>(relaxed = true)
        val tempDir = Files.createTempDirectory("rename-file-command-same-name").toFile()
        val original = tempDir.resolve("source.txt")
        original.writeText("source")
        val command = RenameFileCommand(original.absolutePath, original.name, logger)

        command.execute()
        command.undo()

        assertFalse(command.wasSuccessful())
        assertTrue(original.exists())
        tempDir.deleteRecursively()
    }
}
