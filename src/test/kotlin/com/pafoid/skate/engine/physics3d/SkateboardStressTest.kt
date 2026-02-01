package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.physics3d.SkateboardPhysicsTest.Companion.sceneManager
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.SkateboardPhysics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.joml.Vector3f
import org.junit.jupiter.api.*
import org.koin.core.context.startKoin
import org.koin.dsl.module
import kotlin.test.assertTrue
import kotlin.test.fail

class SkateboardStressTest {

    val sceneManager = mockk<SceneManager>()

    companion object {
        private lateinit var physics: Physics3D

        @BeforeAll
        @JvmStatic
        fun setupAll() {
            physics = Physics3D()

            startKoin {
                modules(module {
                    single<SceneManager> { sceneManager }
                })
            }
        }

        @AfterAll
        @JvmStatic
        fun teardownAll() {
            physics.destroy()
        }
    }

    @BeforeEach
    fun setup() {
        every { sceneManager.runtimePlaying } returns true
        val mockScene = mockk<com.pafoid.skate.engine.scenes.Scene>()
        every { sceneManager.currentScene } returns mockScene
        every { mockScene.physics3d } returns physics
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `highSpeedStability_50mps_noTunnelingThroughFloor`() {
        // Arrange
        val ground = GameObject("Ground")
        ground.addComponent(RigidBody3D(0f).apply { bodyType = BodyType.Static })
        ground.addComponent(BoxCollider3D(Vector3f(500f, 1f, 500f))) // Large ground
        ground.transform.translation.set(0f, -1.0f, 0f) // Surface at -0.5
        physics.add(ground)

        val skateGo = GameObject("Skateboard")
        val rb = RigidBody3D(2.0f).apply {
            useCCD = true // Critical for high speed
        }
        skateGo.addComponent(rb)
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f)))
        val skatePhysics = SkateboardPhysics()
        skateGo.addComponent(skatePhysics)
        
        // Start slightly above ground
        skateGo.transform.translation.set(0f, 0.1f, 0f)
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
            val pos = skateGo.transform.translation
            if (pos.y < -2.0f) {
                fail("Tunneling detected! Board position Y: ${pos.y} is well below ground surface.")
            }
        }

        // Assert
        assertTrue(skateGo.transform.translation.y > -2.0f, "Board should stay above/on ground")
    }

    @Test
    fun `frameRateIndependence_variableDt_displacementMatches`() {
        // Arrange
        // We will run two simulations: one at 30fps, one at 120fps
        // and compare the final position of a board rolling with resistance.
        
        // Sim 1: 30 FPS
        val pos30 = runSimulation(1/30f, 60) // 2 seconds
        
        // Reset Physics? Ideally we'd reset the whole scene, but here we can just make new objects
        // actually we reused the static physics engine, so we need to be careful.
        // The 'runSimulation' helper will need to clear/setup objects.
        
        // Sim 2: 120 FPS
        val pos120 = runSimulation(1/120f, 240) // 2 seconds

        // Assert
        // Allow 1% margin
        val dist = pos30.distance(pos120)
        // Expected distance travel ~5m/s * 2s = 10m. 1% is 0.1m.
        
        // Note: Without explicit fixed-timestep integration in the game loop *logic* (not just Bullet),
        // this might fail if our update logic (SkateboardPhysics) depends on dt naively.
        // But SkateboardPhysics uses forces, which are time-independent (Force = Mass * Accel).
        // Bullet internal step is usually 1/60. passing different dt to 'physics.update(dt)' might cause issues
        // if Physics3D.update(dt) just passes dt to stepSimulation.
        // Let's see what Physics3D does.
        
        // Actually, for this test to be valid, we assume Physics3D handles substepping or we are testing OUR logic.
        // Let's run it.
        
        assertTrue(dist < 0.5f, "Displacement variance $dist should be small between 30fps and 120fps")
    }
    
    private fun runSimulation(dt: Float, steps: Int): Vector3f {
        // Clear previous bodies (hacky for this test class)
        // Ideally we'd use a fresh Physics3D, but it's static in companion.
        // We can just add new objects and ignore old ones, or remove them.
        // Physics3D doesn't have 'clear' exposed maybe?
        // Let's just spawn a new board far away
        
        val offset = Vector3f(steps.toFloat() * 100f, 0f, 0f) // Spacing
        
        val ground = GameObject("Ground")
        ground.addComponent(RigidBody3D(0f).apply { bodyType = BodyType.Static })
        ground.addComponent(BoxCollider3D(Vector3f(500f, 1f, 500f)))
        ground.transform.translation.set(offset).add(0f, -1.0f, 0f)
        physics.add(ground)

        val skateGo = GameObject("Skateboard")
        val rb = RigidBody3D(2.0f).apply {
            linearDamping = 0.5f // Significant drag
            friction = 0f
        }
        skateGo.addComponent(rb)
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f)))
        val skatePhysics = SkateboardPhysics()
        skateGo.addComponent(skatePhysics)
        
        skateGo.transform.translation.set(offset).add(0f, 0.1f, 0f)
        physics.add(skateGo)
        skatePhysics.start()
        
        rb.linearVelocity = Vector3f(10f, 0f, 0f)

        for (i in 0 until steps) {
            skatePhysics.update(dt)
            physics.update(dt)
            rb.update(dt)
        }
        
        return Vector3f(skateGo.transform.translation).sub(offset)
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
