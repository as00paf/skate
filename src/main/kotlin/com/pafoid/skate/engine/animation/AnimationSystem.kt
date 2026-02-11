package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.SkeletonComponent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AnimationSystem : Component(), KoinComponent {
    private val sceneManager: SceneManager by inject()

    override fun update(dt: Float) {
        val scene = sceneManager.currentScene ?: return
        scene.gameObjects.filter { it.hasComponent<SkeletonComponent>() && it.hasComponent<Animator>() }.forEach { go ->
            val animator = go.getComponent<Animator>()
            val skeletonComponent = go.getComponent<SkeletonComponent>()
            
            if (animator != null && skeletonComponent != null) {
                updateAnimation(animator, skeletonComponent, dt)
            }
        }
    }

    override fun editorUpdate(dt: Float) {
        val scene = sceneManager.currentScene ?: return

        scene.gameObjects.filter { it.hasComponent<SkeletonComponent>() && it.hasComponent<Animator>() }.forEach { go ->
            val animator = go.getComponent<Animator>()
            val skeletonComponent = go.getComponent<SkeletonComponent>()
            
            if (animator != null && skeletonComponent != null) {
                updateAnimation(animator, skeletonComponent, dt)
            }
        }
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