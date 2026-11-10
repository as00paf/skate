package com.pafoid.skate.engine.animation.edit

import org.joml.Quaternionf
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.sqrt

/** Small quaternion helpers on `[x, y, z, w]` float rows. */
internal object QuatMath {
    fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]

    /** Negates rows in place so each is in the same hemisphere as the previous one (q and -q are the same rotation). */
    fun makeContinuous(rows: Array<FloatArray>) {
        for (i in 1 until rows.size) if (dot(rows[i - 1], rows[i]) < 0f) for (k in 0 until 4) rows[i][k] = -rows[i][k]
    }

    fun normalize(q: FloatArray): FloatArray {
        val len = sqrt(dot(q, q))
        if (len < 1e-8f) { q[0] = 0f; q[1] = 0f; q[2] = 0f; q[3] = 1f } else for (k in 0 until 4) q[k] /= len
        return q
    }

    /** Angle between two rotations in degrees. */
    fun angleDeg(a: FloatArray, b: FloatArray): Float {
        val la = sqrt(dot(a, a)); val lb = sqrt(dot(b, b))
        if (la < 1e-8f || lb < 1e-8f) return 0f
        val d = (abs(dot(a, b)) / (la * lb)).coerceIn(0f, 1f)
        return Math.toDegrees(2.0 * acos(d.toDouble())).toFloat()
    }

    fun toQuat(row: FloatArray, out: Quaternionf = Quaternionf()): Quaternionf = out.set(row[0], row[1], row[2], row[3])
    fun toRow(q: Quaternionf) = floatArrayOf(q.x, q.y, q.z, q.w)
}
