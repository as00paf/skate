package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.systems.LoggerService
import java.io.File

/**
 * Undoable command for file or folder creation.
 */
class CreateFileCommand(
    private val filePath: String,
    private val isDirectory: Boolean,
    private val logger: LoggerService
) : Command {

    private val file = File(filePath)
    private var wasCreated = false

    override fun execute() {
        try {
            wasCreated = if (isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
        } catch (e: Exception) {
            logger.logEditor("Failed to create: ${file.name} — ${e.message}")
        }
    }

    override fun undo() {
        if (wasCreated && file.exists()) {
            if (isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }
    }

    override fun getDisplayName(): String = "Create ${if (isDirectory) "Folder" else "File"} ${file.name}"
    override fun getTargetName(): String? = file.name
}