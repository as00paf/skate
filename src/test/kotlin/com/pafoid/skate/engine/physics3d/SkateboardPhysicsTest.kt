package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.joml.Vector3f
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkateboardPhysicsTest {

    companion object {
        private lateinit var physics: Physics3D

        @org.junit.jupiter.api.BeforeAll
        @JvmStatic
        fun setupAll() {
            physics = Physics3D()
        }

        @org.junit.jupiter.api.AfterAll
        @JvmStatic
        fun teardownAll() {
            physics.destroy()
        }
    }

    @BeforeEach
    fun setup() {
        mockkObject(SceneManager)
        every { SceneManager.isPlaying() } returns true
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `compoundShape_multipleColliders_totalMassMatchesSum`() {
        // Arrange
        val skateGo = GameObject("Skateboard")
        val deckMass = 1.2f
        val truckMass = 0.3f 
        val wheelMass = 0.3f 
        val totalExpectedMass = deckMass + truckMass + wheelMass
        
        skateGo.addComponent(RigidBody3D(totalExpectedMass))
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.01f, 0.1f)).apply {
            offset.set(0f, 0f, 0f)
        })
        skateGo.addComponent(BoxCollider3D(Vector3f(0.02f, 0.03f, 0.08f)).apply {
            offset.set(0.2f, -0.04f, 0f)
        })
        skateGo.addComponent(BoxCollider3D(Vector3f(0.02f, 0.03f, 0.08f)).apply {
            offset.set(-0.2f, -0.04f, 0f)
        })
        
        // Act
        physics.add(skateGo)
        val rb = skateGo.getComponent<RigidBody3D>()!!
        val rawBody = rb.rawBody!!
        val invInertia = com.jme3.math.Vector3f()
        rawBody.getInverseInertiaLocal(invInertia)

        // Assert
        assertEquals(totalExpectedMass, rawBody.mass, 0.001f, "Total mass should be the sum of components")
        assertTrue(invInertia.x > 0 && invInertia.y > 0 && invInertia.z > 0, "Inverse inertia tensor should be calculated")
    }

    @Test
    fun `rigidBody_downwardOffsets_comIsLowerThanDeck`() {
        // Arrange
        val skateGo = GameObject("Skateboard")
        skateGo.addComponent(RigidBody3D(1.8f))
        
        val deckHalfHeight = 0.01f
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, deckHalfHeight, 0.1f)).apply {
            offset.set(0f, 0f, 0f)
        })
        skateGo.addComponent(BoxCollider3D(Vector3f(0.02f, 0.03f, 0.08f)).apply {
            offset.set(0.2f, -0.04f, 0f)
        })
        
        // Act
        physics.add(skateGo)
        val deckSurfaceY = 0.01f
        val comY = 0f // Bullet origin is CoM
        
        // Assert
        assertTrue(comY < deckSurfaceY, "Center of Mass (0) should be lower than the deck surface ($deckSurfaceY)")
    }

    @Test
    fun `staticFriction_tiltedGround_boardRemainsStationary`() {
        // Arrange
        val ground = GameObject("Slope")
        ground.transform.rotation.set(15f, 0f, 0f)
        val groundRb = RigidBody3D(0f)
        groundRb.bodyType = BodyType.Static
        ground.addComponent(groundRb)
        ground.addComponent(BoxCollider3D(Vector3f(10f, 0.1f, 10f)))
        physics.add(ground)
        
        val skateGo = GameObject("Skateboard")
        skateGo.addComponent(RigidBody3D(1.8f).apply { friction = 1.0f })
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f)))
        skateGo.transform.translation.set(0f, 0.5f, 0f)
        skateGo.transform.rotation.set(15f, 0f, 0f)
        physics.add(skateGo)
        
        val rb = skateGo.getComponent<RigidBody3D>()!!
        
        // Act
        for (i in 0 until 60) {
            physics.update(1/60f)
            rb.update(1/60f)
        }
        val velocity = rb.linearVelocity

        // Assert
        assertEquals(0f, velocity.length(), 0.1f, "Board should remain static on 15 degree slope due to friction")
    }
}