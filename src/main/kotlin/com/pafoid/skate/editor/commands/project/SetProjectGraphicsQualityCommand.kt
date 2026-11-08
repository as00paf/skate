package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.settings.GraphicsQuality

/**
 * Sets the overall graphics quality preset for [project] (REN-30); undo restores the previous one.
 */
class SetProjectGraphicsQualityCommand(private val project: Project, private val quality: GraphicsQuality) : Command {
    private val before = project.graphicsQuality

    val isNoOp: Boolean get() = before == quality

    override fun execute() {
        project.graphicsQuality = quality
    }

    override fun undo() {
        project.graphicsQuality = before
    }

    override fun getDisplayName(): String = "Set Graphics Quality"
    override fun getTargetName(): String = project.name
}
