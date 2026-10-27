package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.core.LoggerService
import java.io.File

/** Moves a file or folder into [targetDir], keeping its `.meta` sidecar and GUID. */
class MoveFileCommand(
    sourcePath: String,
    targetDirPath: String,
    private val logger: LoggerService,
    private val registry: AssetRegistry? = null,
) : ExecutionTrackedCommand {
    private val source = File(sourcePath)
    private val targetDir = File(targetDirPath)
    private val destination = File(targetDir, source.name)
    private var succeeded = false
    private var failureReason: String? = null

    override fun execute() {
        succeeded = false
        failureReason = null
        failureReason = when {
            !source.exists() -> "source does not exist: ${source.name}"
            !targetDir.isDirectory -> "target is not a folder: ${targetDir.name}"
            source.absoluteFile.parentFile == targetDir.absoluteFile -> "already in ${targetDir.name}"
            source.isDirectory && targetDir.absoluteFile.toPath().startsWith(source.absoluteFile.toPath()) ->
                "cannot move a folder into itself"
            destination.exists() -> "destination already exists: ${destination.name}"
            !AssetFileOps.move(source, destination, registry) -> "move failed: ${source.name}"
            else -> null
        }
        succeeded = failureReason == null
        logger.logEditor(
            if (succeeded) "Moved: ${source.name} → ${targetDir.name}" else "Move failed: $failureReason"
        )
    }

    override fun undo() {
        if (!succeeded || !destination.exists()) return
        if (AssetFileOps.move(destination, source, registry)) {
            logger.logEditor("Moved back: ${source.name}")
        } else {
            logger.logEditor("Failed to move back: ${source.name}")
        }
    }

    override fun wasSuccessful(): Boolean = succeeded
    override fun getFailureReason(): String? = failureReason
    override fun getDisplayName(): String = "Move ${source.name} → ${targetDir.name}"
    override fun getTargetName(): String? = source.name
}
