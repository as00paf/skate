package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.systems.LoggerService
import java.io.File

/**
 * Undoable command for file/folder renaming.
 */
class RenameFileCommand(
    private val oldPath: String,
    private val newName: String,
    private val logger: LoggerService
) : Command {

    private val oldFile = File(oldPath)
    private val newFile = File(oldFile.parent, newName)

    override fun execute() {
        if (oldFile.exists() && !newFile.exists()) {
            oldFile.renameTo(newFile)
            logger.logEditor("Renamed: ${oldFile.name} → $newName")
        }
    }

    override fun undo() {
        if (newFile.exists()) {
            newFile.renameTo(oldFile)
            logger.logEditor("Renamed back: $newName → ${oldFile.name}")
        }
    }

    override fun getDisplayName(): String = "Rename ${oldFile.name} → $newName"
    override fun getTargetName(): String? = oldFile.name
}