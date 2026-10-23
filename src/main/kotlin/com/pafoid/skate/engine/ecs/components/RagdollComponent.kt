package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import com.pafoid.skate.engine.physics3d.constraints.IPhysicsConstraint
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class RagdollComponent(// TODO: check Ragdoll Builder
    var state: RagdollState = RagdollState.ANIMATED,
    @Transient // Managed manually, do not serialize raw physics objects
    val boneBodies: MutableMap<String, IRigidBodyHandle> = mutableMapOf<String, IRigidBodyHandle>(),
    @Transient
    val joints: MutableList<IPhysicsConstraint> = mutableListOf<IPhysicsConstraint>()
) : Component() {
}
