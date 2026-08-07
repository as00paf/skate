package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.components.Component

class RenameComponentCommand(
    private val component: Component,
    private val newName: String,
    private val oldName: String
) : Command {
    override fun execute() {
        component.name = newName
    }

    override fun undo() {
        component.name = oldName
    }

    override fun getDisplayName(): String = "Rename ${component.name}"
    override fun getTargetName(): String? = component.name
}
