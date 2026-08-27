package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.SkeletonMath
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

class AnimationSystem : System(priority = ExecutionPriority.DEFAULT) {

    val cache = mutableListOf<AnimatedGameObject>()

    override fun init(scene: Scene) {
        super.init(scene)
        rebuildCache()
        cacheDirty = false
    }

    override fun start() {
        cacheDirty = true
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        if (cacheDirty) rebuildCache()

        cache.forEach { ago ->
            // Update animator state first (selects animation based on state)
            ago.animator.update(dt)
            // Then update the animation
            updateAnimation(ago, dt)
        }
    }

    private fun updateAnimation(ago: AnimatedGameObject, dt: Float) {
        val animator = ago.animator
        val pose = ago.skeleton.pose
        val skeleton = pose.skeleton

        if (animator.isPlaying) {
            val animation = animator.currentAnimation
            if (animation != null) {
                animator.currentTime += dt

                if (animator.blendTime > 0f) {
                    // During blending: blend between previous and new animation
                    animator.blendTime -= dt
                    val alpha = 1f - (animator.blendTime / animator.blendDuration)

                    animator.previousAnimation?.let { prev ->
                        // Update previous animation time
                        animator.previousTime += dt

                        // Apply previous animation to skeleton and snapshot
                        prev.update(animator.previousTime, skeleton)
                        val prevPose = skeleton.getAllBones().map { bone ->
                            if (bone.index < pose.localTransforms.size) Matrix4f(bone.localTransform)
                            else Matrix4f()
                        }

                        // Apply new animation to skeleton and snapshot
                        animation.update(animator.currentTime, skeleton)
                        val newPose = skeleton.getAllBones().map { bone ->
                            if (bone.index < pose.localTransforms.size) Matrix4f(bone.localTransform)
                            else Matrix4f()
                        }

                        // Blend between the two poses
                        skeleton.getAllBones().forEach { bone ->
                            if (bone.index in 0 until pose.localTransforms.size) {
                                val idx = bone.index
                                blendTransforms(bone.localTransform, prevPose[idx], newPose[idx], alpha)
                            }
                        }
                    } ?: animation.update(animator.currentTime, skeleton)
                } else {
                    animation.update(animator.currentTime, skeleton)
                }

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
        SkeletonMath.buildSkinMatrices(pose, ago.skeleton.matrixPalette)
    }

    /**
     * Blends between two transforms using linear interpolation for position/scale
     * and spherical linear interpolation for rotation.
     */
    private fun blendTransforms(
        out: Matrix4f,
        from: Matrix4f,
        to: Matrix4f,
        alpha: Float
    ) {
        val t1 = Vector3f()
        val r1 = Quaternionf()
        val s1 = Vector3f()
        from.getTranslation(t1)
        from.getUnnormalizedRotation(r1)
        from.getScale(s1)

        val t2 = Vector3f()
        val r2 = Quaternionf()
        val s2 = Vector3f()
        to.getTranslation(t2)
        to.getUnnormalizedRotation(r2)
        to.getScale(s2)

        t1.lerp(t2, alpha)
        r1.slerp(r2, alpha)
        s1.lerp(s2, alpha)

        out.translationRotateScale(t1, r1, s1)
    }

    override fun invalidateCache() {
        cache.clear()
        cacheDirty = true
    }

    override fun rebuildCache() {
        cache.clear()
        scene.children.forEach { go ->
            val animator = go.getComponent<Animator>() ?: return@forEach
            val skeleton = go.getComponent<SkeletonComponent>() ?: return@forEach
            cache.add(AnimatedGameObject(animator, skeleton))
        }
    }

    data class AnimatedGameObject(val animator: Animator, val skeleton: SkeletonComponent)
}
