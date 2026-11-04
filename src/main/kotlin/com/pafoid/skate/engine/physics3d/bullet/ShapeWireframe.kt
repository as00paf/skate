package com.pafoid.skate.engine.physics3d.bullet

import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.roundToInt

/** Turns a triangle soup into the line segments worth drawing in the physics debug view. */
internal object ShapeWireframe {

    /** Below this cosine between two face normals, the shared edge is a crease and is drawn. */
    private const val COPLANAR_COS = 0.999f

    /** Vertices closer than this (in shape units) are welded when matching edges. */
    private const val WELD = 1e-3f

    /**
     * Returns the feature edges of [triangles] (9 floats per triangle) as 6 floats per segment: boundary edges and
     * creases between non-coplanar faces. Diagonals splitting flat faces and duplicate edges are left out.
     */
    fun featureEdges(triangles: FloatArray): FloatArray {
        val edges = LinkedHashMap<Pair<Long, Long>, Edge>()
        for (t in 0 until triangles.size / 9) {
            val corners = List(3) { k -> Vector3f(triangles[t * 9 + k * 3], triangles[t * 9 + k * 3 + 1], triangles[t * 9 + k * 3 + 2]) }
            val normal = Vector3f(corners[1]).sub(corners[0]).cross(Vector3f(corners[2]).sub(corners[0]))
            if (normal.lengthSquared() == 0f) continue
            normal.normalize()
            for (k in 0 until 3) {
                val a = corners[k]
                val b = corners[(k + 1) % 3]
                val ka = key(a)
                val kb = key(b)
                if (ka == kb) continue
                val id = if (ka < kb) ka to kb else kb to ka
                edges.getOrPut(id) { Edge(a, b) }.normals += normal
            }
        }
        val out = ArrayList<Float>()
        edges.values.filter { it.isFeature() }.forEach { e ->
            out += e.a.x; out += e.a.y; out += e.a.z
            out += e.b.x; out += e.b.y; out += e.b.z
        }
        return out.toFloatArray()
    }

    /** Above this many points, [hullEdges] is too slow (it tests every point triple) and callers should not use it. */
    const val MAX_HULL_POINTS = 64

    /**
     * Returns the edges of the convex hull of [points] (3 floats per point) as 6 floats per segment: the outline of
     * every hull face, with no diagonals. Brute force over point triples, meant for hulls of at most [MAX_HULL_POINTS].
     */
    fun hullEdges(points: FloatArray): FloatArray {
        val pts = List(points.size / 3) { Vector3f(points[it * 3], points[it * 3 + 1], points[it * 3 + 2]) }
        if (pts.size < 3) return FloatArray(0)
        val extent = pts.maxOf { maxOf(abs(it.x), abs(it.y), abs(it.z)) }
        val eps = 1e-4f * maxOf(1f, extent)
        val planes = HashSet<List<Int>>()
        val edges = LinkedHashSet<Pair<Int, Int>>()
        for (i in pts.indices) for (j in i + 1 until pts.size) for (k in j + 1 until pts.size) {
            val n = Vector3f(pts[j]).sub(pts[i]).cross(Vector3f(pts[k]).sub(pts[i]))
            if (n.lengthSquared() < eps * eps) continue
            n.normalize()
            var above = false
            var below = false
            for (p in pts) {
                val side = n.dot(p) - n.dot(pts[i])
                if (side > eps) above = true else if (side < -eps) below = true
                if (above && below) break
            }
            if (above && below) continue
            if (above) n.negate()
            val d = n.dot(pts[i])
            val key = listOf(n.x * 1000, n.y * 1000, n.z * 1000, d / eps / 10).map { it.roundToInt() }
            if (!planes.add(key)) continue
            val face = pts.indices.filter { abs(n.dot(pts[it]) - d) <= eps }
            val outline = outline2d(face, pts, n)
            outline.indices.forEach { m ->
                val a = outline[m]
                val b = outline[(m + 1) % outline.size]
                edges += if (a < b) a to b else b to a
            }
        }
        val out = FloatArray(edges.size * 6)
        edges.forEachIndexed { e, (a, b) ->
            out[e * 6] = pts[a].x; out[e * 6 + 1] = pts[a].y; out[e * 6 + 2] = pts[a].z
            out[e * 6 + 3] = pts[b].x; out[e * 6 + 4] = pts[b].y; out[e * 6 + 5] = pts[b].z
        }
        return out
    }

    /** Convex outline (monotone chain) of the coplanar points [face], projected on the plane with normal [n]. */
    private fun outline2d(face: List<Int>, pts: List<Vector3f>, n: Vector3f): List<Int> {
        val u = (if (abs(n.y) < 0.9f) Vector3f(0f, 1f, 0f) else Vector3f(1f, 0f, 0f)).cross(n).normalize()
        val v = Vector3f(n).cross(u)
        val proj = face.associateWith { Vector2f(pts[it].dot(u), pts[it].dot(v)) }
        val sorted = face.sortedWith(compareBy({ proj.getValue(it).x }, { proj.getValue(it).y }))
        if (sorted.size < 3) return sorted
        fun cross(o: Int, a: Int, b: Int): Float {
            val po = proj.getValue(o); val pa = proj.getValue(a); val pb = proj.getValue(b)
            return (pa.x - po.x) * (pb.y - po.y) - (pa.y - po.y) * (pb.x - po.x)
        }
        val hull = ArrayList<Int>()
        for (pass in 0..1) {
            val start = hull.size
            for (p in if (pass == 0) sorted else sorted.asReversed()) {
                while (hull.size >= start + 2 && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0f) hull.removeAt(hull.size - 1)
                hull += p
            }
            hull.removeAt(hull.size - 1)
        }
        return hull
    }

    private class Edge(val a: Vector3f, val b: Vector3f) {
        val normals = ArrayList<Vector3f>(2)

        fun isFeature(): Boolean = normals.size == 1 ||
            normals.any { n -> normals.any { m -> n.dot(m) < COPLANAR_COS } }
    }

    private fun key(v: Vector3f): Long {
        val x = (v.x / WELD).roundToInt().toLong() and 0x1FFFFF
        val y = (v.y / WELD).roundToInt().toLong() and 0x1FFFFF
        val z = (v.z / WELD).roundToInt().toLong() and 0x1FFFFF
        return (x shl 42) or (y shl 21) or z
    }
}
