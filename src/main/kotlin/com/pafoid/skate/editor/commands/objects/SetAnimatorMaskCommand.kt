package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.animation.graph.AnimatorMaskDef
import com.pafoid.skate.engine.ecs.components.Animator

/** Changes which bones the Animator's controller drives. */
class SetAnimatorMaskCommand(private val animator: Animator, private val mask: AnimatorMaskDef) : Command {
    private val before = animator.mask

    val isNoOp: Boolean get() = before == mask

    override fun execute() { animator.mask = mask }
    override fun undo() { animator.mask = before }
    override fun getDisplayName(): String = "Set Animator Mask"
    override fun getTargetName(): String? = animator.gameObject?.name
}
