package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene

/**
 * Represents a single rendering pass in the rendering pipeline.
 * 
 * Render passes are executed in order each frame, with each pass
 * responsible for a specific aspect of rendering (e.g., picking,
 * geometry, shadows, post-processing, debug visualization).
 */
interface RenderPass {
    /**
     * Executes this render pass.
     * 
     * @param scene The scene to render
     * @param activeGameObject The currently selected game object (if any)
     * @param hoveredGameObject The currently hovered game object (if any)
     */
    fun execute(scene: Scene, activeGameObject: GameObject?, hoveredGameObject: GameObject?)
}
