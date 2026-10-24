package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
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

    private val ragdolls = query(SkeletonComponent::class, RagdollComponent::class, Transform::class, topLevelOnly = true)

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
                    // Physics drives bones
                    ragdoll.boneBodies.forEach { (boneName, body) ->
                        val bone = skeleton.getBoneByName(boneName) ?: return@forEach

                        body.getLocation(tempVecJoml)
                        body.getRotation(tempQuatJoml)

                        // We need to convert world space physics to local space of the parent bone or 
                        // just override the global transform directly.
                        // The engine builds skin matrices from pose.globalTransforms.
                        // So we can overwrite pose.globalTransforms for the ragdolled bones.

                        // Transform from world to GameObject local space
                        // Actually globalTransforms are in GameObject space.
                        // So we multiply by inverse GameObject transform.
                        val invGoMat = Matrix4f(goTransform.worldMatrix).invert()

                        tempMat.translationRotateScale(
                            tempVecJoml,
                            tempQuatJoml,
                            Vector3f(1f, 1f, 1f) // Scale from physics shape or keep 1
                        )

                        val goSpaceMat = Matrix4f(invGoMat).mul(tempMat)

                        if (bone.index in pose.globalTransforms.indices) {
                            pose.globalTransforms[bone.index].set(goSpaceMat)
                        }
                    }

                    // Rebuild skin matrices based on the overwritten global transforms
                    SkeletonMath.buildSkinMatricesFromGlobal(pose, skeletonComp.matrixPalette)
                }

                RagdollState.ANIMATED -> {
                    // Animation drives physics (Kinematic sync)
                    ragdoll.boneBodies.forEach { (boneName, body) ->
                        val bone = skeleton.getBoneByName(boneName) ?: return@forEach

                        if (bone.index in pose.globalTransforms.indices) {
                            val globalTrans = pose.globalTransforms[bone.index]

                            // Transform from GameObject space to World space
                            val worldMat = Matrix4f(goTransform.worldMatrix).mul(globalTrans)

                            worldMat.getTranslation(tempVecJoml)
                            worldMat.getUnnormalizedRotation(tempQuatJoml)
                            tempQuatJoml.normalize()

                            body.setLocation(tempVecJoml)
                            body.setRotation(tempQuatJoml)

                            // Ensure body is kinematic while animated
                            if (!body.isKinematic) {
                                body.isKinematic = true
                            }
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
