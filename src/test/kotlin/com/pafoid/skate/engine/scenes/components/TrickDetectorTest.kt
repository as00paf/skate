package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.game.skateboard.SkateboardPhysics
import com.pafoid.skate.game.trick.TrickDetector
import com.pafoid.skate.game.trick.TrickManager
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class TrickDetectorTest {

    private lateinit var trickDetector: TrickDetector
    private lateinit var mockGameObject: GameObject
    private lateinit var mockSkateboardPhysics: SkateboardPhysics
    private lateinit var mockRigidBody: RigidBody3D

    @BeforeEach
    fun setUp() {
        mockGameObject = GameObject("test")
        mockSkateboardPhysics = mockk(relaxed = true)
        mockRigidBody = mockk(relaxed = true)
        mockGameObject.addComponent(mockSkateboardPhysics)
        mockGameObject.addComponent(mockRigidBody)

        startKoin {
            modules(module {
                single { TrickManager("/values/test_tricks.properties") }
            })
        }

        trickDetector = TrickDetector()
        trickDetector.gameObject = mockGameObject
        trickDetector.start()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `should detect a kickflip when in air and rotating on X axis`() {
        // Given
        every { mockSkateboardPhysics.isGrounded } returns false
        every { mockRigidBody.angularVelocity } returns Vector3f(Math.toRadians(360.0).toFloat() * 1.5f, 0f, 0f) 

        // When
        for (i in 0 until 50) {
            trickDetector.update(0.016f)
        }

        // Then
        assertEquals("Kickflip", trickDetector.getDetectedTrick())
    }

    @Test
    fun `should detect a heelflip when in air and rotating on negative X axis`() {
        // Given
        trickDetector.accumulatedRotationX = -360f

        // When
        trickDetector.detectTrick()

        // Then
        assertEquals("Heelflip", trickDetector.getDetectedTrick())
    }

    @Test
    fun `should detect a pop shuvit when in air and rotating on Y axis`() {
        // Given
        trickDetector.accumulatedRotationY = 180f

        // When
        trickDetector.detectTrick()

        // Then
        assertEquals("Shove-it", trickDetector.getDetectedTrick())
    }

    @Test
    fun `should detect a 360 pop shuvit when in air and rotating on Y axis`() {
        // Given
        trickDetector.accumulatedRotationY = 360f

        // When
        trickDetector.detectTrick()

        // Then
        assertEquals("360 Shove-it", trickDetector.getDetectedTrick())
    }

    @Test
    fun `should not detect a trick when grounded`() {
        // Given
        every { mockSkateboardPhysics.isGrounded } returns true
        every { mockRigidBody.angularVelocity } returns Vector3f(Math.toRadians(360.0).toFloat(), 0f, 0f)

        // When
        trickDetector.update(1.0f)

        // Then
        assertNull(trickDetector.getDetectedTrick())
    }

    @Test
    fun `should reset trick detection when landing`() {
        // Given
        every { mockSkateboardPhysics.isGrounded } returns false
        every { mockRigidBody.angularVelocity } returns Vector3f(Math.toRadians(360.0).toFloat() * 2, 0f, 0f)

        // When (detect trick, then land)
        for (i in 0 until 50) {
            trickDetector.update(0.016f)
        }

        // Now, land
        every { mockSkateboardPhysics.isGrounded } returns true
        trickDetector.update(0.016f)

        // Then
        assertNull(trickDetector.getDetectedTrick())
    }
}
