package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.ecs.Scene

/**
 * Domain actions for scene management operations.
 * These are published when the user requests a scene operation
 * (e.g., from UI context menu, keyboard shortcut, or search).
 */
sealed class SceneAction(eventName: String) : Event(eventName)

data class SceneRenameRequested(val scene: Scene, val newName: String) : SceneAction("scene.action.rename_requested")
data class SceneSaveRequested(val scene: Scene) : SceneAction("scene.action.save_requested")
data class SceneSaveAsRequested(val scene: Scene) : SceneAction("scene.action.save_as_requested")
data class SceneCloseRequested(val scene: Scene) : SceneAction("scene.action.close_requested")
data class SceneCloseOthersRequested(val keepScene: Scene) : SceneAction("scene.action.close_others_requested")
object SceneCloseAllRequested : SceneAction("scene.action.close_all_requested")
object SceneCreateRequested : SceneAction("scene.action.create_requested")
object SceneOpenRequested : SceneAction("scene.action.open_requested")
data class SceneCreated(val scene: Scene) : SceneAction("scene.action.created")
data class SceneTabSelected(val scene: Scene) : SceneAction("scene.action.tab_selected")
data class SceneDeleteRequested(val scene: Scene) : SceneAction("scene.action.delete_requested")
