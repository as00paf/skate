package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager

class EditorEventHandler(
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
) {
    fun init() {
        eventSystem.subscribe<ViewportAction.GameObjectSelected> { event ->
            sceneManager.currentScene?.selectedGameObject = event.gameObject
        }

        eventSystem.subscribe<ViewportAction.SelectionCleared> {
            sceneManager.currentScene?.selectedGameObject = null
        }
    }
}