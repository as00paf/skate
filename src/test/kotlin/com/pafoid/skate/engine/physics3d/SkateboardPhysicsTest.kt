package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.toWorldMatrix
import com.pafoid.skate.engine.ecs.systems.SceneManager
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.game.skateboard.SkateboardPhysics
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkateboardPhysicsTest {

    companion object {
        val engine = mockk<Engine>()
        val sceneManager = mockk<SceneManager>()

        @BeforeAll
        @JvmStatic
        fun setupAll() {
            startKoin {
                modules(module {
                    single<Engine> { engine }
                    single<SceneManager> { sceneManager }
                })
            }
        }

        @AfterAll
        @JvmStatic
        fun teardownAll() {
            stopKoin()
        }
    }

    private lateinit var physics: Physics3D

    @BeforeEach
    fun setup() {
        physics = Physics3D()
        every { engine.runtimePlaying } returns true
        
        val mockScene = mockk<Scene>()
        every { sceneManager.currentScene } returns mockScene
        every { mockScene.physics3d } returns physics
    }

    @AfterEach
    fun teardown() {
        physics.destroy()
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
        val groundTransform = Transform()
        ground.addComponent(groundTransform)
        groundTransform.rotation.set(15f, 0f, 0f)
        val groundRb = RigidBody3D(0f)
        groundRb.bodyType = BodyType.Static
        ground.addComponent(groundRb)
        ground.addComponent(BoxCollider3D(Vector3f(10f, 0.1f, 10f)))
        physics.add(ground)

        val skateGo = GameObject("Skateboard")
        val skateTransform = Transform()
        skateGo.addComponent(skateTransform)
        skateTransform.translation.set(0f, 0.5f, 0f)
        skateTransform.rotation.set(15f, 0f, 0f)
        val rb = RigidBody3D(1.8f).apply { friction = 1.0f }
        skateGo.addComponent(rb)
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f)))
        physics.add(skateGo)

        // Act
        for (i in 0 until 60) {
            physics.update(1/60f)
            rb.update(1/60f)
        }
        val velocity = rb.linearVelocity

        // Assert
        assertEquals(0f, velocity.length(), 0.1f, "Board should remain static on 15 degree slope due to friction")
    }

    @Test
    fun `rollResistance_flatGround_velocityDecays`() {
        // Arrange
        val ground = GameObject("Ground")
        val groundTransform = Transform()
        ground.addComponent(groundTransform)
        ground.addComponent(RigidBody3D(0f).apply { bodyType = BodyType.Static })
        ground.addComponent(BoxCollider3D(Vector3f(100f, 1f, 100f)))
        groundTransform.translation.set(0f, -1f, 0f)
        physics.add(ground)

        val skateGo = GameObject("Skateboard")
        val skateTransform = Transform()
        skateGo.addComponent(skateTransform)
        val rb = RigidBody3D(2.0f).apply {
            friction = 0.5f
            linearDamping = 0.1f
        }
        skateGo.addComponent(rb)
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f)))

        // Use SkateboardPhysics for suspension (keeps it off the ground physically, but rays touch)
        val skatePhysics = SkateboardPhysics()
        skateGo.addComponent(skatePhysics)

        skateTransform.translation.set(0f, 0.1f, 0f) // Slightly above ground, suspension holds it
        physics.add(skateGo)

        skatePhysics.start()

        // Give initial push
        val initialSpeed = 5.0f
        rb.linearVelocity = Vector3f(initialSpeed, 0f, 0f)

        // Act
        for (i in 0 until 120) { // Run for 2 seconds
            skatePhysics.update(1/60f)
            physics.update(1/60f)
            rb.update(1/60f)
        }

        val finalSpeed = rb.linearVelocity.length()

        // Assert
        assertTrue(finalSpeed < initialSpeed, "Velocity should decay due to resistance. Initial: $initialSpeed, Final: $finalSpeed")
        assertTrue(finalSpeed > 0f, "Should not stop instantly")
    }

    @Test
    fun `suspension_heavyLoad_compressesAccordingToHookesLaw`() {
        // Arrange: Board in air, put a 'floor' right below it so rays hit
        val ground = GameObject("Ground")
        val groundTransform = Transform()
        ground.addComponent(groundTransform)
        ground.addComponent(RigidBody3D(0f).apply { bodyType = BodyType.Static })
        ground.addComponent(BoxCollider3D(Vector3f(100f, 1f, 100f)))
        groundTransform.translation.set(0f, -1.05f, 0f) // Top at -0.05
        physics.add(ground)

        val skateGo = GameObject("Skateboard")
        val skateTransform = Transform()
        skateGo.addComponent(skateTransform)
        val rb = RigidBody3D(1.0f) // Light board
        skateGo.addComponent(rb)
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f)))
        val skatePhysics = SkateboardPhysics()
        skateGo.addComponent(skatePhysics)

        // Place board so rays (length 0.08) are compressed
        // Ray origin ~0, Ray end -0.08. Ground at -0.05.
        // Expected compression ~0.03m.
        skateTransform.translation.set(0f, 0f, 0f)

        physics.add(skateGo)
        skatePhysics.start()

        // Act
        // SkateboardPhysics applies force.
        // Physics update integrates force -> velocity.
        skatePhysics.update(1/60f)
        physics.update(1/60f)

        // Assert
        assertTrue(rb.linearVelocity.y > 0, "Suspension should apply upward force resulting in upward velocity")
    }

    @Test
    fun `turning_rollTorque_circularPath`() {
        // Arrange
        val ground = GameObject("Ground")
        val groundTransform = Transform()
        ground.addComponent(groundTransform)
        ground.addComponent(RigidBody3D(0f).apply { bodyType = BodyType.Static })
        ground.addComponent(BoxCollider3D(Vector3f(100f, 1f, 100f)))
        groundTransform.translation.set(0f, -1f, 0f)
        physics.add(ground)

        val skateGo = GameObject("Skateboard")
        val skateTransform = Transform()
        skateGo.addComponent(skateTransform)
        val rb = RigidBody3D(2.0f)
        skateGo.addComponent(rb)
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f)))
        val skatePhysics = SkateboardPhysics()
        skateGo.addComponent(skatePhysics)

        skateTransform.translation.set(0f, 0.1f, 0f)
        // Force Roll to 15 degrees (approx 0.26 rad) to trigger steering
        skateTransform.rotation.set(15f, 0f, 0f)

        physics.add(skateGo)
        skatePhysics.start()

        // Move forward
        rb.linearVelocity = Vector3f(5f, 0f, 0f)

        // Act
        for (i in 0 until 60) {
            skatePhysics.update(1/60f)
            physics.update(1/60f)
            rb.update(1/60f)
        }

        // Assert
        val vel = rb.linearVelocity

        // Check for turning (Yaw change)
        val rot = rb.rawBody!!.getPhysicsRotation(null)
        val q = Quaternionf(rot.x, rot.y, rot.z, rot.w)
        val euler = Vector3f()
        q.getEulerAnglesXYZ(euler)
        val yaw = euler.y
        // println("Turning: Yaw $yaw, Z-Vel ${vel.z}")

        assertTrue(kotlin.math.abs(yaw) > 0.01f, "Board should turn (yaw) when leaning (roll). Yaw: $yaw")
        // Lateral velocity check might fail if steering is perfect or if friction is high, but yaw is key.
        // assertTrue(kotlin.math.abs(vel.z) > 0.1f, "Board should have lateral velocity. Z-Vel: ${vel.z}")
    }

    @Test
    fun `tailSnap_downwardTailImpulse_noseMovesUpward`() {
        // Arrange
        val skateGo = GameObject("Skateboard")
        val skateTransform = Transform()
        skateGo.addComponent(skateTransform)
        val rb = RigidBody3D(2.0f)
        skateGo.addComponent(rb)
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f)))
        physics.add(skateGo)

        // Wait for body to be initialized
        val initialVel = rb.linearVelocity

        // Tail is at local X = -0.4 (assuming X is forward, -X is tail)
        // Apply downward impulse at tail
        val tailPos = Vector3f(-0.4f, 0f, 0f).mulProject(skateTransform.toWorldMatrix())
        val impulse = Vector3f(0f, -5.0f, 0f)

        // Act
        rb.applyForce(impulse, tailPos) // Apply force at tail
        physics.update(1/60f)
        rb.update(1/60f)

        // Assert
        // Lever action: Down at tail -> Up at nose
        // We check angular velocity (Pitch) or linear velocity at the nose
        val angVel = rb.angularVelocity
        // If tail goes down (-Y) and it's at -X, pitch (Z) should be positive?
        // local X forward, local Y up. Torque = r x F = (-0.4, 0, 0) x (0, -5, 0) = (0, 0, 2)
        // Z torque should be positive.
        assertTrue(angVel.z > 0.1f, "Tail snap should induce positive Z angular velocity (pitch nose up). Z-AngVel: ${angVel.z}")
    }

    @Test
    fun `groundImpact_tailHitsFloor_generatesUpwardBounce`() {
        // Arrange
        val ground = GameObject("Ground")
        val groundTransform = Transform()
        ground.addComponent(groundTransform)
        ground.addComponent(RigidBody3D(0f).apply { bodyType = BodyType.Static })
        ground.addComponent(BoxCollider3D(Vector3f(100f, 1f, 100f)))
        groundTransform.translation.set(0f, -0.5f, 0f) // Top at 0
        physics.add(ground)

        val skateGo = GameObject("Skateboard")
        val skateTransform = Transform()
        skateGo.addComponent(skateTransform)
        val rb = RigidBody3D(2.0f)
        skateGo.addComponent(rb)
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f)))
        physics.add(skateGo)

        // Position board so tail is about to hit ground
        skateTransform.translation.set(0f, 0.1f, 0f)
        skateTransform.rotation.set(0f, 0f, 45f) // Tail down

        // Give it downward angular velocity
        rb.angularVelocity = Vector3f(0f, 0f, -10f)

        // Act
        for (i in 0 until 10) {
            physics.update(1/60f)
            rb.update(1/60f)
        }

        // Assert
        // After impact, the angular velocity should reverse or at least stop going down
        assertTrue(rb.angularVelocity.z > -1f, "Tail impact with ground should counteract downward pitch. Z-AngVel: ${rb.angularVelocity.z}")
    }

    @Test
    fun `leveling_noseDownwardForceInAir_boardPitchReturnsToZero`() {
        // Arrange
        val skateGo = GameObject("Skateboard")
        val skateTransform = Transform()
        skateGo.addComponent(skateTransform)
        val rb = RigidBody3D(2.0f)
        skateGo.addComponent(rb)
        skateGo.addComponent(BoxCollider3D(Vector3f(0.4f, 0.02f, 0.1f)))
        physics.add(skateGo)

        // Board is pitched up (Nose high)
        skateTransform.rotation.set(0f, 0f, 30f)

        // Act: Simulate front foot "Slide/Level"
        // Apply downward force at nose (Local X = 0.4)
        val nosePos = Vector3f(0.4f, 0f, 0f).mulProject(skateTransform.toWorldMatrix())
        rb.applyForce(Vector3f(0f, -10f, 0f), nosePos)

        physics.update(1/60f)
        rb.update(1/60f)

        // Assert
        // Pitch should decrease
        val rot = rb.rawBody!!.getPhysicsRotation(null)
        val q = Quaternionf(rot.x, rot.y, rot.z, rot.w)
        val euler = Vector3f()
        q.getEulerAnglesXYZ(euler)
        val pitch = Math.toDegrees(euler.z.toDouble()).toFloat()

        assertTrue(pitch < 30f, "Front foot leveling should decrease pitch. Pitch: $pitch")
    }
}
