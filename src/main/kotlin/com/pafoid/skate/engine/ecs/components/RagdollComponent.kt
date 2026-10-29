package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
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
    /** A bone name resolved to its skeleton index once, so per-frame sync does no name searches. */
    class BoneBinding(val name: String, val boneIndex: Int)

    @Transient
    internal var bindings: Array<BoneBinding> = emptyArray()

    @Transient
    internal var boundSkeleton: Skeleton? = null

    @Transient
    internal var boundBodyCount = -1
}
