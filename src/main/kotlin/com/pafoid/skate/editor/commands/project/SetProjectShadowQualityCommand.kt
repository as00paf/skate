package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.settings.graphicsSettings
import com.pafoid.skate.editor.settings.ShadowQuality

/**
 * Sets the shadow quality for the exported game of [project] (REN-30); undo restores the previous value.
 */
class SetProjectShadowQualityCommand(private val project: Project, private val quality: ShadowQuality) : Command {
    private val before = project.shadowQuality

    val isNoOp: Boolean get() = before == quality

    override fun execute() {
        project.shadowQuality = quality
        project.graphicsSettings().applyToEngine()
    }

    override fun undo() {
        project.shadowQuality = before
        project.graphicsSettings().applyToEngine()
    }

    override fun getDisplayName(): String = "Set Shadow Quality"
    override fun getTargetName(): String = project.name
}
