package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject

class VisibilityToggleCommand(
    private val gameObject: GameObject,
    private val newVisibility: Boolean
) : Command {
    private val oldVisibility = gameObject.isVisible

    override fun execute() {
        gameObject.isVisible = newVisibility
    }

    override fun undo() {
        gameObject.isVisible = oldVisibility
    }

    override fun getDisplayName(): String = "Toggle Visibility: ${gameObject.name}"
    override fun getTargetName(): String? = gameObject.name
}