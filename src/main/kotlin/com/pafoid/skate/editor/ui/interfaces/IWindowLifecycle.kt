package com.pafoid.skate.editor.ui.interfaces

import com.pafoid.skate.engine.ecs.Scene

/**
 * Window lifecycle interface for proper initialization and cleanup.
 * 
 * This interface provides structured lifecycle hooks for editor windows,
 * enabling proper resource management, scene change handling, and cleanup.
 * 
 * ## Usage
 * 
 * ```kotlin
 * class MyWindow @Inject constructor(
 *     private val selectionViewModel: SelectionViewModel
 * ) : IWindowLifecycle {
 *     
 *     override fun onInit() {
 *         // Initialize resources, subscribe to events
 *     }
 *     
 *     override fun onSceneChanged(oldScene: Scene?, newScene: Scene?) {
 *         // Handle scene changes
 *     }
 *     
 *     override fun onUpdate(dt: Float) {
 *         // Update logic before render
 *     }
 *     
 *     override fun onRender() {
 *         // ImGui rendering
 *     }
 *     
 *     override fun onDestroy() {
 *         // Cleanup resources, unsubscribe from events
 *     }
 * }
 * ```
 * 
 * @see IWindow Legacy interface (to be deprecated)
 * @see IWindowWithScene Legacy interface (to be deprecated)
 */
interface IWindowLifecycle {
    /**
     * Called when window is first created.
     * 
     * Use this for:
     * - One-time initialization
     * - Event subscription
     * - Resource allocation
     */
    fun onInit()
    
    /**
     * Called when the active scene changes.
     * 
     * @param oldScene The previous scene (null if first scene)
     * @param newScene The new scene (null if scene closed)
     * 
     * Use this for:
     * - Updating scene-dependent state
     * - Subscribing to new scene events
     * - Cleaning up old scene references
     */
    fun onSceneChanged(oldScene: Scene?, newScene: Scene?)
    
    /**
     * Called every frame before render.
     * 
     * @param dt Delta time since last frame
     * 
     * Use this for:
     * - Updating dynamic state
     * - Caching frequently accessed data
     * - Time-based animations
     */
    fun onUpdate(dt: Float)
    
    /**
     * Render the window using ImGui immediate mode.
     * 
     * This is called every frame after [onUpdate].
     * All ImGui rendering calls should be made here.
     */
    fun onRender()
    
    /**
     * Called when window is destroyed.
     * 
     * Use this for:
     * - Unsubscribing from events
     * - Releasing resources
     * - Cleanup
     */
    fun onDestroy()
}
