package com.pafoid.skate.editor.scripting

import com.pafoid.skate.editor.commands.project.SaveScriptCommand
import com.pafoid.skate.engine.scripting.host.ScriptError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class ScriptEditorLogicTest {
    private fun kinds(line: String) = ScriptSyntax.tokenize(line)[0].map { line.substring(it.start, it.end) to it.kind }

    @Test
    fun ScriptSyntax_HighlightsKeywordsStringsNumbersComments() {
        val tokens = kinds("val x = \"a//b\" + 42 // note")
        assertTrue("val" to TokenKind.KEYWORD in tokens)
        assertTrue("\"a//b\"" to TokenKind.STRING in tokens)
        assertTrue("42" to TokenKind.NUMBER in tokens)
        assertTrue("// note" to TokenKind.COMMENT in tokens)
    }

    @Test
    fun ScriptSyntax_BlockCommentSpansLines() {
        val lines = ScriptSyntax.tokenize("a /* x\ny */ val")
        assertEquals(TokenKind.COMMENT, lines[1][0].kind)
        assertEquals(TokenKind.KEYWORD, lines[1].last().kind)
    }

    @Test
    fun ScriptTemplates_ValidatesNames() {
        assertEquals("Player.kts", ScriptTemplates.fileName(" Player "))
        assertEquals("a_b.kts", ScriptTemplates.fileName("a_b.kts"))
        assertNull(ScriptTemplates.fileName("../evil"))
        assertNull(ScriptTemplates.fileName("1abc"))
        assertNull(ScriptTemplates.fileName(""))
    }

    @Test
    fun ScriptDocument_TracksDirtyAndLines() {
        val doc = ScriptDocument(File("a.kts"), "one\ntwo\nthree")
        assertFalse(doc.isDirty)
        doc.edit("one\ntwo\nthree\nfour")
        assertTrue(doc.isDirty)
        assertEquals(4, doc.lineCount)
        assertEquals(4, doc.offsetOfLine(2))
        assertEquals(2, doc.lineOfOffset(5))
        doc.markSaved()
        assertFalse(doc.isDirty)
    }

    @Test
    fun ScriptDocument_DiagnosticsReplacedPerSource() {
        val doc = ScriptDocument(File("a.kts"), "x")
        doc.setDiagnostics(DiagnosticSource.COMPILE, listOf(ScriptDiagnostic.from(ScriptError("a.kts", 3, 1, "bad"), DiagnosticSource.COMPILE)))
        doc.setDiagnostics(DiagnosticSource.RUNTIME, listOf(ScriptDiagnostic(5, null, "boom", DiagnosticSource.RUNTIME)))
        doc.setDiagnostics(DiagnosticSource.COMPILE, emptyList())
        assertEquals(listOf(5), doc.diagnostics.map { it.line })
        assertEquals(1, doc.diagnosticsAt(5).size)
    }

    @Test
    fun ScriptDocument_MatchesRelativeAndAbsolutePaths() {
        val doc = ScriptDocument(File("/proj/Assets/Scripts/a.kts"), "")
        assertTrue(doc.matchesPath("Assets/Scripts/a.kts"))
        assertTrue(doc.matchesPath("/proj/Assets/Scripts/a.kts"))
        assertFalse(doc.matchesPath("Assets/Scripts/b.kts"))
    }

    @Test
    fun SaveScriptCommand_WritesAndUndoes() {
        val dir = Files.createTempDirectory("scripts").toFile()
        try {
            val file = File(dir, "Assets/new.kts")
            val create = SaveScriptCommand(file, "v1")
            create.execute()
            assertEquals("v1", file.readText())
            val edit = SaveScriptCommand(file, "v2")
            edit.execute()
            edit.undo()
            assertEquals("v1", file.readText())
            create.undo()
            assertFalse(file.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
