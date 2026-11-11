package com.pafoid.skate.editor.animation

import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.SkeletonComponent

/**
 * What the Animator inspector's Play, Reset and timeline do (ANI-24), by mode:
 * - game not running: Play animates the object in the scene view ([Animator.editPreview]): its controller, else the
 *   selected clip;
 * - game running with a controller: Play/Pause freezes the controller ([Animator.controllerPaused]);
 * - game running without one: the clip's own [Animator.isPlaying].
 * Reset goes back to the start (the controller restarts in its default state) and scrubbing shows that time; both
 * update the pose at once, even while paused. No ImGui: unit-tested.
 */
object AnimatorControls {

    fun isPlaying(animator: Animator, running: Boolean): Boolean = when {
        !running -> animator.editPreview
        animator.stateMachine != null -> !animator.controllerPaused
        else -> animator.isPlaying
    }

    fun togglePlay(animator: Animator, running: Boolean) {
        when {
            !running -> animator.editPreview = !animator.editPreview
            animator.stateMachine != null -> animator.controllerPaused = !animator.controllerPaused
            else -> animator.isPlaying = !animator.isPlaying
        }
    }

    fun reset(animator: Animator, skeleton: SkeletonComponent?) {
        animator.stateMachine?.reset()
        animator.currentTime = 0f
        animator.blendTime = 0f
        animator.previousAnimation = null
        skeleton?.let(animator::applyPose)
    }

    /** Shows the selected clip at [time] and pauses it (scrubbing is for precision). */
    fun scrub(animator: Animator, skeleton: SkeletonComponent?, time: Float, running: Boolean) {
        animator.currentTime = time
        if (running) animator.isPlaying = false else animator.editPreview = false
        skeleton?.let(animator::applyPose)
    }

    /** The clip timeline only drives the pose without a controller (a controller picks its own clips). */
    fun showsClipTimeline(animator: Animator): Boolean = animator.stateMachine == null
}
