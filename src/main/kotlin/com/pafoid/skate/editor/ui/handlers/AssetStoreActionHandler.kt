package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.assetstore.StoreImporter
import com.pafoid.skate.editor.assetstore.StorePaths
import com.pafoid.skate.editor.commands.assetstore.ImportRunner
import com.pafoid.skate.editor.commands.assetstore.ImportStoreAssetCommand
import com.pafoid.skate.editor.commands.assetstore.ImportStoreAssetCommand.Change
import com.pafoid.skate.editor.events.AssetStoreEvent
import com.pafoid.skate.editor.events.EditorError
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.JobSystem
import java.io.File

/**
 * Runs asset store imports as undoable async commands (AST-16): copies on an IO worker, registry and ledger on the
 * main thread. Publishes file-system, ledger and result events after every import, failure, undo and redo.
 */
class AssetStoreActionHandler(
    private val eventSystem: EventSystem,
    private val jobSystem: JobSystem,
    private val projectManager: ProjectManager,
    private val undoRedoManager: UndoRedoManager,
) {
    private val runner = ImportRunner { io, main ->
        jobSystem.runIO {
            io()
            jobSystem.runOnMain { main() }.join()
        }
    }

    init {
        eventSystem.subscribe<AssetStoreEvent.ImportRequested> { event ->
            val request = event.request
            val projectDir = projectManager.getProjectDirectory()?.absoluteFile
            val registry = projectManager.assetRegistry()
            val reason = when {
                projectDir == null || registry == null -> "No project is open"
                !StorePaths.isProjectStaging(projectDir, request.stagingDir) -> "The download belongs to another project"
                else -> null
            }
            if (reason != null) {
                eventSystem.publish(AssetStoreEvent.ImportFinished(request.title, false, reason))
                return@subscribe
            }
            val command = ImportStoreAssetCommand(
                StoreImporter(projectDir!!, registry!!), request, runner,
                projectStillOpen = { sameProject(projectDir) },
                onChange = ::onChange,
            )
            undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<ProjectEvent.Closing> { event ->
            runCatching { StoreImporter.pruneAllBackups(event.project.getProjectDirectory()) }
        }
    }

    private fun sameProject(dir: File): Boolean =
        projectManager.getProjectDirectory()?.let { it.canonicalFile == dir.canonicalFile } == true

    private fun onChange(command: ImportStoreAssetCommand, change: Change) {
        if (change != Change.FAILED) {
            eventSystem.publish(FileSystemEvent.FileSystemChangedEvent(command.importedFolder?.path))
            eventSystem.publish(AssetStoreEvent.LedgerChanged)
        }
        when (change) {
            Change.IMPORTED -> eventSystem.publish(AssetStoreEvent.ImportFinished(command.title, true, null))
            Change.FAILED -> {
                eventSystem.publish(AssetStoreEvent.ImportFinished(command.title, false, command.getFailureReason()))
                // Async commands skip UndoRedoManager's failure dialog, so report it here (EDT-10).
                eventSystem.publish(EditorError(command.errorTitleKey, command.getFailureReason()))
            }
            Change.UNDONE -> Unit
        }
    }
}
