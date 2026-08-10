package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.removeComponent

class RemoveComponentCommand(
    private val gameObject: GameObject,
    private val component: Component,
) : Command {
    override fun execute() {
        gameObject.removeComponent(component)
    }

    override fun undo() {
        gameObject.addComponent(component)
    }

    override fun getDisplayName(): String = "Remove Component"

    override fun getTargetName(): String = gameObject.name
}
