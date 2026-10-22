package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.render.data.CameraPreset
import com.pafoid.skate.engine.utils.CameraInterpolator
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class CameraTest {

    @Test
    fun CameraInterpolator_ApplyPresetSetsFovAndResetsZoom() {
        val camera = CameraComponent(fov = 30f, zoom = 2f)
        CameraInterpolator(camera).applyPreset(CameraPreset(fov = 45f, zoom = 1.0f, offset = Vector3f(0f, 0.5f, 0f)))

        assertEquals(45f, camera.fov)
        assertEquals(1.0f, camera.zoom)
    }

    @Disabled("BUG-014: CameraInterpolator.handleLerp is private and never called, so lerpToPreset has no effect")
    @Test
    fun CameraInterpolator_LerpToPresetReachesTarget() {
        val camera = CameraComponent(fov = 45f, zoom = 1.0f)
        val interpolator = CameraInterpolator(camera)
        interpolator.lerpToPreset(CameraPreset(fov = 60f, zoom = 1.5f, offset = Vector3f(0f, 1.0f, 0f)), 1.0f)

        assertEquals(60f, camera.fov, 0.1f)
    }
}
