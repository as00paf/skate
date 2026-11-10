package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.round

/**
 * Euler-degree view of a rotation track for the curve editor (project convention: XYZ order, degrees).
 * Rotations are stored as quaternions; the editor edits these curves and writes them back with [toTrack].
 */
class EulerCurves(val times: FloatArray, val x: FloatArray, val y: FloatArray, val z: FloatArray) {
    val size: Int get() = times.size

    fun at(i: Int) = Vector3f(x[i], y[i], z[i])

    companion object {
        /**
         * Euler angles of each rotation key. Angles are unwrapped so the curves are continuous: each key picks, among
         * the equivalent Euler triples (±360° per axis and the (x+180, 180-y, z+180) twin), the one closest to the
         * previous key.
         */
        fun fromTrack(track: SkTrack): EulerCurves {
            require(track.path == AnimationPath.ROTATION) { "Euler curves need a rotation track" }
            val n = track.keys.size
            val xs = FloatArray(n); val ys = FloatArray(n); val zs = FloatArray(n)
            var prev: Vector3f? = null
            val q = Quaternionf()
            for (i in 0 until n) {
                QuatMath.toQuat(track.keys[i].value.toFloatArray(), q).normalize()
                val e = toEulerDeg(q)
                val picked = prev?.let { closest(e, it) } ?: simplest(e)
                xs[i] = picked.x; ys[i] = picked.y; zs[i] = picked.z
                prev = picked
            }
            return EulerCurves(track.times, xs, ys, zs)
        }

        /** Quaternion for XYZ Euler degrees (same order as `Quaternionf.rotationXYZ`). */
        fun toQuat(deg: Vector3f, out: Quaternionf = Quaternionf()): Quaternionf =
            out.rotationXYZ(Math.toRadians(deg.x.toDouble()).toFloat(), Math.toRadians(deg.y.toDouble()).toFloat(), Math.toRadians(deg.z.toDouble()).toFloat())

        fun toEulerDeg(q: Quaternionf): Vector3f {
            val r = q.getEulerAnglesXYZ(Vector3f())
            return Vector3f(Math.toDegrees(r.x.toDouble()).toFloat(), Math.toDegrees(r.y.toDouble()).toFloat(), Math.toDegrees(r.z.toDouble()).toFloat())
        }

        private fun closest(e: Vector3f, prev: Vector3f): Vector3f {
            val a = unwrap(e, prev)
            val b = unwrap(Vector3f(e.x + 180f, 180f - e.y, e.z + 180f), prev)
            return if (a.distanceSquared(prev) <= b.distanceSquared(prev)) a else b
        }

        /** Of the two equivalent triples, the one with the smallest angles (yaw 150 reads (0, 150, 0), not (180, 30, 180)). */
        private fun simplest(e: Vector3f): Vector3f {
            val twin = Vector3f(e.x + 180f, 180f - e.y, e.z + 180f)
            val a = unwrap(e, Vector3f()); val b = unwrap(twin, Vector3f())
            return if (magnitude(a) <= magnitude(b)) a else b
        }

        private fun magnitude(v: Vector3f) = abs(v.x) + abs(v.y) + abs(v.z)

        private fun unwrap(e: Vector3f, prev: Vector3f) = Vector3f(near(e.x, prev.x), near(e.y, prev.y), near(e.z, prev.z))

        private fun near(angle: Float, ref: Float) = angle + 360f * round((ref - angle) / 360f)
    }

    /** Copy with key [i] set to [deg]. */
    fun withKey(i: Int, deg: Vector3f): EulerCurves =
        EulerCurves(times, x.copyOf().also { it[i] = deg.x }, y.copyOf().also { it[i] = deg.y }, z.copyOf().also { it[i] = deg.z })

    /**
     * Writes the curves back into [track] as quaternion keys (hemisphere-continuous). Key tangent modes are kept;
     * stored FREE tangents are dropped to AUTO because quaternion tangents cannot be derived from Euler handles.
     */
    fun toTrack(track: SkTrack): SkTrack {
        require(track.path == AnimationPath.ROTATION) { "Euler curves need a rotation track" }
        val rows = Array(size) { QuatMath.toRow(toQuat(at(it))) }
        QuatMath.makeContinuous(rows)
        val keys = List(size) { i ->
            val mode = track.keys.getOrNull(i)?.tangentMode?.takeIf { it != TangentMode.FREE } ?: TangentMode.AUTO
            SkKey(times[i], rows[i].toList(), mode)
        }
        return track.copy(keys = keys)
    }
}
