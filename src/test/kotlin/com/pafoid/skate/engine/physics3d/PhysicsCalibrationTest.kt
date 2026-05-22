package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.math.sqrt

class PhysicsCalibrationTest {

    companion object {
        val engine = mockk<Engine>()
        val sceneManager = mockk<SceneManager>()
        val debugRenderer = mockk<DebugRenderer>()

        @BeforeAll
        @JvmStatic
        fun setupAll() {
            startKoin {
                modules(module {
                    single<Engine> { engine }
                    single<SceneManager> { sceneManager }
                    single<DebugRenderer> { debugRenderer }
                    single { mockk<com.pafoid.skate.editor.systems.StringManager>(relaxed = true) }
                    single { mockk<com.pafoid.skate.editor.systems.LoggerService>(relaxed = true) }
                })
            }
        }

        @AfterAll
        @JvmStatic
        fun teardownAll() {
            stopKoin()
        }
    }

    @Test
    fun `test freefall timing with standard gravity`() {
        val physics = BulletPhysics3D()
        val gravity = 9.81f
        physics.setGravity(Vector3f(0f, -gravity, 0f))

        val dropHeight = 1.0f
        val cube = GameObject("TestCube")
        val transform = Transform()
        cube.addComponent(transform)
        transform.translation.set(0f, dropHeight, 0f)

        val rb = RigidBody3D(1.0f)
        cube.addComponent(rb)

        physics.add(cube)

        // Time to fall distance h: t = sqrt(2h/g)
        val expectedTime = sqrt(2.0 * dropHeight / gravity) // sqrt(2/9.81) approx 0.4515s

        var totalTime = 0f
        val dt = 1f / 60f

        // Simulate until it hits 0
        while (transform.translation.y > 0 && totalTime < 2.0f) {
            physics.update(dt)

            // Sync transform back from RB
            val loc = rb.rawBody?.getPhysicsLocation(null)
            if (loc != null) {
                transform.translation.set(loc.x, loc.y, loc.z)
            }

            totalTime += dt
        }

        // It should take approx expectedTime
        // We might be off by one frame (dt)
        assertEquals(expectedTime, totalTime.toDouble(), dt.toDouble() * 2.0, "Object should hit the ground in approx 0.45s")

        physics.destroy()
    }
}