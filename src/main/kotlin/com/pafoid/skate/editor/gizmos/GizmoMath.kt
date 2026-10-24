package com.pafoid.skate.editor.gizmos

import org.joml.Matrix3f
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.tan

/** Pure gizmo math, independent of rendering and input. */
object GizmoMath {
    /** World-space size that keeps a gizmo at [screenFraction] of the viewport height at [distance]. */
    fun screenSpaceScale(distance: Float, fovRadians: Float, screenFraction: Float = 0.15f): Float =
        2f * distance.coerceAtLeast(0.001f) * tan(fovRadians / 2f) * screenFraction

    /** Unit axis directions (x, y, z) in world space or in the space of the given Euler rotation in degrees. */
    fun axes(rotationDegrees: Vector3f, local: Boolean): Array<Vector3f> {
        val m = Matrix3f()
        if (local) {
            m.rotateXYZ(
                Math.toRadians(rotationDegrees.x.toDouble()).toFloat(),
                Math.toRadians(rotationDegrees.y.toDouble()).toFloat(),
                Math.toRadians(rotationDegrees.z.toDouble()).toFloat(),
            )
        }
        return arrayOf(
            m.transform(Vector3f(1f, 0f, 0f)).normalize(),
            m.transform(Vector3f(0f, 1f, 0f)).normalize(),
            m.transform(Vector3f(0f, 0f, 1f)).normalize(),
        )
    }

    /**
     * Parameter t along the axis line (axisOrigin + t * axisDir) closest to the ray.
     * Returns null when the ray is (nearly) parallel to the axis, e.g. a camera looking along it.
     */
    fun closestAxisParam(rayOrigin: Vector3f, rayDir: Vector3f, axisOrigin: Vector3f, axisDir: Vector3f): Float? {
        val w = Vector3f(axisOrigin).sub(rayOrigin)
        val b = axisDir.dot(rayDir)
        val denom = 1f - b * b
        if (denom < 1e-4f) return null
        val d = axisDir.dot(w)
        val e = rayDir.dot(w)
        return (b * e - d) / denom
    }

    /** Distance between the ray (forward only) and the segment from [a] to [b]. */
    fun rayToSegmentDistance(rayOrigin: Vector3f, rayDir: Vector3f, a: Vector3f, b: Vector3f): Float {
        val seg = Vector3f(b).sub(a)
        val len = seg.length()
        if (len < 1e-6f) return pointToRay(rayOrigin, rayDir, a)
        val dir = Vector3f(seg).div(len)
        val t = closestAxisParam(rayOrigin, rayDir, a, dir)?.coerceIn(0f, len) ?: 0f
        return pointToRay(rayOrigin, rayDir, Vector3f(a).add(Vector3f(dir).mul(t)))
    }

    private fun pointToRay(origin: Vector3f, dir: Vector3f, p: Vector3f): Float {
        val v = Vector3f(p).sub(origin)
        val t = v.dot(dir).coerceAtLeast(0f)
        return Vector3f(origin).add(Vector3f(dir).mul(t)).distance(p)
    }

    /** Index of the nearest axis whose segment passes within [threshold] of the ray, or -1. */
    fun pickAxis(
        rayOrigin: Vector3f, rayDir: Vector3f, origin: Vector3f,
        axes: Array<Vector3f>, length: Float, threshold: Float,
    ): Int {
        var best = -1
        var bestDist = threshold
        axes.forEachIndexed { i, axis ->
            val end = Vector3f(origin).add(Vector3f(axis).mul(length))
            val d = rayToSegmentDistance(rayOrigin, rayDir, origin, end)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    /** Rounds [value] to the nearest multiple of [step]; a non-positive step disables snapping. */
    fun snap(value: Float, step: Float): Float = if (step <= 0f) value else round(value / step) * step

    fun isFacingAxis(cameraForward: Vector3f, axis: Vector3f): Boolean = abs(cameraForward.dot(axis)) > 0.999f
}
