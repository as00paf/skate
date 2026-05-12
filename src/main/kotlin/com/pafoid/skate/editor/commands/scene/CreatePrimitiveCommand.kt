package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import org.joml.Vector3f

class CreatePrimitiveCommand(
    private val name: String,
    private val halfExtents: Vector3f,
    private val scene: Scene,
    private val gameObjectManager: GameObjectManager,
) : Command {
    override fun execute() {
        val primitive = GameObject(name)
        val transform = Transform()
        transform.translation.set(0f, 1f, 0f)
        transform.scale.set(halfExtents)
        primitive.addComponent(transform)

        val renderComponent = RenderComponent()
        primitive.addComponent(renderComponent)

        gameObjectManager.addGameObject(primitive)
        scene.selectedGameObject = primitive
    }

    override fun undo() {
    }

    override fun getDisplayName(): String = "Create $name"
    override fun getTargetName(): String? = name
}
