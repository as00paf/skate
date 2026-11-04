package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.SceneComponent
import com.pafoid.skate.engine.attachComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.removeComponent
import com.pafoid.skate.engine.ui.UiRequirements

class AddComponentCommand(
    private val gameObject: GameObject,
    private val componentType: ComponentType,
    private val logger: LoggerService
) : Command {
    private var addedComponent: Component? = null
    private var replacedComponent: Component? = null
    /** Components added because [addedComponent] requires them (e.g. a UI rect and canvas); undone together. */
    private var requiredComponents: List<Component> = emptyList()

    override fun execute() {
        val component = componentType.instantiate() ?: return

        if ((gameObject is Scene && component !is SceneComponent) ||
            (gameObject !is Scene && component is SceneComponent)
        ) {
            logger.log("Cannot add $component to object ${gameObject.name}")
            return
        }

        replacedComponent = gameObject.getComponent(componentType)
        replacedComponent?.let { gameObject.removeComponent(it) }
        gameObject.attachComponent(component)
        component.generateId()
        component.init(gameObject)
        addedComponent = component
        requiredComponents = if (component.javaClass.packageName == UiRequirements::class.java.packageName) {
            UiRequirements.ensure(gameObject, component)
        } else {
            emptyList()
        }
        if (requiredComponents.isNotEmpty()) {
            logger.log("Added ${requiredComponents.joinToString { it.javaClass.simpleName }} required by ${component.javaClass.simpleName}")
        }
    }

    override fun undo() {
        requiredComponents.forEach { gameObject.removeComponent(it) }
        requiredComponents = emptyList()
        addedComponent?.let { gameObject.removeComponent(it) }
        replacedComponent?.let {
            gameObject.attachComponent(it)
            it.init(gameObject)
        }
    }

    override fun getDisplayName(): String = "Add Component"

    override fun getTargetName(): String = gameObject.name

}
