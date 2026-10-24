package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.joints.PhysicsJoint
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.JointType
import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BulletJointCreationTest {

    @Test
    fun BulletJoint_AddConstraint_CreatesNativeJointForEveryType() {
        NativeLibraryLoader().loadNativeLibrary()
        val physics = BulletPhysics3D(mockk())
        val shape = ColliderShape.Box(Vector3f(0.5f, 0.5f, 0.5f))
        JointType.values().forEach { type ->
            val a = physics.createRigidBody(shape, 1f)
            val b = physics.createRigidBody(shape, 1f)
            val constraint = JointComponent(jointType = type, axis = Vector3f(0f, 0f, 1f), connectedAxis = Vector3f(0f, 0f, 1f))
                .createConstraint(a, b)
            physics.addConstraint(constraint)
            assertTrue(constraint.rawJoint is PhysicsJoint, type.name)
            physics.removeConstraint(constraint)
        }
    }
}
