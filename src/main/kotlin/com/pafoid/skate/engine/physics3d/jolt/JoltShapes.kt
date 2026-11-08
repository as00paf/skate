package com.pafoid.skate.engine.physics3d.jolt

import com.github.stephengold.joltjni.BoxShapeSettings
import com.github.stephengold.joltjni.CapsuleShapeSettings
import com.github.stephengold.joltjni.ConvexHullShapeSettings
import com.github.stephengold.joltjni.CylinderShapeSettings
import com.github.stephengold.joltjni.Float3
import com.github.stephengold.joltjni.IndexedTriangle
import com.github.stephengold.joltjni.MeshShapeSettings
import com.github.stephengold.joltjni.Quat
import com.github.stephengold.joltjni.RotatedTranslatedShapeSettings
import com.github.stephengold.joltjni.ScaledShapeSettings
import com.github.stephengold.joltjni.ShapeRefC
import com.github.stephengold.joltjni.ShapeSettings
import com.github.stephengold.joltjni.StaticCompoundShapeSettings
import com.github.stephengold.joltjni.Vec3
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.ShapePart
import org.joml.Vector3f
import kotlin.math.abs

/**
 * Turns [ColliderShape]s into Jolt shapes. Every function returns a [ShapeRefC] the caller owns and must close once
 * the shape is attached to a body (the body keeps its own reference). Mapping notes are in docs/design/PHY-17.md.
 */
internal object JoltShapes {
    private val IDENTITY = Quat(0f, 0f, 0f, 1f)

    /** The shape for a whole body: a lone centred part as is, otherwise a static compound; no part gives a unit box. */
    fun body(parts: List<ShapePart>, scale: Vector3f): ShapeRefC {
        val base = when {
            parts.isEmpty() -> create(ColliderShape.Box(Vector3f(1f)))
            parts.size == 1 && parts[0].offset.lengthSquared() == 0f -> create(parts[0].shape)
            else -> compound(parts.flatMap { (shape, offset) ->
                if (shape is ColliderShape.CompoundHull) shape.hulls.map { hull(it) to offset }
                else listOf(create(shape) to offset)
            })
        }
        return scaled(base, scale)
    }

    fun create(shape: ColliderShape): ShapeRefC = when (shape) {
        is ColliderShape.Box -> build(
            BoxShapeSettings(vec(shape.halfExtents), JoltConversions.boxConvexRadius(shape.margin, shape.halfExtents))
        )
        is ColliderShape.Capsule -> alongAxis(build(CapsuleShapeSettings(shape.height / 2f, shape.radius)), shape.axis)
        is ColliderShape.Cylinder -> {
            val halfHeight = shape.height / 2f
            val convexRadius = JoltConversions.cylinderConvexRadius(shape.margin, shape.radius, halfHeight)
            alongAxis(build(CylinderShapeSettings(halfHeight, shape.radius, convexRadius)), shape.axis)
        }
        is ColliderShape.ConvexHull -> hull(shape.points)
        is ColliderShape.CompoundHull -> compound(shape.hulls.map { hull(it) to Vector3f() })
        is ColliderShape.TriangleMesh -> mesh(shape)
    }

    private fun hull(points: List<Vector3f>): ShapeRefC =
        build(ConvexHullShapeSettings(*points.map { vec(it) }.toTypedArray()))

    /**
     * Bullet meshes collide on both faces, Jolt's only on the front during simulation. The mesh is built twice (as is
     * and with reversed winding) and combined, as two separate meshes so each keeps Jolt's internal-edge detection.
     */
    private fun mesh(shape: ColliderShape.TriangleMesh): ShapeRefC {
        val vertices = shape.vertices.map { Float3(it.x, it.y, it.z) }.toTypedArray()
        val triangles = shape.indices.chunked(3).filter { it.size == 3 }
        val front = build(MeshShapeSettings(vertices, *triangles.map { IndexedTriangle(it[0], it[1], it[2]) }.toTypedArray()))
        val back = build(MeshShapeSettings(vertices, *triangles.map { IndexedTriangle(it[0], it[2], it[1]) }.toTypedArray()))
        return compound(listOf(front to Vector3f(), back to Vector3f()))
    }

    /** Static compound of [children] at their offsets; closes the children (the compound keeps its own references). */
    private fun compound(children: List<Pair<ShapeRefC, Vector3f>>): ShapeRefC {
        val settings = StaticCompoundShapeSettings()
        children.forEach { (child, offset) -> settings.addShape(vec(offset), IDENTITY, child) }
        return build(settings).also { children.forEach { it.first.close() } }
    }

    /** Wraps a Y-aligned [shape] so it runs along engine [axis]; closes [shape] when wrapped. */
    private fun alongAxis(shape: ShapeRefC, axis: Int): ShapeRefC {
        val rotation = JoltConversions.axisRotation(axis) ?: return shape
        val q = Quat(rotation.x, rotation.y, rotation.z, rotation.w)
        return build(RotatedTranslatedShapeSettings(Vec3(0f, 0f, 0f), q, shape)).also { shape.close() }
    }

    /** Applies the Transform scale (made valid for the shape, e.g. uniform for capsules); closes [base] when wrapped. */
    private fun scaled(base: ShapeRefC, scale: Vector3f): ShapeRefC {
        if (abs(scale.x - 1f) < 1e-6f && abs(scale.y - 1f) < 1e-6f && abs(scale.z - 1f) < 1e-6f) return base
        val valid = base.makeScaleValid(vec(scale))
        return build(ScaledShapeSettings(base, valid)).also { base.close() }
    }

    /** Creates the shape and frees the settings; throws when Jolt rejects them (e.g. a degenerate hull). */
    private fun build(settings: ShapeSettings): ShapeRefC {
        try {
            val result = settings.create()
            try {
                if (result.hasError()) throw IllegalArgumentException("Jolt rejected the collision shape: ${result.error}")
                return result.get()
            } finally {
                result.close()
            }
        } finally {
            settings.close()
        }
    }

    fun vec(v: Vector3f) = Vec3(v.x, v.y, v.z)
}
