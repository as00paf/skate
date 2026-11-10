package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.physics3d.ColliderShape
import org.joml.Vector3f

interface Collider3D {
    val offset: Vector3f
    fun toShape(): ColliderShape

    /**
     * The shape to simulate, or null when this collider is unavailable (its source failed to load) and must add nothing
     * to the body. Bodies are built from this; [toShape] stays for callers that always need a shape.
     */
    fun toShapeOrNull(): ColliderShape? = toShape()

    /** True when a body carrying this collider can only be static (e.g. triangle meshes). */
    val staticOnly: Boolean get() = false
}
