package com.pafoid.skate.engine.physics3d.bullet

import com.jme3.math.Matrix3f
import com.jme3.math.Quaternion
import org.joml.Quaternionf
import org.joml.Vector3f

typealias JmeVector3f = com.jme3.math.Vector3f

fun JmeVector3f.toVector3f(): Vector3f = Vector3f(x, y, z)

fun Vector3f.toJme(): JmeVector3f = JmeVector3f(x, y, z)

fun Quaternion.toQuaternionf(): Quaternionf = Quaternionf(x, y, z, w)

fun Quaternionf.toQuaternion(): Quaternion = Quaternion(x, y, z, w)

fun org.joml.Matrix3f.toJme(): Matrix3f {
    val m = Matrix3f()
    // JOML fields are named m{column}{row}, jme's set takes (row, column)
    m.set(0, 0, m00); m.set(0, 1, m10); m.set(0, 2, m20)
    m.set(1, 0, m01); m.set(1, 1, m11); m.set(1, 2, m21)
    m.set(2, 0, m02); m.set(2, 1, m12); m.set(2, 2, m22)
    return m
}
