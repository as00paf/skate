package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.physics3d.ColliderShape
import org.joml.Vector3f

interface Collider3D {
    val offset: Vector3f
    fun toShape(): ColliderShape
}
