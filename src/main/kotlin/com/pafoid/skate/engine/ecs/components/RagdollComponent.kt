package com.pafoid.skate.engine.ecs.components

import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.physics3d.constraints.IPhysicsConstraint
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class RagdollComponent : Component() {
    // TODO: check Ragdoll Builder
    var state: RagdollState = RagdollState.ANIMATED

    @Transient // Managed manually, do not serialize raw physics objects
    val boneBodies = mutableMapOf<String, PhysicsRigidBody>()

    @Transient
    val joints = mutableListOf<IPhysicsConstraint>()
}
