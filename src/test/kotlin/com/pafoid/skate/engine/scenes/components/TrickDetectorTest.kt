package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import io.mockk.every
import io.mockk.mockk
import com.pafoid.skate.engine.utils.TrickManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
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
        mockGameObject = mockk(relaxed = true)
        mockSkateboardPhysics = mockk(relaxed = true)
        mockRigidBody = mockk(relaxed = true)

        startKoin {
            modules(module {
                single { TrickManager("/values/test_tricks.properties") }
            })
        }

        every { mockGameObject.getComponent(SkateboardPhysics::class.java) } returns mockSkateboardPhysics
        every { mockGameObject.getComponent(RigidBody3D::class.java) } returns mockRigidBody
        every { mockGameObject.getComponent(PlayerController::class.java) } returns null

        trickDetector = TrickDetector()
        trickDetector.gameObject = mockGameObject
        trickDetector.start()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    //@Test
    //fun `should detect a 360 flip when in air and rotating on X axis`() {
    //    // Known issue with mocking com.jme3.bullet classes - VerifyError
    //    // TODO: Re-enable when mocking issue is resolved or alternative physics mocking is in place.
    //    // Given
    //    // every { mockSkateboardPhysics.isGrounded } returns false
    //    // every { mockRigidBody.angularVelocity } returns Vector3f(Math.toRadians(360.0).toFloat() * 1.5f, 0f, 0f)
    //
    //    // When (simulate enough time for 360 rotation)
    //    // for (i in 0 until 10) {
    //    //     trickDetector.update(0.016f) // ~60 FPS
    //    // }
    //
    //    // Then
    //    // assertEquals("360 Flip", trickDetector.getDetectedTrick())
    //}

    //@Test
    //fun `should detect a kickflip when in air and rotating on X axis`() {
    //    // Known issue with mocking com.jme3.bullet classes - VerifyError
    //    // TODO: Re-enable when mocking issue is resolved or alternative physics mocking is in place.
    //    // Given
    //    every { mockSkateboardPhysics.isGrounded } returns false
    //    every { mockRigidBody.angularVelocity } returns Vector3f(Math.toRadians(360.0).toFloat() * 1.5f, 0f, 0f) 
    //
    //    // When
    //    for (i in 0 until 10) {
    //        trickDetector.update(0.016f)
    //    }
    //
    //    // Then
    //    assertEquals("Kickflip", trickDetector.getDetectedTrick())
    //}

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

    //@Test
    //fun `should not detect a trick when grounded`() {
    //    // Known issue with mocking com.jme3.bullet classes - VerifyError
    //    // TODO: Re-enable when mocking issue is resolved or alternative physics mocking is in place.
    //    // Given
    //    // every { mockSkateboardPhysics.isGrounded } returns true
    //    // every { mockRigidBody.angularVelocity } returns Vector3f(Math.toRadians(360.0).toFloat(), 0f, 0f)
    //
    //    // When
    //    // trickDetector.update(1.0f)
    //
    //    // Then
    //    // assertNull(trickDetector.getDetectedTrick())
    //}

    //@Test
    //fun `should reset trick detection when landing`() {
    //    // Known issue with mocking com.jme3.bullet classes - VerifyError
    //    // TODO: Re-enable when mocking issue is resolved or alternative physics mocking is in place.
    //    // Given
    //    // every { mockSkateboardPhysics.isGrounded } returns false
    //    // every { mockRigidBody.angularVelocity } returns Vector3f(Math.toRadians(360.0).toFloat() * 2, 0f, 0f)
    //
    //    // When (detect trick, then land)
    //    // for (i in 0 until 10) {
    //    //     trickDetector.update(0.016f)
    //    // }
    //
    //    // Now, land
    //    // every { mockSkateboardPhysics.isGrounded } returns true
    //    // trickDetector.update(0.016f)
    //
    //    // Then
    //    // assertNull(trickDetector.getDetectedTrick())
    //}
}
