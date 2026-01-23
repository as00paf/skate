package com.pafoid.skate.engine

import com.pafoid.skate.engine.scenes.components.Component
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.Objects

class Transform(
    val translation: Vector3f = Vector3f(),
    val scale: Vector3f = Vector3f(1f, 1f, 1f),
    val rotation: Vector3f = Vector3f()
): Component() {
    
    fun copy(to: Transform) {
        to.translation.set(this.translation)
        to.scale.set(this.scale)
        to.rotation.set(this.rotation)
    }

    fun copyFrom(from: Transform) {
        this.translation.set(from.translation)
        this.scale.set(from.scale)
        this.rotation.set(from.rotation)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is Transform) return false
        
        return other.translation == this.translation && other.scale == this.scale && other.rotation == this.rotation
    }

    override fun hashCode(): Int {
        return Objects.hash(translation, scale, rotation)
    }
}

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