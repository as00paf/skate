package com.pafoid.skate.engine.editor

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.AllowDuringPlayCommand
import com.pafoid.skate.editor.commands.AsyncCommand
import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.systems.EditorMutationGate
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import kotlinx.coroutines.CompletableJob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlinx.coroutines.Job

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

    class ExecuteOnlyMockCommand(
        private val state: MutableList<String>,
        private val value: String
    ) : ExecuteOnlyCommand {
        override fun execute() {
            state.add(value)
        }

        override fun undo() = Unit

        override fun getDisplayName(): String = "Execute only"
        override fun getTargetName(): String? = null
    }

    class AsyncMockCommand(
        private val state: MutableList<String>,
        private val value: String,
        private val outcomes: MutableList<Boolean>,
        private val pushOnSuccess: Boolean
    ) : AsyncCommand {
        private var completionJob: Job? = null
        @Volatile
        private var completedSuccessfully = false

        override fun execute() {
            val success = outcomes.removeFirstOrNull() ?: false
            if (success) {
                state.add(value)
            }
            completedSuccessfully = success
            completionJob = Job().also { it.complete() }
        }

        override fun undo() {
            state.remove(value)
        }

        override fun getCompletionJob(): Job? = completionJob

        override fun didCompleteSuccessfully(): Boolean = completedSuccessfully

        override fun shouldPushToHistoryOnSuccess(): Boolean = pushOnSuccess

        override fun getDisplayName(): String = "Async mock"
        override fun getTargetName(): String? = null
    }

    class ReentrantAsyncMockCommand : AsyncCommand {
        private var completionJob: CompletableJob? = null
        @Volatile
        private var completedSuccessfully = false

        override fun execute() {
            completedSuccessfully = false
            completionJob = Job()
        }

        override fun undo() = Unit

        override fun getCompletionJob(): Job? = completionJob

        override fun didCompleteSuccessfully(): Boolean = completedSuccessfully

        override fun shouldPushToHistoryOnSuccess(): Boolean = true

        override fun getDisplayName(): String = "Reentrant async mock"

        override fun getTargetName(): String? = null

        fun latestJob(): CompletableJob = completionJob ?: error("Completion job not initialized")

        fun complete(job: CompletableJob, success: Boolean) {
            completedSuccessfully = success
            job.complete()
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

    @Test
    fun `execute only command is not tracked and clears redo history`() {
        val manager = UndoRedoManager()
        val state = mutableListOf<String>()
        manager.executeCommand(MockCommand(state, "A"))
        manager.executeCommand(MockCommand(state, "B"))
        manager.undo()

        assertEquals(1, manager.getUndoCount())
        assertEquals(1, manager.getRedoCount())

        manager.executeCommand(ExecuteOnlyMockCommand(state, "EXEC"))

        assertEquals(listOf("A", "EXEC"), state)
        assertEquals(1, manager.getUndoCount())
        assertEquals(0, manager.getRedoCount())
    }

    @Test
    fun `async command pushes history only after successful completion`() {
        val manager = UndoRedoManager()
        val state = mutableListOf<String>()

        manager.executeCommand(AsyncMockCommand(state, "ASYNC_OK", outcomes = mutableListOf(true), pushOnSuccess = true))
        manager.executeCommand(AsyncMockCommand(state, "ASYNC_FAIL", outcomes = mutableListOf(false), pushOnSuccess = true))

        assertEquals(listOf("ASYNC_OK"), state)
        assertEquals(1, manager.getUndoCount())
    }

    @Test
    fun `async execute only command does not push history on success`() {
        val manager = UndoRedoManager()
        val state = mutableListOf<String>()

        manager.executeCommand(
            AsyncMockCommand(
                state,
                "ASYNC_EXECUTE_ONLY",
                outcomes = mutableListOf(true),
                pushOnSuccess = false
            )
        )

        assertEquals(listOf("ASYNC_EXECUTE_ONLY"), state)
        assertEquals(0, manager.getUndoCount())
    }

    @Test
    fun `async redo keeps command in redo history when completion fails`() {
        val manager = UndoRedoManager()
        val state = mutableListOf<String>()
        val command = AsyncMockCommand(
            state = state,
            value = "ASYNC_REDO",
            outcomes = mutableListOf(true, false),
            pushOnSuccess = true
        )

        manager.executeCommand(command)
        manager.undo()
        manager.redo()

        assertEquals(emptyList<String>(), state)
        assertEquals(0, manager.getUndoCount())
        assertEquals(1, manager.getRedoCount())
    }

    @Test
    fun `async command ignores stale completion from previous execution`() {
        val manager = UndoRedoManager()
        val command = ReentrantAsyncMockCommand()

        manager.executeCommand(command)
        val firstJob = command.latestJob()

        manager.executeCommand(command)
        val secondJob = command.latestJob()

        command.complete(firstJob, success = true)
        assertEquals(0, manager.getUndoCount())

        command.complete(secondJob, success = true)
        assertEquals(1, manager.getUndoCount())
    }

    @Test
    fun `clear invalidates pending async execute completion`() {
        val manager = UndoRedoManager()
        val command = ReentrantAsyncMockCommand()

        manager.executeCommand(command)
        val firstJob = command.latestJob()
        manager.clear()

        command.complete(firstJob, success = true)
        assertEquals(0, manager.getUndoCount())
        assertEquals(0, manager.getRedoCount())
    }

    @Test
    fun `clear invalidates pending async redo completion`() {
        val manager = UndoRedoManager()
        val command = ReentrantAsyncMockCommand()

        manager.executeCommand(command)
        val executeJob = command.latestJob()
        command.complete(executeJob, success = true)
        assertEquals(1, manager.getUndoCount())

        manager.undo()
        manager.redo()
        val redoJob = command.latestJob()
        manager.clear()

        command.complete(redoJob, success = true)
        assertEquals(0, manager.getUndoCount())
        assertEquals(0, manager.getRedoCount())
    }
}
