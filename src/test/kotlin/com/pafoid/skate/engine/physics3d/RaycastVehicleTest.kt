package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.VehicleComponent
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class RaycastVehicleTest {

    private class Rig(backend: PhysicsBackend) {
        val vehicle = VehicleComponent()
        val body = RigidBody3D(1000f)
        val system: PhysicsSystem

        init {
            val scene = Scene()
            scene.addChild(GameObject("Ground").apply {
                addComponent(Transform().apply { translation.set(0f, -0.5f, 0f) })
                addComponent(RigidBody3D(0f, BodyType.Static))
                addComponent(BoxCollider3D(Vector3f(200f, 0.5f, 200f)))
            })
            scene.addChild(GameObject("Car").apply {
                addComponent(Transform().apply { translation.set(0f, 1f, 0f) })
                addComponent(body)
                addComponent(BoxCollider3D(Vector3f(1f, 0.3f, 1.8f)))
                addComponent(vehicle)
            })
            system = PhysicsSystem(mockk(relaxed = true), null, PhysicsBackends.create(backend))
            val manager = SystemManager()
            manager.addSystem(system)
            manager.loadScene(scene)
            manager.start()
            scene.start()
        }

        fun step(n: Int) = repeat(n) { system.fixedUpdate(1f / 60f) }
        val pos get() = body.getWorldPosition()
        val velocity get() = body.linearVelocity
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun vehicle_Idle_SettlesOnSuspension(backend: PhysicsBackend) {
        val rig = Rig(backend)

        rig.step(240)

        assertTrue(rig.vehicle.wheelStates.all { it.grounded })
        assertEquals(0.93f, rig.pos.y, 0.1f)
        assertEquals(0f, rig.velocity.length(), 0.1f)
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun vehicle_Throttle_AcceleratesAlongForward(backend: PhysicsBackend) {
        val rig = Rig(backend)
        rig.step(120)
        rig.vehicle.throttle = 1f

        rig.step(120)

        assertTrue(rig.velocity.z > 2f, "forward speed ${rig.velocity.z}")
        assertEquals(0f, rig.velocity.x, 0.5f)
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun vehicle_SteeringWhileMoving_TurnsTheCar(backend: PhysicsBackend) {
        val rig = Rig(backend)
        rig.step(120)
        rig.vehicle.throttle = 1f
        rig.step(90)
        rig.vehicle.steering = 1f

        rig.step(90)

        assertTrue(Math.abs(rig.velocity.x) > 0.5f, "lateral speed ${rig.velocity.x}")
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun vehicle_Brake_StopsTheCar(backend: PhysicsBackend) {
        val rig = Rig(backend)
        rig.step(60)
        rig.vehicle.throttle = 1f
        rig.step(120)
        rig.vehicle.throttle = 0f
        rig.vehicle.brake = 1f

        rig.step(180)

        assertEquals(0f, rig.velocity.z, 0.3f)
    }
}
