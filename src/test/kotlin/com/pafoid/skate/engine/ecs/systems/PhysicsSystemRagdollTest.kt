package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RagdollComponent
import com.pafoid.skate.engine.ecs.components.RagdollState
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.constraints.HingeConstraint3D
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhysicsSystemRagdollTest {

    private class Rig {
        val backend: BulletPhysics3D
        val scene = Scene()
        val ragdoll = RagdollComponent()
        val system: PhysicsSystem

        init {
            NativeLibraryLoader().loadNativeLibrary()
            backend = BulletPhysics3D(mockk(relaxed = true))
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

    @Test
    fun ragdollPhysics_AnimatedBones_StayKinematic() {
        val rig = Rig()

        rig.step(60)

        assertEquals(10f, rig.y("upper"), 0.001f)
    }

    @Test
    fun ragdollPhysics_RagdollState_SimulatesBonesUnderGravity() {
        val rig = Rig()
        rig.step(2)
        rig.ragdoll.state = RagdollState.RAGDOLL

        rig.step(60)

        assertTrue(rig.y("upper") < 8f, "bones should fall, was ${rig.y("upper")}")
    }

    @Test
    fun ragdollPhysics_RemovedRagdoll_LeavesTheWorld() {
        val rig = Rig()
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
