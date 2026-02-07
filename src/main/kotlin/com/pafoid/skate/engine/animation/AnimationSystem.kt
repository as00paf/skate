package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.RenderComponent
import com.pafoid.skate.engine.scenes.components.SkeletonComponent
import com.pafoid.skate.engine.scenes.components.Transform
import com.pafoid.skate.engine.scenes.components.toWorldMatrix
import com.pafoid.skate.engine.utils.BoneNameMapper
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AnimationSystem : Component(), KoinComponent {
    private val sceneManager: SceneManager by inject()
    private val debugDraw: DebugDraw by inject()

    // Reusable objects to minimize allocations in hot loops/recursive calls
    private val tempBonePos = Vector3f()
    private val tempChildPos = Vector3f()
    private val tempBoneQuat = Quaternionf()
    private val boneColor = Vector3f(0f, 1f, 1f) // Cyan for bones

    override fun update(dt: Float) {
        val scene = sceneManager.currentScene ?: return
        // Find all GameObjects that have both an Animator and a SkeletonComponent
        for (go in scene.gameObjects) {
            val animator = go.getComponent<Animator>()
            val skeletonComponent = go.getComponent<SkeletonComponent>()

            if (animator != null && skeletonComponent != null) {
                updateAnimation(go, animator, skeletonComponent, dt)
            }
        }
    }

    override fun editorUpdate(dt: Float) {
        val scene = sceneManager.currentScene ?: return

        // Find all GameObjects that have both an Animator and a SkeletonComponent
        for (go in scene.gameObjects) {
            val animator = go.getComponent<Animator>()
            val skeletonComponent = go.getComponent<SkeletonComponent>()

            // Visualize bones in editor mode
            val goTransform = go.getComponent<Transform>()
            val transformMatrix = goTransform?.toWorldMatrix() ?: Matrix4f().identity()
            skeletonComponent?.pose?.skeletonAsset?.rootBone?.let { visualizeBone(it, transformMatrix) }

            if (animator != null && skeletonComponent != null && animator.isPlaying) {
                updateAnimation(go, animator, skeletonComponent, dt)
            }
        }
    }

    private fun visualizeBone(bone: Bone, modelMatrix: Matrix4f) {
        bone.worldTransform.getTranslation(tempBonePos)
        modelMatrix.transformPosition(tempBonePos)

        // Capture bone position for this recursion level
        val currentBonePos = Vector3f(tempBonePos)

        for (child in bone.children) {
            child.worldTransform.getTranslation(tempChildPos)
            modelMatrix.transformPosition(tempChildPos)

            debugDraw.addLine3D(currentBonePos, tempChildPos, boneColor)
            visualizeBone(child, modelMatrix)
        }

        // Draw bone point as a tiny box
        bone.worldTransform.getUnnormalizedRotation(tempBoneQuat)
        debugDraw.addBox3D(currentBonePos, tempBoneQuat, Vector3f(0.01f), boneColor)
    }

    private fun updateAnimation(
        go: GameObject,
        animator: Animator,
        skeletonComponent: SkeletonComponent,
        dt: Float
    ) {
        val pose = skeletonComponent.pose ?: return
        val skeleton = pose.skeletonAsset

        if (animator.isPlaying) {
            val animation = animator.currentAnimation ?: animator.animations.firstOrNull() ?: return

            animator.currentTime += dt

            if (animator.blendTime > 0f) {
                animator.blendTime -= dt
                val alpha = 1f - (animator.blendTime / animator.blendDuration)
                animator.previousAnimation?.let { prev ->
                    animator.previousTime += dt
                    // Apply the animation to the pose's local transforms directly
                    prev.update(animator.previousTime, skeleton)
                    animation.updateBlended(animator.currentTime, skeleton, alpha)
                    
                    // Copy the skeleton's bone transforms to the pose's local transforms
                    skeleton.getAllBones().forEachIndexed { index, bone ->
                        if (index < pose.localTransforms.size) {
                            pose.localTransforms[index].set(bone.localTransform)
                        }
                    }
                } ?: run {
                    // Apply animation and copy to pose
                    animation.update(animator.currentTime, skeleton)
                    skeleton.getAllBones().forEachIndexed { index, bone ->
                        if (index < pose.localTransforms.size) {
                            pose.localTransforms[index].set(bone.localTransform)
                        }
                    }
                }
            } else {
                // Apply animation and copy to pose
                animation.update(animator.currentTime, skeleton)
                skeleton.getAllBones().forEachIndexed { index, bone ->
                    if (index < pose.localTransforms.size) {
                        pose.localTransforms[index].set(bone.localTransform)
                    }
                }
            }
            
            // Compute global transforms
            SkeletonMath.computeGlobalTransforms(skeleton.rootBone, pose.localTransforms, pose.globalTransforms)
            
            // Update the matrix palette in the skeleton component
            SkeletonMath.buildSkinMatrices(pose, skeletonComponent.getMatrixPalette())
        }

        // Process bone overrides if needed
        skeleton.getAllBones().forEach { bone ->
            // Decompose matrix
            val translation = Vector3f()
            val rotation = Quaternionf()
            val scale = Vector3f()
            bone.localTransform.getTranslation(translation)
            bone.localTransform.getUnnormalizedRotation(rotation)
            bone.localTransform.getScale(scale)

            // Apply override by multiplying rotations
            //rotation.mul(overrideRotation)

            // Recompose matrix
            bone.localTransform.translationRotateScale(translation, rotation, scale)
        }
    }
}