package com.pafoid.skate.editor.ui.viewmodels

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.EventSystem
import com.pafoid.skate.engine.events.SceneChanged
import com.pafoid.skate.engine.events.SceneClosed
import com.pafoid.skate.engine.events.SceneOpened

/**
 * ViewModel for scene state.
 * 
 * Provides observable scene state to UI components.
 * This ViewModel decouples UI windows from direct SceneManager access
 * by centralizing scene state management and event subscription.
 * 
 * ## Usage
 * 
 * ```kotlin
 * class SceneHierarchyWindow @Inject constructor(
 *     private val sceneViewModel: SceneViewModel
 * ) : IWindowLifecycle {
 *     
 *     private var currentScene: Scene? = null
 *     
 *     override fun onSceneChanged(oldScene: Scene?, newScene: Scene?) {
 *         currentScene = sceneViewModel.currentScene
 *     }
 *     
 *     override fun onRender() {
 *         currentScene?.let { renderHierarchy(it) }
 *     }
 * }
 * ```
 * 
 * @param sceneManager For accessing scene list and current scene
 * @param eventSystem For subscribing to scene events
 */
class SceneViewModel(
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem
) {
    private var _currentScene: Scene? = null
    private var _openScenes: List<Scene> = emptyList()
    
    /**
     * The currently active scene.
     * 
     * This property is updated when:
     * - A [SceneOpened] event is received
     * - A [SceneChanged] event is received
     * - [refresh] is called
     */
    val currentScene: Scene? get() = _currentScene
    
    /**
     * List of all open scenes.
     * 
     * This is updated when scenes are opened or closed.
     */
    val openScenes: List<Scene> get() = _openScenes
    
    /**
     * Initialize the ViewModel by subscribing to scene events.
     * 
     * Call this in [onInit] of your window lifecycle.
     */
    fun init() {
        // Initial state
        refresh()
        
        // Subscribe to scene events
        eventSystem.subscribe<SceneOpened> { event ->
            _currentScene = event.scene
            refreshOpenScenes()
        }
        
        eventSystem.subscribe<SceneChanged> {
            _currentScene = sceneManager.currentScene
        }
        
        eventSystem.subscribe<SceneClosed> { event ->
            if (_currentScene == event.scene) {
                _currentScene = null
            }
            refreshOpenScenes()
        }
    }
    
    /**
     * Refresh the current scene state from SceneManager.
     * 
     * Call this when you need to ensure you have the latest state.
     */
    fun refresh() {
        _currentScene = sceneManager.currentScene
        refreshOpenScenes()
    }
    
    /**
     * Refresh the list of open scenes.
     */
    private fun refreshOpenScenes() {
        // This assumes SceneManager has an openScenes property
        // Adjust based on your actual API
        _openScenes = try {
            sceneManager.javaClass.getMethod("getOpenScenes")
                .invoke(sceneManager) as? List<Scene> ?: emptyList()
        } catch (e: Exception) {
            // Fallback: try to get currentScene as a single-item list
            listOfNotNull(sceneManager.currentScene)
        }
    }
    
    /**
     * Get a scene by name.
     * 
     * @param name The scene name to search for
     * @return The scene with the given name, or null if not found
     */
    fun getSceneByName(name: String): Scene? {
        return _openScenes.find { it.name == name }
    }
    
    /**
     * Check if a scene is currently open.
     * 
     * @param scene The scene to check
     * @return true if the scene is open, false otherwise
     */
    fun isSceneOpen(scene: Scene): Boolean {
        return _openScenes.contains(scene)
    }
    
    /**
     * Cleanup the ViewModel.
     * 
     * Call this in [onDestroy] of your window lifecycle.
     */
    fun destroy() {
        _currentScene = null
        _openScenes = emptyList()
    }
}
