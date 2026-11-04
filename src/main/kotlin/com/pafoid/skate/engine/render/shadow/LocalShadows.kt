package com.pafoid.skate.engine.render.shadow

import com.pafoid.skate.engine.render.lighting.LightData
import com.pafoid.skate.engine.render.lighting.LightType
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/** One shadowed spot/point light: [lightIndex] into the frame's light list, layers `[baseLayer, baseLayer + layerCount)`. */
data class LocalShadowSlot(val lightIndex: Int, val type: LightType, val baseLayer: Int, val tanHalfFov: Float, val near: Float, val far: Float) {
    val layerCount: Int get() = LocalShadows.layersFor(type)
}

/** Budgeting and projection maths for spot/point light shadows (one layer per spot, six cube faces per point). */
object LocalShadows {
    /** Must match `MAX_LOCAL_SHADOW_LAYERS` in `shader_3d_default.glsl`. */
    const val MAX_LAYERS = 18

    /** Must match `MAX_SHADOWED_LIGHTS` in `shader_3d_default.glsl`. */
    const val MAX_SHADOWED_LIGHTS = 8

    const val POINT_FACES = 6
    private const val MIN_NEAR = 0.05f
    private const val SPOT_FOV_MARGIN = 0.05f // radians, keeps the cone edge off the map border
    private const val MAX_SPOT_FOV = 2.96f    // ~170 degrees

    fun layersFor(type: LightType): Int = if (type == LightType.POINT) POINT_FACES else 1

    /**
     * Picks which lights get shadow maps within [maxLayers]. Lights whose sphere of influence is closest to [camera]
     * win; a point light that no longer fits is skipped so cheaper spot lights can still use the remaining layers.
     * Lights with zero range are ignored. Result order follows the layer order.
     */
    fun allocate(lights: List<LightData>, camera: Vector3f, maxLayers: Int, out: MutableList<LocalShadowSlot>): MutableList<LocalShadowSlot> {
        out.clear()
        val budget = min(maxLayers, MAX_LAYERS)
        if (budget <= 0 || lights.isEmpty()) return out
        val order = lights.indices.filter { lights[it].range > 0f }
            .sortedWith(compareBy<Int> { max(0f, lights[it].position.distance(camera) - lights[it].range) }.thenBy { it })
        var layer = 0
        for (i in order) {
            if (out.size >= MAX_SHADOWED_LIGHTS) break
            val l = lights[i]
            val cost = layersFor(l.type)
            if (layer + cost > budget) continue
            val near = nearPlane(l.range)
            val tanHalf = if (l.type == LightType.POINT) 1f else tan(spotFov(l.cosOuter) * 0.5f)
            out.add(LocalShadowSlot(i, l.type, layer, tanHalf, near, l.range))
            layer += cost
        }
        return out
    }

    fun nearPlane(range: Float): Float = max(MIN_NEAR, range * 0.005f)

    /** Full spot frustum angle (radians) for an outer cone cosine. */
    fun spotFov(cosOuter: Float): Float = min(MAX_SPOT_FOV, 2f * acos(cosOuter.coerceIn(-1f, 1f)) + SPOT_FOV_MARGIN)

    /** View-projection for a spot light at [position] looking along [direction]. */
    fun spotMatrix(position: Vector3f, direction: Vector3f, fov: Float, near: Float, far: Float, out: Matrix4f): Matrix4f {
        val d = scratch.set(direction).let { if (it.lengthSquared() < 1e-12f) it.set(0f, -1f, 0f) else it.normalize() }
        val up = if (abs(d.y) > 0.99f) UP_Z else UP_Y
        return out.setPerspective(fov, 1f, near, far)
            .lookAt(position.x, position.y, position.z, position.x + d.x, position.y + d.y, position.z + d.z, up.x, up.y, up.z)
    }

    /** View-projection for cube face [face] (`+X, -X, +Y, -Y, +Z, -Z`, matching [cubeFace]) of a point light. */
    fun pointFaceMatrix(position: Vector3f, face: Int, near: Float, far: Float, out: Matrix4f): Matrix4f {
        val f = FACE_DIRS[face]
        val u = FACE_UPS[face]
        return out.setPerspective(Math.PI.toFloat() / 2f, 1f, near, far)
            .lookAt(position.x, position.y, position.z, position.x + f.x, position.y + f.y, position.z + f.z, u.x, u.y, u.z)
    }

    /** Cube face holding direction (x, y, z) from the light: the dominant axis. Mirrored by `cubeFace()` in GLSL. */
    fun cubeFace(x: Float, y: Float, z: Float): Int {
        val ax = abs(x); val ay = abs(y); val az = abs(z)
        return when {
            ax >= ay && ax >= az -> if (x > 0f) 0 else 1
            ay >= az -> if (y > 0f) 2 else 3
            else -> if (z > 0f) 4 else 5
        }
    }

    /** World size of one shadow texel at [distance] from the light. */
    fun texelWorldSize(distance: Float, tanHalfFov: Float, resolution: Int): Float = 2f * distance * tanHalfFov / resolution

    private val scratch = Vector3f() // render thread only
    private val UP_Y = Vector3f(0f, 1f, 0f)
    private val UP_Z = Vector3f(0f, 0f, 1f)
    private val FACE_DIRS = arrayOf(
        Vector3f(1f, 0f, 0f), Vector3f(-1f, 0f, 0f), Vector3f(0f, 1f, 0f),
        Vector3f(0f, -1f, 0f), Vector3f(0f, 0f, 1f), Vector3f(0f, 0f, -1f),
    )
    private val FACE_UPS = arrayOf(
        Vector3f(0f, -1f, 0f), Vector3f(0f, -1f, 0f), Vector3f(0f, 0f, 1f),
        Vector3f(0f, 0f, -1f), Vector3f(0f, -1f, 0f), Vector3f(0f, -1f, 0f),
    )
}
