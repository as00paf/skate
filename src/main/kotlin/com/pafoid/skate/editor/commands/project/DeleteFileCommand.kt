package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.systems.LoggerService
import java.io.File

/**
 * Undoable command for file/folder deletion.
 * Moves to a temporary location instead of permanent delete for safety.
 */
class DeleteFileCommand(
    private val filePath: String,
    private val logger: LoggerService
) : Command {

    private var tempFile: File? = null
    private val originalFile = File(filePath)

    override fun execute() {
        if (!originalFile.exists()) return
        tempFile = File(originalFile.parentFile, ".trash_${originalFile.name}_${System.currentTimeMillis()}")
        try {
            originalFile.renameTo(tempFile!!)
            logger.logEditor("Deleted: ${originalFile.name}")
        } catch (e: Exception) {
            logger.logEditor("Failed to delete: ${originalFile.name} — ${e.message}")
        }
    }

    override fun undo() {
        val temp = tempFile ?: return
        if (temp.exists()) {
            temp.renameTo(originalFile)
            logger.logEditor("Restored: ${originalFile.name}")
        }
    }

    override fun getDisplayName(): String = "Delete ${originalFile.name}"
    override fun getTargetName(): String? = originalFile.name
}
