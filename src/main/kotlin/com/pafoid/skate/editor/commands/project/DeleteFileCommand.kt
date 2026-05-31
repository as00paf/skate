package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.systems.LoggerService
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Undoable command for file/folder deletion.
 * Moves the file to the system temp directory instead of permanent delete for safety.
 * This avoids leaving visible `.trash_*` artifacts in the project folder.
 * Undo restores the file from the system temp location back to its original path.
 */
class DeleteFileCommand(
    private val filePath: String,
    private val logger: LoggerService
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
            val movedViaRename = originalFile.renameTo(trashFile)
            if (!movedViaRename) {
                // Cross-filesystem fallback (e.g. system temp on a different drive on Windows)
                try {
                    Files.move(originalFile.toPath(), trashFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (e: Exception) {
                    failureReason = "could not move to trash"
                    logger.logEditor("Failed to delete: ${originalFile.name} — could not move to trash")
                    return
                }
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
        if (temp.exists()) {
            val renamedBack = temp.renameTo(originalFile)
            if (!renamedBack) {
                // Cross-filesystem fallback
                try {
                    Files.move(temp.toPath(), originalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    logger.logEditor("Restored: ${originalFile.name}")
                } catch (e: Exception) {
                    logger.logEditor("Failed to restore: ${originalFile.name}")
                }
            } else {
                logger.logEditor("Restored: ${originalFile.name}")
            }
        }
    }

    override fun wasSuccessful(): Boolean = executeSucceeded

    override fun getFailureReason(): String? = failureReason

    override fun getDisplayName(): String = "Delete ${originalFile.name}"
    override fun getTargetName(): String? = originalFile.name
}
