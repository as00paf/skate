package com.pafoid.skate.engine.render.graph

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.renderer.passes.RenderPass

/**
 * Orchestrates a series of [RenderPass] nodes based on resource dependencies.
 * 
 * The graph handles:
 * - Resource registration and tracking
 * - Pass execution order (simple linear for now, but extensible)
 * - Input/Output resource mapping between nodes
 */
class RenderGraph {
    private val passes = mutableListOf<RenderPass>()
    private val resources = mutableMapOf<String, RenderResource>()
    
    /**
     * Adds a pass to the graph in the order it should be executed.
     * 
     * @param pass The render pass to add
     */
    fun addPass(pass: RenderPass) {
        passes.add(pass)
    }
    
    /**
     * Registers a resource in the graph.
     * 
     * @param resource The resource to register
     */
    fun registerResource(resource: RenderResource) {
        resources[resource.name] = resource
    }
    
    /**
     * Executes all passes in the graph in order.
     * 
     * @param scene The scene to render
     * @param activeGameObject Selected game object
     * @param hoveredGameObject Hovered game object
     */
    fun execute(scene: Scene, activeGameObject: GameObject?, hoveredGameObject: GameObject?) {
        val context = RenderContext(
            scene = scene,
            activeGameObject = activeGameObject,
            hoveredGameObject = hoveredGameObject,
            resources = resources
        )
        
        // 1. Prepare all nodes (beginFrame, clear, etc.)
        passes.forEach { it.prepare() }
        
        // 2. Execute all nodes
        passes.forEach { it.execute(context) }
        
        // 3. Post-execution cleanup (unbind, state reset)
        passes.forEach { it.cleanup() }
    }
    
    /**
     * Resets the graph state for the next frame.
     */
    fun reset() {
        // Clear temporary resources if any
    }
    
    /**
     * Clears all passes and resources from the graph.
     */
    fun clear() {
        passes.clear()
        resources.clear()
    }
}
