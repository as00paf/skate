package com.pafoid.skate.engine.physics3d.components

import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.utils.JmeVector3f
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f

@Serializable
class CustomCollider3D(
    @Transient val collisionShape: CollisionShape? = null
) : Component(), Collider3D {
    @Contextual override val offset: Vector3f = Vector3f()

    override fun createShape(): CollisionShape {
        // If no shape provided, return a small default box to avoid crashes
        return collisionShape ?: BoxCollisionShape(JmeVector3f(0.1f, 0.1f, 0.1f))
    }
}