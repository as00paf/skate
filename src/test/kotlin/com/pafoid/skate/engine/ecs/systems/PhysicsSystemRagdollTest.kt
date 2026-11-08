package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RagdollComponent
import com.pafoid.skate.engine.ecs.components.RagdollState
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.physics3d.PhysicsBackends
import com.pafoid.skate.engine.physics3d.constraints.HingeConstraint3D
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class PhysicsSystemRagdollTest {

    private class Rig(backendType: PhysicsBackend) {
        val backend: IPhysics3D = PhysicsBackends.create(backendType)
        val scene = Scene()
        val ragdoll = RagdollComponent()
        val system: PhysicsSystem

        init {
            system = PhysicsSystem(mockk(relaxed = true), null, backend)
            val shape = ColliderShape.Capsule(0.1f, 0.3f, 1)
            val upper = backend.createRigidBody(shape, 5f).also { it.isKinematic = true; it.setLocation(Vector3f(0f, 10f, 0f)) }
            val lower = backend.createRigidBody(shape, 5f).also { it.isKinematic = true; it.setLocation(Vector3f(0f, 9.5f, 0f)) }
            ragdoll.boneBodies["upper"] = upper
            ragdoll.boneBodies["lower"] = lower
            ragdoll.joints.add(
                HingeConstraint3D(upper, lower, Vector3f(0f, -0.25f, 0f), Vector3f(0f, 0.25f, 0f), Vector3f(1f, 0f, 0f), Vector3f(1f, 0f, 0f), -3f, 3f)
            )
            val go = GameObject("Ragdoll")
            go.addComponent(ragdoll)
            scene.addChild(go)
            val manager = SystemManager()
            manager.addSystem(system)
            manager.loadScene(scene)
            manager.start()
            scene.start()
        }

        fun step(n: Int) = repeat(n) { system.fixedUpdate(1f / 60f) }
        fun y(name: String) = ragdoll.boneBodies.getValue(name).getLocation(Vector3f()).y
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun ragdollPhysics_AnimatedBones_StayKinematic(backend: PhysicsBackend) {
        val rig = Rig(backend)

        rig.step(60)

        assertEquals(10f, rig.y("upper"), 0.001f)
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun ragdollPhysics_RagdollState_SimulatesBonesUnderGravity(backend: PhysicsBackend) {
        val rig = Rig(backend)
        rig.step(2)
        rig.ragdoll.state = RagdollState.RAGDOLL

        rig.step(60)

        assertTrue(rig.y("upper") < 8f, "bones should fall, was ${rig.y("upper")}")
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun ragdollPhysics_RemovedRagdoll_LeavesTheWorld(backend: PhysicsBackend) {
        val rig = Rig(backend)
        rig.step(2)
        rig.ragdoll.state = RagdollState.RAGDOLL
        rig.step(2)

        rig.scene.children.toList().forEach { rig.scene.removeChild(it) }
        rig.step(2)
        val y = rig.y("upper")
        rig.step(30)

        assertEquals(y, rig.y("upper"), 0.001f)
    }
}
