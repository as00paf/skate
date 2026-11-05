package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.ReportsFailure
import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator

/**
 * Assigns (or with null, removes) the Animator's controller asset and rebuilds its state machine. A controller that
 * fails to load (missing file, no skeleton, clips that do not match) is reported in the error dialog; the Animator
 * keeps its previous controller and nothing is added to the history (EDT-10).
 */
class SetAnimatorControllerCommand(
    private val animator: Animator,
    private val path: String?,
    private val logger: LoggerService,
) : ReportsFailure {
    override val errorTitleKey = "error.animator_controller.title"
    private var succeeded = true
    private val before = animator.controllerPath

    val isNoOp: Boolean get() = before == path

    override fun execute() {
        succeeded = apply(path)
        if (!succeeded) apply(before)
    }

    override fun undo() {
        apply(before)
    }

    private fun apply(p: String?): Boolean {
        if (p == null) {
            animator.clearController()
            return true
        }
        return animator.loadController(p, logger)
    }

    override fun wasSuccessful(): Boolean = succeeded
    override fun getFailureReason(): String? = path

    override fun getDisplayName(): String = "Set Animator Controller"
    override fun getTargetName(): String? = animator.gameObject?.name
}
