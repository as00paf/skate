package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.physics3d.ColliderShape
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class CustomCollider3D(
    val hullPoints: List<@Contextual Vector3f> = listOf(
        Vector3f(-0.1f, -0.1f, -0.1f),
        Vector3f(0.1f, -0.1f, -0.1f),
        Vector3f(0.1f, 0.1f, -0.1f),
        Vector3f(-0.1f, 0.1f, -0.1f),
        Vector3f(-0.1f, -0.1f, 0.1f),
        Vector3f(0.1f, -0.1f, 0.1f),
        Vector3f(0.1f, 0.1f, 0.1f),
        Vector3f(-0.1f, 0.1f, 0.1f)
    ),
    @Contextual override val offset: Vector3f = Vector3f()
) : Component(), Collider3D {

    override fun toShape(): ColliderShape =
        if (hullPoints.size >= 4) {
            ColliderShape.ConvexHull(hullPoints.map { Vector3f(it) })
        } else {
            ColliderShape.Box(Vector3f(0.1f, 0.1f, 0.1f))
        }
}