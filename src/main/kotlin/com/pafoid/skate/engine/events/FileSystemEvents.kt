package com.pafoid.skate.engine.events


sealed class FileSystemEvent(eventName: String) : Event(eventName)

data class FileSystemChangedEvent(val affectedPath: String?) : FileSystemEvent("filesystem.changed")
data class OpenSceneFileEvent(val scenePath: String) : FileSystemEvent("filesystem.open_scene")
