package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.project.Project

/**
 * Sets the MSAA sample count for [project] (REN-30); undo restores the previous value.
 */
class SetProjectMsaaSamplesCommand(private val project: Project, private val samples: Int) : Command {
    private val before = project.msaaSamples

    val isNoOp: Boolean get() = before == samples

    override fun execute() {
        project.msaaSamples = samples
    }

    override fun undo() {
        project.msaaSamples = before
    }

    override fun getDisplayName(): String = "Set MSAA Samples"
    override fun getTargetName(): String = project.name
}
