package com.pafoid.skate.engine.scenes.components

import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.controls.IInputBuffer
import com.pafoid.skate.engine.physics3d.Physics3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneManager
import io.mockk.*
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
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
    private lateinit var rawBody: PhysicsRigidBody
    private lateinit var scene: Scene
    private lateinit var physics3d: Physics3D

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        
        skateboard = GameObject("Skateboard")
        physics = SkateboardPhysics()
        skateboard.addComponent(physics)
        
        rb3d = mockk(relaxed = true)
        rawBody = mockk(relaxed = true)
        
        scene = mockk(relaxed = true)
        physics3d = mockk(relaxed = true)
        
        mockkObject(SceneManager)
        every { SceneManager.getCurrentScene() } returns scene
        every { scene.physics3d } returns physics3d
        
        // Manual component registration since we use a real GameObject
        // We'll override getComponent to return our mock rb3d
        // Actually, let's just add it normally
        skateboard.addComponent(rb3d)
        every { rb3d.rawBody } returns rawBody
    }

    @Test
    fun `test suspension force application when grounded`() {
        // Mock a hit result for the raycasts
        val hit = mockk<PhysicsRayTestResult>()
        every { hit.hitFraction } returns 0.5f // Halfway hit
        
        every { physics3d.rayTest(any(), any()) } returns listOf(hit)
        
        physics.start()
        physics.update(0.016f)
        
        // Verify that applyForce was called on the raw Bullet body
        verify(atLeast = 1) { rawBody.applyForce(any(), any()) }
        assertTrue(physics.isGrounded, "Board should be grounded when rays hit")
    }

    @Test
    fun `test no suspension force when in air`() {
        every { physics3d.rayTest(any(), any()) } returns emptyList()
        
        physics.start()
        physics.update(0.016f)
        
        verify(exactly = 0) { rawBody.applyForce(any(), any()) }
        assertTrue(!physics.isGrounded, "Board should not be grounded when rays miss")
    }

    @Test
    fun `test flick applies torque impulse`() {
        val board = GameObject("Board")
        val controller = PlayerController()
        board.addComponent(controller)
        board.addComponent(rb3d)
        board.addComponent(physics)
        
        val fakeBuffer = FakeInputBuffer()
        fakeBuffer.flickVelocity = Vector2f(10f, 0f)
        controller.inputBuffer = fakeBuffer
        
        controller.start()
        controller.update(0.016f)
        
        // Should apply a torque impulse
        verify(atLeast = 1) { rb3d.applyTorqueImpulse(any()) }
    }
}
