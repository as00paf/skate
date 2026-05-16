package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.render.data.CameraPreset
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CameraTest {

    @Test
    fun `test camera preset interpolation`() {
        val camera = Camera()
        camera.fov = 45f
        camera.zoom = 1.0f

        val presetLow = CameraPreset(fov = 45f, zoom = 1.0f, offset = Vector3f(0f, 0.5f, 0f))
        val presetHigh = CameraPreset(fov = 60f, zoom = 1.5f, offset = Vector3f(0f, 1.0f, 0f))

        camera.applyPreset(presetLow)
        assertEquals(45f, camera.fov)

        camera.lerpToPreset(presetHigh, 1.0f)

        // After 0.5 seconds, it should be halfway
        camera.update(0.5f)

        // We expect halfway between 45 and 60 (52.5) and 1.0 and 1.5 (1.25)
        assertEquals(52.5f, camera.fov, 0.1f)
        assertEquals(1.25f, camera.zoom, 0.1f)

        // After another 0.5 seconds, it should be at target
        camera.update(0.5f)
        assertEquals(60f, camera.fov, 0.1f)
        assertEquals(1.5f, camera.zoom, 0.1f)
    }
}
