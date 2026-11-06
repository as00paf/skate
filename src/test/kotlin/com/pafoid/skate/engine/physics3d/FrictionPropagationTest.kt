package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class FrictionPropagationTest {

    private lateinit var physics: IPhysics3D

    private fun use(backend: PhysicsBackend) {
        physics = PhysicsBackends.create(backend)
    }

    @AfterEach
    fun tearDown() {
        if (::physics.isInitialized) physics.destroy()
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#core")
    fun `friction should propagate to rawBody on initialization`(backend: PhysicsBackend) {
        use(backend)
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
        physics.step(1f / 60f)

        // The rawBody should now be initialized after adding to physics
        assertNotNull(rb.rawBody, "Raw body should be initialized after adding to physics")
        assertEquals(0.75f, rb.rawBody?.friction ?: 0f, 0.001f)
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#core")
    fun `friction should propagate to rawBody on runtime update`(backend: PhysicsBackend) {
        use(backend)
        val go = GameObject("TestObject2")
        val rb = RigidBody3D()
        go.addComponent(rb)

        physics.add(go)
        
        // Update the game object to ensure rawBody is initialized
        physics.step(1f / 60f)

        rb.friction = 0.88f

        assertEquals(0.88f, rb.rawBody?.friction)
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#core")
    fun `damping should propagate to rawBody on initialization`(backend: PhysicsBackend) {
        use(backend)
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
        physics.step(1f / 60f)

        // The rawBody should now be initialized after adding to physics
        assertNotNull(rb.rawBody, "Raw body should be initialized after adding to physics")
        assertEquals(0.1f, rb.rawBody?.linearDamping ?: 0f, 0.001f)
        assertEquals(0.2f, rb.rawBody?.angularDamping ?: 0f, 0.001f)
    }
}
