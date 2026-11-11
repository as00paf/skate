package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ReportsFailure
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/** GPL-26: Apply in the config inspector is undoable and a failed write is reported (EDT-10). */
class SaveConfigFileCommandTest {

    private val dir: File = createTempDirectory("gpl26cmd").toFile()

    @AfterEach
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun applyWritesAndUndoRestoresThePreviousText() {
        val file = File(dir, "skater.character.json").apply { writeText("""{"a":1}""") }
        val command = SaveConfigFileCommand(file, """{"a":2}""")

        command.execute()
        assertTrue(command.wasSuccessful())
        assertEquals("""{"a":2}""", file.readText())

        command.undo()
        assertEquals("""{"a":1}""", file.readText())

        command.execute() // redo
        assertEquals("""{"a":2}""", file.readText())
    }

    @Test
    fun aFailedWrite_IsReportedWithItsReason() {
        val notAFile = File(dir, "skater.character.json").apply { mkdirs() } // a directory cannot be written as text
        val command = SaveConfigFileCommand(notAFile, "{}")

        command.execute()

        assertFalse(command.wasSuccessful())
        assertTrue((command as ReportsFailure).errorTitleKey == "error.save_config.title")
        assertNotNull(command.getFailureReason())
        command.undo() // nothing to undo
        assertTrue(notAFile.isDirectory)
    }
}
