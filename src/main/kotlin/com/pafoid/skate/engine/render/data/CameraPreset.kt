package com.pafoid.skate.engine.render.data

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class CameraPreset(
    val fov: Float,
    val zoom: Float,
    @Contextual val offset: Vector3f
) {
    companion object {
        val LOW = CameraPreset(fov = 45f, zoom = 1.0f, offset = Vector3f(0f, 0.4f, 0f))
        val HIGH = CameraPreset(fov = 50f, zoom = 1.0f, offset = Vector3f(0f, 0.8f, 0f))
        val WIDE = CameraPreset(fov = 70f, zoom = 1.0f, offset = Vector3f(0f, 0.6f, 0f))
    }
}