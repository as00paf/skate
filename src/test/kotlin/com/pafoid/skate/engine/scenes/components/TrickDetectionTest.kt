package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.controls.input.IInputBuffer
import com.pafoid.skate.engine.controls.input.IInputProvider
import com.pafoid.skate.engine.physics3d.Physics3D
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.prefabs.PrefabsGenerator
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.utils.TrickManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.joml.Vector3f
import org.junit.jupiter.api.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals

class TrickDetectionTest {

    companion object {
        private lateinit var physics: Physics3D
    }

    private lateinit var skateboard: GameObject
    private lateinit var rb: RigidBody3D
    private lateinit var skatePhysics: SkateboardPhysics
    private lateinit var trickDetector: TrickDetector
    private val sceneManager = mockk<SceneManager>()

    @BeforeEach
    fun setup() {
        startKoin {
            modules(module {
                single { sceneManager }
                single<IInputProvider> { mockk(relaxed = true) }
                single { mockk<ResourceManager>(relaxed = true) }
                single { mockk<IInputBuffer>(relaxed = true) }
                single { mockk<PrefabsGenerator>(relaxed = true) }
                single { mockk<DebugDraw>(relaxed = true) }
                single { TrickManager("/values/test_tricks.properties") }
            })
        }
        physics = Physics3D()

        every { sceneManager.runtimePlaying } returns true
        
        val mockScene = mockk<Scene>()
        every { sceneManager.currentScene } returns mockScene
        every { mockScene.physics3d } returns physics

        skateboard = GameObject("Skateboard")
        rb = RigidBody3D(2.0f)
        skatePhysics = SkateboardPhysics()
        trickDetector = TrickDetector()

        skateboard.addComponent(rb)
        skateboard.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f)))
        skateboard.addComponent(skatePhysics)
        skateboard.addComponent(trickDetector)

        physics.add(skateboard)
        skatePhysics.start()
        trickDetector.start()
    }

    @AfterEach
    fun teardown() {
        physics.destroy()
        stopKoin()
        unmockkAll()
    }

    @Test
    fun `detect360ShoveIt_rotate360OnY_returns360ShoveIt`() {
        // Arrange
        // Move to air
        skateboard.transform.translation.set(0f, 5f, 0f)
        rb.update(0f)
        physics.update(1/60f)
        
        // Spin on Y (Yaw)
        // 360 degrees per second -> 6 deg per frame at 60fps
        rb.angularVelocity = Vector3f(0f, Math.toRadians(360.0).toFloat(), 0f)

        // Act
        // Run for 1.1 seconds to be sure we hit 360
        for (i in 0 until 66) {
            skatePhysics.update(1/60f)
            physics.update(1/60f)
            rb.update(1/60f)
            trickDetector.update(1/60f)
        }

        // Assert
        assertEquals("360 Shove-it", trickDetector.getDetectedTrick())
    }

    @Test
    fun `detectKickflip_rotate360OnX_returnsKickflip`() {
        // Arrange
        // Move to air
        skateboard.transform.translation.set(0f, 5f, 0f)
        rb.update(0f)
        physics.update(1/60f)
        
        // Spin on X (Roll)
        rb.angularVelocity = Vector3f(Math.toRadians(360.0).toFloat(), 0f, 0f)

        // Act
        for (i in 0 until 66) {
            skatePhysics.update(1/60f)
            physics.update(1/60f)
            rb.update(1/60f)
            trickDetector.update(1/60f)
        }

        // Assert
        assertEquals("Kickflip", trickDetector.getDetectedTrick())
    }

    @Test
    fun `detectFakieOllie_movingBackwardsAndPopping_identifiesAsFakieOllie`() {
        // Arrange
        val controller = PlayerController()
        skateboard.addComponent(controller)
        controller.start()
        
        // Move backwards (X is forward, so -X velocity)
        rb.linearVelocity = Vector3f(-5f, 0f, 0f)
        controller.update(0f)
        
        // Pop (simulate jumping)
        // TrickDetector doesn't currently use stance or velocity to identify Fakie vs Nollie
        
        // Act
        // Simulate a small pop/jump
        rb.applyImpulse(Vector3f(0f, 10f, 0f))
        
        for (i in 0 until 10) {
            skatePhysics.update(1/60f)
            physics.update(1/60f)
            rb.update(1/60f)
            trickDetector.update(1/60f)
            controller.update(1/60f)
        }

        // Assert
        val trick = trickDetector.getDetectedTrick() ?: "Ollie"
        // If moving backwards and popped, it's a Fakie Ollie
        assertEquals("Fakie Ollie", trick)
    }
}