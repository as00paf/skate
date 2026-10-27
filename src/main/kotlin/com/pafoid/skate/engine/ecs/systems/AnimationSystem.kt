package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.animation.AnimationUpdateGate
import com.pafoid.skate.engine.animation.AnimationVisibility
import com.pafoid.skate.engine.animation.ik.IkPass
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.utils.SkeletonMath
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.IdentityHashMap

class AnimationSystem(
    /** Null disables visibility culling (everything updates every frame). */
    var visibility: AnimationVisibility? = null,
    /** Seconds between updates for off-screen characters; 0 disables the throttle. */
    var offscreenInterval: Float = AnimationUpdateGate.DEFAULT_OFFSCREEN_INTERVAL,
) : System(priority = ExecutionPriority.DEFAULT) {

    private val animated = query(Animator::class, SkeletonComponent::class, topLevelOnly = true)
    private val gates = IdentityHashMap<SkeletonComponent, AnimationUpdateGate>()
    private val tmpCenter = Vector3f()
    private val tmpScale = Vector3f()
    private val tmpWorldToModel = Matrix4f()

    override fun invalidateCache() {
        super.invalidateCache()
        gates.clear()
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        visibility?.beginFrame()

        var updated = 0
        var skipped = 0
        animated.forEach { go ->
            val ago = AnimatedGameObject(
                go.getComponent<Animator>() ?: return@forEach,
                go.getComponent<SkeletonComponent>() ?: return@forEach,
            )
            val gate = gates.getOrPut(ago.skeleton) { AnimationUpdateGate() }
            gate.offscreenInterval = offscreenInterval
            val step = gate.consume(isVisible(ago.skeleton), dt)
            if (step <= 0f && dt > 0f) {
                skipped++
                return@forEach
            }
            updated++
            // Update animator state first (selects animation based on state)
            ago.animator.update(step)
            // Then update the animation
            updateAnimation(ago, step)
        }
        EngineStats.animationsUpdated.set(updated)
        EngineStats.animationsSkipped.set(skipped)
    }

    private fun isVisible(skeleton: SkeletonComponent): Boolean {
        val test = visibility ?: return true
        val world = skeleton.gameObject?.getComponent<Transform>()?.worldMatrix ?: return true
        world.transformPosition(skeleton.boundsCenter, tmpCenter)
        world.getScale(tmpScale)
        val scale = maxOf(tmpScale.x, tmpScale.y, tmpScale.z)
        return test.isVisible(tmpCenter, skeleton.boundsRadius * scale)
    }

    private fun applyIk(ago: AnimatedGameObject, sourceFresh: Boolean) {
        val rig = ago.animator.ik
        if (!rig.isActive && rig.basePose == null) return
        val worldMatrix = ago.skeleton.gameObject?.getComponent<Transform>()?.worldMatrix
        val worldToModel = worldMatrix?.let { tmpWorldToModel.set(it).invert() }
        val pose = ago.skeleton.pose
        IkPass.run(rig, pose.skeleton, pose.localTransforms, worldToModel, sourceFresh)
    }

    private fun updateAnimation(ago: AnimatedGameObject, dt: Float) {
        val animator = ago.animator
        val pose = ago.skeleton.pose
        val skeleton = pose.skeleton

        val machine = animator.stateMachine
        if (machine != null) {
            machine.update(dt)
            val result = machine.pose
            for (i in 0 until minOf(result.boneCount, pose.localTransforms.size)) {
                result.toMatrix(i, pose.localTransforms[i])
            }
            applyIk(ago, sourceFresh = true)
            SkeletonMath.buildSkinMatrices(pose, ago.skeleton.matrixPalette)
            ago.skeleton.markPaletteDirty()
            return
        }

        var sourceFresh = false

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
                sourceFresh = true
            }
        }

        applyIk(ago, sourceFresh)

        // Apply global transforms and skin matrices (ALWAYS, even if not playing)
        SkeletonMath.computeGlobalTransforms(skeleton.rootBone, pose.localTransforms, pose.globalTransforms)
        SkeletonMath.buildSkinMatrices(pose, ago.skeleton.matrixPalette)
        ago.skeleton.markPaletteDirty()
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

    data class AnimatedGameObject(val animator: Animator, val skeleton: SkeletonComponent)
}
