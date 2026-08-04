package com.pafoid.skate.engine.editor

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.systems.UndoRedoManager
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UndoRedoManagerTest {
    class MockCommand(
        private val state: MutableList<String>,
        private val value: String
    ) : Command {
        override fun execute() {
            state.add(value)
        }

        override fun undo() {
            state.remove(value)
        }

        override fun getDisplayName(): String = "Mock Command"
        override fun getTargetName(): String? = null
    }

    @Test
    fun `test undo redo`() {
        val manager = UndoRedoManager(mockk(), mockk())
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

    @Test
    fun `execute only command is not tracked and clears redo history`() {
        val manager = UndoRedoManager(mockk(), mockk())
        val state = mutableListOf<String>()
        manager.executeCommand(MockCommand(state, "A"))
        manager.executeCommand(MockCommand(state, "B"))
        manager.undo()

        assertEquals(1, manager.getUndoCount())
        assertEquals(1, manager.getRedoCount())

        manager.executeCommand(MockCommand(state, "EXEC"))

        assertEquals(listOf("A", "EXEC"), state)
        assertEquals(1, manager.getUndoCount())
        assertEquals(0, manager.getRedoCount())
    }
}
