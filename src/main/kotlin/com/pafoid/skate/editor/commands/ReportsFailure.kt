package com.pafoid.skate.editor.commands

/**
 * A command whose failure the user must see (EDT-10). When it fails, [UndoRedoManager]
 * [com.pafoid.skate.editor.systems.UndoRedoManager] publishes an
 * [EditorError][com.pafoid.skate.editor.events.EditorError] with [errorTitleKey] and [getFailureReason].
 */
interface ReportsFailure : ExecutionTrackedCommand {
    val errorTitleKey: String
}
