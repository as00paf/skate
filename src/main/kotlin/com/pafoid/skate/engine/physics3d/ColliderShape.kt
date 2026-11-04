package com.pafoid.skate.engine.physics3d

import org.joml.Vector3f

/** Backend-agnostic description of a collision shape; each backend turns it into its native shape. */
sealed class ColliderShape {
    data class Box(val halfExtents: Vector3f, val margin: Float = 0.04f) : ColliderShape()
    data class Capsule(val radius: Float, val height: Float, val axis: Int) : ColliderShape()
    data class Cylinder(val radius: Float, val height: Float, val axis: Int, val margin: Float = 0.04f) : ColliderShape()
    data class ConvexHull(val points: List<Vector3f>) : ColliderShape()

    /** A concave piece decomposed into convex [hulls], all in the body's local frame. Works on any body type. */
    data class CompoundHull(val hulls: List<List<Vector3f>>) : ColliderShape()

    /**
     * Triangle soup: [indices] holds three [vertices] indices per triangle. Backends only put it on static bodies
     * (a body with a triangle mesh is simulated as static whatever its mass or type).
     */
    data class TriangleMesh(val vertices: List<Vector3f>, val indices: List<Int>) : ColliderShape()
}
