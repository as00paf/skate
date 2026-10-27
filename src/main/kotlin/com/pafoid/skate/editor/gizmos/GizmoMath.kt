package com.pafoid.skate.editor.gizmos

import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.round
import kotlin.math.tan

/** Pure gizmo math, independent of rendering and input. */
object GizmoMath {
    /** World-space size that keeps a gizmo at [screenFraction] of the viewport height at [distance]. */
    fun screenSpaceScale(distance: Float, fovRadians: Float, screenFraction: Float = 0.15f): Float =
        2f * distance.coerceAtLeast(0.001f) * tan(fovRadians / 2f) * screenFraction

    /**
     * Gizmo world size for a camera: perspective cameras use the effective vertical fov (fov * zoom, as the
     * projection does), orthographic cameras use the visible height (viewportHeight * zoom) regardless of distance.
     */
    fun gizmoSize(
        orthographic: Boolean, distance: Float, fovDegrees: Float, zoom: Float, viewportHeight: Int,
        screenFraction: Float = 0.15f,
    ): Float =
        if (orthographic) viewportHeight * zoom * screenFraction
        else screenSpaceScale(distance, Math.toRadians((fovDegrees * zoom).toDouble()).toFloat(), screenFraction)

    /** Copies of [axes] flipped so each points toward the viewer ([viewDir] runs from camera to gizmo). */
    fun facingViewer(axes: Array<Vector3f>, viewDir: Vector3f): Array<Vector3f> =
        Array(axes.size) { i -> if (axes[i].dot(viewDir) > 0f) Vector3f(axes[i]).negate() else Vector3f(axes[i]) }

    /** Converts a world-space translation delta into the local space of a parent with [parentWorld] (null = root). */
    fun toParentSpace(delta: Vector3f, parentWorld: org.joml.Matrix4f?): Vector3f {
        if (parentWorld == null) return Vector3f(delta)
        return org.joml.Matrix4f(parentWorld).invert().transformDirection(Vector3f(delta))
    }

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

    fun rayPlaneHit(rayOrigin: Vector3f, rayDir: Vector3f, planePoint: Vector3f, normal: Vector3f): Vector3f? {
        val denom = normal.dot(rayDir)
        if (abs(denom) < 1e-5f) return null
        val t = Vector3f(planePoint).sub(rayOrigin).dot(normal) / denom
        if (t < 0f) return null
        return Vector3f(rayOrigin).add(Vector3f(rayDir).mul(t))
    }

    /** Distance from the ray/plane hit point to the circle of [radius] around [center]; MAX_VALUE if no hit. */
    fun distanceToCircle(rayOrigin: Vector3f, rayDir: Vector3f, center: Vector3f, normal: Vector3f, radius: Float): Float {
        val hit = rayPlaneHit(rayOrigin, rayDir, center, normal) ?: return Float.MAX_VALUE
        return abs(hit.distance(center) - radius)
    }

    /** Index of the nearest rotation ring within [threshold], or -1. */
    fun pickRing(
        rayOrigin: Vector3f, rayDir: Vector3f, center: Vector3f,
        axes: Array<Vector3f>, radius: Float, threshold: Float,
    ): Int {
        var best = -1
        var bestDist = threshold
        axes.forEachIndexed { i, axis ->
            val d = distanceToCircle(rayOrigin, rayDir, center, axis, radius)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    /** Signed angle in radians from [from] to [to] around [axis]. */
    fun signedAngle(from: Vector3f, to: Vector3f, axis: Vector3f): Float =
        atan2(axis.dot(Vector3f(from).cross(to)), from.dot(to))

    /**
     * Plane handle under the ray, or -1. Handle i lies in the plane perpendicular to axis i,
     * spanning [PLANE_MIN]..[PLANE_MAX] (in units of [size]) along the other two axes.
     */
    fun pickPlane(rayOrigin: Vector3f, rayDir: Vector3f, origin: Vector3f, axes: Array<Vector3f>, size: Float): Int {
        var best = -1
        var bestT = Float.MAX_VALUE
        for (i in 0..2) {
            val hit = rayPlaneHit(rayOrigin, rayDir, origin, axes[i]) ?: continue
            val rel = Vector3f(hit).sub(origin)
            val u = rel.dot(axes[(i + 1) % 3]) / size
            val v = rel.dot(axes[(i + 2) % 3]) / size
            if (u in PLANE_MIN..PLANE_MAX && v in PLANE_MIN..PLANE_MAX) {
                val t = hit.distance(rayOrigin)
                if (t < bestT) {
                    bestT = t
                    best = i
                }
            }
        }
        return best
    }

    const val PLANE_MIN = 0.2f
    const val PLANE_MAX = 0.5f

    fun pivot(points: List<Vector3f>): Vector3f {
        val c = Vector3f()
        if (points.isEmpty()) return c
        points.forEach { c.add(it) }
        return c.div(points.size.toFloat())
    }

    fun quaternionOf(rotationDegrees: Vector3f): Quaternionf = Quaternionf().rotationXYZ(
        Math.toRadians(rotationDegrees.x.toDouble()).toFloat(),
        Math.toRadians(rotationDegrees.y.toDouble()).toFloat(),
        Math.toRadians(rotationDegrees.z.toDouble()).toFloat(),
    )

    fun eulerDegrees(q: Quaternionf): Vector3f {
        val e = q.getEulerAnglesXYZ(Vector3f())
        return Vector3f(
            Math.toDegrees(e.x.toDouble()).toFloat(),
            Math.toDegrees(e.y.toDouble()).toFloat(),
            Math.toDegrees(e.z.toDouble()).toFloat(),
        )
    }

    /** Rotates [rotationDegrees] by [angleRadians] around the world-space [axis]. */
    fun rotateEuler(rotationDegrees: Vector3f, axis: Vector3f, angleRadians: Float): Vector3f =
        eulerDegrees(Quaternionf().rotationAxis(angleRadians, axis).mul(quaternionOf(rotationDegrees)))
}
