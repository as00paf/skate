package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.BoneNameMapper
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.game.player.PlayerState
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File

@Serializable
data class Animator(
    val animations: MutableList<Animation> = mutableListOf(),
    @Transient var currentAnimation: Animation? = null,
    @Transient var previousAnimation: Animation? = null,
    var currentTime: Float = 0f,
    var isPlaying: Boolean = false,
    var blendTime: Float = 0f,
    var blendDuration: Float = 0.2f,
    var previousTime: Float = 0f,
) : Component() {
    // Event-driven state
    var isMoving = false
    var isSprinting = false
    var isInAir = false
    var isGrounded = true

    // Track current animation state to avoid redundant play() calls
    private var currentState: AnimationState = AnimationState.IDLE

    val normalizedTime: Float
        get() {
            val anim = currentAnimation ?: return 0f
            return (currentTime % anim.duration) / anim.duration
        }

    fun addAnimation(animation: Animation): Boolean {
        val skeleton = gameObject.getComponent<SkeletonComponent>()?.pose?.skeleton ?: return false
        val isValid = validateSkeletonCompatibility(skeleton, animation)
        val alreadyHasAnimation = animations.any { it.name == animation.name }

        if (!isValid || alreadyHasAnimation) return false
        animations.add(animation)
        return true
    }

    /**
     * Rebuilds the animations list from serialized file paths.
     * Called by SceneSerializer after scene deserialization.
     */
    fun resolveAnimationsFromPaths(assetsManager: AssetsManager, logger: LoggerService) {
        val skeleton = gameObject.getComponent<SkeletonComponent>()?.pose?.skeleton ?: return
        val animationPaths = animations.map { it.path }
        animations.clear()
        currentAnimation = null

        for (path in animationPaths) {
            val file = File(path)
            if (file.exists()) {
                try {
                    val animation = assetsManager.loadAnimationSync(path, skeleton)
                    animations.add(animation)
                } catch (e: Exception) {
                    logger.log("Failed to load animation from path: $path - ${e.message}", LogLevel.ERROR)
                }
            }
        }

        if (animations.isNotEmpty()) {
            currentAnimation = animations.first()
        }
    }

    /**
     * Starts playing a new animation with an optional [blend] time for smooth transitions.
     *
     * @param animation The animation to play.
     * @param blend Transition duration in seconds.
     */
    fun play(animation: Animation, blend: Float = 0.2f) {
        if (currentAnimation == animation) return

        previousAnimation = currentAnimation
        previousTime = currentTime
        blendTime = blend
        blendDuration = blend

        currentAnimation = animation
        currentTime = 0f
        isPlaying = true
    }

    fun play(name: String, blend: Float = 1f) {
        val anim = animations.find { it.name.contains(name, ignoreCase = true) } ?: return
        play(anim, blend)
    }

    enum class AnimationState {
        IDLE,
        WALK,
        RUN,
        JUMP,
        FALLING,
        LANDING
    }

    // TODO: fix double implementation
    override fun update(dt: Float) {
        // Determine target state based on event-driven state
        val targetState = when {
            isInAir && !isGrounded -> AnimationState.FALLING
            isSprinting -> AnimationState.RUN
            isMoving -> AnimationState.WALK
            else -> AnimationState.IDLE
        }

        // Only play animation if state changed
        if (targetState != currentState) {
            currentState = targetState
            when (currentState) {
                AnimationState.FALLING -> play("falling idle")
                AnimationState.RUN -> play("running")
                AnimationState.WALK -> play("walking")
                AnimationState.IDLE -> play("idle")
                else -> {} // JUMP and LANDING are triggered by events
            }
        }

        // Fallback to PlayerStateManager if event system not available
        // This ensures animations work even if EventSystem isn't set up
        val stateManager = gameObject.getComponent<PlayerStateManager>()
        if (stateManager != null) {
            updateFromStateManager(stateManager)
        }
    }

    /**
     * Updates animation based on PlayerStateManager state (fallback method).
     */
    private fun updateFromStateManager(stateManager: PlayerStateManager) {
        val targetState = when (stateManager.currentState) {
            PlayerState.WALKING -> AnimationState.WALK
            PlayerState.RUNNING -> AnimationState.RUN
            PlayerState.JUMPING -> AnimationState.JUMP
            PlayerState.FALLING -> AnimationState.FALLING
            PlayerState.LANDING -> AnimationState.LANDING
            else -> AnimationState.IDLE
        }

        // Only play animation if state changed
        if (targetState != currentState) {
            currentState = targetState
            when (currentState) {
                AnimationState.WALK -> play("walking")
                AnimationState.RUN -> play("running")
                AnimationState.JUMP -> play("jump")
                AnimationState.FALLING -> play("falling idle")
                AnimationState.LANDING -> play("hard landing")
                AnimationState.IDLE -> play("idle")
            }
        }
    }

    fun validateSkeletonCompatibility(skeleton: Skeleton?, animation: Animation): Boolean {
        skeleton ?: return false
        // Check if all animation channels target bones that exist in the skeleton
        val missingBones = mutableListOf<String>()
        for (channel in animation.channels) {
            val boneName = BoneNameMapper.map(channel.targetNodeName)
            val bone = skeleton.getBoneByName(boneName)
            if (bone == null) {
                missingBones.add(boneName)
            }
        }

        if (missingBones.isNotEmpty()) {
            // Check if the missing bones are critical or just extra detail bones
            val criticalBones = missingBones.filter { isCriticalBone(it) }

            if (criticalBones.isNotEmpty()) {
                return false
            }
        }

        // Check if the skeleton has significantly more bones than the animation targets
        //val skeletonBoneNames = skeleton.getAllBones().map { it.name }.toSet()
        //val animationBoneNames = animation.channels.map { BoneNameMapper.map(it.targetNodeName) }.toSet()
        //val extraSkeletonBones = skeletonBoneNames - animationBoneNames

        // Only warn if there are many extra bones (indicating a major topology mismatch)
        //if (extraSkeletonBones.size > skeletonBoneNames.size * 0.5) { // More than 50% of bones unused
        //Skeleton has many bones not targeted by animation. This may indicate a topology mismatch.
        //}

        return true
    }

    private fun isCriticalBone(boneName: String): Boolean {
        // Define critical bones that are essential for basic animation
        val criticalBonePatterns = listOf(
            "hip", "hips", "pelvis", "spine", "chest", "neck", "head",
            "leftupperarm", "rightupperarm", "leftlowerarm", "rightlowerarm",
            "lefthand", "righthand", "leftthigh", "rightthigh",
            "leftcalf", "rightcalf", "leftfoot", "rightfoot"
        )

        val lowerName = boneName.lowercase()
        return criticalBonePatterns.any { lowerName.contains(it) }
    }
}