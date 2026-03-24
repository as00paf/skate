package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.graph.RenderContext

/**
 * Represents a single rendering pass in the rendering pipeline.
 * 
 * Render passes are executed in order each frame, with each pass
 * responsible for a specific aspect of rendering (e.g., picking,
 * geometry, shadows, post-processing, debug visualization).
 */
interface RenderPass {
    /**
     * Unique name of the render pass for identification in the graph.
     */
    val name: String get() = this::class.simpleName ?: "UnknownPass"
    
    /**
     * Set of input resource names that this pass depends on.
     */
    val inputs: Set<String> get() = emptySet()
    
    /**
     * Set of output resource names that this pass provides.
     */
    val outputs: Set<String> get() = emptySet()

    /**
     * Executes this render pass.
     * 
     * @param scene The scene to render
     * @param activeGameObject The currently selected game object (if any)
     * @param hoveredGameObject The currently hovered game object (if any)
     */
    @Deprecated("Use execute(context: RenderContext) instead", ReplaceWith("execute(RenderContext(scene, activeGameObject, hoveredGameObject))"))
    fun execute(scene: Scene, activeGameObject: GameObject?, hoveredGameObject: GameObject?) {
        execute(RenderContext(scene, activeGameObject, hoveredGameObject))
    }
    
    /**
     * Called before any passes are executed to prepare resources for the frame.
     */
    fun prepare() {}

    /**
     * Executes this render pass with a contextual data set.
     * 
     * @param context Data and resource context for execution
     */
    fun execute(context: RenderContext) {}
    
    /**
     * Called after the pass has finished execution for cleanup or unbinding.
     */
    fun cleanup() {}
}
