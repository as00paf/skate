package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.gameplay.CharacterBehavior
import com.pafoid.skate.engine.gameplay.CharacterMotion
import com.pafoid.skate.engine.gameplay.MountRequest

/**
 * How a [CharacterBehavior]'s motion reaches the [Animator] each frame (GPL-06). With a controller, a request (bool
 * parameter) goes out when the state changes, and the controller's transitions blend to it.
 * - **Blend times:** the behavior's [CharacterBehavior.animationBlendTimes] override the controller's fade times.
 *   Without a controller they set the crossfade of the clip.
 * - **Parameters:** the behavior writes its per-frame values (speed, air time...) every frame.
 */
object CharacterAnimationDriver {

    /** Crossfade used when a behavior names no blend time for a clip (single-clip playback only). */
    const val DEFAULT_BLEND = 0.2f

    /**
     * Applies [motion] from [behavior] to [animator] given the [previousState] the animator was last driven with,
     * and returns the state to remember. Mounting or dismounting swaps clip sets even when the state name stays the
     * same (on-foot IDLE -> riding IDLE), so a mount change always re-requests.
     */
    fun drive(behavior: CharacterBehavior, motion: CharacterMotion, previousState: String, animator: Animator?): String {
        if (animator == null) return motion.state
        val blendTimes = behavior.animationBlendTimes
        animator.stateMachine?.let { machine ->
            if (machine.blendOverrides != blendTimes) {
                machine.blendOverrides.clear()
                machine.blendOverrides.putAll(blendTimes)
            }
        }
        if (motion.state != previousState || motion.mount != MountRequest.NONE) {
            motion.animation?.let { clip -> animator.play(clip, blendTimes[clip] ?: DEFAULT_BLEND) }
        }
        animator.parameters?.let { behavior.writeAnimationParameters(it) }
        return motion.state
    }
}
