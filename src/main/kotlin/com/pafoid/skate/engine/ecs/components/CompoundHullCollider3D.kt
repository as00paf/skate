package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.physics3d.ColliderShape
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * One collider made of several convex [hulls] (a concave obstacle decomposed into convex pieces), in the object's local
 * frame and shifted by [offset]. Hulls with fewer than 4 points are ignored; with none left it falls back to a small box.
 */
@Serializable
data class CompoundHullCollider3D(
    val hulls: List<List<@Contextual Vector3f>> = listOf(DEFAULT_HULL),
    @Contextual override val offset: Vector3f = Vector3f()
) : Component(), Collider3D {

    override fun toShape(): ColliderShape {
        val valid = hulls.filter { it.size >= MIN_HULL_POINTS }
        return if (valid.isEmpty()) ColliderShape.Box(Vector3f(0.1f))
        else ColliderShape.CompoundHull(valid.map { hull -> hull.map { Vector3f(it) } })
    }

    companion object {
        const val MIN_HULL_POINTS = 4

        private val DEFAULT_HULL: List<Vector3f> get() = listOf(
            Vector3f(-0.1f, -0.1f, -0.1f), Vector3f(0.1f, -0.1f, -0.1f),
            Vector3f(0.1f, 0.1f, -0.1f), Vector3f(-0.1f, 0.1f, -0.1f),
            Vector3f(-0.1f, -0.1f, 0.1f), Vector3f(0.1f, -0.1f, 0.1f),
            Vector3f(0.1f, 0.1f, 0.1f), Vector3f(-0.1f, 0.1f, 0.1f),
        )
    }
}
