package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f
import org.joml.Vector3f

@Serializable
data class CameraComponent(
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

    private fun updateAspectRatio() {
        aspectRatio = if (viewportHeight > 0) {
            viewportWidth.toFloat() / viewportHeight.toFloat()
        } else {
            16f / 9f // Fallback to 16:9 if height is 0
        }
    }

}