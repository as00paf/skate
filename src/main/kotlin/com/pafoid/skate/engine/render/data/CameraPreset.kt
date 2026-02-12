package com.pafoid.skate.engine.render.data

import org.joml.Vector3f

data class CameraPreset(
    val fov: Float,
    val distance: Float,
    val offset: Vector3f
) {
    companion object {
        val LOW = CameraPreset(fov = 45f, distance = 3.0f, offset = Vector3f(0f, 0.4f, 0f))
        val HIGH = CameraPreset(fov = 50f, distance = 4.5f, offset = Vector3f(0f, 0.8f, 0f))
        val WIDE = CameraPreset(fov = 70f, distance = 5.0f, offset = Vector3f(0f, 0.6f, 0f))
    }
}