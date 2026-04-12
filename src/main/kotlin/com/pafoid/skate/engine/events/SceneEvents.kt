package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.ecs.Scene

sealed class SceneEvent(eventName: String) : Event(eventName)
data class SceneOpened(val scene: Scene) : SceneEvent("editor.scene_opened")
data class SceneSaved(val scene: Scene) : SceneEvent("editor.scene_saved")
object SceneChanged : SceneEvent("editor.scene_changed")
data class SceneClosing(val scene: Scene) : SceneEvent("editor.scene_closing")
data class SceneClosed(val scene: Scene) : SceneEvent("editor.scene_closed")
data class SceneRenamed(val scene: Scene, val oldName: String, val newName: String) : SceneEvent("editor.scene_renamed")
