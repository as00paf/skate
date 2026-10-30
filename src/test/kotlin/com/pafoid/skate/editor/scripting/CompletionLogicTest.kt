package com.pafoid.skate.editor.scripting

import com.pafoid.skate.engine.scripting.completion.Completion
import com.pafoid.skate.engine.scripting.completion.CompletionKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompletionLogicTest {
    private fun item(label: String, insert: String = label, cursor: Int = insert.length) =
        Completion(label, CompletionKind.METHOD, insert, cursor, replaceStart = 0, replaceEnd = 0)

    @Test
    fun CompletionSession_FiltersPrefixBeforeSubstring() {
        val items = listOf(item("getName"), item("name"), item("rename"))
        val session = CompletionSession(items, 0, "na", 2)
        assertEquals(listOf("name", "getName", "rename"), session.filtered.map { it.label })
    }

    @Test
    fun CompletionSession_ClosesWhenNothingMatchesOrWordEnds() {
        val session = CompletionSession(listOf(item("move")), 0, "m", 1)
        assertFalse(session.update("mx", 2))
        val again = CompletionSession(listOf(item("move")), 0, "m", 1)
        assertFalse(again.update("m.", 2))
    }

    @Test
    fun CompletionSession_SelectionWraps() {
        val session = CompletionSession(listOf(item("a"), item("b")), 0, "", 0)
        session.move(-1)
        assertEquals("b", session.current()?.label)
        session.move(1)
        assertEquals("a", session.current()?.label)
    }

    @Test
    fun CompletionSession_AcceptReplacesPrefixAndHonoursCursorOffset() {
        val snippet = item("log", "log.info(\"\")", cursor = 10).copy(replaceStart = 4, replaceEnd = 6)
        val session = CompletionSession(listOf(snippet), 4, "ctx.lo", 6)
        val edit = session.accept(6)!!
        assertEquals("ctx.log.info(\"\")", edit.apply("ctx.lo"))
        assertEquals(14, edit.caretAfter)
    }

    @Test
    fun CompletionSession_AutoPopupTriggers() {
        assertTrue(CompletionSession.shouldAutoPopup("ctx.", 4, '.'))
        assertTrue(CompletionSession.shouldAutoPopup("ct", 2, 't'))
        assertFalse(CompletionSession.shouldAutoPopup("ct(", 3, '('))
        assertFalse(CompletionSession.shouldAutoPopup("// no", 5, 'o'))
        assertFalse(CompletionSession.shouldAutoPopup("\"ab", 3, 'b'))
    }

    @Test
    fun BracketAutoClose_ClosesAndSkipsOver() {
        assertEquals("()", BracketAutoClose.afterTyped("(", 1, '(')!!.let { "(" + it.insert })
        assertNull(BracketAutoClose.afterTyped("(a", 2, 'a'))
        val skip = BracketAutoClose.afterTyped("()) ", 2, ')')!!
        assertEquals("() ", skip.apply("()) "))
        assertNull(BracketAutoClose.afterTyped("(x", 1, '('))
    }

    @Test
    fun ParameterHints_FindsActiveArgument() {
        val sigs = mapOf("find" to "fun find(name: String, deep: Boolean): ScriptEntity?")
        val hint = ParameterHints.at("scene.find(\"a\", ", 16, sigs)!!
        assertEquals(1, hint.activeIndex)
        assertEquals(listOf("name: String", "deep: Boolean"), hint.parameters)
        assertNull(ParameterHints.at("scene.find(\"a\")", 15, sigs))
    }
}
