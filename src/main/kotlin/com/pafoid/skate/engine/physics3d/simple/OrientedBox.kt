package com.pafoid.skate.engine.physics3d.simple

import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs

/** Sphere-vs-box contact: [normal] points from the box to the sphere, [depth] is the overlap, [point] lies on the box. */
internal class BoxContact(val normal: Vector3f, val depth: Float, val point: Vector3f)

/** First hit of a (possibly inflated) segment on a box: [distance] along the unit direction and the face [normal]. */
internal class BoxHit(val distance: Float, val normal: Vector3f)

/**
 * An oriented box in world space (PHY-25): [halfExtents] along the box's own axes, centred at [localCenter] in the owning
 * body's frame. [place] puts it at the body's pose; the queries then work in world space.
 */
internal class OrientedBox(halfExtents: Vector3f, localCenter: Vector3f = Vector3f()) {
    val halfExtents = Vector3f(halfExtents).absolute()
    private val localCenter = Vector3f(localCenter)
    val center = Vector3f()
    private val rotation = Quaternionf()
    private val inverse = Quaternionf()

    fun place(position: Vector3f, rotation: Quaternionf) {
        this.rotation.set(rotation).normalize()
        inverse.set(this.rotation).conjugate()
        center.set(localCenter).rotate(this.rotation).add(position)
    }

    private fun toLocal(point: Vector3f, out: Vector3f): Vector3f = out.set(point).sub(center).rotate(inverse)

    /** World-space axis-aligned bounds. */
    fun bounds(min: Vector3f, max: Vector3f) {
        val extent = Vector3f()
        val axis = Vector3f()
        for (k in 0 until 3) {
            axis.zero().setComponent(k, halfExtents[k]).rotate(rotation)
            extent.add(abs(axis.x), abs(axis.y), abs(axis.z))
        }
        min.set(center).sub(extent)
        max.set(center).add(extent)
    }

    /** Contact with the sphere at [sphereCenter] of [radius], or null when they do not overlap. */
    fun sphereContact(sphereCenter: Vector3f, radius: Float): BoxContact? {
        val local = toLocal(sphereCenter, Vector3f())
        val clamped = Vector3f(
            local.x.coerceIn(-halfExtents.x, halfExtents.x),
            local.y.coerceIn(-halfExtents.y, halfExtents.y),
            local.z.coerceIn(-halfExtents.z, halfExtents.z),
        )
        val offset = Vector3f(local).sub(clamped)
        val distance = offset.length()
        if (distance > INSIDE_EPSILON) {
            if (distance >= radius) return null
            return BoxContact(offset.div(distance).rotate(rotation), radius - distance, toWorld(clamped))
        }
        // Centre inside the box: push out through the nearest face
        var axis = 0
        var gap = Float.MAX_VALUE
        for (k in 0 until 3) {
            val g = halfExtents[k] - abs(local[k])
            if (g < gap) {
                gap = g
                axis = k
            }
        }
        val sign = if (local[axis] < 0f) -1f else 1f
        val normal = Vector3f().setComponent(axis, sign)
        val surface = Vector3f(local).setComponent(axis, sign * halfExtents[axis])
        return BoxContact(normal.rotate(rotation), radius + gap, toWorld(surface))
    }

    /**
     * First entry of the segment from [from] along unit [direction] for [length] into the box grown by [inflate] on
     * every side (a swept sphere of that radius, with square corners). Starting inside gives no hit, like the other
     * backends' rays.
     */
    fun raycast(from: Vector3f, direction: Vector3f, length: Float, inflate: Float = 0f): BoxHit? {
        val origin = toLocal(from, Vector3f())
        val dir = Vector3f(direction).rotate(inverse)
        var near = Float.NEGATIVE_INFINITY
        var far = Float.POSITIVE_INFINITY
        var axis = -1
        var sign = 0f
        for (k in 0 until 3) {
            val h = halfExtents[k] + inflate
            val o = origin[k]
            val d = dir[k]
            if (abs(d) < 1e-9f) {
                if (abs(o) > h) return null
                continue
            }
            val t1 = (-h - o) / d
            val t2 = (h - o) / d
            val enter = minOf(t1, t2)
            if (enter > near) {
                near = enter
                axis = k
                sign = if (d > 0f) -1f else 1f
            }
            far = minOf(far, maxOf(t1, t2))
            if (near > far) return null
        }
        if (axis < 0 || near < 0f || near > length) return null
        return BoxHit(near, Vector3f().setComponent(axis, sign).rotate(rotation))
    }

    private fun toWorld(local: Vector3f): Vector3f = Vector3f(local).rotate(rotation).add(center)

    private companion object {
        const val INSIDE_EPSILON = 1e-6f
    }
}
