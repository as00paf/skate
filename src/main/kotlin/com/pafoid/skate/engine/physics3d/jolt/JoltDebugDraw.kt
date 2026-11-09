package com.pafoid.skate.engine.physics3d.jolt

import com.github.stephengold.joltjni.Jolt
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.ShapePart
import com.pafoid.skate.engine.physics3d.ShapeWireframe
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Collider wireframes for Jolt bodies, drawn from the bodies' backend-neutral [ShapePart]s so they look like the Bullet
 * backend's: boxes and cylinders through the renderer's primitives (capsules as a cylinder spanning the caps), hulls and
 * meshes as feature edges from [ShapeWireframe], cached per body.
 */
internal object JoltDebugDraw {
    val COLOR = Vector3f(0f, 1f, 0f)
    private val UNIT_BOX = listOf(ShapePart(ColliderShape.Box(Vector3f(1f)), Vector3f()))

    private val pos = Vector3f()
    private val rot = Quaternionf()
    private val center = Vector3f()
    private val halfExtents = Vector3f()
    private val from = Vector3f()
    private val to = Vector3f()

    fun draw(renderer: DebugRenderer, handle: JoltRigidBodyHandle) {
        handle.getLocation(pos)
        handle.getRotation(rot)
        val scale = handle.scale
        val parts = handle.parts.ifEmpty { UNIT_BOX }
        for (i in parts.indices) {
            val (shape, offset) = parts[i]
            rot.transform(center.set(offset).mul(scale)).add(pos)
            when (shape) {
                is ColliderShape.Box -> renderer.addBox3D(center, rot, halfExtents.set(shape.halfExtents).mul(scale), COLOR)
                is ColliderShape.Capsule -> {
                    val radius = shape.radius * radialScale(scale, shape.axis)
                    val height = shape.height * scale[shape.axis]
                    renderer.addCylinder3D(center, rot, radius, height + 2f * radius, shape.axis, COLOR)
                }
                is ColliderShape.Cylinder ->
                    renderer.addCylinder3D(center, rot, shape.radius * radialScale(scale, shape.axis), shape.height * scale[shape.axis], shape.axis, COLOR)
                else -> drawEdges(renderer, handle.debugEdges.getOrPut(i) { localEdges(parts[i], scale) })
            }
        }
    }

    private fun drawEdges(renderer: DebugRenderer, edges: FloatArray) {
        for (i in edges.indices step 6) {
            rot.transform(from.set(edges[i], edges[i + 1], edges[i + 2])).add(pos)
            rot.transform(to.set(edges[i + 3], edges[i + 4], edges[i + 5])).add(pos)
            renderer.addLine3D(from, to, COLOR)
        }
    }

    /** Capsule and cylinder radii follow the larger of the two scale components across their axis. */
    private fun radialScale(scale: Vector3f, axis: Int): Float = when (axis) {
        0 -> maxOf(scale.y, scale.z)
        2 -> maxOf(scale.x, scale.y)
        else -> maxOf(scale.x, scale.z)
    }

    /** Edges of a hull, compound hull or mesh in the body's scaled local frame (offset applied), 6 floats per segment. */
    fun localEdges(part: ShapePart, scale: Vector3f): FloatArray {
        val edges = when (val shape = part.shape) {
            is ColliderShape.ConvexHull -> hullEdges(shape.points)
            is ColliderShape.CompoundHull -> shape.hulls.map { hullEdges(it) }.fold(FloatArray(0)) { acc, e -> acc + e }
            is ColliderShape.TriangleMesh -> ShapeWireframe.featureEdges(meshTriangles(shape))
            else -> FloatArray(0)
        }
        for (i in edges.indices) {
            val axis = i % 3
            edges[i] = (edges[i] + part.offset[axis]) * scale[axis]
        }
        return edges
    }

    private fun hullEdges(points: List<Vector3f>): FloatArray =
        if (points.size <= ShapeWireframe.MAX_HULL_POINTS) ShapeWireframe.hullEdges(flatten(points))
        else ShapeWireframe.featureEdges(joltHullTriangles(points))

    private fun meshTriangles(mesh: ColliderShape.TriangleMesh): FloatArray {
        val out = FloatArray(mesh.indices.size / 3 * 9)
        var k = 0
        for (t in 0 until mesh.indices.size / 3) for (c in 0..2) {
            val v = mesh.vertices[mesh.indices[t * 3 + c]]
            out[k++] = v.x; out[k++] = v.y; out[k++] = v.z
        }
        return out
    }

    /**
     * Jolt's debug mesh for a hull too large for [ShapeWireframe.hullEdges], moved so its bounds line up with the input
     * points (Jolt may return it relative to the centre of mass).
     */
    private fun joltHullTriangles(points: List<Vector3f>): FloatArray {
        val shape = JoltShapes.create(ColliderShape.ConvexHull(points))
        try {
            val buffer = Jolt.newDirectFloatBuffer(shape.countDebugTriangles() * 9)
            shape.copyDebugTriangles(buffer)
            val triangles = FloatArray(buffer.capacity()).also { buffer.get(0, it) }
            val shift = FloatArray(3) { axis ->
                points.minOf { it[axis] } - (axis until triangles.size step 3).minOf { triangles[it] }
            }
            for (i in triangles.indices) triangles[i] += shift[i % 3]
            return triangles
        } finally {
            shape.close()
        }
    }

    private fun flatten(points: List<Vector3f>): FloatArray {
        val out = FloatArray(points.size * 3)
        points.forEachIndexed { i, p -> out[i * 3] = p.x; out[i * 3 + 1] = p.y; out[i * 3 + 2] = p.z }
        return out
    }
}
