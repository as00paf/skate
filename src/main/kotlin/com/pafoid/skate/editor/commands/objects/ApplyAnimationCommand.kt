package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.getComponent

class ApplyAnimationCommand(
    private val gameObject: GameObject,
    private val newAnimation: Animation,
    private val eventSystem: EventSystem
) : ExecuteOnlyCommand {
    override fun execute() {
        val animator = gameObject.getComponent<Animator>()
        animator?.let { anim ->
            anim.addAnimation(newAnimation)
            eventSystem.publish(ViewportAction.AnimationApplied(gameObject, newAnimation))
        }
    }

    override fun undo() {
        // Not supported for now - animation stack restoration is not yet implemented.
        eventSystem.publish(ViewportAction.AnimationRemoved(gameObject, newAnimation))
    }

    override fun getDisplayName(): String = "Apply Animation"
    override fun getTargetName(): String? = gameObject.name
}

