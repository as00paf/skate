package com.pafoid.skate.editor.animation

import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.SkeletonComponent

/**
 * What the Animator inspector's clip list, Play, Stop, Reset and timeline do (ANI-24, ANI-25), by mode:
 * - game not running: Play animates the object in the scene view ([Animator.editPreview]);
 * - game running with a controller: Play/Pause freezes the controller ([Animator.controllerPaused]);
 * - game running without one: the clip's own [Animator.isPlaying].
 *
 * Picking a clip on an object with a controller (the skater) plays it **over** the controller
 * ([Animator.inspectorClip]): it wins over the controller and over gameplay, which keeps writing parameters and
 * requests into the controller while it waits. Stop hands back to the controller where it was; Reset hands back and
 * restarts it in its default state. Reset and scrubbing update the pose at once, even while paused. No ImGui:
 * unit-tested.
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

    /** Plays [clip] from its start (over the controller when there is one) and starts playback. */
    fun selectClip(animator: Animator, clip: Animation, skeleton: SkeletonComponent?, running: Boolean) {
        if (animator.stateMachine != null) {
            animator.inspectorClip = clip
            animator.inspectorTime = 0f
            if (running) animator.controllerPaused = false
        } else {
            animator.play(clip, 0f)
            animator.currentTime = 0f
        }
        if (!running) animator.editPreview = true
        skeleton?.let(animator::applyPose)
    }

    /** True while the inspector's clip plays over the controller. */
    fun overridesController(animator: Animator): Boolean = animator.inspectorClip != null

    /** Hands the pose back to the controller, which carries on from where it was. */
    fun stop(animator: Animator, skeleton: SkeletonComponent?) {
        animator.inspectorClip = null
        animator.inspectorTime = 0f
        skeleton?.let(animator::applyPose)
    }

    /** Back to the start: hands back to the controller and restarts it in its default state; the clip to 0 s. */
    fun reset(animator: Animator, skeleton: SkeletonComponent?) {
        animator.inspectorClip = null
        animator.inspectorTime = 0f
        animator.stateMachine?.reset()
        animator.currentTime = 0f
        animator.blendTime = 0f
        animator.previousAnimation = null
        skeleton?.let(animator::applyPose)
    }

    /** The clip the timeline shows: the inspector's clip over a controller, else the selected clip without one. */
    fun timelineClip(animator: Animator): Animation? = when {
        animator.inspectorClip != null -> animator.inspectorClip
        animator.stateMachine == null -> animator.currentAnimation
        else -> null
    }

    fun timelineTime(animator: Animator): Float = if (animator.inspectorClip != null) animator.inspectorTime else animator.currentTime

    /** Shows the timeline clip at [time] and pauses (scrubbing is for precision). */
    fun scrub(animator: Animator, skeleton: SkeletonComponent?, time: Float, running: Boolean) {
        if (animator.inspectorClip != null) {
            animator.inspectorTime = time
            if (running) animator.controllerPaused = true
        } else {
            animator.currentTime = time
            if (running) animator.isPlaying = false
        }
        if (!running) animator.editPreview = false
        skeleton?.let(animator::applyPose)
    }

    /** The clip timeline is shown when it drives the pose (no controller, or a clip played over it). */
    fun showsClipTimeline(animator: Animator): Boolean = timelineClip(animator) != null
}
