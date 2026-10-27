package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.core.LoggerService
import java.io.File

class DeleteFileCommand(
    private val filePath: String,
    private val logger: LoggerService,
    private val registry: AssetRegistry? = null,
) : ExecutionTrackedCommand {

    private var tempFile: File? = null
    private val originalFile = File(filePath)
    private var executeSucceeded = false
    private var failureReason: String? = null

    override fun execute() {
        executeSucceeded = false
        failureReason = null

        if (!originalFile.exists()) {
            failureReason = "file does not exist: ${originalFile.name}"
            logger.logEditor("Delete skipped, file does not exist: ${originalFile.absolutePath}")
            return
        }

        val tmpDir = File(System.getProperty("java.io.tmpdir") ?: System.getProperty("user.home", "."))
        val trashFile = File(tmpDir, ".trash_${originalFile.name}_${System.currentTimeMillis()}")

        try {
            if (!AssetFileOps.trash(originalFile, trashFile)) {
                failureReason = "could not move to trash"
                logger.logEditor("Failed to delete: ${originalFile.name} — could not move to trash")
                return
            }
            tempFile = trashFile
            executeSucceeded = true
            logger.logEditor("Deleted: ${originalFile.name}")
        } catch (e: Exception) {
            failureReason = e.message ?: "unexpected error during delete"
            logger.logEditor("Failed to delete: ${originalFile.name} — ${e.message}")
        }
    }

    override fun undo() {
        if (!executeSucceeded) {
            return
        }
        val temp = tempFile ?: return
        if (AssetFileOps.restore(temp, originalFile, registry)) {
            logger.logEditor("Restored: ${originalFile.name}")
        } else if (temp.exists()) {
            logger.logEditor("Failed to restore: ${originalFile.name}")
        }
    }

    override fun wasSuccessful(): Boolean = executeSucceeded

    override fun getFailureReason(): String? = failureReason

    override fun getDisplayName(): String = "Delete ${originalFile.name}"
    override fun getTargetName(): String? = originalFile.name
}
