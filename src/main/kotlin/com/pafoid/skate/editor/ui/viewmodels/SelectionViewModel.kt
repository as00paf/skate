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
    private val eventSystem: EventSystem
) {
    private var _selectedGameObject: GameObject? = null
    
    /**
     * The currently selected GameObject.
     * 
     * This property is updated when:
     * - [select] is called
     * - [clear] is called
     * - A [GameObjectSelected] event is received
     * - A [SelectionCleared] event is received
     */
    val selectedGameObject: GameObject? get() = _selectedGameObject
    
    /**
     * Initialize the ViewModel by subscribing to selection events.
     * 
     * Call this in [onInit] of your window lifecycle.
     */
    fun init() {
        // Subscribe to selection events
        eventSystem.subscribe<GameObjectSelected> { event ->
            _selectedGameObject = event.gameObject
        }
        
        eventSystem.subscribe<SelectionCleared> {
            _selectedGameObject = null
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
        _selectedGameObject = gameObject
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
     * Get the selected GameObject from the scene manager.
     * 
     * This is a convenience method that queries the scene manager directly.
     * Prefer using [selectedGameObject] for most cases.
     * 
     * @return The selected GameObject from the current scene
     */
    fun getFromScene(): GameObject? {
        return sceneManager.currentScene?.let { scene ->
            // This assumes the scene has a getSelectedGameObject extension
            // You may need to adjust based on your actual API
            try {
                scene.javaClass.getMethod("getSelectedGameObject").invoke(scene) as? GameObject
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Cleanup the ViewModel.
     * 
     * Call this in [onDestroy] of your window lifecycle.
     * The EventSystem will automatically remove listeners on destroy.
     */
    fun destroy() {
        _selectedGameObject = null
    }
}
