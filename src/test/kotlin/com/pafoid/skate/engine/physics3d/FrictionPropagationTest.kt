package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.components.Transform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import com.pafoid.skate.engine.render.DebugDraw
import io.mockk.mockk
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach

class FrictionPropagationTest {

    companion object {
        private lateinit var physics: Physics3D

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            startKoin {
                modules(module {
                    single { mockk<DebugDraw>(relaxed = true) }
                })
            }
            physics = Physics3D()
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
        rb.friction = 0.75f
        go.addComponent(rb)
        
        physics.add(go)
        
        assertEquals(0.75f, rb.rawBody?.friction)
    }

    @Test
    fun `friction should propagate to rawBody on runtime update`() {
        val go = GameObject("TestObject2")
        val rb = RigidBody3D()
        go.addComponent(rb)
        
        physics.add(go)
        
        rb.friction = 0.88f
        
        assertEquals(0.88f, rb.rawBody?.friction)
    }

    @Test
    fun `damping should propagate to rawBody on initialization`() {
        val go = GameObject("TestObject3")
        val rb = RigidBody3D()
        rb.linearDamping = 0.1f
        rb.angularDamping = 0.2f
        go.addComponent(rb)
        
        physics.add(go)
        
        assertEquals(0.1f, rb.rawBody?.linearDamping)
        assertEquals(0.2f, rb.rawBody?.angularDamping)
    }
}
