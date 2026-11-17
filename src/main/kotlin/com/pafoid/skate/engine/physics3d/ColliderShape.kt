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

    /**
     * Terrain height grid (PHY-15, `docs/design/REN-28.md` §8): [samplesPerSide] × [samplesPerSide] heights, row-major
     * by Z (`heights[z * samplesPerSide + x]`), [spacing] metres apart. The local origin is the corner sample: sample
     * (x, z) sits at `(x * spacing, heights[z * samplesPerSide + x], z * spacing)`, so neighbouring chunks that share
     * their edge samples meet exactly. Each quad is split along its (x, z)–(x+1, z+1) diagonal on every backend. It
     * collides from above and is static only (a heightfield body never moves); rotation and scale are not applied.
     */
    class Heightfield(val samplesPerSide: Int, val spacing: Float, val heights: FloatArray) : ColliderShape() {
        init {
            require(samplesPerSide >= 2) { "A heightfield needs at least 2 samples per side, got $samplesPerSide" }
            require(spacing > 0f && spacing.isFinite()) { "Heightfield spacing must be positive, got $spacing" }
            require(heights.size == samplesPerSide * samplesPerSide) {
                "Expected ${samplesPerSide * samplesPerSide} heights, got ${heights.size}"
            }
            require(heights.all { it.isFinite() }) { "Heightfield heights must be finite" }
        }

        val minY: Float = heights.min()
        val maxY: Float = heights.max()

        /** Side length in metres. */
        val size: Float get() = (samplesPerSide - 1) * spacing

        fun height(x: Int, z: Int): Float = heights[z * samplesPerSide + x]

        override fun equals(other: Any?): Boolean = other is Heightfield && samplesPerSide == other.samplesPerSide &&
            spacing == other.spacing && heights.contentEquals(other.heights)

        override fun hashCode(): Int = (samplesPerSide * 31 + spacing.hashCode()) * 31 + heights.contentHashCode()

        override fun toString(): String = "Heightfield(samplesPerSide=$samplesPerSide, spacing=$spacing, y=$minY..$maxY)"
    }
}
