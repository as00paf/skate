package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.assetstore.AttributionLedger
import com.pafoid.skate.editor.assetstore.LedgerEntry
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.core.LoggerService
import java.io.File

/**
 * Deletes a file or folder (moved to a temp trash, with its `.meta` sidecars) and restores it on undo. With a
 * [registry], the deleted files' GUIDs also leave the project's attribution ledger (AST-25), so store credits stay
 * correct; undo puts the ledger entries back.
 */
class DeleteFileCommand(
    private val filePath: String,
    private val logger: LoggerService,
    private val registry: AssetRegistry? = null,
) : ExecutionTrackedCommand {

    private var tempFile: File? = null
    private val originalFile = File(filePath)
    private var executeSucceeded = false
    private var failureReason: String? = null
    private var forgottenEntries: List<LedgerEntry> = emptyList()

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

        val ids = assetIds(originalFile)
        try {
            if (!AssetFileOps.trash(originalFile, trashFile)) {
                failureReason = "could not move to trash"
                logger.logEditor("Failed to delete: ${originalFile.name} — could not move to trash")
                return
            }
            tempFile = trashFile
            executeSucceeded = true
            forgottenEntries = forgetInLedger(ids)
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
            registry?.let { reg ->
                runCatching { AttributionLedger.restoreEntries(reg.root, forgottenEntries) }
                    .onFailure { logger.logEditor("Attribution ledger not restored for ${originalFile.name}: ${it.message}") }
            }
            forgottenEntries = emptyList()
            logger.logEditor("Restored: ${originalFile.name}")
        } else if (temp.exists()) {
            logger.logEditor("Failed to restore: ${originalFile.name}")
        }
    }

    /** GUIDs of [file] (or of every file in a folder) read from their sidecars. */
    private fun assetIds(file: File): Set<String> {
        if (registry == null) return emptySet()
        val files = if (file.isDirectory) file.walkTopDown().filter { it.isFile && !AssetMeta.isSidecar(it) }.toList() else listOf(file)
        return files.mapNotNull { AssetMeta.read(it)?.guid?.value }.toSet()
    }

    /** A ledger problem never fails the delete: the file is already gone, so the error is only logged. */
    private fun forgetInLedger(ids: Set<String>): List<LedgerEntry> {
        val root = registry?.root ?: return emptyList()
        return try {
            AttributionLedger.forgetAssets(root, ids)
        } catch (e: Exception) {
            logger.logEditor("Attribution ledger not updated after deleting ${originalFile.name}: ${e.message}")
            emptyList()
        }
    }

    override fun wasSuccessful(): Boolean = executeSucceeded

    override fun getFailureReason(): String? = failureReason

    override fun getDisplayName(): String = "Delete ${originalFile.name}"
    override fun getTargetName(): String? = originalFile.name
}
