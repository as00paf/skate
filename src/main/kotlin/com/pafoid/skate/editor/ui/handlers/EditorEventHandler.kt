package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.editor.events.GameObjectSelected
import com.pafoid.skate.editor.events.SelectionCleared

class EditorEventHandler(
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
) {
    fun init() {
        eventSystem.subscribe<GameObjectSelected> { event ->
            sceneManager.currentScene?.selectedGameObject = event.gameObject
        }

        eventSystem.subscribe<SelectionCleared> {
            sceneManager.currentScene?.selectedGameObject = null
        }
    }
}