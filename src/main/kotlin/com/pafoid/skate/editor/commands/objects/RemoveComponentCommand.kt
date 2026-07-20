package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.getComponent

class RemoveComponentCommand(
    private val gameObject: GameObject,
    private val componentType: ComponentType,
) : Command {
    private var removedComponent: Component? = null

    override fun execute() {
        removedComponent = gameObject.getComponent(componentType)
        removedComponent?.let { gameObject.components.remove(it) }
    }

    override fun undo() {
        removedComponent?.let {
            gameObject.components.add(it)
            it.init(gameObject)
        }
    }

    override fun getDisplayName(): String = "Remove Component"

    override fun getTargetName(): String = gameObject.name
}
