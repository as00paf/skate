package com.pafoid.skate.editor.ui.viewmodels

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.EventSystem
import com.pafoid.skate.engine.events.GameObjectSelected
import com.pafoid.skate.engine.events.SelectionCleared

/**
 * ViewModel for selection state.
 * 
 * Provides observable selection state to UI components.
 * This ViewModel decouples UI windows from direct scene queries
 * by centralizing selection state management.
 * 
 * ## Usage
 * 
 * ```kotlin
 * class PropertiesWindow @Inject constructor(
 *     private val selectionViewModel: SelectionViewModel
 * ) : IWindowLifecycle {
 *     
 *     override fun onRender() {
 *         val selected = selectionViewModel.selectedGameObject
 *         selected?.let { renderProperties(it) }
 *     }
 * }
 * ```
 * 
 * @param sceneManager For accessing current scene
 * @param eventSystem For publishing/subscribing to selection events
 */
class SelectionViewModel(
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
    private val workspace: com.pafoid.skate.engine.core.EditorWorkspace
) {
    /**
     * The currently selected GameObject.
     * 
     * This property is updated when:
     * - [select] is called
     * - [clear] is called
     * - A [GameObjectSelected] event is received
     * - A [SelectionCleared] event is received
     */
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
