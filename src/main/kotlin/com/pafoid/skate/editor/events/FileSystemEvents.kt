package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

sealed class FileSystemEvent(eventName: String) : Event(eventName){
    data class FileSystemChangedEvent(val affectedPath: String?) : FileSystemEvent("filesystem.changed")
    data class FileSystemOperationFailed(val path: String, val operation: String, val reason: String) : FileSystemEvent("filesystem.operation_failed")

    /** Shows [path] in the Project window: opens its folders, selects it and scrolls to it (EDT-61). */
    data class RevealInProject(val path: String) : FileSystemEvent("filesystem.reveal")

    /** A file was selected in the Project window; the inspector shows its import settings when it has some (EDT-66). */
    data class FileSelected(val path: String) : FileSystemEvent("filesystem.selected")

    /** Import [path] again from its source, even when unchanged (Project window ▸ Reimport, EDT-66). */
    data class ReimportRequested(val path: String) : FileSystemEvent("filesystem.reimport")
}
