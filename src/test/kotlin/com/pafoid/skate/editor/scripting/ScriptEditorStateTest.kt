package com.pafoid.skate.editor.scripting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ScriptEditorStateTest {
    private fun doc(name: String, text: String = "") = ScriptDocument(File("Scripts/$name"), text)

    @Test
    fun TextSearch_FindAll_IgnoresCaseUnlessAsked() {
        assertEquals(listOf(0, 8), TextSearch.findAll("Foo bar foo", "foo"))
        assertEquals(listOf(8), TextSearch.findAll("Foo bar foo", "foo", matchCase = true))
        assertEquals(emptyList<Int>(), TextSearch.findAll("abc", ""))
    }

    @Test
    fun TextSearch_FindAll_DoesNotOverlap() {
        assertEquals(listOf(0, 2), TextSearch.findAll("aaaa", "aa"))
    }

    @Test
    fun TextSearch_Next_WrapsBothWays() {
        val text = "x a x b x"
        assertEquals(4, TextSearch.next(text, "x", 1))
        assertEquals(0, TextSearch.next(text, "x", 9))
        assertEquals(4, TextSearch.next(text, "x", 8, backwards = true))
        assertEquals(8, TextSearch.next(text, "x", 0, backwards = true))
        assertNull(TextSearch.next(text, "z", 0))
    }

    @Test
    fun TextSearch_ReplaceAt_OnlyReplacesAMatch() {
        val edit = TextSearch.replaceAt("val speed = 1", "speed", "velocity", 4)!!
        assertEquals("val velocity = 1", edit.apply("val speed = 1"))
        assertEquals(12, edit.caretAfter)
        assertNull(TextSearch.replaceAt("val speed = 1", "speed", "v", 3))
        assertNull(TextSearch.replaceAt("val speed = 1", "speed", "v", 20))
    }

    @Test
    fun TextSearch_ReplaceAll_CountsReplacements() {
        assertEquals("b-b-B" to 2, TextSearch.replaceAll("a-a-B", "A", "b"))
        assertEquals("a" to 0, TextSearch.replaceAll("a", "x", "y"))
    }

    @Test
    fun GoToLine_ParsesLineAndColumn() {
        assertEquals(GoToLine.Target(3, 1), GoToLine.parse(" 3 ", 10))
        assertEquals(GoToLine.Target(3, 5), GoToLine.parse("3:5", 10))
        assertEquals(GoToLine.Target(10, 1), GoToLine.parse("99", 10))
        assertNull(GoToLine.parse("0", 10))
        assertNull(GoToLine.parse("abc", 10))
        assertNull(GoToLine.parse("1:2:3", 10))
        assertNull(GoToLine.parse("", 10))
    }

    @Test
    fun GoToLine_Offset_ClampsColumnToLineEnd() {
        val text = "ab\ncdef\ng"
        assertEquals(0, GoToLine.offset(text, GoToLine.Target(1, 1)))
        assertEquals(5, GoToLine.offset(text, GoToLine.Target(2, 3)))
        assertEquals(7, GoToLine.offset(text, GoToLine.Target(2, 50)))
        assertEquals(9, GoToLine.offset(text, GoToLine.Target(3, 2)))
    }

    @Test
    fun GutterMarkers_GroupByLine_CompileFirst_SkipLineless() {
        val d = doc("A.kts", "a\nb\nc")
        d.setDiagnostics(DiagnosticSource.RUNTIME, listOf(ScriptDiagnostic(2, null, "boom", DiagnosticSource.RUNTIME)))
        d.setDiagnostics(
            DiagnosticSource.COMPILE,
            listOf(
                ScriptDiagnostic(2, 1, "unresolved x\nmore", DiagnosticSource.COMPILE),
                ScriptDiagnostic(null, null, "no position", DiagnosticSource.COMPILE),
            ),
        )
        val markers = d.gutterMarkers()
        assertEquals(setOf(2), markers.keys)
        assertEquals(DiagnosticSource.COMPILE, markers[2]!!.source)
        assertEquals("unresolved x\nboom", markers[2]!!.tooltip)
    }

    @Test
    fun ScriptTabs_OpenSameFileTwice_KeepsOneTab() {
        val tabs = ScriptTabs()
        val first = tabs.open(doc("A.kts"))
        val again = tabs.open(doc("A.kts"))
        assertSame(first, again)
        assertEquals(1, tabs.documents.size)
        assertSame(first, tabs.selectRequest)
    }

    @Test
    fun ScriptTabs_CloseClean_ClosesAndSelectsNeighbour() {
        val tabs = ScriptTabs()
        val a = tabs.open(doc("A.kts"))
        val b = tabs.open(doc("B.kts"))
        val c = tabs.open(doc("C.kts"))
        tabs.select(b)
        assertTrue(tabs.requestClose(b))
        assertEquals(listOf(a, c), tabs.documents)
        assertSame(c, tabs.active)
        assertTrue(tabs.requestClose(c))
        assertSame(a, tabs.active)
        assertTrue(tabs.requestClose(a))
        assertNull(tabs.active)
    }

    @Test
    fun ScriptTabs_CloseDirty_AsksFirst() {
        val tabs = ScriptTabs()
        val a = tabs.open(doc("A.kts", "x"))
        a.edit("y")
        assertTrue(tabs.hasUnsaved)
        assertFalse(tabs.requestClose(a))
        assertSame(a, tabs.pendingClose)
        tabs.cancelPending()
        assertEquals(listOf(a), tabs.documents)
        assertNull(tabs.pendingClose)
        tabs.requestClose(a)
        tabs.discardPending()
        assertTrue(tabs.documents.isEmpty())
        assertNull(tabs.pendingClose)
    }

    @Test
    fun ScriptTabs_Label_MarksUnsavedAndErrors_WithStableId() {
        val tabs = ScriptTabs()
        val a = tabs.open(doc("A.kts", "x"))
        assertEquals("A.kts###" + a.file.absolutePath, tabs.label(a))
        a.edit("y")
        assertTrue(tabs.label(a).startsWith("* A.kts###"))
        a.setDiagnostics(DiagnosticSource.COMPILE, listOf(ScriptDiagnostic(1, 1, "e", DiagnosticSource.COMPILE)))
        assertTrue(tabs.label(a).startsWith("* ! A.kts###"))
        a.markSaved()
        assertTrue(tabs.label(a).startsWith("! A.kts###"))
    }
}
