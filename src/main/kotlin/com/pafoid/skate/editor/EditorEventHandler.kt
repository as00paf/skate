package com.pafoid.skate.editor

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.EventSystem
import com.pafoid.skate.engine.events.GameObjectSelected
import com.pafoid.skate.engine.events.SelectionCleared

class EditorEventHandler(
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
    private val workspace: EditorWorkspace
) {

    val selectedGameObject: GameObject? get() = workspace.getSelectedGameObject()

    /**
     * Initialize the ViewModel by subscribing to selection events.
     *
     * Call this in [onInit] of your window lifecycle.
     */
    fun init() {
        // Subscribe to selection events
        eventSystem.subscribe<GameObjectSelected> { event ->
            workspace.setSelectedGameObject(event.gameObject)
        }

        eventSystem.subscribe<SelectionCleared> {
            workspace.setSelectedGameObject(null)
        }
    }

    /**
     * Select a GameObject.
     *
     * This publishes a [GameObjectSelected] event which will
     * update all subscribed UI components.
     *
     * @param gameObject The GameObject to select, or null to clear
     */
    fun select(gameObject: GameObject?) {
        workspace.setSelectedGameObject(gameObject)
        if (gameObject != null) {
            eventSystem.publish(GameObjectSelected(gameObject))
        } else {
            eventSystem.publish(SelectionCleared)
        }
    }

    /**
     * Clear the current selection.
     *
     * This publishes a [SelectionCleared] event.
     */
    fun clear() {
        select(null)
    }


    /**
     * Cleanup the ViewModel.
     *
     * Call this in [onDestroy] of your window lifecycle.
     * The EventSystem will automatically remove listeners on destroy.
     */
    fun destroy() {
        workspace.setSelectedGameObject(null)
    }
}