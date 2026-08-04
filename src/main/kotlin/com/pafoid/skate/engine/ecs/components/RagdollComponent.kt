package com.pafoid.skate.engine.ecs.components

import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.physics3d.constraints.IPhysicsConstraint
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RagdollComponent(// TODO: check Ragdoll Builder
    var state: RagdollState = RagdollState.ANIMATED,
    @Transient // Managed manually, do not serialize raw physics objects
    val boneBodies: MutableMap<String, PhysicsRigidBody> = mutableMapOf<String, PhysicsRigidBody>(),
    @Transient
    val joints: MutableList<IPhysicsConstraint> = mutableListOf<IPhysicsConstraint>()
) : Component() {
}
