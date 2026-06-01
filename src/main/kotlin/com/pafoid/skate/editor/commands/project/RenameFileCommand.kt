package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.logEditor
import java.io.File

/**
 * Undoable command for file/folder renaming.
 */
class RenameFileCommand(
    private val oldPath: String,
    private val newName: String,
    private val logger: LoggerService
) : ExecutionTrackedCommand {

    private val oldFile = File(oldPath)
    private val newFile = File(oldFile.parent, newName)
    private var executeSucceeded = false
    private var failureReason: String? = null

    override fun execute() {
        executeSucceeded = false
        failureReason = null

        if (!oldFile.exists()) {
            failureReason = "source does not exist: ${oldFile.name}"
            logger.logEditor("Rename failed: source does not exist: ${oldFile.absolutePath}")
            return
        }

        if (oldFile.parentFile == null) {
            failureReason = "source parent directory not found"
            logger.logEditor("Rename failed: source parent directory not found: ${oldFile.absolutePath}")
            return
        }

        if (oldFile.absolutePath == newFile.absolutePath) {
            failureReason = "source and destination are the same: ${oldFile.name}"
            logger.logEditor("Rename skipped: source and destination are the same for ${oldFile.name}")
            return
        }

        if (newFile.exists()) {
            failureReason = "destination already exists: ${newFile.name}"
            logger.logEditor("Rename failed: destination already exists: ${newFile.absolutePath}")
            return
        }

        val renamed = oldFile.renameTo(newFile)
        if (!renamed) {
            failureReason = "rename failed: ${oldFile.name} → $newName"
            logger.logEditor("Rename failed: ${oldFile.name} → $newName")
            return
        }

        executeSucceeded = true
        logger.logEditor("Renamed: ${oldFile.name} → $newName")
    }

    override fun undo() {
        if (!executeSucceeded || !newFile.exists()) {
            return
        }

        val renamedBack = newFile.renameTo(oldFile)
        if (renamedBack) {
            logger.logEditor("Renamed back: $newName → ${oldFile.name}")
        } else {
            logger.logEditor("Failed to rename back: $newName → ${oldFile.name}")
        }
    }

    override fun wasSuccessful(): Boolean = executeSucceeded

    override fun getFailureReason(): String? = failureReason

    override fun getDisplayName(): String = "Rename ${oldFile.name} → $newName"
    override fun getTargetName(): String? = oldFile.name
}
