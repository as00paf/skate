package com.pafoid.skate.engine.physics3d.water

import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.ShapePart
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.ceil

/** One buoyancy probe: a sphere at [local] (body frame, scaled) of [radius] standing for [volume] m³ of the shape. */
class BuoyancySample(val local: Vector3f, val radius: Float, val volume: Float)

/**
 * Turns collider shapes into buoyancy sample spheres (PHY-16, `docs/design/REN-29.md` §8.2). The sample volumes add up
 * to the shape's volume, and each sphere's radius matches its volume share, so a fully submerged body displaces exactly
 * its own volume and the submerged fraction changes smoothly as it crosses the surface.
 */
object BuoyancyMath {
    /** Hulls fill about this much of their bounding box; the hull volume is not computed exactly. */
    const val HULL_FILL = 0.6f
    private const val CYLINDER_FILL = (PI / 4.0).toFloat()
    private const val LARGE_SIDE = 2f
    private const val MAX_AXIS_SAMPLES = 9

    /** Samples for a whole body: every part at its offset, then the Transform [scale] (volumes scale with it). */
    fun samples(parts: List<ShapePart>, scale: Vector3f = Vector3f(1f)): List<BuoyancySample> {
        val volumeScale = scale.x * scale.y * scale.z
        return parts.flatMap { (shape, offset) ->
            samples(shape).map { s ->
                val volume = s.volume * volumeScale
                BuoyancySample(Vector3f(s.local).add(offset).mul(scale), radiusFor(volume), volume)
            }
        }
    }

    /** Samples for one shape in its own frame. Static-only shapes (meshes, heightfields) have none. */
    fun samples(shape: ColliderShape): List<BuoyancySample> = when (shape) {
        is ColliderShape.Box -> grid(Vector3f(), Vector3f(shape.halfExtents).absolute(), 1f)
        is ColliderShape.Capsule -> {
            val r = shape.radius
            val volume = (4.0 / 3.0 * PI).toFloat() * r * r * r + PI.toFloat() * r * r * shape.height
            if (shape.height < r) sphere(r, volume) else alongAxis(shape.axis, shape.height + 2f * r, r, volume)
        }
        is ColliderShape.Cylinder -> {
            val r = shape.radius
            if (shape.height < 2f * r) {
                grid(Vector3f(), Vector3f(r).setComponent(shape.axis, shape.height / 2f), CYLINDER_FILL)
            } else {
                alongAxis(shape.axis, shape.height, r, PI.toFloat() * r * r * shape.height)
            }
        }
        is ColliderShape.ConvexHull -> hull(shape.points)
        is ColliderShape.CompoundHull -> shape.hulls.flatMap { hull(it) }
        is ColliderShape.TriangleMesh, is ColliderShape.Heightfield -> emptyList()
    }

    /** Volume of all samples. */
    fun volume(samples: List<BuoyancySample>): Float = samples.sumOf { it.volume.toDouble() }.toFloat()

    /**
     * Fraction (0..1) of a sphere of [radius] under water when the surface is [depth] metres above its centre
     * (negative: below). Uses the spherical cap volume `π s² (3r − s) / 3` with `s = depth + r`.
     */
    fun sphereCapFraction(depth: Float, radius: Float): Float {
        if (radius <= 0f) return if (depth >= 0f) 1f else 0f
        if (depth <= -radius) return 0f
        if (depth >= radius) return 1f
        val s = depth + radius
        return (s * s * (3f * radius - s) / (4f * radius * radius * radius)).coerceIn(0f, 1f)
    }

    /** Radius of a sphere of [volume]. */
    fun radiusFor(volume: Float): Float = cbrt(3f * volume / (4f * PI.toFloat()))

    private fun hull(points: List<Vector3f>): List<BuoyancySample> {
        if (points.isEmpty()) return emptyList()
        val min = Vector3f(Float.MAX_VALUE)
        val max = Vector3f(-Float.MAX_VALUE)
        points.forEach { min.min(it); max.max(it) }
        val center = Vector3f(min).add(max).mul(0.5f)
        return grid(center, Vector3f(max).sub(min).mul(0.5f), HULL_FILL)
    }

    /** 2 × 2 × 2 samples over the box ([center], [half]), or 3 × 3 × 3 when a side is over 2 m; [fill] of its volume. */
    private fun grid(center: Vector3f, half: Vector3f, fill: Float): List<BuoyancySample> {
        val n = if (maxOf(half.x, half.y, half.z) * 2f > LARGE_SIDE) 3 else 2
        val volume = 8f * half.x * half.y * half.z * fill
        if (volume <= 0f) return emptyList()
        val share = volume / (n * n * n)
        val radius = radiusFor(share)
        fun cell(i: Int, h: Float) = (-1f + (2f * i + 1f) / n) * h
        val out = ArrayList<BuoyancySample>(n * n * n)
        for (x in 0 until n) for (y in 0 until n) for (z in 0 until n) {
            out += BuoyancySample(Vector3f(cell(x, half.x), cell(y, half.y), cell(z, half.z)).add(center), radius, share)
        }
        return out
    }

    /** A centre sample and six at half the radius along ±X, ±Y, ±Z. */
    private fun sphere(r: Float, volume: Float): List<BuoyancySample> {
        val share = volume / 7f
        val radius = radiusFor(share)
        val offsets = listOf(Vector3f()) + (0..2).flatMap { axis ->
            listOf(Vector3f().setComponent(axis, r / 2f), Vector3f().setComponent(axis, -r / 2f))
        }
        return offsets.map { BuoyancySample(it, radius, share) }
    }

    /** Evenly spaced samples along [axis] over [length], about one per radius. */
    private fun alongAxis(axis: Int, length: Float, r: Float, volume: Float): List<BuoyancySample> {
        val n = ceil(length / r).toInt().coerceIn(2, MAX_AXIS_SAMPLES)
        val share = volume / n
        val radius = radiusFor(share)
        return (0 until n).map { i ->
            BuoyancySample(Vector3f().setComponent(axis, (-0.5f + (i + 0.5f) / n) * length), radius, share)
        }
    }
}
