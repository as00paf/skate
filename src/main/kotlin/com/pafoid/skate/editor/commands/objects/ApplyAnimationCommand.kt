package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.removeComponent

/**
 * Adds [newAnimation] to the object's Animator, creating the Animator when there is none. Undo removes the clip, and
 * the Animator too when this command created it (EDT-10). A refused clip (no skeleton, incompatible, already there)
 * is not recorded in the history.
 */
class ApplyAnimationCommand(
    private val gameObject: GameObject,
    private val newAnimation: Animation,
    private val logger: LoggerService
) : Command, ExecutionTrackedCommand {
    private var succeeded = false
    private var createdAnimator: Animator? = null

    override fun execute() {
        succeeded = false
        createdAnimator?.let { created ->
            gameObject.addComponent(created)
            succeeded = true
            return
        }
        val skeleton = gameObject.getComponent<SkeletonComponent>()?.pose?.skeleton
        val existing = gameObject.getComponent<Animator>()
        val validator = existing ?: Animator(mutableListOf())
        when {
            skeleton == null -> logger.log(
                "Animation '${newAnimation.name}' cannot be added because ${gameObject.name} does not currently have a skeleton.",
                LogLevel.ERROR
            )
            !validator.validateSkeletonCompatibility(skeleton, newAnimation) -> logger.log(
                "Animation '${newAnimation.name}' is incompatible with the current skeleton. Skipping.",
                LogLevel.ERROR
            )
            existing?.animations?.any { it.name == newAnimation.name } == true -> logger.log(
                "'${gameObject.name}' already contains '${newAnimation.name}' animation.",
                LogLevel.ERROR
            )
            existing != null -> {
                existing.animations.add(newAnimation)
                succeeded = true
            }
            else -> {
                val animator = Animator(mutableListOf(newAnimation))
                gameObject.addComponent(animator)
                createdAnimator = animator
                succeeded = true
            }
        }
        if (succeeded) logger.log("Animation '${newAnimation.name}' added successfully!", LogLevel.ACTION)
    }

    override fun undo() {
        val created = createdAnimator
        if (created != null) gameObject.removeComponent(created)
        else gameObject.getComponent<Animator>()?.animations?.remove(newAnimation)
    }

    override fun wasSuccessful(): Boolean = succeeded

    override fun getDisplayName(): String = "Apply Animation"
    override fun getTargetName(): String = gameObject.name
}
