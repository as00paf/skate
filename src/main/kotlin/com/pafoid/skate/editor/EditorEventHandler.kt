package com.pafoid.skate.editor

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.scene.setSelectedGameObject
import com.pafoid.skate.engine.events.GameObjectSelected
import com.pafoid.skate.engine.events.SelectionCleared

class EditorEventHandler(
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
) {
    fun init() {
        eventSystem.subscribe<GameObjectSelected> { event ->
            sceneManager.currentScene?.setSelectedGameObject(event.gameObject)
        }

        eventSystem.subscribe<SelectionCleared> {
            sceneManager.currentScene?.setSelectedGameObject(null)
        }
    }
}