package com.pafoid.skate.engine.editor

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.AllowDuringPlayCommand
import com.pafoid.skate.editor.systems.EditorMutationGate
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
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

    class AllowInPlayCommand(
        private val state: MutableList<String>,
        private val value: String
    ) : Command, AllowDuringPlayCommand {
        override fun execute() {
            state.add(value)
        }

        override fun undo() {
            state.remove(value)
        }

        override fun getDisplayName(): String = "Allow in play"
        override fun getTargetName(): String? = null
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

    @Test
    fun `blocks command execution while runtime playing`() {
        val engine = Engine().apply { runtimePlaying = true }
        val manager = UndoRedoManager(EditorMutationGate(engine, LoggerService()), LoggerService())
        val state = mutableListOf<String>()
        manager.executeCommand(MockCommand(state, "A"))
        assertEquals(emptyList<String>(), state)
        assertEquals(0, manager.getUndoCount())
    }

    @Test
    fun `allows allowlisted command while runtime playing`() {
        val engine = Engine().apply { runtimePlaying = true }
        val manager = UndoRedoManager(EditorMutationGate(engine, LoggerService()), LoggerService())
        val state = mutableListOf<String>()
        manager.executeCommand(AllowInPlayCommand(state, "A"))
        assertEquals(listOf("A"), state)
        assertEquals(1, manager.getUndoCount())
    }
}
