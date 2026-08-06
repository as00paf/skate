package com.pafoid.skate.engine.utils

import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.render.data.CameraPreset

class CameraInterpolator(private val camera: CameraComponent) {

    // Interpolation
    private var targetPreset: CameraPreset? = null
    private var lerpTime = 0f
    private var lerpDuration = 0f
    private var startFov = 0f
    private var startDistance = 0f

    fun applyPreset(preset: CameraPreset) {
        targetPreset = null
        camera.fov = preset.fov
        camera.zoom = 1.0f
    }

    fun lerpToPreset(preset: CameraPreset, duration: Float) {
        targetPreset = preset
        lerpDuration = duration
        lerpTime = 0f
        startFov = camera.fov
        startDistance = camera.zoom
    }

    private fun handleLerp(dt: Float) {
        val target = targetPreset ?: return
        lerpTime += dt
        val t = (lerpTime / lerpDuration).coerceIn(0f, 1f)

        camera.fov = Interpolator.lerp(startFov, target.fov, t)
        camera.zoom = Interpolator.lerp(startDistance, target.zoom, t)

        if (t >= 1f) {
            targetPreset = null
        }
    }
}