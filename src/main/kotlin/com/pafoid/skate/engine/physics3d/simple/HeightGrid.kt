package com.pafoid.skate.engine.physics3d.simple

import com.pafoid.skate.engine.physics3d.ColliderShape
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The real geometry of a static SimplePhysics3D body (PHY-25 boxes, PHY-15 heightfields), placed at the body's pose.
 * Moving bodies are spheres, so a surface answers sphere contacts and (inflated) ray casts.
 */
internal interface StaticSurface {
    fun place(position: Vector3f, rotation: Quaternionf)

    /** World-space axis-aligned bounds. */
    fun bounds(min: Vector3f, max: Vector3f)

    /** Contact with a sphere, the normal pointing from the surface to the sphere; null when they do not touch. */
    fun sphereContact(sphereCenter: Vector3f, radius: Float): BoxContact?

    /** First hit of the segment from [from] along unit [direction] for [length], swept as a sphere of [inflate]. */
    fun raycast(from: Vector3f, direction: Vector3f, length: Float, inflate: Float = 0f): BoxHit?
}

/**
 * A [ColliderShape.Heightfield] for SimplePhysics3D (PHY-15): two triangles per quad split along the (x, z)–(x+1, z+1)
 * diagonal like the Bullet and Jolt backends. Its corner sample sits at the body position; rotation is ignored
 * (terrain is axis-aligned). Rays hit the upper faces only; a sphere whose centre is below the surface is pushed up.
 */
internal class HeightGrid(private val field: ColliderShape.Heightfield) : StaticSurface {
    private val origin = Vector3f()
    private val n = field.samplesPerSide
    private val spacing = field.spacing

    override fun place(position: Vector3f, rotation: Quaternionf) {
        origin.set(position)
    }

    override fun bounds(min: Vector3f, max: Vector3f) {
        min.set(origin).add(0f, field.minY, 0f)
        max.set(origin).add(field.size, field.maxY, field.size)
    }

    /** Surface height at world ([x], [z]) with the face normal in [normal], or null outside the grid. */
    fun heightAt(x: Float, z: Float, normal: Vector3f? = null): Float? {
        val gx = (x - origin.x) / spacing
        val gz = (z - origin.z) / spacing
        if (gx < 0f || gz < 0f || gx > n - 1 || gz > n - 1) return null
        val ix = floor(gx).toInt().coerceAtMost(n - 2)
        val iz = floor(gz).toInt().coerceAtMost(n - 2)
        val fx = gx - ix
        val fz = gz - iz
        val h00 = field.height(ix, iz)
        val h10 = field.height(ix + 1, iz)
        val h01 = field.height(ix, iz + 1)
        val h11 = field.height(ix + 1, iz + 1)
        // Slopes along X and Z of the triangle containing the point
        val (dx, dz) = if (fz >= fx) (h11 - h01) to (h01 - h00) else (h10 - h00) to (h11 - h10)
        normal?.set(-dx / spacing, 1f, -dz / spacing)?.normalize()
        return origin.y + h00 + fx * dx + fz * dz
    }

    override fun sphereContact(sphereCenter: Vector3f, radius: Float): BoxContact? {
        val normal = Vector3f()
        heightAt(sphereCenter.x, sphereCenter.z, normal)?.let { h ->
            if (sphereCenter.y <= h) {
                // Centre at or under the surface: push out along the face normal
                val depth = (h - sphereCenter.y) * normal.y + radius
                return BoxContact(normal, depth, Vector3f(sphereCenter.x, h, sphereCenter.z))
            }
        }
        val best = Vector3f()
        var bestSq = radius * radius
        var found = false
        val closest = Vector3f()
        forEachTriangle(sphereCenter.x - radius, sphereCenter.z - radius, sphereCenter.x + radius, sphereCenter.z + radius) { a, b, c ->
            closestPointOnTriangle(sphereCenter, a, b, c, closest)
            val d = closest.distanceSquared(sphereCenter)
            if (d < bestSq) {
                bestSq = d
                best.set(closest)
                found = true
            }
        }
        if (!found) return null
        val distance = sqrt(bestSq)
        if (distance > 1e-6f) normal.set(sphereCenter).sub(best).div(distance) else heightAt(best.x, best.z, normal) ?: normal.set(0f, 1f, 0f)
        return BoxContact(normal, radius - distance, best)
    }

    override fun raycast(from: Vector3f, direction: Vector3f, length: Float, inflate: Float): BoxHit? =
        if (inflate > 0f) sphereSweep(from, direction, length, inflate) else rayTriangles(from, direction, length)

    private fun rayTriangles(from: Vector3f, direction: Vector3f, length: Float): BoxHit? {
        val to = Vector3f(from).fma(length, direction)
        var bestT = Float.MAX_VALUE
        val bestNormal = Vector3f()
        val e1 = Vector3f()
        val e2 = Vector3f()
        val p = Vector3f()
        val q = Vector3f()
        val s = Vector3f()
        forEachTriangle(minOf(from.x, to.x), minOf(from.z, to.z), maxOf(from.x, to.x), maxOf(from.z, to.z)) { a, b, c ->
            e1.set(b).sub(a)
            e2.set(c).sub(a)
            // Upper faces only: the face normal e1 × e2 points up for both triangles of a quad
            val faceNormal = Vector3f(e1).cross(e2)
            if (faceNormal.dot(direction) >= 0f) return@forEachTriangle
            direction.cross(e2, p)
            val det = e1.dot(p)
            if (det > -1e-12f && det < 1e-12f) return@forEachTriangle
            val inv = 1f / det
            s.set(from).sub(a)
            val u = s.dot(p) * inv
            if (u < 0f || u > 1f) return@forEachTriangle
            s.cross(e1, q)
            val v = direction.dot(q) * inv
            if (v < 0f || u + v > 1f) return@forEachTriangle
            val t = e2.dot(q) * inv
            if (t < 0f || t > length || t >= bestT) return@forEachTriangle
            bestT = t
            bestNormal.set(faceNormal).normalize()
        }
        return if (bestT == Float.MAX_VALUE) null else BoxHit(bestT, bestNormal)
    }

    /** Marches a sphere along the segment, then bisects the first touching step; starting in contact gives no hit. */
    private fun sphereSweep(from: Vector3f, direction: Vector3f, length: Float, radius: Float): BoxHit? {
        val at = Vector3f()
        fun touches(t: Float) = sphereContact(at.set(from).fma(t, direction), radius)
        if (touches(0f) != null) return null
        val step = max(minOf(radius, spacing) * 0.5f, 1e-3f)
        var free = 0f
        while (free < length) {
            val next = minOf(free + step, length)
            if (touches(next) != null) {
                var hit = next
                repeat(BISECTION_STEPS) {
                    val mid = (free + hit) * 0.5f
                    if (touches(mid) != null) hit = mid else free = mid
                }
                val contact = touches(hit) ?: return BoxHit(free, Vector3f(0f, 1f, 0f))
                return BoxHit(free, contact.normal)
            }
            free = next
        }
        return null
    }

    /** Visits the world-space triangles of every quad overlapping the XZ rectangle, upward winding. */
    private inline fun forEachTriangle(minX: Float, minZ: Float, maxX: Float, maxZ: Float, visit: (Vector3f, Vector3f, Vector3f) -> Unit) {
        val x0 = floor((minX - origin.x) / spacing).toInt().coerceAtLeast(0)
        val z0 = floor((minZ - origin.z) / spacing).toInt().coerceAtLeast(0)
        val x1 = floor((maxX - origin.x) / spacing).toInt().coerceAtMost(n - 2)
        val z1 = floor((maxZ - origin.z) / spacing).toInt().coerceAtMost(n - 2)
        if (x0 > x1 || z0 > z1) return
        val p00 = Vector3f()
        val p10 = Vector3f()
        val p01 = Vector3f()
        val p11 = Vector3f()
        for (z in z0..z1) for (x in x0..x1) {
            corner(x, z, p00)
            corner(x + 1, z, p10)
            corner(x, z + 1, p01)
            corner(x + 1, z + 1, p11)
            visit(p00, p01, p11)
            visit(p00, p11, p10)
        }
    }

    private fun corner(x: Int, z: Int, out: Vector3f): Vector3f =
        out.set(origin.x + x * spacing, origin.y + field.height(x, z), origin.z + z * spacing)

    private companion object {
        const val BISECTION_STEPS = 16

        /** Closest point to [p] on triangle [a][b][c] (Ericson, Real-Time Collision Detection 5.1.5). */
        fun closestPointOnTriangle(p: Vector3f, a: Vector3f, b: Vector3f, c: Vector3f, out: Vector3f): Vector3f {
            val abx = b.x - a.x; val aby = b.y - a.y; val abz = b.z - a.z
            val acx = c.x - a.x; val acy = c.y - a.y; val acz = c.z - a.z
            val apx = p.x - a.x; val apy = p.y - a.y; val apz = p.z - a.z
            val d1 = abx * apx + aby * apy + abz * apz
            val d2 = acx * apx + acy * apy + acz * apz
            if (d1 <= 0f && d2 <= 0f) return out.set(a)
            val bpx = p.x - b.x; val bpy = p.y - b.y; val bpz = p.z - b.z
            val d3 = abx * bpx + aby * bpy + abz * bpz
            val d4 = acx * bpx + acy * bpy + acz * bpz
            if (d3 >= 0f && d4 <= d3) return out.set(b)
            val vc = d1 * d4 - d3 * d2
            if (vc <= 0f && d1 >= 0f && d3 <= 0f) {
                val v = d1 / (d1 - d3)
                return out.set(a.x + v * abx, a.y + v * aby, a.z + v * abz)
            }
            val cpx = p.x - c.x; val cpy = p.y - c.y; val cpz = p.z - c.z
            val d5 = abx * cpx + aby * cpy + abz * cpz
            val d6 = acx * cpx + acy * cpy + acz * cpz
            if (d6 >= 0f && d5 <= d6) return out.set(c)
            val vb = d5 * d2 - d1 * d6
            if (vb <= 0f && d2 >= 0f && d6 <= 0f) {
                val w = d2 / (d2 - d6)
                return out.set(a.x + w * acx, a.y + w * acy, a.z + w * acz)
            }
            val va = d3 * d6 - d5 * d4
            if (va <= 0f && (d4 - d3) >= 0f && (d5 - d6) >= 0f) {
                val w = (d4 - d3) / ((d4 - d3) + (d5 - d6))
                return out.set(b.x + w * (c.x - b.x), b.y + w * (c.y - b.y), b.z + w * (c.z - b.z))
            }
            val denom = 1f / (va + vb + vc)
            val v = vb * denom
            val w = vc * denom
            return out.set(a.x + abx * v + acx * w, a.y + aby * v + acy * w, a.z + abz * v + acz * w)
        }
    }
}
