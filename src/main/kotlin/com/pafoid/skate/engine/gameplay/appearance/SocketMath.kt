package com.pafoid.skate.engine.gameplay.appearance

import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

/** Pure transform math for rigid attachments (GPL-24 §6.2). Rotations are Euler XYZ degrees, as in `Transform`. */
object SocketMath {

    /** Translation, then X·Y·Z rotation, then scale: the same order `TransformSystem` builds local matrices in. */
    fun trs(translation: Vector3f, rotationDeg: Vector3f, scale: Vector3f, out: Matrix4f): Matrix4f =
        out.translation(translation)
            .rotateXYZ(Math.toRadians(rotationDeg.x.toDouble()).toFloat(), Math.toRadians(rotationDeg.y.toDouble()).toFloat(), Math.toRadians(rotationDeg.z.toDouble()).toFloat())
            .scale(scale)

    /**
     * Model-space matrix of an attachment: the bone's model-space [boneGlobal] (`SkeletonPose.globalTransforms`), then
     * the socket offset, then the item's own [offset], [rotationDeg] and [scale].
     */
    fun attachment(
        boneGlobal: Matrix4f,
        socket: Socket,
        offset: Vector3f,
        rotationDeg: Vector3f,
        scale: Vector3f,
        out: Matrix4f = Matrix4f(),
    ): Matrix4f {
        val socketMatrix = trs(socket.offset, socket.rotationDeg, ONE, Matrix4f())
        val item = trs(offset, rotationDeg, scale, Matrix4f())
        return out.set(boneGlobal).mul(socketMatrix).mul(item)
    }

    /** Splits [m] into a `Transform`'s translation, Euler XYZ rotation in degrees and scale. */
    fun decompose(m: Matrix4f, translation: Vector3f, rotationDeg: Vector3f, scale: Vector3f) {
        m.getTranslation(translation)
        m.getScale(scale)
        // R = Rx(a)·Ry(b)·Rz(c): row 0 is (cb·cc, −cb·sc, sb), row 1 col 2 is −sa·cb, row 2 col 2 is ca·cb.
        // Columns are divided by their scale (JOML's getNormalizedRotation is wrong for non-uniform scale); mCR = column C, row R.
        val sx = if (scale.x != 0f) scale.x else 1f
        val sy = if (scale.y != 0f) scale.y else 1f
        val sz = if (scale.z != 0f) scale.z else 1f
        val r02 = (m.m20() / sz).coerceIn(-1f, 1f)
        val b = asin(r02)
        val a: Float
        val c: Float
        if (abs(r02) < 0.9999f) {
            a = atan2(-m.m21() / sz, m.m22() / sz)
            c = atan2(-m.m10() / sy, m.m00() / sx)
        } else {
            // Gimbal lock: only a ± c is defined; put it all in a
            a = atan2(m.m12() / sy, m.m11() / sy)
            c = 0f
        }
        rotationDeg.set(Math.toDegrees(a.toDouble()).toFloat(), Math.toDegrees(b.toDouble()).toFloat(), Math.toDegrees(c.toDouble()).toFloat())
    }

    private val ONE = Vector3f(1f, 1f, 1f)
}
