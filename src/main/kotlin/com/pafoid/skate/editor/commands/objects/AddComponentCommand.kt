package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.SceneComponent
import com.pafoid.skate.engine.getComponent

class AddComponentCommand(
    private val gameObject: GameObject,
    private val componentType: ComponentType,
    private val logger: LoggerService
) : Command {
    private var addedComponent: Component? = null
    private var replacedComponent: Component? = null

    override fun execute() {
        val component = componentType.instantiate() ?: return

        if ((gameObject is Scene && component !is SceneComponent) ||
            (gameObject !is Scene && component is SceneComponent)
        ) {
            logger.log("Cannot add $component to object ${gameObject.name}")
            return
        }

        replacedComponent = gameObject.getComponent(componentType)
        replacedComponent?.let { gameObject.components.remove(it) }
        gameObject.components.add(component)
        component.init(gameObject)
        addedComponent = component
    }

    override fun undo() {
        addedComponent?.let { gameObject.components.remove(it) }
        replacedComponent?.let {
            gameObject.components.add(it)
            it.init(gameObject)
        }
    }

    override fun getDisplayName(): String = "Add Component"

    override fun getTargetName(): String = gameObject.name

}
