package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.SkeletonComponent
import com.pafoid.skate.engine.scenes.components.Transform
import com.pafoid.skate.engine.scenes.components.toWorldMatrix
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
        scene.gameObjects.filter { it.hasComponent<SkeletonComponent>() && it.hasComponent<Animator>() }.forEach { go ->
            val animator = go.getComponent<Animator>()
            val skeletonComponent = go.getComponent<SkeletonComponent>()
            updateAnimation(
                animator!!,
                skeletonComponent!!,
                dt
            ) // TODO: fix nullability by caching animated gameObjects in scene
        }
    }

    override fun editorUpdate(dt: Float) {
        val scene = sceneManager.currentScene ?: return

        scene.gameObjects.filter { it.hasComponent<SkeletonComponent>() && it.hasComponent<Animator>() }.forEach { go ->
            val animator = go.getComponent<Animator>()
            val skeletonComponent = go.getComponent<SkeletonComponent>()
            updateAnimation(
                animator!!,
                skeletonComponent!!,
                dt
            ) // TODO: fix nullability by caching animated gameObjects in scene

            val goTransform = go.getComponent<Transform>()
            val transformMatrix = goTransform?.toWorldMatrix() ?: Matrix4f().identity()
            visualizeBone(skeletonComponent, transformMatrix)
        }
    }

    private fun visualizeBone(skeletonComponent: SkeletonComponent, modelMatrix: Matrix4f) {
        val pose = skeletonComponent.pose
        val skeleton = pose.skeleton

        visualizeBoneRecursive(skeleton.rootBone, modelMatrix)
    }

    private fun visualizeBoneRecursive(bone: Bone, modelMatrix: Matrix4f) {
        bone.worldTransform.getTranslation(tempBonePos)
        modelMatrix.transformPosition(tempBonePos)

        val currentBonePos = Vector3f(tempBonePos)

        for (child in bone.children) {
            child.worldTransform.getTranslation(tempChildPos)
            modelMatrix.transformPosition(tempChildPos)

            debugDraw.addLine3D(currentBonePos, tempChildPos, boneColor)
            visualizeBoneRecursive(child, modelMatrix)
        }

        bone.worldTransform.getUnnormalizedRotation(tempBoneQuat)
        debugDraw.addBox3D(currentBonePos, tempBoneQuat, Vector3f(0.01f), boneColor)
    }

    private fun updateAnimation(
        animator: Animator,
        skeletonComponent: SkeletonComponent,
        dt: Float
    ) {
        val pose = skeletonComponent.pose ?: return
        val skeleton = pose.skeleton

        if (animator.isPlaying) {
            val animation = animator.currentAnimation
            if (animation != null) {
                animator.currentTime += dt

                if (animator.blendTime > 0f) {
                    animator.blendTime -= dt
                    val alpha = 1f - (animator.blendTime / animator.blendDuration)
                    animator.previousAnimation?.let { prev ->
                        animator.previousTime += dt
                        prev.update(animator.previousTime, skeleton)
                        animation.updateBlended(animator.currentTime, skeleton, alpha)
                    } ?: animation.update(animator.currentTime, skeleton)
                } else {
                    animation.update(animator.currentTime, skeleton)
                }

                animation.update(animator.currentTime, skeleton)

                // Copy the skeleton's bone transforms to the pose's local transforms
                skeleton.getAllBones().forEach { bone ->
                    if (bone.index in 0 until pose.localTransforms.size) {
                        pose.localTransforms[bone.index].set(bone.localTransform)
                    }
                }
            }
        }

        // Apply global transforms and skin matrices (ALWAYS, even if not playing)
        SkeletonMath.computeGlobalTransforms(skeleton.rootBone, pose.localTransforms, pose.globalTransforms)
        SkeletonMath.buildSkinMatrices(pose, skeletonComponent.getMatrixPalette())
    }
}