package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.commands.AsyncCommand
import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.CommandCategory
import kotlinx.coroutines.Job

/**
 * Manages undo/redo operations using the command pattern.
 *
 * This class maintains two stacks:
 * - **Undo stack**: Contains executed commands that can be undone
 * - **Redo stack**: Contains undone commands that can be redone
 *
 * Usage:
 * 1. Execute commands via `executeCommand(command)` which runs the command and pushes it to the undo stack
 * 2. Undo the last command with `undo()`, which moves it to the redo stack
 * 3. Redo a command with `redo()`, which moves it back to the undo stack
 *
 * The undo stack has a maximum size of 100 entries to prevent unbounded memory growth.
 * When a new command is executed after an undo, the redo stack is cleared.
 *
 * Async command completion can finalize from a background context; history mutations are guarded internally.
 */
class UndoRedoManager(
    private val mutationGate: EditorMutationGate? = null,
    private val logger: LoggerService? = null,
) {
    private val undoStack = mutableListOf<Command>()
    private val redoStack = mutableListOf<Command>()
    private val pendingAsyncRedoCommands = mutableSetOf<Command>()
    private val maxStackSize = 100
    private val historyLock = Any()

    fun executeCommand(command: Command) {
        if (mutationGate?.canExecute(command) == false) {
            logger?.logEditor("Command blocked in play mode: ${command.getDisplayName()}")
            return
        }
        when (command.getCategory()) {
            CommandCategory.UNDOABLE -> {
                command.execute()
                synchronized(historyLock) {
                    pushCommandInternal(command)
                }
            }

            CommandCategory.EXECUTE_ONLY -> {
                command.execute()
                synchronized(historyLock) {
                    redoStack.clear()
                }
            }

            CommandCategory.ASYNC -> executeAsyncCommand(command)
        }
    }

    fun pushCommand(command: Command) {
        synchronized(historyLock) {
            pushCommandInternal(command)
        }
    }

    private fun pushCommandInternal(command: Command) {
        undoStack.add(command)
        if (undoStack.size > maxStackSize) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo() {
        synchronized(historyLock) {
            if (undoStack.isNotEmpty()) {
                val command = undoStack.removeAt(undoStack.size - 1)
                command.undo()
                redoStack.add(command)
            }
        }
    }

    fun redo() {
        val command = synchronized(historyLock) {
            val redoCommand = redoStack.lastOrNull() ?: return
            if (redoCommand.getCategory() != CommandCategory.ASYNC) {
                return@synchronized redoCommand
            }

            if (!pendingAsyncRedoCommands.add(redoCommand)) {
                return
            }
            redoCommand
        }

        if (command.getCategory() == CommandCategory.ASYNC) {
            executeAsyncRedoCommand(command)
            return
        }

        synchronized(historyLock) {
            if (redoStack.isNotEmpty()) {
                val redoCommand = redoStack.removeAt(redoStack.size - 1)
                redoCommand.execute()
                pushRedoCommandToUndoInternal(redoCommand)
            }
        }
    }

    // History accessors for UI
    fun getUndoHistory(): List<Command> = synchronized(historyLock) { undoStack.toList() }
    fun getRedoHistory(): List<Command> = synchronized(historyLock) { redoStack.toList() }
    fun getUndoCount(): Int = synchronized(historyLock) { undoStack.size }
    fun getRedoCount(): Int = synchronized(historyLock) { redoStack.size }

    // Clear all history
    fun clear() {
        synchronized(historyLock) {
            undoStack.clear()
            redoStack.clear()
        }
    }

    // Undo to a specific index in the stack
    fun undoTo(index: Int) {
        while (getUndoCount() > index && getUndoCount() > 0) {
            undo()
        }
    }

    // Redo to a specific index in the stack
    fun redoTo(index: Int) {
        while (getRedoCount() > index && getRedoCount() > 0) {
            redo()
        }
    }

    private fun executeAsyncCommand(command: Command) {
        val asyncCommand = command as? AsyncCommand
        if (asyncCommand == null) {
            logger?.logEditor("Async command category requires AsyncCommand contract: ${command.getDisplayName()}")
            return
        }

        command.execute()
        val completionJob = asyncCommand.getCompletionJob()
        if (completionJob == null) {
            logger?.logEditor("Async command did not expose completion job: ${command.getDisplayName()}")
            return
        }

        completionJob.invokeOnCompletion { throwable ->
            if (!isCurrentCompletion(asyncCommand, completionJob)) {
                return@invokeOnCompletion
            }
            if (throwable != null || !asyncCommand.didCompleteSuccessfully()) {
                return@invokeOnCompletion
            }
            synchronized(historyLock) {
                if (asyncCommand.shouldPushToHistoryOnSuccess()) {
                    pushCommandInternal(command)
                } else {
                    redoStack.clear()
                }
            }
        }
    }

    private fun executeAsyncRedoCommand(command: Command) {
        val asyncCommand = command as? AsyncCommand
        if (asyncCommand == null) {
            logger?.logEditor("Async command category requires AsyncCommand contract: ${command.getDisplayName()}")
            synchronized(historyLock) {
                pendingAsyncRedoCommands.remove(command)
            }
            return
        }

        command.execute()
        val completionJob = asyncCommand.getCompletionJob()
        if (completionJob == null) {
            logger?.logEditor("Async redo command did not expose completion job: ${command.getDisplayName()}")
            synchronized(historyLock) {
                pendingAsyncRedoCommands.remove(command)
            }
            return
        }

        completionJob.invokeOnCompletion { throwable ->
            if (!isCurrentCompletion(asyncCommand, completionJob)) {
                return@invokeOnCompletion
            }
            synchronized(historyLock) {
                pendingAsyncRedoCommands.remove(command)
            }
            if (throwable != null || !asyncCommand.didCompleteSuccessfully()) {
                return@invokeOnCompletion
            }

            synchronized(historyLock) {
                val redoIndex = redoStack.lastIndexOf(command)
                if (redoIndex < 0) {
                    return@synchronized
                }
                redoStack.removeAt(redoIndex)
                pushRedoCommandToUndoInternal(command)
            }
        }
    }

    private fun isCurrentCompletion(asyncCommand: AsyncCommand, completionJob: Job): Boolean {
        val currentJob = asyncCommand.getCompletionJob()
        return currentJob === completionJob
    }

    private fun pushRedoCommandToUndoInternal(command: Command) {
        undoStack.add(command)
        if (undoStack.size > maxStackSize) {
            undoStack.removeAt(0)
        }
    }
}
