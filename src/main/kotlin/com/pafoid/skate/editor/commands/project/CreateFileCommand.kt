package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.systems.LoggerService
import java.io.File

/**
 * Undoable command for file or folder creation.
 */
class CreateFileCommand(
    private val filePath: String,
    private val isDirectory: Boolean,
    private val logger: LoggerService
) : ExecutionTrackedCommand {

    private val file = File(filePath)
    private var wasCreated = false
    private var executeSucceeded = false

    override fun execute() {
        executeSucceeded = false
        wasCreated = false

        if (file.exists()) {
            logger.logEditor("Create skipped, path already exists: ${file.absolutePath}")
            return
        }

        try {
            wasCreated = if (isDirectory) {
                file.mkdirs()
            } else {
                val parent = file.parentFile
                if (parent != null && !parent.exists()) {
                    val parentCreated = parent.mkdirs()
                    if (!parentCreated && !parent.exists()) {
                        logger.logEditor("Failed to create parent directory for: ${file.absolutePath}")
                        false
                    } else {
                        file.createNewFile()
                    }
                } else if (parent != null && !parent.isDirectory) {
                    logger.logEditor("Failed to create: ${file.name} — parent is not a directory")
                    false
                } else {
                    file.createNewFile()
                }
            }

            if (!wasCreated) {
                logger.logEditor("Failed to create: ${file.name}")
                return
            }

            if (isDirectory && !file.isDirectory) {
                logger.logEditor("Failed to create directory: ${file.name}")
                wasCreated = false
                return
            }

            if (!isDirectory && !file.isFile) {
                logger.logEditor("Failed to create file: ${file.name}")
                wasCreated = false
                return
            }

            executeSucceeded = true
            logger.logEditor("Created: ${file.name}")
        } catch (e: Exception) {
            logger.logEditor("Failed to create: ${file.name} — ${e.message}")
            wasCreated = false
            executeSucceeded = false
        }
    }

    override fun undo() {
        if (!wasCreated || !file.exists()) {
            return
        }

        if (isDirectory) {
            val deleted = file.delete()
            if (!deleted) {
                logger.logEditor("Undo skipped for directory creation: ${file.name} is not empty or cannot be removed")
                return
            }
            logger.logEditor("Undo create directory: ${file.name}")
            return
        }

        val deleted = file.delete()
        if (deleted) {
            logger.logEditor("Undo create file: ${file.name}")
        } else {
            logger.logEditor("Failed to undo file creation: ${file.name}")
        }
    }

    override fun wasSuccessful(): Boolean = executeSucceeded

    override fun getDisplayName(): String = "Create ${if (isDirectory) "Folder" else "File"} ${file.name}"
    override fun getTargetName(): String? = file.name
}
