package com.pafoid.skate.editor.gizmos

import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Pure geometry for camera/light visuals in the scene view and for picking them. No rendering or ECS access. */
object EditorVisuals {
    class Segment(val a: Vector3f, val b: Vector3f)

    /** An object that can be clicked in the scene view through its icon. */
    class Target<T>(val value: T, val position: Vector3f)

    /** Frustum wireframe of a camera at [position]/[rotation] looking down its local -Z, drawn out to [length]. */
    fun cameraFrustum(
        position: Vector3f, rotation: Quaternionf, orthographic: Boolean, fovDegrees: Float, aspect: Float,
        orthoHeight: Float, near: Float, far: Float, length: Float,
    ): List<Segment> {
        val farDist = minOf(far, length).coerceAtLeast(0.01f)
        val nearDist = minOf(near, farDist * 0.5f).coerceAtLeast(0.001f)
        fun rect(d: Float): List<Vector3f> {
            val halfH = if (orthographic) minOf(orthoHeight / 2f, length / 2f) else tan(Math.toRadians(fovDegrees / 2.0)).toFloat() * d
            val halfW = halfH * aspect
            return listOf(Vector3f(-halfW, -halfH, -d), Vector3f(halfW, -halfH, -d), Vector3f(halfW, halfH, -d), Vector3f(-halfW, halfH, -d))
                .map { rotation.transform(it).add(position) }
        }
        val n = rect(nearDist)
        val f = rect(farDist)
        val segments = ArrayList<Segment>()
        for (i in 0..3) {
            segments.add(Segment(n[i], n[(i + 1) % 4]))
            segments.add(Segment(f[i], f[(i + 1) % 4]))
            segments.add(Segment(n[i], f[i]))
        }
        return segments
    }

    /** Distance at which a point light's attenuation `1 / (c + l*d + q*d^2)` falls to [cutoff]. */
    fun pointLightRange(constant: Float, linear: Float, quadratic: Float, cutoff: Float = 0.02f): Float {
        val k = constant - 1f / cutoff
        val range = when {
            quadratic > 1e-6f -> (-linear + sqrt(linear * linear - 4f * quadratic * k)) / (2f * quadratic)
            linear > 1e-6f -> -k / linear
            else -> DEFAULT_RANGE
        }
        return if (range.isNaN()) DEFAULT_RANGE else range.coerceIn(0.1f, MAX_RANGE)
    }

    /** Three axis-aligned circles around [center]. */
    fun rangeSphere(center: Vector3f, radius: Float, segments: Int = 32): List<Segment> =
        listOf(Vector3f(1f, 0f, 0f), Vector3f(0f, 1f, 0f), Vector3f(0f, 0f, 1f)).flatMap { axis ->
            val (u, v) = basis(axis)
            circle(center, u, v, radius, segments)
        }

    /** Cone from [apex] along [direction] with the given half-angle, ending in a circle at [range]. */
    fun spotCone(apex: Vector3f, direction: Vector3f, outerAngleDegrees: Float, range: Float, segments: Int = 24): List<Segment> {
        val dir = Vector3f(direction).normalize()
        val centre = Vector3f(apex).add(Vector3f(dir).mul(range))
        val radius = range * tan(Math.toRadians(outerAngleDegrees.coerceIn(0.1f, 89f).toDouble())).toFloat()
        val (u, v) = basis(dir)
        val segs = ArrayList(circle(centre, u, v, radius, segments))
        for (k in 0 until 4) {
            val ang = PI / 2 * k
            val rim = Vector3f(centre).add(Vector3f(u).mul((cos(ang) * radius).toFloat())).add(Vector3f(v).mul((sin(ang) * radius).toFloat()))
            segs.add(Segment(Vector3f(apex), rim))
        }
        return segs
    }

    /** Arrow of [length] from [origin] along [direction], with a small head. */
    fun arrow(origin: Vector3f, direction: Vector3f, length: Float): List<Segment> {
        val dir = Vector3f(direction).normalize()
        val tip = Vector3f(origin).add(Vector3f(dir).mul(length))
        val (u, v) = basis(dir)
        val back = Vector3f(tip).sub(Vector3f(dir).mul(length * 0.2f))
        val head = length * 0.08f
        return listOf(Segment(Vector3f(origin), tip)) + listOf(u, Vector3f(u).negate(), v, Vector3f(v).negate()).map {
            Segment(Vector3f(tip), Vector3f(back).add(Vector3f(it).mul(head)))
        }
    }

    /** Light bulb symbol facing the viewer: a circle with rays. */
    fun lightIcon(center: Vector3f, right: Vector3f, up: Vector3f, size: Float): List<Segment> {
        val segs = ArrayList(circle(center, right, up, size * 0.5f, 16))
        for (k in 0 until 8) {
            val ang = 2.0 * PI * k / 8
            val d = Vector3f(right).mul(cos(ang).toFloat()).add(Vector3f(up).mul(sin(ang).toFloat()))
            segs.add(Segment(Vector3f(center).add(Vector3f(d).mul(size * 0.7f)), Vector3f(center).add(Vector3f(d).mul(size))))
        }
        return segs
    }

    /** Camera symbol facing the viewer: a body rectangle with a lens triangle. */
    fun cameraIcon(center: Vector3f, right: Vector3f, up: Vector3f, size: Float): List<Segment> {
        fun p(x: Float, y: Float) = Vector3f(center).add(Vector3f(right).mul(x * size)).add(Vector3f(up).mul(y * size))
        val body = listOf(p(-1f, -0.6f), p(0.3f, -0.6f), p(0.3f, 0.6f), p(-1f, 0.6f))
        val segs = ArrayList<Segment>()
        for (i in 0..3) segs.add(Segment(body[i], body[(i + 1) % 4]))
        segs.add(Segment(p(0.3f, -0.2f), p(1f, -0.6f)))
        segs.add(Segment(p(1f, -0.6f), p(1f, 0.6f)))
        segs.add(Segment(p(1f, 0.6f), p(0.3f, 0.2f)))
        return segs
    }

    /** Nearest target whose icon sphere of [radius] is hit by the ray, or null. */
    fun <T> pick(rayOrigin: Vector3f, rayDir: Vector3f, targets: List<Target<T>>, radius: Float): T? {
        var best: T? = null
        var bestT = Float.MAX_VALUE
        for (target in targets) {
            val toTarget = Vector3f(target.position).sub(rayOrigin)
            val t = toTarget.dot(rayDir)
            if (t < 0f) continue
            val closest = Vector3f(rayOrigin).add(Vector3f(rayDir).mul(t))
            if (closest.distance(target.position) <= radius && t < bestT) {
                bestT = t
                best = target.value
            }
        }
        return best
    }

    private fun circle(center: Vector3f, u: Vector3f, v: Vector3f, radius: Float, segments: Int): List<Segment> {
        fun point(k: Int): Vector3f {
            val ang = 2.0 * PI * k / segments
            return Vector3f(center).add(Vector3f(u).mul((cos(ang) * radius).toFloat())).add(Vector3f(v).mul((sin(ang) * radius).toFloat()))
        }
        return (0 until segments).map { Segment(point(it), point((it + 1) % segments)) }
    }

    private fun basis(normal: Vector3f): Pair<Vector3f, Vector3f> {
        val n = Vector3f(normal).normalize()
        val helper = if (kotlin.math.abs(n.y) > 0.9f) Vector3f(1f, 0f, 0f) else Vector3f(0f, 1f, 0f)
        val u = Vector3f(n).cross(helper).normalize()
        val v = Vector3f(n).cross(u).normalize()
        return u to v
    }

    const val DEFAULT_RANGE = 10f
    const val MAX_RANGE = 500f
}
