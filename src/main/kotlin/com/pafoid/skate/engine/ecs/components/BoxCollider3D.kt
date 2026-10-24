package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.physics3d.ColliderShape
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class BoxCollider3D(
    @Contextual val halfExtents: Vector3f = Vector3f(1f, 1f, 1f),
    @Contextual override val offset: Vector3f = Vector3f(),
    var margin: Float = 0.04f
) : Component(), Collider3D {

    override fun toShape(): ColliderShape = ColliderShape.Box(Vector3f(halfExtents), margin)
}