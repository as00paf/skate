package com.pafoid.skate.engine.ecs.components.helpers

import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator

/**
 * Helper for managing animation assignments in Animator component with path tracking.
 * Ensures that animationPaths is always kept in sync with loaded animations.
 */
class AnimatorHelper(
    private val logger: LoggerService
) {

    /**
     * Adds an animation to an Animator and ensures its path is populated.
     * This ensures animationPaths stays in sync with the animations list.
     */
    fun addAnimationWithPath(
        animator: Animator,
        animation: Animation
    ) {
        // Add animation to animator (this validates skeleton compatibility)
        animator.addAnimation(animation)

        // Ensure path is populated for serialization
        if (animation.path.isNotBlank() && !animator.animationPaths.contains(animation.path)) {
            animator.animationPaths.add(animation.path)
        }
    }

    /**
     * Adds multiple animations to an Animator with path tracking.
     */
    fun addAnimationsWithPaths(
        animator: Animator,
        animations: List<Animation>
    ) {
        animations.forEach { addAnimationWithPath(animator, it) }
    }

    /**
     * Ensures all loaded animations have their paths populated in animator.animationPaths.
     * Useful for fixing up animations loaded directly without using this helper.
     */
    fun syncAnimationPaths(animator: Animator) {
        animator.getLoadedAnimations().forEach { animation ->
            if (animation.path.isNotBlank() && !animator.animationPaths.contains(animation.path)) {
                animator.animationPaths.add(animation.path)
            }
        }
    }

    /**
     * Clears invalid animation paths (paths that don't correspond to loaded animations).
     */
    fun cleanupInvalidPaths(animator: Animator) {
        val loadedAnimationPaths = animator.getLoadedAnimations()
            .mapNotNull { it.path }
            .filter { it.isNotBlank() }
            .toSet()

        val toRemove = animator.animationPaths.filter { it !in loadedAnimationPaths }
        if (toRemove.isNotEmpty()) {
            animator.animationPaths.removeAll(toRemove)
        }
    }
}
