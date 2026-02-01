package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.controls.input.IInputProvider
import com.pafoid.skate.engine.controls.input.InputProvider
import com.pafoid.skate.engine.controls.listeners.KeyListener
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.startKoin

class CameraTest {

    @MockK
    private lateinit var inputProvider: IInputProvider

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        every { inputProvider.isCursorDisabled() } returns false
        every { inputProvider.getAxes(any()) } returns null

        startKoin {
            modules(
                module {
                    single { KeyListener() }
                    single<IInputProvider> { InputProvider(mockk(), get()) }
                }
            )
        }
    }

    @Test
    fun `test camera preset interpolation`() {
        val camera = Camera()
        camera.fov = 45f
        camera.desiredDistance = 5f
        
        val presetLow = CameraPreset(fov = 45f, distance = 5f, offset = Vector3f(0f, 0.5f, 0f))
        val presetHigh = CameraPreset(fov = 60f, distance = 10f, offset = Vector3f(0f, 1.0f, 0f))
        
        camera.applyPreset(presetLow)
        assertEquals(45f, camera.fov)
        
        camera.lerpToPreset(presetHigh, 1.0f)
        
        // After 0.5 seconds, it should be halfway
        camera.update(0.5f)
        
        // We expect halfway between 45 and 60 (52.5) and 5 and 10 (7.5)
        assertEquals(52.5f, camera.fov, 0.1f)
        assertEquals(7.5f, camera.desiredDistance, 0.1f)
        
        // After another 0.5 seconds, it should be at target
        camera.update(0.5f)
        assertEquals(60f, camera.fov, 0.1f)
        assertEquals(10f, camera.desiredDistance, 0.1f)
    }
}
