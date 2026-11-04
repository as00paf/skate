package com.pafoid.skate.engine.assets.procedural

import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds triangle meshes in code (no GL): flat or smooth quads, boxes, tubes, profile extrusions and surfaces of
 * revolution. [toObj] writes the result as a Wavefront OBJ that the regular model loader reads, so procedural
 * meshes are ordinary model files on disk (and survive scene serialization like any other model).
 *
 * Faces wind counter-clockwise when seen from outside. Each builder call takes the outward direction and fixes the
 * winding to match it, so callers never need to reason about vertex order.
 */
class MeshBuilder {

    private val positions = ArrayList<Vector3f>()
    private val normals = ArrayList<Vector3f>()
    private val uvs = ArrayList<Vector2f>()
    private val colors = ArrayList<Vector3f>()
    private val indices = ArrayList<Int>()

    /** Vertex colour (linear RGB) given to every vertex added from now on; it multiplies the material's base colour. */
    var color: Vector3f = Vector3f(1f)

    val vertexCount: Int get() = positions.size
    val triangleCount: Int get() = indices.size / 3

    /** Axis-aligned bounds of every vertex, as (min, max). */
    fun bounds(): Pair<Vector3f, Vector3f> {
        val min = Vector3f(Float.MAX_VALUE)
        val max = Vector3f(-Float.MAX_VALUE)
        positions.forEach { min.min(it); max.max(it) }
        return min to max
    }

    /** Triangles as position triples, for tests and tools. */
    fun triangles(): List<Triple<Vector3f, Vector3f, Vector3f>> =
        indices.chunked(3).map { (a, b, c) -> Triple(positions[a], positions[b], positions[c]) }

    /** Triangle normals as built (from winding), for tests. */
    fun faceNormals(): List<Vector3f> = triangles().map { (a, b, c) -> faceNormal(a, b, c) }

    private fun vertex(p: Vector3f, n: Vector3f, uv: Vector2f): Int {
        positions += Vector3f(p)
        normals += Vector3f(n)
        uvs += Vector2f(uv)
        colors += Vector3f(color)
        return positions.size - 1
    }

    /** Vertex colours as built, for tests. */
    fun vertexColors(): List<Vector3f> = colors.map { Vector3f(it) }

    /** Copies [other]'s triangles (with their colours) into this mesh, moved by [transform]. */
    fun append(other: MeshBuilder, transform: Matrix4f = Matrix4f()): MeshBuilder {
        val base = positions.size
        val normalMatrix = Matrix4f(transform).invert().transpose()
        for (i in 0 until other.vertexCount) {
            positions += transform.transformPosition(Vector3f(other.positions[i]))
            normals += normalMatrix.transformDirection(Vector3f(other.normals[i])).normalize()
            uvs += Vector2f(other.uvs[i])
            colors += Vector3f(other.colors[i])
        }
        other.indices.forEach { indices += base + it }
        return this
    }

    /**
     * Adds a smooth surface through a grid of [points] (rows of equal length; a row may collapse to one repeated
     * point, like the tip of a board). Each quad faces [outward] of its centre; vertex normals come from the
     * neighbouring points so the surface shades as one curved sheet.
     */
    fun grid(points: List<List<Vector3f>>, outward: (Vector3f) -> Vector3f): MeshBuilder {
        require(points.size >= 2 && points.all { it.size == points[0].size && it.size >= 2 })
        val rows = points.size; val cols = points[0].size
        val normal = { r: Int, c: Int ->
            val along = Vector3f(points[minOf(r + 1, rows - 1)][c]).sub(points[maxOf(r - 1, 0)][c])
            val across = Vector3f(points[r][minOf(c + 1, cols - 1)]).sub(points[r][maxOf(c - 1, 0)])
            val n = along.cross(across)
            val out = outward(points[r][c])
            if (n.lengthSquared() < EPSILON) Vector3f(out).normalize()
            else n.normalize().let { if (it.dot(out) < 0f) it.negate() else it }
        }
        for (r in 0 until rows - 1) for (c in 0 until cols - 1) {
            val a = points[r][c]; val b = points[r][c + 1]; val cc = points[r + 1][c + 1]; val d = points[r + 1][c]
            val centre = Vector3f(a).add(b).add(cc).add(d).div(4f)
            quad(a, b, cc, d, outward(centre), listOf(normal(r, c), normal(r, c + 1), normal(r + 1, c + 1), normal(r + 1, c)))
        }
        return this
    }

    /**
     * Adds the quad a-b-c-d (a closed loop) with per-vertex [vertexNormals] (null: flat) and [quadUvs]
     * (null: planar projection). The winding is flipped when its normal points away from [outward].
     * Degenerate quads are skipped.
     */
    fun quad(
        a: Vector3f, b: Vector3f, c: Vector3f, d: Vector3f,
        outward: Vector3f,
        vertexNormals: List<Vector3f>? = null,
        quadUvs: List<Vector2f>? = null,
    ) {
        val n1 = Vector3f(b).sub(a).cross(Vector3f(c).sub(a))
        val n2 = Vector3f(c).sub(a).cross(Vector3f(d).sub(a))
        val n = Vector3f(n1).add(n2)
        if (n.lengthSquared() < EPSILON) return
        n.normalize()
        val corners = listOf(a, b, c, d)
        val cornerNormals = vertexNormals ?: List(4) { n }
        val cornerUvs = quadUvs ?: corners.map { planarUv(it, n) }
        // Flipping keeps a as the first corner (a-d-c-b), so both windings split along the same a-c diagonal and
        // the degenerate checks below still match their triangles.
        val flipped = n.dot(outward) < 0f
        val order = if (flipped) listOf(0, 3, 2, 1) else listOf(0, 1, 2, 3)
        val base = order.map { vertex(corners[it], cornerNormals[it], cornerUvs[it]) }
        val abc = if (flipped) listOf(base[0], base[2], base[3]) else listOf(base[0], base[1], base[2])
        val acd = if (flipped) listOf(base[0], base[1], base[2]) else listOf(base[0], base[2], base[3])
        if (n1.lengthSquared() >= EPSILON) indices.addAll(abc)
        if (n2.lengthSquared() >= EPSILON) indices.addAll(acd)
    }

    /** Adds triangle a-b-c with a flat normal, wound to face [outward]. */
    fun triangle(a: Vector3f, b: Vector3f, c: Vector3f, outward: Vector3f) {
        val n = faceNormal(a, b, c)
        if (n.lengthSquared() < EPSILON) return
        val corners = if (n.dot(outward) < 0f) listOf(a, c, b) else listOf(a, b, c)
        val faceN = if (n.dot(outward) < 0f) Vector3f(n).negate() else n
        corners.forEach { indices += vertex(it, faceN, planarUv(it, faceN)) }
    }

    /** Adds a closed axis-aligned box. */
    fun box(min: Vector3f, max: Vector3f): MeshBuilder {
        val p = { x: Float, y: Float, z: Float -> Vector3f(x, y, z) }
        quad(p(min.x, max.y, min.z), p(max.x, max.y, min.z), p(max.x, max.y, max.z), p(min.x, max.y, max.z), UP)
        quad(p(min.x, min.y, min.z), p(max.x, min.y, min.z), p(max.x, min.y, max.z), p(min.x, min.y, max.z), DOWN)
        quad(p(min.x, min.y, max.z), p(max.x, min.y, max.z), p(max.x, max.y, max.z), p(min.x, max.y, max.z), FORWARD)
        quad(p(min.x, min.y, min.z), p(max.x, min.y, min.z), p(max.x, max.y, min.z), p(min.x, max.y, min.z), BACK)
        quad(p(max.x, min.y, min.z), p(max.x, min.y, max.z), p(max.x, max.y, max.z), p(max.x, max.y, min.z), RIGHT)
        quad(p(min.x, min.y, min.z), p(min.x, min.y, max.z), p(min.x, max.y, max.z), p(min.x, max.y, min.z), LEFT)
        return this
    }

    /** Adds a capped tube of [radius] from [from] to [to] with smooth sides. */
    fun tube(from: Vector3f, to: Vector3f, radius: Float, sides: Int = 12): MeshBuilder {
        val axis = Vector3f(to).sub(from)
        if (axis.lengthSquared() < EPSILON) return this
        axis.normalize()
        val helper = if (abs(axis.y) < 0.9f) UP else RIGHT
        val u = Vector3f(axis).cross(helper).normalize()
        val v = Vector3f(axis).cross(u).normalize()
        val ring = (0..sides).map { i ->
            val angle = (2.0 * Math.PI * i / sides).toFloat()
            Vector3f(u).mul(cos(angle)).add(Vector3f(v).mul(sin(angle)))
        }
        for (i in 0 until sides) {
            val n0 = ring[i]; val n1 = ring[i + 1]
            val a = Vector3f(n0).mul(radius).add(from)
            val b = Vector3f(n1).mul(radius).add(from)
            val c = Vector3f(n1).mul(radius).add(to)
            val d = Vector3f(n0).mul(radius).add(to)
            val outward = Vector3f(n0).add(n1)
            quad(a, b, c, d, outward, listOf(n0, n1, n1, n0))
            triangle(Vector3f(from), a, b, Vector3f(axis).negate())
            triangle(Vector3f(to), d, c, axis)
        }
        return this
    }

    /**
     * Extrudes a height profile across x in [[x0], [x1]]: [profile] holds (s, height) points with s non-decreasing
     * along [run] (unit, horizontal), and the solid fills from y = 0 up to the profile. Repeating an s with two
     * heights gives a vertical step. The top is smooth-shaded where neighbouring segments bend less than
     * [smoothAngleDeg]. Side caps, end walls and the bottom close the solid.
     */
    fun extrudeProfile(
        profile: List<Vector2f>,
        x0: Float,
        x1: Float,
        run: Vector3f = FORWARD,
        smoothAngleDeg: Float = 40f,
    ): MeshBuilder {
        require(profile.size >= 2) { "a profile needs at least two points" }
        val side = Vector3f(run).cross(UP).negate().normalize() // +X when run is +Z
        val at = { s: Float, y: Float, x: Float -> Vector3f(run).mul(s).add(Vector3f(side).mul(x)).add(0f, y, 0f) }
        val toWorld2D = { n: Vector2f -> Vector3f(run).mul(n.x).add(0f, n.y, 0f) }

        val segNormals = profileNormals(profile)
        val pointNormals = smoothedPointNormals(segNormals, smoothAngleDeg)
        var arc = 0f
        for (i in 0 until profile.size - 1) {
            val p = profile[i]; val q = profile[i + 1]
            val length = p.distance(q)
            if (length < 1e-6f) continue
            val nIn = toWorld2D(pointNormals[i].second)
            val nOut = toWorld2D(pointNormals[i + 1].first)
            val uvs = listOf(
                Vector2f(x0 * UV_SCALE, arc * UV_SCALE), Vector2f(x1 * UV_SCALE, arc * UV_SCALE),
                Vector2f(x1 * UV_SCALE, (arc + length) * UV_SCALE), Vector2f(x0 * UV_SCALE, (arc + length) * UV_SCALE),
            )
            quad(at(p.x, p.y, x0), at(p.x, p.y, x1), at(q.x, q.y, x1), at(q.x, q.y, x0),
                toWorld2D(segNormals[i]), listOf(nIn, nIn, nOut, nOut), uvs)
            arc += length

            // Side caps: one convex column per segment, from the ground to the profile.
            if (q.x - p.x > 1e-6f) {
                for ((x, dir) in listOf(x0 to Vector3f(side).negate(), x1 to side)) {
                    quad(at(p.x, 0f, x), at(q.x, 0f, x), at(q.x, q.y, x), at(p.x, p.y, x), dir)
                }
            }
        }
        val first = profile.first(); val last = profile.last()
        if (first.y > 0f) quad(at(first.x, 0f, x0), at(first.x, 0f, x1), at(first.x, first.y, x1), at(first.x, first.y, x0), Vector3f(run).negate())
        if (last.y > 0f) quad(at(last.x, 0f, x0), at(last.x, 0f, x1), at(last.x, last.y, x1), at(last.x, last.y, x0), run)
        quad(at(first.x, 0f, x0), at(first.x, 0f, x1), at(last.x, 0f, x1), at(last.x, 0f, x0), DOWN)
        return this
    }

    /**
     * Revolves [profile] (radius, height) points around the Y axis in [sectors] steps. The outward side of each
     * segment is its left normal (-dy, dr), so a path walked outward along a floor, up a wall and down an outer wall
     * faces into the open on every segment.
     */
    fun revolve(profile: List<Vector2f>, sectors: Int, smoothAngleDeg: Float = 40f): MeshBuilder {
        require(profile.size >= 2 && sectors >= 3)
        val segNormals = profileNormals(profile)
        val pointNormals = smoothedPointNormals(segNormals, smoothAngleDeg)
        val circumference = (2.0 * Math.PI).toFloat()
        for (k in 0 until sectors) {
            val a0 = circumference * k / sectors
            val a1 = circumference * (k + 1) / sectors
            val d0 = Vector3f(sin(a0), 0f, cos(a0))
            val d1 = Vector3f(sin(a1), 0f, cos(a1))
            val radial = { d: Vector3f, p: Vector2f -> Vector3f(d).mul(p.x).add(0f, p.y, 0f) }
            val radialN = { d: Vector3f, n: Vector2f -> Vector3f(d).mul(n.x).add(0f, n.y, 0f).normalize() }
            var arc = 0f
            for (i in 0 until profile.size - 1) {
                val p = profile[i]; val q = profile[i + 1]
                val length = p.distance(q)
                if (length < 1e-6f) continue
                val nIn = pointNormals[i].second; val nOut = pointNormals[i + 1].first
                val mid = Vector3f(d0).add(d1).normalize()
                val u0 = k.toFloat() / sectors * UV_ARC_TILES; val u1 = (k + 1).toFloat() / sectors * UV_ARC_TILES
                quad(
                    radial(d0, p), radial(d1, p), radial(d1, q), radial(d0, q),
                    radialN(mid, segNormals[i]),
                    listOf(radialN(d0, nIn), radialN(d1, nIn), radialN(d1, nOut), radialN(d0, nOut)),
                    listOf(Vector2f(u0, arc * UV_SCALE), Vector2f(u1, arc * UV_SCALE),
                        Vector2f(u1, (arc + length) * UV_SCALE), Vector2f(u0, (arc + length) * UV_SCALE)),
                )
                arc += length
            }
        }
        return this
    }

    /**
     * Wavefront OBJ text: one `v`/`vt`/`vn` per vertex and `f i/i/i` triangles. When any vertex is not white the
     * `v` lines carry its colour (`v x y z r g b`, read by Assimp as vertex colours).
     */
    fun toObj(name: String): String {
        val sb = StringBuilder()
        sb.append("# ").append(name).append(" (generated by MeshBuilder, do not edit)\n")
        sb.append("o ").append(name.replace(' ', '_')).append('\n')
        val coloured = colors.any { it.x != 1f || it.y != 1f || it.z != 1f }
        positions.forEachIndexed { i, it ->
            sb.append("v ").append(f(it.x)).append(' ').append(f(it.y)).append(' ').append(f(it.z))
            if (coloured) colors[i].let { c -> sb.append(' ').append(f(c.x)).append(' ').append(f(c.y)).append(' ').append(f(c.z)) }
            sb.append('\n')
        }
        uvs.forEach { sb.append("vt ").append(f(it.x)).append(' ').append(f(it.y)).append('\n') }
        normals.forEach { sb.append("vn ").append(f(it.x)).append(' ').append(f(it.y)).append(' ').append(f(it.z)).append('\n') }
        indices.chunked(3).forEach { tri ->
            sb.append('f')
            tri.forEach { i -> val k = i + 1; sb.append(' ').append(k).append('/').append(k).append('/').append(k) }
            sb.append('\n')
        }
        return sb.toString()
    }

    companion object {
        val UP: Vector3f get() = Vector3f(0f, 1f, 0f)
        val DOWN: Vector3f get() = Vector3f(0f, -1f, 0f)
        val FORWARD: Vector3f get() = Vector3f(0f, 0f, 1f)
        val BACK: Vector3f get() = Vector3f(0f, 0f, -1f)
        val RIGHT: Vector3f get() = Vector3f(1f, 0f, 0f)
        val LEFT: Vector3f get() = Vector3f(-1f, 0f, 0f)

        /** Texture repeats every 2 m. */
        const val UV_SCALE = 0.5f
        private const val UV_ARC_TILES = 8f
        private const val EPSILON = 1e-10f

        private fun f(v: Float): String = String.format(Locale.ROOT, "%.4f", if (abs(v) < 5e-5f) 0f else v)

        private fun faceNormal(a: Vector3f, b: Vector3f, c: Vector3f): Vector3f {
            val n = Vector3f(b).sub(a).cross(Vector3f(c).sub(a))
            return if (n.lengthSquared() < EPSILON) n else n.normalize()
        }

        private fun planarUv(p: Vector3f, n: Vector3f): Vector2f = when {
            abs(n.y) >= abs(n.x) && abs(n.y) >= abs(n.z) -> Vector2f(p.x, p.z)
            abs(n.x) >= abs(n.z) -> Vector2f(p.z, p.y)
            else -> Vector2f(p.x, p.y)
        }.mul(UV_SCALE)

        /** Left normal (-dy, ds) of every profile segment (zero for repeated points). */
        fun profileNormals(profile: List<Vector2f>): List<Vector2f> = (0 until profile.size - 1).map { i ->
            val d = Vector2f(profile[i + 1]).sub(profile[i])
            if (d.lengthSquared() < 1e-12f) Vector2f() else Vector2f(-d.y, d.x).normalize()
        }

        /** Per profile point, the (incoming, outgoing) shading normal: averaged where the bend is gentle. */
        private fun smoothedPointNormals(seg: List<Vector2f>, smoothAngleDeg: Float): List<Pair<Vector2f, Vector2f>> {
            val limit = Math.toRadians(smoothAngleDeg.toDouble())
            return (0..seg.size).map { i ->
                val before = seg.getOrNull(i - 1)?.takeIf { it.lengthSquared() > 0f }
                val after = seg.getOrNull(i)?.takeIf { it.lengthSquared() > 0f }
                when {
                    before != null && after != null &&
                        acos(before.dot(after).coerceIn(-1f, 1f).toDouble()) <= limit -> {
                        val avg = Vector2f(before).add(after).normalize()
                        avg to Vector2f(avg)
                    }
                    else -> Vector2f(before ?: after ?: Vector2f(0f, 1f)) to Vector2f(after ?: before ?: Vector2f(0f, 1f))
                }
            }
        }
    }
}
