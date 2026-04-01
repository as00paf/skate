package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.ecs.Scene

/**
 * Base class for all scene events.
 * 
 * Scene events are published when scenes are opened, saved, or closed.
 * 
 * @property eventName The event name for scripting integration
 */
sealed class SceneEvent(eventName: String) : GameEvent(eventName)

/**
 * Published when a new scene is opened.
 * 
 * @param scene The opened scene
 * 
 * ## Usage
 * 
 * ```kotlin
 * eventSystem.subscribe<SceneOpened> { event ->
 *     hierarchyWindow.refresh(event.scene)
 * }
 * ```
 */
data class SceneOpened(val scene: Scene) : SceneEvent("editor.scene_opened")

/**
 * Published when a scene is saved.
 * 
 * @param scene The saved scene
 */
data class SceneSaved(val scene: Scene) : SceneEvent("editor.scene_saved")

/**
 * Published when the active scene changes.
 * 
 * This is published after [SceneOpened] to notify windows
 * that they should update their scene references.
 */
object SceneChanged : SceneEvent("editor.scene_changed")

/**
 * Published when a scene is about to be closed.
 * 
 * @param scene The scene being closed
 */
data class SceneClosing(val scene: Scene) : SceneEvent("editor.scene_closing")

/**
 * Published when a scene is closed.
 * 
 * @param scene The closed scene
 */
data class SceneClosed(val scene: Scene) : SceneEvent("editor.scene_closed")
