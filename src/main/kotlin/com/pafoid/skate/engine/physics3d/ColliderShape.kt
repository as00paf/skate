package com.pafoid.skate.engine.physics3d

import org.joml.Vector3f

/** Backend-agnostic description of a collision shape; each backend turns it into its native shape. */
sealed class ColliderShape {
    data class Box(val halfExtents: Vector3f, val margin: Float = 0.04f) : ColliderShape()
    data class Capsule(val radius: Float, val height: Float, val axis: Int) : ColliderShape()
    data class Cylinder(val radius: Float, val height: Float, val axis: Int, val margin: Float = 0.04f) : ColliderShape()
    data class ConvexHull(val points: List<Vector3f>) : ColliderShape()
}
