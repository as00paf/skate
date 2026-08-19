package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.removeComponent

class RemoveComponentCommand(
    private val component: Component,
) : Command {
    override fun execute() {
        component.gameObject?.removeComponent(component)
    }

    override fun undo() {
        component.gameObject?.addComponent(component)
    }

    override fun getDisplayName(): String = "Remove Component"

    override fun getTargetName(): String? = component.gameObject?.name
}
