package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SceneManager
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.game.skateboard.SkateboardPhysics
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertTrue
import kotlin.test.fail

class SkateboardStressTest {

    private val engine = mockk<Engine>()
    private val sceneManager = mockk<SceneManager>()
    private lateinit var physics: Physics3D

    @BeforeEach
    fun setup() {
        startKoin {
            modules(module {
                single<Engine> { engine }
                single<SceneManager> { sceneManager }
            })
        }
        physics = Physics3D()

        every { engine.runtimePlaying } returns true
        val mockScene = mockk<Scene>()
        every { sceneManager.currentScene } returns mockScene
        every { mockScene.physics3d } returns physics
    }

    @AfterEach
    fun teardown() {
        physics.destroy()
        stopKoin()
        unmockkAll()
    }

    @Test
    fun `highSpeedStability_50mps_noTunnelingThroughFloor`() {
        // Arrange
        val ground = GameObject("Ground")
        val groundTransform = Transform()
        ground.addComponent(groundTransform)
        ground.addComponent(RigidBody3D(0f).apply { bodyType = BodyType.Static })
        ground.addComponent(BoxCollider3D(Vector3f(500f, 1f, 500f))) // Large ground
        groundTransform.translation.set(0f, -1.0f, 0f) // Surface at -0.5
        physics.add(ground)

        val skateGo = GameObject("Skateboard")
        val skateTransform = Transform()
        skateGo.addComponent(skateTransform)
        val rb = RigidBody3D(2.0f).apply {
            useCCD = true // Critical for high speed
        }
        skateGo.addComponent(rb)
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f)))
        val skatePhysics = SkateboardPhysics()
        skateGo.addComponent(skatePhysics)

        // Start slightly above ground
        skateTransform.translation.set(0f, 0.1f, 0f)
        physics.add(skateGo)
        skatePhysics.start()

        // Set high velocity downwards? Or horizontal?
        // "Verify raycast wheels do not tunnel through the floor" implies suspension test?
        // Let's test horizontal speed over bumps OR vertical drop impact.
        // Task says: "Run the simulation at 50 m/s; verify raycast wheels do not 'tunnel'"
        // Usually refers to CCD checks. Let's do a high speed horizontal move.

        rb.linearVelocity = Vector3f(50f, -5f, 0f) // Fast forward and slightly down

        // Act
        for (i in 0 until 60) {
            skatePhysics.update(1/60f)
            physics.update(1/60f)
            rb.update(1/60f)

            // Check if we fell through (Ground surface at -0.5)
            val pos = skateTransform.translation
            if (pos.y < -2.0f) {
                fail("Tunneling detected! Board position Y: ${pos.y} is well below ground surface.")
            }
        }

        // Assert
        assertTrue(skateTransform.translation.y > -2.0f, "Board should stay above/on ground")
    }

    @Test
    fun `frameRateIndependence_variableDt_displacementMatches`() {
        // Arrange
        // We simulate the Engine's fixed timestep loop.
        // Sim 1: 30 FPS input (dt = 0.0333)
        val pos30 = runSimulationWithAccumulator(1/30f, 60) // 2 seconds total time
        
        // Sim 2: 120 FPS input (dt = 0.00833)
        val pos120 = runSimulationWithAccumulator(1/120f, 240) // 2 seconds total time

        // Assert
        val dist = pos30.distance(pos120)
        
        // With fixed timestep accumulation, the physics steps should be identical (120 steps of 1/60).
        // So results should be identical or extremely close (float error).
        // 0.01f margin is reasonable for float precision.
        assertTrue(dist < 0.01f, "Displacement variance $dist should be negligible with fixed timestep accumulator")
    }
    
    private fun runSimulationWithAccumulator(dt: Float, frames: Int): Vector3f {
        val offset = Vector3f(dt * 1000f, 0f, 0f) // Spacing

        val ground = GameObject("Ground")
        val groundTransform = Transform()
        ground.addComponent(groundTransform)
        ground.addComponent(RigidBody3D(0f).apply { bodyType = BodyType.Static })
        ground.addComponent(BoxCollider3D(Vector3f(500f, 1f, 500f)))
        groundTransform.translation.set(offset).add(0f, -1.0f, 0f)
        physics.add(ground)

        val skateGo = GameObject("Skateboard")
        val skateTransform = Transform()
        skateGo.addComponent(skateTransform)
        val rb = RigidBody3D(2.0f).apply {
            linearDamping = 0.5f
            friction = 0f
        }
        skateGo.addComponent(rb)
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f)))
        val skatePhysics = SkateboardPhysics()
        skateGo.addComponent(skatePhysics)

        skateTransform.translation.set(offset).add(0f, 0.1f, 0f)
        physics.add(skateGo)
        skatePhysics.start()

        rb.linearVelocity = Vector3f(10f, 0f, 0f)

        var accumulator = 0f
        val fixedStep = 1.0f / 60.0f

        for (i in 0 until frames) {
            accumulator += dt
            while (accumulator >= fixedStep) {
                skatePhysics.update(fixedStep)
                physics.update(fixedStep)
                rb.update(fixedStep)
                accumulator -= fixedStep
            }
        }

        return Vector3f(skateTransform.translation).sub(offset)
    }

    @Test
    fun `accessViolation_stressTest_instantiation`() {
        // Repeatedly create and destroy bodies to check JNI stability
        for (i in 0 until 100) {
            val go = GameObject("Stress_$i")
            val rb = RigidBody3D(1.0f)
            go.addComponent(rb)
            go.addComponent(BoxCollider3D(Vector3f(1f, 1f, 1f)))
            
            physics.add(go)
            
            // Simulate a bit
            physics.update(1/60f)
            
            // Remove
            physics.remove(go)
            rb.destroy()
        }
        // If we get here without a JVM crash, we passed.
        assertTrue(true)
    }
}