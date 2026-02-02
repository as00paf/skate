package com.pafoid.skate.engine.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UndoRedoManagerTest {

    class MockCommand(private val state: MutableList<String>, private val value: String) : Command {
        override fun execute() {
            state.add(value)
        }

        override fun undo() {
            state.remove(value)
        }
    }

    @Test
    fun `test undo redo`() {
        val manager = UndoRedoManager()
        val state = mutableListOf<String>()

        val cmd1 = MockCommand(state, "A")
        val cmd2 = MockCommand(state, "B")

        manager.executeCommand(cmd1)
        assertEquals(listOf("A"), state)

        manager.executeCommand(cmd2)
        assertEquals(listOf("A", "B"), state)

        manager.undo()
        assertEquals(listOf("A"), state)

        manager.undo()
        assertEquals(emptyList<String>(), state)

        manager.redo()
        assertEquals(listOf("A"), state)

        manager.redo()
        assertEquals(listOf("A", "B"), state)
    }
}
