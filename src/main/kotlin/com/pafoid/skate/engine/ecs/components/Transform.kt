package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.editor.imgui.MImGui
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.*

@Serializable
class Transform(
    @Contextual val translation: Vector3f = Vector3f(),
    @Contextual val scale: Vector3f = Vector3f(1f, 1f, 1f),
    @Contextual val rotation: Vector3f = Vector3f()
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

    override fun imgui() {
        MImGui.drawVec3TransformControl("Position", translation)
        MImGui.drawVec3TransformControl("Rotation", rotation, 0f, MImGui.SENSIBILITY_ROTATION)
        MImGui.drawVec3TransformControl("Scale", scale, 1f, MImGui.SENSIBILITY_SCALE, true)
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

fun Transform.toWorldMatrix(): Matrix4f {
    val worldMatrix = toMatrix()
    val parent = gameObject.parent
    if (parent != null) {
        val parentTransform = parent.getComponent<Transform>()
        val parentMatrix = parentTransform?.toWorldMatrix() ?: Matrix4f().identity() // fallback for backward compatibility
        parentMatrix.mul(worldMatrix, worldMatrix)
    }
    return worldMatrix
}