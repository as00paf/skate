package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.utils.toDegrees
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.asin
import kotlin.math.atan2

@Serializable
data class CameraComponent(
    @Contextual val position: Vector3f = Vector3f(),
    var pitch: Float = 0f,
    var yaw: Float = 0f,
    var roll: Float = 0f,
    var isOrthographic: Boolean = false,
    var fov: Float = 45f,
    var nearPlane: Float = 0.1f,
    var farPlane: Float = 1000f,
    var zoom: Float = 1.0f,
    var isDefault: Boolean = false
) : Component() {

    @Transient
    var viewportWidth: Int = 1920
        set(value) {
            field = value
            updateAspectRatio()
        }

    @Transient
    var viewportHeight: Int = 1080
        set(value) {
            field = value
            updateAspectRatio()
        }

    @Transient
    var aspectRatio: Float = 16f / 9f

    @Transient
    val camForward = Vector3f(0f, 0f, -1f)
    @Transient
    val camRight = Vector3f(1f, 0f, 0f)
    @Transient
    val projection = Matrix4f()
    @Transient
    val inverseProjection = Matrix4f()
    @Transient
    val view = Matrix4f()
    @Transient
    val inverseView = Matrix4f()

    fun lookAt(target: Vector3f) {
        val dir = Vector3f(target).sub(position).normalize()
        pitch = asin(-dir.y.toDouble()).toDegrees().toFloat()
        yaw = atan2(dir.x.toDouble(), -dir.z.toDouble()).toDegrees().toFloat()
    }

    private fun updateAspectRatio() {
        aspectRatio = if (viewportHeight > 0) {
            viewportWidth.toFloat() / viewportHeight.toFloat()
        } else {
            16f / 9f // Fallback to 16:9 if height is 0
        }
    }

}