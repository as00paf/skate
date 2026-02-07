package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.RenderComponent
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
    private val tempJointPos = Vector3f()
    private val tempChildPos = Vector3f()
    private val tempJointQuat = Quaternionf()
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
            skeletonComponent?.skeleton?.rootJoint?.let { visualizeJoint(it, transformMatrix) }

            if (animator != null && skeletonComponent != null && animator.isPlaying) {
                updateAnimation(go, animator, skeletonComponent, dt)
            }
        }
    }

    private fun visualizeJoint(joint: Joint, modelMatrix: Matrix4f) {
        joint.worldTransform.getTranslation(tempJointPos)
        modelMatrix.transformPosition(tempJointPos)

        // Capture joint position for this recursion level
        val currentJointPos = Vector3f(tempJointPos)

        for (child in joint.children) {
            child.worldTransform.getTranslation(tempChildPos)
            modelMatrix.transformPosition(tempChildPos)

            debugDraw.addLine3D(currentJointPos, tempChildPos, boneColor)
            visualizeJoint(child, modelMatrix)
        }

        // Draw joint point as a tiny box
        joint.worldTransform.getUnnormalizedRotation(tempJointQuat)
        debugDraw.addBox3D(currentJointPos, tempJointQuat, Vector3f(0.01f), boneColor)
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
                    // Apply the animation to the skeleton using the built-in methods
                    prev.update(animator.previousTime, skeleton)
                    animation.updateBlended(animator.currentTime, skeleton, alpha)
                } ?: animation.update(animator.currentTime, skeleton)
            } else {
                animation.update(animator.currentTime, skeleton)
            }
        }

        // Apply bone overrides if they exist, but only if not actively animating this joint
        // This prevents bone overrides from interfering with active animations
        go.getComponent<BoneOverride>()?.let { overrideComponent ->
            // Only apply overrides for joints that are not being animated in the current animation
            val animatedJoints = mutableSetOf<String>()
            animator.currentAnimation?.channels?.forEach { channel ->
                val mappedName = com.pafoid.skate.engine.utils.BoneNameMapper.map(channel.targetNodeName)
                animatedJoints.add(mappedName)
            }

            skeleton.getAllJoints().forEach { joint ->
                // Only apply override if this joint is not currently being animated
                if (joint.name !in animatedJoints) {
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
        }

        // Always update skeleton matrices even if animation is paused
        // This allows procedural logic in other components to take effect
        skeleton.update()
    }
}