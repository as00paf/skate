package com.pafoid.skate.engine.ecs.components

import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.HullCollisionShape
import com.pafoid.skate.engine.utils.JmeVector3f
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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

    @Transient
    private var cachedShape: CollisionShape? = null

    override fun createShape(): CollisionShape {
        cachedShape?.let { return it }
        val shape = if (hullPoints.size >= 4) {
            val jmePoints = hullPoints.map { JmeVector3f(it.x, it.y, it.z) }.toTypedArray()
            HullCollisionShape(*jmePoints)
        } else {
            BoxCollisionShape(JmeVector3f(0.1f, 0.1f, 0.1f))
        }
        cachedShape = shape
        return shape
    }
}