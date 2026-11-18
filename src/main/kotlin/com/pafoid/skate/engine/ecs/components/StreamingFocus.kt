package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Serializable

/**
 * Terrain chunks stream around this entity (REN-28 §15.3): the player, network players, a vehicle driving away. The
 * active camera is an implicit focus for the render rings; physics rings follow these components only.
 */
@Serializable
data class StreamingFocus(
    /** Also stream the physics ring (colliders) around it. */
    var physics: Boolean = true,
    /** Scales every ring around this focus. */
    var radiusScale: Float = 1f,
    /** Higher focuses load first; one step is worth 100 m of distance. */
    var priority: Int = 0,
) : Component()
