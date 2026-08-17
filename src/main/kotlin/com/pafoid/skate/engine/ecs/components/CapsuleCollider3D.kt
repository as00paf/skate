package com.pafoid.skate.engine.ecs.components

import com.jme3.bullet.collision.shapes.CapsuleCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
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

    override fun createShape(): CollisionShape {
        return CapsuleCollisionShape(radius, height, axis)
    }
}
