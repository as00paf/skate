package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.BodyType
import org.joml.Vector3f

class Floor(
    name: String,
    model: `3dModel`,
    position: Vector3f = Vector3f(0f, 0f, 0f),
    size: Int = 10,
): GameObject(name) {

    init {
        addComponent(Transform(position, Vector3f(1f * size, 0.1f, 1f * size)))
        addComponent(
            RenderComponent(
                model = model,
                albedoTextureGuid = model.mesh[0].material.baseColorTexture?.filePath.orEmpty(),
                textureScale = 3f * size / 2,
                castShadow = false,  // Floor doesn't cast shadows (too large)
                receiveShadow = true  // But receives shadows from objects
            )
        )
        addComponent(RigidBody3D(0f, bodyType = BodyType.Static))
        addComponent(BoxCollider3D(Vector3f(1f, 1f, 1f)))
    }

}