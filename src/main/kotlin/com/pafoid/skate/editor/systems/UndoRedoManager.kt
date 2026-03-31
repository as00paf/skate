package com.pafoid.skate.editor.systems

class UndoRedoManager {
    private val undoStack = mutableListOf<Command>()
    private val redoStack = mutableListOf<Command>()
    private val maxStackSize = 100

    fun executeCommand(command: Command) {
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
