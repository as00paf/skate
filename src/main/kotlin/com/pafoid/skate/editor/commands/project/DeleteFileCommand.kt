package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.systems.LoggerService
import java.io.File

/**
 * Undoable command for file/folder deletion.
 * Moves to a temporary location instead of permanent delete for safety.
 */
class DeleteFileCommand(
    private val filePath: String,
    private val logger: LoggerService
) : ExecutionTrackedCommand {

    private var tempFile: File? = null
    private val originalFile = File(filePath)
    private var executeSucceeded = false

    override fun execute() {
        executeSucceeded = false

        if (!originalFile.exists()) {
            logger.logEditor("Delete skipped, file does not exist: ${originalFile.absolutePath}")
            return
        }

        val parent = originalFile.parentFile
        if (parent == null) {
            logger.logEditor("Failed to delete: ${originalFile.name} — parent directory not found")
            return
        }

        val trashFile = File(parent, ".trash_${originalFile.name}_${System.currentTimeMillis()}")
        try {
            val moved = originalFile.renameTo(trashFile)
            if (!moved) {
                logger.logEditor("Failed to delete: ${originalFile.name} — could not move to trash")
                return
            }
            tempFile = trashFile
            executeSucceeded = true
            logger.logEditor("Deleted: ${originalFile.name}")
        } catch (e: Exception) {
            logger.logEditor("Failed to delete: ${originalFile.name} — ${e.message}")
        }
    }

    override fun undo() {
        if (!executeSucceeded) {
            return
        }
        val temp = tempFile ?: return
        if (temp.exists()) {
            if (temp.renameTo(originalFile)) {
                logger.logEditor("Restored: ${originalFile.name}")
            } else {
                logger.logEditor("Failed to restore: ${originalFile.name}")
            }
        }
    }

    override fun wasSuccessful(): Boolean = executeSucceeded

    override fun getDisplayName(): String = "Delete ${originalFile.name}"
    override fun getTargetName(): String? = originalFile.name
}
