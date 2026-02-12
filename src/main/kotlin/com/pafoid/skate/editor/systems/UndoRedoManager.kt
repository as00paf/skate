package com.pafoid.skate.editor.systems

interface Command {
    fun execute()
    fun undo()
}

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
}
