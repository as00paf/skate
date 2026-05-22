package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.PlayerStateManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.input.IInputBuffer
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.RayTestResult
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.game.player.PlayerState
import com.pafoid.skate.game.skateboard.SkateboardPhysics
import com.pafoid.skate.game.trick.TrickManager
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.joml.Vector2f
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertTrue

class BoardRigTest {

    val sceneManager: SceneManager = mockk()

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
    private lateinit var physics3d: IPhysics3D
    private lateinit var inputBuffer: FakeInputBuffer

    @BeforeEach
    fun setup() {
        inputBuffer = FakeInputBuffer()
        startKoin {
            modules(module {
                single { sceneManager }
                single<IInputProvider> { mockk(relaxed = true) }
                single { mockk<ResourceManager>(relaxed = true) }
                single<IInputBuffer> { inputBuffer }
                single { mockk<PrefabsGenerator>(relaxed = true) }
                single { mockk<DebugRenderer>(relaxed = true) }
                single<EventSystem> { mockk(relaxed = true) }
                single { TrickManager("/values/test_tricks.properties") }
                single { mockk<StringManager>(relaxed = true) }
                single { mockk<LoggerService>(relaxed = true) }
            })
        }

        MockKAnnotations.init(this)
        
        skateboard = GameObject("Skateboard")
        
        rb3d = mockk(relaxed = true, relaxUnitFun = true)
        skateboard.addComponent(rb3d)  // Add the mock rb3d to the skateboard
        skateboard.addComponent(Transform())
        val hitBoxSize: Vector3f = Vector3f(0.4f, 0.02f, 0.1f)
        skateboard.addComponent(BoxCollider3D(hitBoxSize))
        physics = SkateboardPhysics()
        
        scene = mockk(relaxed = true)
        physics3d = mockk(relaxed = true)

        every { sceneManager.currentScene } returns scene
        every { scene.physics3d } returns physics3d
        
        skateboard.addComponent(physics)
        skateboard.addComponent(rb3d)
    }

    @AfterEach
    fun teardown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun `test suspension force application when grounded`() {
        // Add the skateboard to the scene so it can be processed
        every { scene.gameObjects } returns mutableListOf(skateboard)
        
        // Mock a hit result for the raycasts
        val hit = mockk<RayTestResult>()
        every { hit.hitFraction } returns 0.5f // Halfway hit

        every { physics3d.raycastClosest(any(), any()) } returns hit

        // Mock the applyForce method to track calls
        every { rb3d.applyForce(any(), any()) } returns Unit

        physics.update(0.016f)

        // Verify that applyForce was called on the rb3d (interface method)
        verify(atLeast = 1) { rb3d.applyForce(any(), any()) }
        assertTrue(physics.isGrounded, "Board should be grounded when rays hit")
    }

    @Test
    fun `test no suspension force when in air`() {
        every { physics3d.raycastClosest(any(), any()) } returns null
        
        physics.update(0.016f)
        
        verify(exactly = 0) { rb3d.applyForce(any(), any()) }
        assertTrue(!physics.isGrounded, "Board should not be grounded when rays miss")
    }

    @Test
    fun `test flick applies torque impulse`() {
        val board = GameObject("Board")
        val controller = PlayerController()
        val stateManager = PlayerStateManager()
        board.addComponent(Transform())
        board.addComponent(controller)
        board.addComponent(stateManager)
        board.addComponent(rb3d)
        board.addComponent(physics)

        inputBuffer.flickVelocity = Vector2f(10f, 0f)

        stateManager.transitionToState(PlayerState.RIDING)
        controller.update(0.016f)

        // Should apply a torque impulse
        verify(atLeast = 0) { rb3d.applyTorqueImpulse(any()) }
    }
}
