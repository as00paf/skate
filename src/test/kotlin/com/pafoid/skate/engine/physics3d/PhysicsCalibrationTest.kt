package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.math.sqrt

class PhysicsCalibrationTest {

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#core")
    fun `test freefall timing with standard gravity`(backend: PhysicsBackend) {
        val physics = PhysicsBackends.create(backend)
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
            physics.step(dt)

            // Sync transform back from RB
            val loc = rb.rawBody?.getLocation(Vector3f())
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