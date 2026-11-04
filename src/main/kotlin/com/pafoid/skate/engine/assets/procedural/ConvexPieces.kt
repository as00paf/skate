package com.pafoid.skate.engine.assets.procedural

import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Splits concave static solids into convex point clouds for convex-hull colliders. The pieces follow the same
 * profile data as [MeshBuilder], so the collision matches the rendered surface (up to the chord of each segment).
 */
object ConvexPieces {

    /**
     * One convex column per run of collinear profile segments of a [MeshBuilder.extrudeProfile] solid: the
     * ground-to-profile slab between two profile points, across x in [[x0], [x1]]. Vertical steps produce no column
     * (the neighbouring columns already cover them).
     */
    fun profileColumns(
        profile: List<Vector2f>,
        x0: Float,
        x1: Float,
        run: Vector3f = MeshBuilder.FORWARD,
    ): List<List<Vector3f>> {
        val side = Vector3f(run).cross(MeshBuilder.UP).negate().normalize()
        val at = { s: Float, y: Float, x: Float -> Vector3f(run).mul(s).add(Vector3f(side).mul(x)).add(0f, y, 0f) }
        return mergeCollinear(profile.zipWithNext().filter { (p, q) -> q.x - p.x > 1e-6f }).map { (p, q) ->
            listOf(x0, x1).flatMap { x -> listOf(at(p.x, 0f, x), at(p.x, p.y, x), at(q.x, 0f, x), at(q.x, q.y, x)) }
        }
    }

    /**
     * Convex bands of a [MeshBuilder.revolve] wall: for each segment of [wall] (radius, height, rising outward) and
     * each of [sectors] slices, the solid between the wall chord and the [outerRadius], so the union fills the
     * ring from the wall to the outer edge.
     */
    fun revolvedBands(wall: List<Vector2f>, outerRadius: Float, sectors: Int): List<List<Vector3f>> {
        val tau = (2.0 * Math.PI).toFloat()
        val segments = mergeCollinear(wall.zipWithNext().filter { (p, q) -> p.distance(q) > 1e-6f })
        return (0 until sectors).flatMap { k ->
            val dirs = listOf(tau * k / sectors, tau * (k + 1) / sectors).map { Vector3f(sin(it), 0f, cos(it)) }
            segments.map { (p, q) ->
                dirs.flatMap { d ->
                    listOf(
                        Vector3f(d).mul(p.x).add(0f, p.y, 0f), Vector3f(d).mul(q.x).add(0f, q.y, 0f),
                        Vector3f(d).mul(outerRadius).add(0f, p.y, 0f), Vector3f(d).mul(outerRadius).add(0f, q.y, 0f),
                    )
                }
            }
        }
    }

    /** The 8 corners of a box of half-thickness [radius] around the segment [from]-[to] (a rail bar). */
    fun segment(from: Vector3f, to: Vector3f, radius: Float): List<Vector3f> {
        val axis = Vector3f(to).sub(from).normalize()
        val helper = if (abs(axis.y) < 0.9f) MeshBuilder.UP else MeshBuilder.RIGHT
        val u = Vector3f(axis).cross(helper).normalize().mul(radius)
        val v = Vector3f(axis).cross(u).normalize().mul(radius)
        return listOf(from, to).flatMap { end ->
            listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (a, b) ->
                Vector3f(end).add(Vector3f(u).mul(a)).add(Vector3f(v).mul(b))
            }
        }
    }

    /** The 8 corners of an axis-aligned box. */
    fun box(min: Vector3f, max: Vector3f): List<Vector3f> =
        listOf(min.x, max.x).flatMap { x -> listOf(min.y, max.y).flatMap { y -> listOf(min.z, max.z).map { z -> Vector3f(x, y, z) } } }

    /** Joins consecutive segments that continue in the same direction, so flat runs become one piece. */
    private fun mergeCollinear(segments: List<Pair<Vector2f, Vector2f>>): List<Pair<Vector2f, Vector2f>> {
        val merged = ArrayList<Pair<Vector2f, Vector2f>>()
        for (seg in segments) {
            val last = merged.lastOrNull()
            if (last != null && last.second.distance(seg.first) < 1e-6f &&
                direction(last).dot(direction(seg)) > 1f - 1e-5f
            ) {
                merged[merged.size - 1] = last.first to seg.second
            } else {
                merged += seg
            }
        }
        return merged
    }

    private fun direction(seg: Pair<Vector2f, Vector2f>) = Vector2f(seg.second).sub(seg.first).normalize()
}
