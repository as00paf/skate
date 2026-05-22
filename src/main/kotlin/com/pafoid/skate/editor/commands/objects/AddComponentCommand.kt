package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.CylinderCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D

class AddComponentCommand(
    private val gameObject: GameObject,
    private val componentType: ComponentType,
) : Command {
    private var addedComponent: Component? = null
    private var replacedComponent: Component? = null

    override fun execute() {
        val component = createComponent(componentType) ?: return
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

    // TODO: move?
    private fun createComponent(type: ComponentType): Component? =
        when (type) {
            ComponentType.AUDIO -> AudioComponent()
            ComponentType.BOX_COLLIDER_3D -> BoxCollider3D()
            ComponentType.CYLINDER_COLLIDER_3D -> CylinderCollider3D()
            ComponentType.RENDER -> RenderComponent()
            ComponentType.RIGID_BODY_3D -> RigidBody3D(1f)
            ComponentType.TRANSFORM -> Transform()
        }
}
