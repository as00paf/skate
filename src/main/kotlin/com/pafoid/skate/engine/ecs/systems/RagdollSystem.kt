package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RagdollComponent
import com.pafoid.skate.engine.ecs.components.RagdollState
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.hasComponent
import com.pafoid.skate.engine.utils.SkeletonMath
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

class RagdollSystem : System(priority = ExecutionPriority.DEFAULT) {
    override val runsAfter = listOf(AnimationSystem::class)

    private val tempVecJoml = Vector3f()
    private val tempQuatJoml = Quaternionf()
    private val tempMat = Matrix4f()
    private val invGoMat = Matrix4f()
    private val tempWorldMat = Matrix4f()

    private val ragdolls = query(SkeletonComponent::class, RagdollComponent::class, Transform::class, topLevelOnly = true)

    /** Resolves bone names to indices; redone only when the skeleton or the set of bone bodies changes. */
    private fun bindingsFor(ragdoll: RagdollComponent, skeleton: Skeleton): Array<RagdollComponent.BoneBinding> {
        if (ragdoll.boundSkeleton !== skeleton || ragdoll.boundBodyCount != ragdoll.boneBodies.size) {
            ragdoll.bindings = ragdoll.boneBodies.keys.mapNotNull { name ->
                skeleton.getBoneByName(name)?.let { RagdollComponent.BoneBinding(name, it.index) }
            }.toTypedArray()
            ragdoll.boundSkeleton = skeleton
            ragdoll.boundBodyCount = ragdoll.boneBodies.size
        }
        return ragdoll.bindings
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        ragdolls.forEach { go ->
            val ragdoll = go.getComponent<RagdollComponent>() ?: return@forEach
            val skeletonComp = go.getComponent<SkeletonComponent>() ?: return@forEach
            val goTransform = go.getComponent<Transform>() ?: return@forEach

            val pose = skeletonComp.pose
            val skeleton = pose.skeleton

            when (ragdoll.state) {
                RagdollState.RAGDOLL -> {
                    // Physics drives bones: overwrite the GameObject-space global transforms of the ragdolled bones,
                    // from which the skin matrices are rebuilt below.
                    goTransform.worldMatrix.invert(invGoMat)
                    val bindings = bindingsFor(ragdoll, skeleton)
                    for (i in bindings.indices) {
                        val binding = bindings[i]
                        val body = ragdoll.boneBodies[binding.name] ?: continue
                        if (binding.boneIndex !in pose.globalTransforms.indices) continue

                        body.getLocation(tempVecJoml)
                        body.getRotation(tempQuatJoml)
                        tempMat.translationRotate(tempVecJoml, tempQuatJoml)
                        invGoMat.mul(tempMat, pose.globalTransforms[binding.boneIndex])
                    }

                    // Rebuild skin matrices based on the overwritten global transforms
                    SkeletonMath.buildSkinMatricesFromGlobal(pose, skeletonComp.matrixPalette)
                    skeletonComp.markPaletteDirty()
                }

                RagdollState.ANIMATED -> {
                    // Animation drives physics (Kinematic sync)
                    val bindings = bindingsFor(ragdoll, skeleton)
                    for (i in bindings.indices) {
                        val binding = bindings[i]
                        val body = ragdoll.boneBodies[binding.name] ?: continue
                        if (binding.boneIndex !in pose.globalTransforms.indices) continue

                        // Transform from GameObject space to World space
                        goTransform.worldMatrix.mul(pose.globalTransforms[binding.boneIndex], tempWorldMat)

                        tempWorldMat.getTranslation(tempVecJoml)
                        tempWorldMat.getUnnormalizedRotation(tempQuatJoml)
                        tempQuatJoml.normalize()

                        body.setLocation(tempVecJoml)
                        body.setRotation(tempQuatJoml)

                        // Ensure body is kinematic while animated
                        if (!body.isKinematic) {
                            body.isKinematic = true
                        }
                    }
                }

                RagdollState.BLENDING -> {
                    // Interpolate from physics to animation or vice-versa
                    // Simplified: just fall back to ANIMATED for now.
                    ragdoll.state = RagdollState.ANIMATED
                }
            }
        }
    }
}
