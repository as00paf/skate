package com.pafoid.skate.engine.render.data

import org.joml.Vector3f

/**
 * Camera preset for quick camera configuration changes.
 *
 * @param fov Field of view in degrees
 * @param zoom Zoom level (1.0 = normal, < 1.0 = zoom in, > 1.0 = zoom out)
 * @param offset Camera offset from target position
 */
data class CameraPreset(
    val fov: Float,
    val zoom: Float,
    val offset: Vector3f
) {
    companion object {
        val LOW = CameraPreset(fov = 45f, zoom = 1.0f, offset = Vector3f(0f, 0.4f, 0f))
        val HIGH = CameraPreset(fov = 50f, zoom = 1.0f, offset = Vector3f(0f, 0.8f, 0f))
        val WIDE = CameraPreset(fov = 70f, zoom = 1.0f, offset = Vector3f(0f, 0.6f, 0f))
    }
}