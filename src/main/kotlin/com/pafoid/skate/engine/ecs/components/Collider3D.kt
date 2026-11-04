package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.physics3d.ColliderShape
import org.joml.Vector3f

interface Collider3D {
    val offset: Vector3f
    fun toShape(): ColliderShape

    /** True when a body carrying this collider can only be static (e.g. triangle meshes). */
    val staticOnly: Boolean get() = false
}
