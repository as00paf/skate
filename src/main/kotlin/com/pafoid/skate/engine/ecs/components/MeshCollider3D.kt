package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.physics3d.ColliderShape
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * Static triangle-mesh collider: exact collision for static geometry (terrain, park pieces). [indices] holds three
 * [vertices] indices per triangle, in the object's local frame shifted by [offset]. A body carrying it is always
 * simulated as static. Incomplete or out-of-range triangles are dropped; with none left it falls back to a 1 m quad.
 */
@Serializable
data class MeshCollider3D(
    val vertices: List<@Contextual Vector3f> = DEFAULT_VERTICES,
    val indices: List<Int> = DEFAULT_INDICES,
    @Contextual override val offset: Vector3f = Vector3f()
) : Component(), Collider3D {

    override val staticOnly: Boolean get() = true

    override fun toShape(): ColliderShape {
        val valid = indices.chunked(3)
            .filter { tri -> tri.size == 3 && tri.all { it in vertices.indices } }
            .flatten()
        return if (valid.isEmpty()) ColliderShape.TriangleMesh(DEFAULT_VERTICES, DEFAULT_INDICES)
        else ColliderShape.TriangleMesh(vertices.map { Vector3f(it) }, valid)
    }

    companion object {
        private val DEFAULT_VERTICES: List<Vector3f> get() = listOf(
            Vector3f(-1f, 0f, -1f), Vector3f(1f, 0f, -1f), Vector3f(1f, 0f, 1f), Vector3f(-1f, 0f, 1f),
        )
        private val DEFAULT_INDICES = listOf(0, 2, 1, 0, 3, 2)

        /** Builds a collider from a triangle list, sharing equal vertices. */
        fun fromTriangles(triangles: List<Triple<Vector3f, Vector3f, Vector3f>>, offset: Vector3f = Vector3f()): MeshCollider3D {
            val vertices = ArrayList<Vector3f>()
            val lookup = HashMap<Vector3f, Int>()
            val indices = ArrayList<Int>(triangles.size * 3)
            triangles.forEach { (a, b, c) ->
                listOf(a, b, c).forEach { v -> indices += lookup.getOrPut(Vector3f(v)) { vertices += Vector3f(v); vertices.size - 1 } }
            }
            return MeshCollider3D(vertices, indices, offset)
        }
    }
}
