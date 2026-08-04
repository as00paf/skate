package com.pafoid.skate.engine.ecs.components

import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CylinderCollisionShape
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class CylinderCollider3D(
    var radius: Float = 0.5f,
    var height: Float = 1.0f,
    var axis: Int = 1, // 0=X, 1=Y, 2=Z
    @Contextual override val offset: Vector3f = Vector3f(),
    var margin: Float = 0.04f
) : Component(), Collider3D {

    override fun createShape(): CollisionShape {
        val shape = CylinderCollisionShape(radius, height, axis)
        shape.margin = margin
        return shape
    }
}