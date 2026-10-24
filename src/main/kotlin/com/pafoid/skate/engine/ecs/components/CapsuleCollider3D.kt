package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.physics3d.ColliderShape
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class CapsuleCollider3D(
    var radius: Float = 0.5f,
    var height: Float = 1.0f,
    var axis: Int = 1, // 1 for Y-axis which is default in JBullet
    @Contextual override val offset: Vector3f = Vector3f()
) : Component(), Collider3D {

    override fun toShape(): ColliderShape = ColliderShape.Capsule(radius, height, axis)
}
