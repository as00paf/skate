package com.pafoid.skate.engine.events

/**
 * Emitted when the project file system changes (create/delete/rename).
 * Subscribers like AssetBrowserWindow should refresh their cached data.
 */
data class FileSystemChangedEvent(val affectedPath: String?) : GameEvent("filesystem.changed")

/**
 * Emitted when a .scene file is double-clicked in the Project window.
 */
data class OpenSceneFileEvent(val scenePath: String) : GameEvent("filesystem.open_scene")
