package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import io.mockk.mockk
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class FrictionPropagationTest {

    companion object {
        private lateinit var physics: BulletPhysics3D

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            startKoin {
                modules(module {
                    single { mockk<com.pafoid.skate.engine.render.renderer.DebugRenderer>(relaxed = true) }
                    single { mockk<StringManager>(relaxed = true) }
                    single { mockk<LoggerService>(relaxed = true) }
                })
            }
            physics = BulletPhysics3D()
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            stopKoin()
        }
    }

    @Test
    fun `friction should propagate to rawBody on initialization`() {
        val go = GameObject("TestObject1")
        val rb = RigidBody3D()
        go.addComponent(rb)
        
        // Add a transform component which is required for syncBodyProperties
        val transform = Transform()
        go.addComponent(transform)
        
        // Add a collider to ensure rawBody gets created
        val collider = BoxCollider3D()
        go.addComponent(collider)

        // Set friction after components are added but before adding to physics
        rb.friction = 0.75f
        
        physics.add(go)
        
        // Update the game object to ensure rawBody is initialized
        physics.update(go)

        // The rawBody should now be initialized after adding to physics
        assertNotNull(rb.rawBody, "Raw body should be initialized after adding to physics")
        assertEquals(0.75f, rb.rawBody?.friction ?: 0f, 0.001f)
    }

    @Test
    fun `friction should propagate to rawBody on runtime update`() {
        val go = GameObject("TestObject2")
        val rb = RigidBody3D()
        go.addComponent(rb)

        physics.add(go)
        
        // Update the game object to ensure rawBody is initialized
        physics.update(go)

        rb.friction = 0.88f

        assertEquals(0.88f, rb.rawBody?.friction)
    }

    @Test
    fun `damping should propagate to rawBody on initialization`() {
        val go = GameObject("TestObject3")
        val rb = RigidBody3D()
        go.addComponent(rb)
        
        // Add a transform component which is required for syncBodyProperties
        val transform = Transform()
        go.addComponent(transform)
        
        // Add a collider to ensure rawBody gets created
        val collider = BoxCollider3D()
        go.addComponent(collider)

        // Set damping after components are added but before adding to physics
        rb.linearDamping = 0.1f
        rb.angularDamping = 0.2f

        physics.add(go)
        
        // Update the game object to ensure rawBody is initialized
        physics.update(go)

        // The rawBody should now be initialized after adding to physics
        assertNotNull(rb.rawBody, "Raw body should be initialized after adding to physics")
        assertEquals(0.1f, rb.rawBody?.linearDamping ?: 0f, 0.001f)
        assertEquals(0.2f, rb.rawBody?.angularDamping ?: 0f, 0.001f)
    }
}
