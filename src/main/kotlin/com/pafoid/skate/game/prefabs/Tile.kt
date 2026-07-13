package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.ModularTile
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import org.joml.Vector3f
import java.io.File

class Tile(
    name: String,
    model: TexturedModel,
    position: Vector3f = Vector3f(0f, 0f, 0f),
    tileCount: Int = 1,
    size: Int = 10,
): GameObject(name) {

    init {
        val transform = Transform(position, Vector3f(1f * size, 0.1f, 1f * size))
        addComponent(transform)
        addComponent(
            RenderComponent(
                model = model,
                modelGuid = File(model.path).absolutePath,
                albedoTextureGuid = model.mesh[0].material.baseColorPath.orEmpty(),
                textureScale = 3f * size / 2,
                castShadow = false,  // Floor doesn't cast shadows (too large)
                receiveShadow = true  // But receives shadows from objects
            )
        )
        addComponent(ModularTile(tileCount))
        addComponent(RigidBody3D(0f).apply { bodyType = BodyType.Static })
        addComponent(BoxCollider3D(Vector3f(1f, 1f, 1f)))
    }

}