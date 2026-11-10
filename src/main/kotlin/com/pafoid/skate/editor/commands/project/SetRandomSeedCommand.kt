package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.project.Project

/**
 * Sets the project's random seed (ECS-14); undo restores the previous one. [apply] pushes the seed to the running
 * engine so Play uses it right away. Saving the project writes it to the `.skateproject`.
 */
class SetRandomSeedCommand(
    private val project: Project,
    private val seed: Long,
    private val apply: (Long) -> Unit = {},
) : Command {
    private val before = project.randomSeed

    val isNoOp: Boolean get() = before == seed

    override fun execute() {
        project.randomSeed = seed
        apply(seed)
    }

    override fun undo() {
        project.randomSeed = before
        apply(before)
    }

    override fun getDisplayName(): String = "Set Random Seed"
    override fun getTargetName(): String = project.name
}
