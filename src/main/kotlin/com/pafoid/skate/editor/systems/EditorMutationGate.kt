package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.commands.AllowDuringPlayCommand
import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.logEditor

class EditorMutationGate(
    private val engine: Engine,
    private val logger: LoggerService,
) {
    fun canExecute(command: Command): Boolean {
        if (!engine.runtimePlaying) return true
        return command is AllowDuringPlayCommand
    }

    fun blockIfPlaying(operation: String): Boolean {
        if (!engine.runtimePlaying) return false
        logger.logEditor("Blocked '$operation' while runtime play is active")
        return true
    }
}
