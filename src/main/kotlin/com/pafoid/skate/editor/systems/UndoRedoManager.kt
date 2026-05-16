package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.commands.Command

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
 * Thread safety: This class is NOT thread-safe. All operations should be performed on the main thread.
 */
class UndoRedoManager(
    private val mutationGate: EditorMutationGate? = null,
    private val logger: LoggerService? = null,
) {
    private val undoStack = mutableListOf<Command>()
    private val redoStack = mutableListOf<Command>()
    private val maxStackSize = 100

    fun executeCommand(command: Command) {
        if (mutationGate?.canExecute(command) == false) {
            logger?.logEditor("Command blocked in play mode: ${command.getDisplayName()}")
            return
        }
        command.execute()
        pushCommand(command)
    }

    fun pushCommand(command: Command) {
        undoStack.add(command)
        if (undoStack.size > maxStackSize) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val command = undoStack.removeAt(undoStack.size - 1)
            command.undo()
            redoStack.add(command)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val command = redoStack.removeAt(redoStack.size - 1)
            command.execute()
            undoStack.add(command)
        }
    }

    // History accessors for UI
    fun getUndoHistory(): List<Command> = undoStack.toList()
    fun getRedoHistory(): List<Command> = redoStack.toList()
    fun getUndoCount(): Int = undoStack.size
    fun getRedoCount(): Int = redoStack.size

    // Clear all history
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    // Undo to a specific index in the stack
    fun undoTo(index: Int) {
        while (undoStack.size > index && undoStack.isNotEmpty()) {
            undo()
        }
    }

    // Redo to a specific index in the stack
    fun redoTo(index: Int) {
        while (redoStack.size > index && redoStack.isNotEmpty()) {
            redo()
        }
    }
}
