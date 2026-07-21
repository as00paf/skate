package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.getComponent

class ApplyAnimationCommand(
    private val gameObject: GameObject,
    private val newAnimation: Animation,
    private val logger: LoggerService
) : ExecuteOnlyCommand {
    override fun execute() {
        val animator =
            gameObject.getComponent<Animator>() ?: gameObject.addComponent(Animator()).getComponent<Animator>()
        val skeleton = gameObject.getComponent<SkeletonComponent>()?.pose?.skeleton
        val isValid = animator?.validateSkeletonCompatibility(skeleton, newAnimation) == true
        val alreadyHasAnimation = animator?.animations?.any { it.name == newAnimation.name } == true

        when {
            skeleton == null -> logger.log(
                "Animation '${newAnimation.name}' cannot be added because ${gameObject.name} does not currently have a skeleton.",
                LogLevel.ERROR
            )

            !isValid -> logger.log(
                "Animation '${newAnimation.name}' is incompatible with the current skeleton. Skipping.",
                LogLevel.ERROR
            )

            alreadyHasAnimation -> logger.log(
                "'${gameObject.name}' already contains '${newAnimation.name}' animation.",
                LogLevel.ERROR
            )

            else -> {
                if (animator.animations.add(newAnimation)) {
                    logger.log("Animation '${newAnimation.name}' added successfully!", LogLevel.ACTION)
                }
            }
        }
    }

    override fun undo() {
        if (gameObject.getComponent<Animator>()?.animations?.remove(newAnimation) == true) {
            logger.log("Animation '${newAnimation.name}' removed successfully!", LogLevel.ACTION)
        }
    }

    override fun getDisplayName(): String = "Apply Animation"
    override fun getTargetName(): String? = gameObject.name
}

