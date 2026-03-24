package com.pafoid.skate.engine.physics3d

import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.ecs.components.RagdollComponent
import com.pafoid.skate.engine.physics3d.components.CapsuleCollider3D
import com.pafoid.skate.engine.physics3d.constraints.ConeTwistConstraint3D
import org.joml.Matrix3f
import org.joml.Vector3f

/**
 * Utility to map a humanoid skeleton (like Mixamo rig) to physics bodies and constraints
 * to build a RagdollComponent.
 */
object RagdollBuilder {

    /**
     * Builds a ragdoll configuration for a standard mixamorig skeleton.
     * Maps essential bones to capsule colliders and connects them with cone-twist constraints.
     */
    fun buildMixamoRagdoll(skeleton: Skeleton, totalMass: Float = 70f): RagdollComponent {
        val ragdoll = RagdollComponent()

        // Define common mixamo bone names that need physics bodies and their mass fraction
        val bodyParts = mapOf(
            "mixamorig:Hips" to 0.15f,
            "mixamorig:Spine" to 0.10f,
            "mixamorig:Spine1" to 0.10f,
            "mixamorig:Spine2" to 0.10f,
            "mixamorig:Head" to 0.05f,

            "mixamorig:LeftUpLeg" to 0.10f,
            "mixamorig:LeftLeg" to 0.05f,
            "mixamorig:RightUpLeg" to 0.10f,
            "mixamorig:RightLeg" to 0.05f,

            "mixamorig:LeftArm" to 0.05f,
            "mixamorig:LeftForeArm" to 0.05f,
            "mixamorig:RightArm" to 0.05f,
            "mixamorig:RightForeArm" to 0.05f
        )

        // 1. Create rigid bodies for mapped bones
        bodyParts.forEach { (boneName, massFraction) ->
            val bone = skeleton.getBoneByName(boneName)
            if (bone != null) {
                // Approximate capsule size
                val radius = 0.1f
                val height = 0.3f

                // Construct collision shape
                val shape = CapsuleCollider3D(radius, height, 1).createShape()
                val mass = totalMass * massFraction
                val body = PhysicsRigidBody(shape, mass)

                // Initialize as kinematic (animated mode by default)
                body.isKinematic = true

                ragdoll.boneBodies[boneName] = body
            }
        }

        // 2. Setup structural constraints (joints)
        connectBones(ragdoll, "mixamorig:Hips", "mixamorig:Spine")
        connectBones(ragdoll, "mixamorig:Spine", "mixamorig:Spine1")
        connectBones(ragdoll, "mixamorig:Spine1", "mixamorig:Spine2")
        connectBones(ragdoll, "mixamorig:Spine2", "mixamorig:Head")

        connectBones(ragdoll, "mixamorig:Hips", "mixamorig:LeftUpLeg")
        connectBones(ragdoll, "mixamorig:LeftUpLeg", "mixamorig:LeftLeg")

        connectBones(ragdoll, "mixamorig:Hips", "mixamorig:RightUpLeg")
        connectBones(ragdoll, "mixamorig:RightUpLeg", "mixamorig:RightLeg")

        connectBones(ragdoll, "mixamorig:Spine2", "mixamorig:LeftArm")
        connectBones(ragdoll, "mixamorig:LeftArm", "mixamorig:LeftForeArm")

        connectBones(ragdoll, "mixamorig:Spine2", "mixamorig:RightArm")
        connectBones(ragdoll, "mixamorig:RightArm", "mixamorig:RightForeArm")

        return ragdoll
    }

    private fun connectBones(ragdoll: RagdollComponent, parentName: String, childName: String) {
        val bodyA = ragdoll.boneBodies[parentName] ?: return
        val bodyB = ragdoll.boneBodies[childName] ?: return

        // Approximate pivot points for a generic humanoid structure.
        // In a comprehensive implementation, these would be derived from the skeleton's bind pose.
        val joint = ConeTwistConstraint3D(
            bodyA = bodyA,
            bodyB = bodyB,
            pivotA = Vector3f(0f, 0.15f, 0f),  // Top of parent bone
            pivotB = Vector3f(0f, -0.15f, 0f), // Bottom of child bone
            rotA = Matrix3f().identity(),
            rotB = Matrix3f().identity(),
            swingSpan1 = 0.5f,
            swingSpan2 = 0.5f,
            twistSpan = 0.2f
        )

        ragdoll.joints.add(joint)
    }
}
