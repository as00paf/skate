package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.Event

/**
 * Domain actions for scene management operations.
 * These are published when the user requests a scene operation
 * (e.g., from UI context menu, keyboard shortcut, or search).
 */
sealed class SceneAction(eventName: String) : Event(eventName){
    // Create
    data class Created(val scene: Scene) : SceneAction("scene.action.created")
    object CreateRequested : SceneAction("scene.action.create_requested")

    // Open
    data class Opened(val scene: Scene) : SceneAction("editor.scene_opened")
    data class OpenSucceeded(val scene: Scene) : SceneAction("scene.action.open_succeeded")
    data class OpenFailed(val reason: String) : SceneAction("scene.action.open_failed")
    object OpenRequested : SceneAction("scene.action.open_requested")
    data class OpenPathRequested(val scenePath: String) : SceneAction("scene.action.open_path_requested")
    object OpenCancelled : SceneAction("scene.action.open_cancelled")

    // Close
    data class Closing(val scene: Scene) : SceneAction("editor.scene_closing")
    data class Closed(val scene: Scene) : SceneAction("editor.scene_closed")
    data class CloseRequested(val scene: Scene) : SceneAction("scene.action.close_requested")
    data class CloseOthersRequested(val keepScene: Scene) : SceneAction("scene.action.close_others_requested")
    object CloseAllRequested : SceneAction("scene.action.close_all_requested")

    // Save
    data class Saved(val scene: Scene) : SceneAction("editor.scene_saved")
    data class SaveRequested(val scene: Scene) : SceneAction("scene.action.save_requested")
    data class SaveAsRequested(val scene: Scene) : SceneAction("scene.action.save_as_requested")

    // Rename
    data class RenameRequested(val scene: Scene, val newName: String) : SceneAction("scene.action.rename_requested")
    data class Renamed(val scene: Scene, val oldName: String, val newName: String) : SceneAction("editor.scene_renamed")

    // Delete
    data class DeleteRequested(val scene: Scene) : SceneAction("scene.action.delete_requested")

    // Change
    object Changed : SceneAction("editor.scene_changed")
    object ResetScene : SceneAction("editor.reset_scene")
}
