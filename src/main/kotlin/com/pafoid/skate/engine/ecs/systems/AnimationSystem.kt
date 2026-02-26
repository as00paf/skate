package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.utils.SkeletonMath
import imgui.ImGui
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * System responsible for updating skeletal animations on animated GameObjects.
 *
 * Maintains a cached list of eligible GameObjects (those with both SkeletonComponent
 * and Animator) to avoid O(n) filtering every frame.
 *
 * @param stringManager String manager for localized UI strings
 */
class AnimationSystem(
    private val stringManager: StringManager
) : System() {

    // Cached list of GameObjects eligible for animation updates
    private val animatedObjects = mutableListOf<GameObject>()
    private var cacheDirty = false

    override fun init(scene: Scene) {
        super.init(scene)
        // Initial population of cache
        rebuildCache()
        cacheDirty = false
    }

    override fun start() {
        // Listen for GameObject additions/removals to invalidate cache
        // Note: For a more robust solution, we'd listen for component additions too
        cacheDirty = true
    }

    override fun update(dt: Float) {
        if (cacheDirty) rebuildCache()

        for (go in animatedObjects) {
            val animator = go.getComponent<Animator>()
            val skeletonComponent = go.getComponent<SkeletonComponent>()

            if (animator != null && skeletonComponent != null) {
                updateAnimation(animator, skeletonComponent, dt)
            }
        }
    }

    override fun editorUpdate(dt: Float) {
        if (cacheDirty) rebuildCache()

        for (go in animatedObjects) {
            val animator = go.getComponent<Animator>()
            val skeletonComponent = go.getComponent<SkeletonComponent>()

            if (animator != null && skeletonComponent != null) {
                updateAnimation(animator, skeletonComponent, dt)
            }
        }
    }

    /**
     * Rebuilds the cache of animated GameObjects.
     * This is an O(n) operation but only called when the cache is dirty.
     */
    private fun rebuildCache() {
        animatedObjects.clear()

        for (go in scene.gameObjectManager.gameObjects) {
            if (go.hasComponent<SkeletonComponent>() && go.hasComponent<Animator>()) {
                animatedObjects.add(go)
            }
        }
    }

    /**
     * Invalidates the animated objects cache, forcing a rebuild on next update.
     * Call this when GameObjects or components are added/removed.
     */
    fun invalidateCache() {
        cacheDirty = true
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
        SkeletonMath.buildSkinMatrices(pose, skeletonComponent.getMatrixPalette())
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
        val t1 = Vector3f();
        val r1 = Quaternionf();
        val s1 = Vector3f()
        from.getTranslation(t1)
        from.getUnnormalizedRotation(r1)
        from.getScale(s1)

        val t2 = Vector3f();
        val r2 = Quaternionf();
        val s2 = Vector3f()
        to.getTranslation(t2)
        to.getUnnormalizedRotation(r2)
        to.getScale(s2)

        t1.lerp(t2, alpha)
        r1.slerp(r2, alpha)
        s1.lerp(s2, alpha)

        out.translationRotateScale(t1, r1, s1)
    }

    /**
     * Global animation speed multiplier for all animations.
     * Useful for slow-motion or time-scale effects.
     */
    var globalSpeedMultiplier: Float = 1.0f

    /**
     * Renders ImGui interface for debugging and tuning animations.
     *
     * ## Controls
     *
     * - Animated object count display
     * - Per-object animation state (name, time, playing state)
     * - Global speed multiplier
     * - Cache statistics
     */
    override fun imgui() {
        ImGui.text(stringManager.getString("lbl.animation_system.animated_objects", animatedObjects.size))
        ImGui.text(stringManager.getString("lbl.animation_system.cache_dirty", cacheDirty))

        ImGui.separator()

        // Global speed multiplier
        val speedArr = floatArrayOf(globalSpeedMultiplier)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.animation_system.global_speed_multiplier"),
                speedArr,
                0.1f,
                0f,
                3f,
                "%.2f"
            )
        ) {
            globalSpeedMultiplier = speedArr[0].coerceIn(0f, 3f)
        }

        ImGui.separator()
        ImGui.text(stringManager.getString("lbl.animation_system.per_object_state"))

        // Show each animated object's state
        animatedObjects.forEach { go ->
            val animator = go.getComponent<Animator>()
            val skeletonComponent = go.getComponent<SkeletonComponent>()

            if (animator != null && skeletonComponent != null) {
                val goName = go.name
                val currentAnim = animator.currentAnimation
                val isPlaying = animator.isPlaying
                val currentTime = animator.currentTime
                val duration = animator.duration
                val blendTime = animator.blendTime

                ImGui.text("$goName:")
                ImGui.indent()

                if (currentAnim != null) {
                    ImGui.text(stringManager.getString("lbl.animation_system.animation", currentAnim.name))
                    ImGui.text(stringManager.getString("lbl.animation_system.time", currentTime, duration))
                    ImGui.text(stringManager.getString("lbl.animation_system.playing", isPlaying))

                    if (blendTime > 0f) {
                        ImGui.text(stringManager.getString("lbl.animation_system.blending", blendTime))
                    }
                } else {
                    ImGui.text(stringManager.getString("lbl.animation_system.no_animation"))
                }

                ImGui.unindent()
            }
        }

        if (animatedObjects.isEmpty()) {
            ImGui.text(stringManager.getString("lbl.animation_system.no_objects"))
        }
    }
}