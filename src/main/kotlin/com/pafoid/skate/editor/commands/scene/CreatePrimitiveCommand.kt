package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.data.PrimitiveType
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import org.joml.Vector3f

class CreatePrimitiveCommand(
    private val name: String,
    private val type: PrimitiveType,
    private val halfExtents: Vector3f,
    private val scene: Scene,
    private val gameObjectManager: GameObjectManager,
    private val assetsManager: AssetsManager
) : ExecuteOnlyCommand {
    override fun execute() {
        val primitive = GameObject(name)
        val transform = Transform()
        transform.translation.set(0f, 1f, 0f)
        transform.scale.set(halfExtents)
        primitive.addComponent(transform)

        val texture = assetsManager.getTexture(Assets.Bundled.WOOD_BROWN)
        val baseModel = when (type) {
            PrimitiveType.PLANE -> assetsManager.loadModel(Assets.Bundled.CUBE)
            PrimitiveType.CUBE -> assetsManager.loadModel(Assets.Bundled.CUBE)
            PrimitiveType.CYLINDER -> assetsManager.loadModel(Assets.Bundled.CYLINDER)
            PrimitiveType.SPHERE -> assetsManager.loadModel(Assets.Bundled.SPHERE)
        }
        val model = TexturedModel(
            path = baseModel.path,
            mesh = baseModel.mesh.map { it.copy(material = Material(texture)) }
        )
        val renderComponent =
            RenderComponent(model, albedoTextureGuid = model.mesh[0].material.baseColorTexture?.filePath.orEmpty())
        primitive.addComponent(renderComponent)

        gameObjectManager.addGameObject(primitive)
        scene.selectedGameObject = primitive
    }

    override fun undo() {
    }

    override fun getDisplayName(): String = "Create $name"
    override fun getTargetName(): String = name
}
