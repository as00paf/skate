package com.pafoid.skate.engine.render.lighting

import com.pafoid.skate.engine.assets.data.models.`3dModel`
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.sqrt

/** Local-space bounding sphere of a model (centre of the AABB, radius to the farthest vertex), cached per model. */
class ModelBounds(val center: Vector3f, val radius: Float) {
    /** World-space sphere for light culling; the radius follows the largest axis scale of [world]. */
    fun toWorld(world: Matrix4f, outCenter: Vector3f): Float {
        world.transformPosition(center, outCenter)
        val sx = sqrt(world.m00() * world.m00() + world.m01() * world.m01() + world.m02() * world.m02())
        val sy = sqrt(world.m10() * world.m10() + world.m11() * world.m11() + world.m12() * world.m12())
        val sz = sqrt(world.m20() * world.m20() + world.m21() * world.m21() + world.m22() * world.m22())
        return radius * max(sx, max(sy, sz))
    }

    companion object {
        private val cache = WeakHashMap<`3dModel`, ModelBounds>()
        private val EMPTY = ModelBounds(Vector3f(), 0f)

        fun of(model: `3dModel`): ModelBounds = cache.getOrPut(model) { compute(model.mesh.map { it.vertices }) }

        fun compute(vertexArrays: List<FloatArray>): ModelBounds {
            val min = Vector3f(Float.MAX_VALUE); val maxV = Vector3f(-Float.MAX_VALUE)
            var any = false
            for (v in vertexArrays) for (i in 0 until v.size / 3) {
                any = true
                min.min(Vector3f(v[i * 3], v[i * 3 + 1], v[i * 3 + 2])); maxV.max(Vector3f(v[i * 3], v[i * 3 + 1], v[i * 3 + 2]))
            }
            if (!any) return EMPTY
            val c = Vector3f(min).add(maxV).mul(0.5f)
            var r2 = 0f
            for (v in vertexArrays) for (i in 0 until v.size / 3) {
                val dx = v[i * 3] - c.x; val dy = v[i * 3 + 1] - c.y; val dz = v[i * 3 + 2] - c.z
                r2 = max(r2, dx * dx + dy * dy + dz * dz)
            }
            return ModelBounds(c, sqrt(r2))
        }
    }
}
