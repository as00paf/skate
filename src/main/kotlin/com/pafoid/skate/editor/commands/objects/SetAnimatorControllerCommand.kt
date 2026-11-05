package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator

/** Assigns (or with null, removes) the Animator's controller asset and rebuilds its state machine. */
class SetAnimatorControllerCommand(
    private val animator: Animator,
    private val path: String?,
    private val logger: LoggerService,
) : Command {
    private val before = animator.controllerPath

    val isNoOp: Boolean get() = before == path

    override fun execute() = apply(path)
    override fun undo() = apply(before)

    private fun apply(p: String?) {
        if (p == null) animator.clearController() else animator.loadController(p, logger)
    }

    override fun getDisplayName(): String = "Set Animator Controller"
    override fun getTargetName(): String? = animator.gameObject?.name
}
