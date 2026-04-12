package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import org.joml.Vector3f

class CreatePrimitiveCommand(
    private val name: String,
    private val halfExtents: Vector3f,
    private val scene: Scene
) : Command {
    override fun execute() {
        val primitive = GameObject(name)
        val transform = Transform()
        transform.translation.set(0f, 1f, 0f)
        transform.scale.set(halfExtents)
        primitive.addComponent(transform)

        val renderComponent = RenderComponent()
        primitive.addComponent(renderComponent)

        scene.gameObjectManager.addGameObject(primitive)
        scene.gameObjectManager.setSelectedGameObject(primitive)
    }

    override fun undo() {
    }

    override fun getDisplayName(): String = "Create $name"
    override fun getTargetName(): String? = name
}
