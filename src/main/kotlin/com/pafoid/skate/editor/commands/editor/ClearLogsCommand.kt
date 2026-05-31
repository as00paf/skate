package com.pafoid.skate.editor.commands.editor

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.systems.LoggerService

/**
 * Clears all engine and editor console logs.
 * This is an execute-only command — clearing logs is not undoable.
 */
class ClearLogsCommand(private val logger: LoggerService) : ExecuteOnlyCommand {

    override fun execute() {
        logger.clearAllLogs()
    }

    override fun undo() {
        // Execute-only: clearing logs cannot be undone
    }

    override fun getDisplayName(): String = "Clear Console Logs"

    override fun getTargetName(): String? = null
}
