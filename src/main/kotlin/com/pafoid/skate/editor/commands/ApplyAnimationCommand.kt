package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.events.AnimationApplied
import com.pafoid.skate.engine.events.AnimationRemoved

class ApplyAnimationCommand(
    private val gameObject: GameObject,
    private val oldAnimationPath: String?,
    private val newAnimationPath: String,
    private val resourceManager: ResourceManager,
    private val eventSystem: EventSystem
) : Command {
    override fun execute() {
        val animator = gameObject.getComponent<Animator>()
        animator?.let { anim ->
            val animation = resourceManager.getAnimation(newAnimationPath)
            animation?.let {
                anim.addAnimation(it)
                eventSystem.publish(AnimationApplied(gameObject, newAnimationPath))
            }
        }
    }

    override fun undo() {
        // Animation undo is complex - for now we just log
        // A full implementation would require tracking which animation was added
        eventSystem.publish(AnimationRemoved(gameObject, newAnimationPath))
    }

    override fun getDisplayName(): String = "Apply Animation"
    override fun getTargetName(): String? = gameObject.name
}