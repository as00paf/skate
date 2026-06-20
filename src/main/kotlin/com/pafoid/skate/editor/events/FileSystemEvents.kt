package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

sealed class FileSystemEvent(eventName: String) : Event(eventName){
    data class FileSystemChangedEvent(val affectedPath: String?) : FileSystemEvent("filesystem.changed")
    data class FileSystemOperationFailed(val path: String, val operation: String, val reason: String) : FileSystemEvent("filesystem.operation_failed")
}
