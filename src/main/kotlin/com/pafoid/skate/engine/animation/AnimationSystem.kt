package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.components.RenderComponent
import com.pafoid.skate.engine.scenes.components.SkeletonComponent
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AnimationSystem : KoinComponent {
    private val resourceManager: ResourceManager by inject()
    private val logger: LoggerService by inject()

    fun update(scene: Scene, dt: Float) {
        // Find all GameObjects that have both an Animator and a SkeletonComponent
        for (go in scene.gameObjects) {
            val animator = go.getComponent<Animator>()
            val skeletonComponent = go.getComponent<SkeletonComponent>()
            
            if (animator != null && skeletonComponent != null) {
                updateAnimation(go, animator, skeletonComponent, dt)
            }
        }
    }

    private fun updateAnimation(
        go: GameObject,
        animator: Animator,
        skeletonComponent: SkeletonComponent,
        dt: Float
    ) {
        val renderComponent = go.getComponent<RenderComponent>() ?: return
        val skeleton = skeletonComponent.skeleton ?: return

        if (animator.isPlaying) {
            val animation = animator.currentAnimation ?: renderComponent.model.animations.firstOrNull() ?: return

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
        }

        // Apply bone overrides if they exist
        go.getComponent<BoneOverride>()?.let { overrideComponent ->
            skeleton.getAllJoints().forEach { joint ->
                overrideComponent.getOverride(joint.name)?.let { overrideRotation ->
                    // Decompose matrix
                    val translation = Vector3f()
                    val rotation = Quaternionf()
                    val scale = Vector3f()
                    joint.localTransform.getTranslation(translation)
                    joint.localTransform.getUnnormalizedRotation(rotation)
                    joint.localTransform.getScale(scale)

                    // Apply override by multiplying rotations
                    rotation.mul(overrideRotation)

                    // Recompose matrix
                    joint.localTransform.translationRotateScale(translation, rotation, scale)
                }
            }
        }

        // Always update skeleton matrices even if animation is paused
        // This allows procedural logic in other components to take effect
        skeleton.update()
    }
}