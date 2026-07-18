package com.pafoid.skate.engine.render.graph

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene

/**
 * Contextual data for a render pass.
 *
 * Contains state and resource information for pass execution.
 *
 * @param scene The scene currently being rendered
 * @param activeGameObject The currently selected game object (if any)
 * @param hoveredGameObject The currently hovered game object (if any)
 * @param resources The current map of resources in the graph
 */
data class RenderContext(
    val scene: Scene,
    val activeGameObject: GameObject? = null,
    val hoveredGameObject: GameObject? = null,
    private val resources: Map<String, Int> = emptyMap()
) {
    /**
     * Gets a resource from the context by name.
     * 
     * @param name The name of the resource to retrieve
     * @return The resource if found, or null otherwise
     */
    fun getResource(name: String): Int? = resources[name]

}
