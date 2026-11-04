package com.pafoid.skate.engine.render.shadow

import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.tan

/** Cascade split distances (view-space depth, positive) using the practical split scheme (PSSM). */
object CascadeSplits {
    const val MAX_CASCADES = 4
    const val DEFAULT_LAMBDA = 0.75f

    /**
     * Fills [out] with `count + 1` distances: `out[0] = near`, `out[count] = far`, and each inner split a blend of the
     * logarithmic (`lambda = 1`) and uniform (`lambda = 0`) schemes.
     */
    fun compute(near: Float, far: Float, count: Int, lambda: Float = DEFAULT_LAMBDA, out: FloatArray = FloatArray(count + 1)): FloatArray {
        require(count in 1..MAX_CASCADES) { "cascade count $count not in 1..$MAX_CASCADES" }
        require(near > 0f && far > near) { "need 0 < near < far, got $near..$far" }
        require(out.size >= count + 1) { "out needs ${count + 1} slots" }
        val l = lambda.coerceIn(0f, 1f)
        out[0] = near
        for (i in 1 until count) {
            val p = i.toFloat() / count
            val log = near * (far / near).pow(p)
            val uniform = near + (far - near) * p
            out[i] = l * log + (1f - l) * uniform
        }
        out[count] = far
        return out
    }
}

/** Result of fitting one cascade. [texelWorldSize] drives the normal-offset bias, [depthRange] converts world bias to depth. */
class CascadeFit {
    val matrix = Matrix4f()

    /** Half extent of the light-space box (world units). */
    var radius = 0f
    var texelWorldSize = 0f
    var depthRange = 0f
}

/**
 * Fits a stable orthographic light matrix around a camera frustum slice.
 *
 * The slice is enclosed in its bounding sphere (rotation-invariant size, so cascades do not "breathe" as the camera
 * turns) and the sphere centre is snapped to whole shadow texels in a fixed light frame (so shadow edges do not
 * shimmer as the camera moves). Allocation-free after construction.
 */
class CascadeFitter {
    private val corners = Array(8) { Vector3f() }
    private val center = Vector3f()
    private val lightRotation = Matrix4f()
    private val up = Vector3f()
    private val dir = Vector3f()
    private val lightCenter = Vector3f()

    /** Corners of the perspective slice `[sliceNear, sliceFar]` in world space (camera looks down -Z in view space). */
    fun perspectiveSliceCorners(inverseView: Matrix4f, fovY: Float, aspect: Float, sliceNear: Float, sliceFar: Float): Array<Vector3f> {
        val t = tan(fovY * 0.5f)
        var i = 0
        for (k in 0..1) {
            val d = if (k == 0) sliceNear else sliceFar
            val hy = d * t
            val hx = hy * aspect
            for (sy in SIGNS) for (sx in SIGNS) inverseView.transformPosition(sx * hx, sy * hy, -d, corners[i++])
        }
        return corners
    }

    /** Corners of an orthographic slice with half extents [halfWidth] x [halfHeight]. */
    fun orthographicSliceCorners(inverseView: Matrix4f, halfWidth: Float, halfHeight: Float, sliceNear: Float, sliceFar: Float): Array<Vector3f> {
        var i = 0
        for (k in 0..1) {
            val d = if (k == 0) sliceNear else sliceFar
            for (sy in SIGNS) for (sx in SIGNS) inverseView.transformPosition(sx * halfWidth, sy * halfHeight, -d, corners[i++])
        }
        return corners
    }

    /**
     * Fits [corners] for a light shining along [lightDirection].
     *
     * @param casterExtension How far towards the light the depth range extends, so casters outside the slice still
     * land in the map (prevents missing shadows from tall objects behind the camera).
     */
    fun fit(corners: Array<Vector3f>, lightDirection: Vector3f, resolution: Int, casterExtension: Float, out: CascadeFit): CascadeFit {
        center.zero()
        for (c in corners) center.add(c)
        center.div(corners.size.toFloat())
        var r2 = 0f
        for (c in corners) r2 = maxOf(r2, c.distanceSquared(center))
        // Round up so float noise in the corners never changes the projection size frame to frame.
        val radius = ceil(kotlin.math.sqrt(r2) * RADIUS_QUANTUM) / RADIUS_QUANTUM
        return fitSphere(center, radius, lightDirection, resolution, casterExtension, out)
    }

    /** Fits a sphere directly (used for manual bounds). */
    fun fitSphere(sphereCenter: Vector3f, radius: Float, lightDirection: Vector3f, resolution: Int, casterExtension: Float, out: CascadeFit): CascadeFit {
        require(resolution > 2 && radius > 0f)
        lightBasis(lightDirection)
        // Pad by one texel per side so snapping the centre never pushes the sphere off the map.
        val texel = 2f * radius / (resolution - 2)
        val half = radius + texel
        // Light-space centre, snapped to the texel grid of the fixed light frame.
        val lc = lightRotation.transformPosition(sphereCenter, lightCenter)
        val cx = floor(lc.x / texel) * texel
        val cy = floor(lc.y / texel) * texel
        // Light looks down -Z: the sphere spans view depth [-lc.z - r, -lc.z + r]; extend the near side for casters.
        val zNear = -lc.z - radius - casterExtension
        val zFar = -lc.z + radius
        out.matrix.setOrtho(cx - half, cx + half, cy - half, cy + half, zNear, zFar).mul(lightRotation)
        out.radius = half
        out.texelWorldSize = texel
        out.depthRange = zFar - zNear
        return out
    }

    private fun lightBasis(lightDirection: Vector3f) {
        dir.set(lightDirection)
        if (dir.lengthSquared() < 1e-12f) dir.set(0f, -1f, 0f) else dir.normalize()
        if (abs(dir.y) > 0.99f) up.set(0f, 0f, 1f) else up.set(0f, 1f, 0f)
        lightRotation.setLookAt(0f, 0f, 0f, dir.x, dir.y, dir.z, up.x, up.y, up.z)
    }

    companion object {
        private val SIGNS = floatArrayOf(-1f, 1f)
        private const val RADIUS_QUANTUM = 16f
    }
}

/** Per-cascade shader bias values. Kept here so the maths is tested and the shader just multiplies. */
object ShadowBias {
    /** Base receiver bias along the light, in metres, added to the component's `depthBias`. */
    const val BASE_DEPTH_BIAS_WORLD = 0.002f

    /** Normal offset in texels at grazing angles; scaled by the PCF kernel because wider kernels reach further. */
    const val NORMAL_OFFSET_TEXELS = 1.0f

    /** World-space normal offset at a grazing angle (`sin(theta) = 1`). */
    fun normalOffset(texelWorldSize: Float, pcfRadius: Int, extraWorld: Float = 0f): Float =
        texelWorldSize * NORMAL_OFFSET_TEXELS * (1f + 0.5f * pcfRadius) + extraWorld

    /** Receiver depth bias in [0,1] depth units for an ortho cascade with the given depth range. */
    fun depthBias(depthRange: Float, extraWorld: Float = 0f): Float =
        if (depthRange <= 0f) 0f else (BASE_DEPTH_BIAS_WORLD + extraWorld) / depthRange
}
