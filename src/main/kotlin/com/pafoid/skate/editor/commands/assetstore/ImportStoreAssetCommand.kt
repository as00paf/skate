package com.pafoid.skate.editor.commands.assetstore

import com.pafoid.skate.editor.assetstore.ImportOutcome
import com.pafoid.skate.editor.assetstore.StoreImportRequest
import com.pafoid.skate.editor.assetstore.StoreImporter
import com.pafoid.skate.editor.commands.ReportsFailure

/**
 * Copies a staged store download into `Assets/Store/<provider>/<slug>/` (AST-16 §7). Undo removes the imported files
 * through `AssetRegistry.delete` (or restores the previous version of a re-import) and the ledger entry; redo imports
 * again from the staging folder, which is kept.
 */
class ImportStoreAssetCommand(
    private val importer: StoreImporter,
    private val request: StoreImportRequest,
) : ReportsFailure {
    override val errorTitleKey = "assetstore.error.import"

    private var outcome: ImportOutcome? = null
    private var failure: String? = null

    val importedFolder get() = outcome?.targetDir

    override fun execute() {
        failure = null
        outcome = try {
            importer.import(request)
        } catch (e: Exception) {
            failure = e.message ?: e.javaClass.simpleName
            null
        }
    }

    override fun undo() {
        val done = outcome ?: return
        importer.revert(done, request.details.summary.ref)
        outcome = null
    }

    override fun wasSuccessful(): Boolean = outcome != null

    override fun getFailureReason(): String? = failure

    override fun getDisplayName(): String = "Import from Asset Store"

    override fun getTargetName(): String = request.title
}
