package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.assetstore.StoreImporter
import com.pafoid.skate.editor.commands.assetstore.ImportStoreAssetCommand
import com.pafoid.skate.editor.events.AssetStoreEvent
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem

/** Runs asset store imports as undoable commands (AST-16), on the main thread where the event is published. */
class AssetStoreActionHandler(
    private val eventSystem: EventSystem,
    private val projectManager: ProjectManager,
    private val undoRedoManager: UndoRedoManager,
) {
    init {
        eventSystem.subscribe<AssetStoreEvent.ImportRequested> { event ->
            val projectDir = projectManager.getProjectDirectory()
            val registry = projectManager.assetRegistry()
            if (projectDir == null || registry == null) {
                eventSystem.publish(AssetStoreEvent.ImportFinished(event.request.title, false, "No project is open"))
                return@subscribe
            }
            val command = ImportStoreAssetCommand(StoreImporter(projectDir, registry), event.request)
            undoRedoManager.executeCommand(command)
            if (command.wasSuccessful()) {
                eventSystem.publish(FileSystemEvent.FileSystemChangedEvent(command.importedFolder?.path))
            }
            eventSystem.publish(AssetStoreEvent.ImportFinished(event.request.title, command.wasSuccessful(), command.getFailureReason()))
        }
    }
}
