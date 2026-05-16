package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.CylinderCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D

class RemoveComponentCommand(
    private val gameObject: GameObject,
    private val componentType: ComponentType,
) : Command {
    private var removedComponent: Component? = null

    override fun execute() {
        removedComponent = when (componentType) {
            ComponentType.AUDIO -> gameObject.getComponent<AudioComponent>()
            ComponentType.BOX_COLLIDER_3D -> gameObject.getComponent<BoxCollider3D>()
            ComponentType.CYLINDER_COLLIDER_3D -> gameObject.getComponent<CylinderCollider3D>()
            ComponentType.RENDER -> gameObject.getComponent<RenderComponent>()
            ComponentType.RIGID_BODY_3D -> gameObject.getComponent<RigidBody3D>()
            ComponentType.TRANSFORM -> null
        }
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
