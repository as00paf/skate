package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

sealed class UndoRedoAction(eventName: String) : Event(eventName) {
    /** Undo the last executed command. */
    object Undo : UndoRedoAction("undoredo.undo")

    /** Redo the last undone command. */
    object Redo : UndoRedoAction("undoredo.redo")

    /**
     * Undo commands until the undo stack has [targetSize] entries remaining.
     * Used when clicking a past entry in the command history list.
     */
    data class UndoTo(val targetSize: Int) : UndoRedoAction("undoredo.undo_to")

    /**
     * Redo commands until the redo stack has [targetSize] entries remaining.
     * Used when clicking a future entry in the command history list.
     */
    data class RedoTo(val targetSize: Int) : UndoRedoAction("undoredo.redo_to")

    /** Clear all undo/redo history. */
    object ClearHistory : UndoRedoAction("undoredo.clear_history")
}
