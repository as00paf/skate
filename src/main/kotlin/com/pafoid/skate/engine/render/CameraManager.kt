package com.pafoid.skate.engine.render

import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import org.koin.core.component.KoinComponent

/**
 * Manages the active camera used by the renderer based on engine state.
 */
class CameraManager(
    private val sceneManager: SceneManager,
    private val editorCamera: EditorCamera,
    private val eventSystem: EventSystem,
) : KoinComponent {

    private var isRuntimePlaying = false

    init {
        eventSystem.subscribe<ViewportAction.SetRuntimePlaying> {
            isRuntimePlaying = it.playing
        }
    }

    /**
     * Returns the appropriate camera instance based on whether the game is running.
     */
    fun getActiveCamera(): Camera? {
        return if (isRuntimePlaying) {
            sceneManager.currentScene?.camera
        } else {
            // Access the Camera instance held by the EditorCamera system
            // We need to ensure we can access it; assuming a getter or public property
            editorCamera.camera
        }
    }
}
