package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.editor.data.PrimitiveType
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.Material
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
    scene: Scene,
    gameObjectManager: GameObjectManager,
    private val assetsManager: AssetsManager
) : AddBuiltObjectCommand(scene, gameObjectManager) {
    override fun build(): GameObject {
        val primitive = GameObject(name)
        val transform = Transform()
        transform.translation.set(0f, 1f, 0f)
        transform.scale.set(halfExtents)
        primitive.addComponent(transform)
        val material = Material(baseColorFactor = 1f)
        val baseModel = when (type) {
            PrimitiveType.PLANE -> assetsManager.loadModel(EngineDefaults.Models.CUBE)
            PrimitiveType.CUBE -> assetsManager.loadModel(EngineDefaults.Models.CUBE)
            PrimitiveType.CYLINDER -> assetsManager.loadModel(EngineDefaults.Models.CYLINDER)
            PrimitiveType.SPHERE -> assetsManager.loadModel(EngineDefaults.Models.SPHERE)
        }
        val model = `3dModel`(path = baseModel.path, mesh = baseModel.mesh)
        val renderComponent = RenderComponent(model, material = material)
        primitive.addComponent(renderComponent)
        return primitive
    }

    override fun getDisplayName(): String = "Create $name"
    override fun getTargetName(): String = name
}
