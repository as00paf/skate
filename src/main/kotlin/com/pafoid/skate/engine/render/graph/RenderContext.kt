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
 */
data class RenderContext(
    //TODO: remove this useless class
    val scene: Scene,
    val activeGameObject: GameObject? = null,
    val hoveredGameObject: GameObject? = null,
)