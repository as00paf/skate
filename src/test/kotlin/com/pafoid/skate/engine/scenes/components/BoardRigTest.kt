package com.pafoid.skate.engine.scenes.components

import com.jme3.bullet.collision.PhysicsRayTestResult
import com.pafoid.skate.engine.controls.IInputBuffer
import com.pafoid.skate.engine.controls.IInputProvider
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneManager
import io.mockk.*
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class BoardRigTest {

    class FakeInputBuffer : IInputBuffer {
        var flickVelocity = Vector2f(0f, 0f)
        override fun push(timestamp: Float, mousePos: Vector2f, joystickAxes: FloatArray?) {}
        override fun getFlickVelocity(timeWindow: Float): Vector2f = flickVelocity
        override fun getJoystickFlickVelocity(jid: Int, timeWindow: Float): Vector2f = flickVelocity
        override fun getRightStickFlickVelocity(jid: Int, timeWindow: Float): Vector2f = flickVelocity
    }

    private lateinit var skateboard: GameObject
    private lateinit var physics: SkateboardPhysics
    private lateinit var rb3d: RigidBody3D
    private lateinit var scene: Scene
    private lateinit var physics3d: com.pafoid.skate.engine.physics3d.IPhysics3D

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        
        skateboard = GameObject("Skateboard")
        physics = SkateboardPhysics()
        
        rb3d = mockk(relaxed = true, relaxUnitFun = true)
        
        scene = mockk(relaxed = true)
        physics3d = mockk(relaxed = true)
        
        mockkObject(SceneManager)
        every { SceneManager.getCurrentScene() } returns scene
        every { scene.physics3d } returns physics3d
        
        skateboard.addComponent(physics)
        skateboard.addComponent(rb3d)
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `test suspension force application when grounded`() {
        // Mock a hit result for the raycasts
        val hit = mockk<PhysicsRayTestResult>()
        every { hit.hitFraction } returns 0.5f // Halfway hit
        
        every { physics3d.rayTest(any(), any()) } returns listOf(hit)
        
        physics.start()
        physics.update(0.016f)
        
        // Verify that applyForce was called on the rb3d (interface method)
        verify(atLeast = 1) { rb3d.applyForce(any(), any()) }
        assertTrue(physics.isGrounded, "Board should be grounded when rays hit")
    }

    @Test
    fun `test no suspension force when in air`() {
        every { physics3d.rayTest(any(), any()) } returns emptyList()
        
        physics.start()
        physics.update(0.016f)
        
        verify(exactly = 0) { rb3d.applyForce(any(), any()) }
        assertTrue(!physics.isGrounded, "Board should not be grounded when rays miss")
    }
}
