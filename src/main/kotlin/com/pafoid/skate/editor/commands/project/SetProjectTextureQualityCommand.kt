package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.settings.TextureQuality

/**
 * Sets the texture quality for [project] (REN-30); undo restores the previous value.
 */
class SetProjectTextureQualityCommand(private val project: Project, private val quality: TextureQuality) : Command {
    private val before = project.textureQuality

    val isNoOp: Boolean get() = before == quality

    override fun execute() {
        project.textureQuality = quality
    }

    override fun undo() {
        project.textureQuality = before
    }

    override fun getDisplayName(): String = "Set Texture Quality"
    override fun getTargetName(): String = project.name
}
