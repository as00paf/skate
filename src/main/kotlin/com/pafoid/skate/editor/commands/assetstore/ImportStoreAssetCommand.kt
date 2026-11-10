package com.pafoid.skate.editor.commands.assetstore

import com.pafoid.skate.editor.assetstore.ImportOutcome
import com.pafoid.skate.editor.assetstore.StagedImport
import com.pafoid.skate.editor.assetstore.StoreImportRequest
import com.pafoid.skate.editor.assetstore.StoreImporter
import com.pafoid.skate.editor.commands.AsyncCommand
import com.pafoid.skate.editor.commands.KeyedCommand
import com.pafoid.skate.editor.commands.ReportsFailure
import kotlinx.coroutines.Job

/** Runs [io] on a worker, then [main] on the main thread; the returned job completes after [main] has run. */
fun interface ImportRunner {
    fun launch(io: () -> Unit, main: () -> Unit): Job
}

/**
 * Copies a staged store download into `Assets/Store/<provider>/<slug>/` (AST-16 §7). File copies and backups run on
 * an IO worker ([StoreImporter.stage]); registration, sidecars and the ledger run on the main thread
 * ([StoreImporter.commit]). Undo removes the imported files through `AssetRegistry.delete` (or restores the previous
 * version of a re-import) and the ledger entry; redo imports again from the staging folder, which is kept.
 *
 * [projectStillOpen] is checked before committing (a project switched during the copy discards the import) and before
 * undoing.
 * [onChange] is called on the main thread after every import, failure and undo, so views can refresh.
 */
class ImportStoreAssetCommand(
    private val importer: StoreImporter,
    private val request: StoreImportRequest,
    private val runner: ImportRunner,
    private val projectStillOpen: () -> Boolean = { true },
    private val onChange: (ImportStoreAssetCommand, Change) -> Unit = { _, _ -> },
) : AsyncCommand, ReportsFailure, KeyedCommand {
    enum class Change { IMPORTED, FAILED, UNDONE }

    override val errorTitleKey = "assetstore.error.import.title"
    override val displayNameKey = "assetstore.command.import"

    @Volatile private var outcome: ImportOutcome? = null
    @Volatile private var failure: String? = null
    @Volatile private var job: Job? = null

    /** Import folder of the last successful run; kept after undo so views can refresh it. */
    @Volatile var importedFolder: java.io.File? = null
        private set
    val title: String get() = request.title

    override fun execute() {
        outcome = null
        failure = null
        var staged: StagedImport? = null
        job = runner.launch(
            io = {
                staged = try {
                    importer.stage(request)
                } catch (e: Exception) {
                    failure = e.message ?: e.javaClass.simpleName
                    null
                }
            },
            main = {
                staged?.let { s ->
                    if (!projectStillOpen()) {
                        importer.discard(s)
                        failure = "The project changed during the import"
                    } else {
                        outcome = try {
                            importer.commit(s).also { importedFolder = it.targetDir }
                        } catch (e: Exception) {
                            failure = e.message ?: e.javaClass.simpleName
                            null
                        }
                    }
                }
                onChange(this, if (outcome != null) Change.IMPORTED else Change.FAILED)
            },
        )
    }

    /** A no-op when another project is open now (undo history outlives a project; its backups are pruned on close). */
    override fun undo() {
        val done = outcome ?: return
        if (!projectStillOpen()) return
        importer.revert(done, request.ref)
        outcome = null
        onChange(this, Change.UNDONE)
    }

    override fun getCompletionJob(): Job? = job

    override fun didCompleteSuccessfully(): Boolean = outcome != null

    override fun wasSuccessful(): Boolean = outcome != null

    override fun getFailureReason(): String? = failure

    override fun getDisplayName(): String = displayNameKey

    override fun getTargetName(): String = request.title
}
