package com.pafoid.skate.engine

import com.pafoid.skate.engine.scenes.components.Component
import org.joml.Matrix4f
import org.joml.Vector3f

class Transform(
    val translation: Vector3f = Vector3f(),
    val scale: Vector3f = Vector3f(1f, 1f, 1f),
    val rotation: Vector3f = Vector3f()
): Component()

fun Transform.toMatrix(): Matrix4f {
    val matrix = Matrix4f()
    matrix.identity()
    matrix.translate(translation)
    matrix.rotate(Math.toRadians(rotation.x.toDouble()).toFloat(), Vector3f(1f, 0f, 0f))
    matrix.rotate(Math.toRadians(rotation.y.toDouble()).toFloat(), Vector3f(0f, 1f, 0f))
    matrix.rotate(Math.toRadians(rotation.z.toDouble()).toFloat(), Vector3f(0f, 0f, 1f))
    matrix.scale(scale)
    return matrix
}