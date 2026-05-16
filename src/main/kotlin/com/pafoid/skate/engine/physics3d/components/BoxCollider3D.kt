package com.pafoid.skate.engine.physics3d.components

import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.utils.JmeVector3f
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
class BoxCollider3D(@Contextual val halfExtents: Vector3f = Vector3f(1f, 1f, 1f)) : Component(), Collider3D {
    @Contextual override val offset: Vector3f = Vector3f()
    var margin: Float = 0.04f

    override fun createShape(): CollisionShape {
        val shape = BoxCollisionShape(JmeVector3f(halfExtents.x, halfExtents.y, halfExtents.z))
        shape.margin = margin
        return shape
    }
}