package com.pafoid.skate.editor.commands.editor

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.core.LoggerService

class ClearLogsCommand(private val logger: LoggerService) : ExecuteOnlyCommand {

    val backup = logger.logs

    override fun execute() {
        logger.clearLogs()
    }

    override fun undo() {
        logger.logs.addAll(backup)
    }

    override fun getDisplayName(): String = "Clear Console Logs"

    override fun getTargetName(): String = "Console"
}
